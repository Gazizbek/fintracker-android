package kz.alem.corporation.fintracker.sync

import android.app.Activity
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kz.alem.corporation.fintracker.AppContainer

/**
 * Cloud-sync subscription (port of the iOS SubscriptionManager, StoreKit → Play Billing).
 *
 * Play Billing is the local source of truth. The backend is used for display metadata
 * (expiry, auto-renew) and for the cross-platform case: when Play has no purchase but the
 * backend reports an active, unexpired subscription (e.g. bought on iOS), access is
 * granted anyway. A backend "inactive" never revokes a locally confirmed purchase.
 */
class SubscriptionManager(private val container: AppContainer) {

    private val prefs get() = container.prefs

    private val _hasSubscription = MutableStateFlow(false)
    val hasSubscription: StateFlow<Boolean> = _hasSubscription.asStateFlow()

    private val _expiresAt = MutableStateFlow<Long?>(null)
    val expiresAt: StateFlow<Long?> = _expiresAt.asStateFlow()

    private val _autoRenewStatus = MutableStateFlow<Boolean?>(null)
    val autoRenewStatus: StateFlow<Boolean?> = _autoRenewStatus.asStateFlow()

    private val _monthlyProduct = MutableStateFlow<ProductDetails?>(null)
    val monthlyProduct: StateFlow<ProductDetails?> = _monthlyProduct.asStateFlow()

    private val _yearlyProduct = MutableStateFlow<ProductDetails?>(null)
    val yearlyProduct: StateFlow<ProductDetails?> = _yearlyProduct.asStateFlow()

    private val _selectedProductId = MutableStateFlow<String?>(null)
    val selectedProductId: StateFlow<String?> = _selectedProductId.asStateFlow()

    private val _isPurchasing = MutableStateFlow(false)
    val isPurchasing: StateFlow<Boolean> = _isPurchasing.asStateFlow()

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

    private val _purchaseError = MutableStateFlow<String?>(null)
    val purchaseError: StateFlow<String?> = _purchaseError.asStateFlow()

    private val _billingUnavailable = MutableStateFlow(false)
    val billingUnavailable: StateFlow<Boolean> = _billingUnavailable.asStateFlow()

    private var lastBackendSyncAt = 0L
    private val connectMutex = Mutex()

    private val billingClient: BillingClient = BillingClient.newBuilder(container.context)
        .setListener { result, purchases -> onPurchasesUpdated(result, purchases) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    init {
        // Show the cached state instantly so the UI doesn't flash "not subscribed".
        _hasSubscription.value = prefs.hasSubscription
        _expiresAt.value = prefs.subscriptionExpiresAt
        val expiry = _expiresAt.value
        if (expiry != null && expiry < System.currentTimeMillis()) {
            _hasSubscription.value = false
            prefs.hasSubscription = false
        }
        container.appScope.launch {
            loadProducts()
            checkLocalEntitlements()
            syncWithBackend()
        }
    }

    // region Billing connection

    private suspend fun ensureConnected(): Boolean = connectMutex.withLock {
        if (billingClient.isReady) return true
        val deferred = CompletableDeferred<Boolean>()
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                deferred.complete(result.responseCode == BillingClient.BillingResponseCode.OK)
            }

            override fun onBillingServiceDisconnected() {
                if (!deferred.isCompleted) deferred.complete(false)
            }
        })
        val ready = deferred.await()
        _billingUnavailable.value = !ready
        ready
    }

    // endregion

    // region Products

    suspend fun loadProducts() {
        if (!ensureConnected()) return
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(SyncConfig.ALL_PRODUCT_IDS.map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            })
            .build()
        val result = billingClient.queryProductDetails(params)
        val products = result.productDetailsList.orEmpty()
        _monthlyProduct.value = products.firstOrNull { it.productId == SyncConfig.MONTHLY_PRODUCT_ID }
        _yearlyProduct.value = products.firstOrNull { it.productId == SyncConfig.YEARLY_PRODUCT_ID }
        if (_selectedProductId.value == null) {
            _selectedProductId.value = (_yearlyProduct.value ?: _monthlyProduct.value)?.productId
        }
    }

    fun selectProduct(productId: String) {
        _selectedProductId.value = productId
    }

    // endregion

    // region Entitlements

    private suspend fun activePurchases(): List<Purchase>? {
        if (!ensureConnected()) return null
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        val result = billingClient.queryPurchasesAsync(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return null
        return result.purchasesList.filter { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                purchase.products.any { it in SyncConfig.ALL_PRODUCT_IDS }
        }
    }

    /**
     * Grants access when Play reports an active purchase. Finding nothing deliberately keeps
     * the cached state (fresh install / store hiccup); [syncWithBackend] reconciles.
     */
    suspend fun checkLocalEntitlements() {
        val purchases = activePurchases() ?: return
        val purchase = purchases.firstOrNull()
        if (purchase == null) {
            val expiry = _expiresAt.value
            if (expiry != null && expiry < System.currentTimeMillis()) applySubscriptionInactive()
            return
        }
        applySubscriptionActive(_expiresAt.value)
        acknowledgeIfNeeded(purchase)
    }

    /** Re-verifies the device's Play purchase under the signed-in uid. */
    suspend fun attachEntitlementToCurrentUser() {
        val purchase = activePurchases()?.firstOrNull() ?: return
        notifyBackend(purchase)
    }

    private suspend fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        billingClient.acknowledgePurchase(params)
    }

    // endregion

    // region Purchase

    suspend fun purchase(activity: Activity) {
        val product = listOfNotNull(_yearlyProduct.value, _monthlyProduct.value)
            .firstOrNull { it.productId == _selectedProductId.value }
            ?: _yearlyProduct.value ?: _monthlyProduct.value
            ?: return
        // A purchase must be attached to a Firebase uid for the backend verification.
        if (!container.authManager.isSignedIn) {
            try {
                container.authManager.signInAnonymously()
            } catch (e: Exception) {
                _purchaseError.value = e.localizedMessage
                return
            }
        }
        val offerToken = product.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return
        _purchaseError.value = null
        _isPurchasing.value = true
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            .build()
        val result = billingClient.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _isPurchasing.value = false
            if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
                _purchaseError.value = result.debugMessage.ifEmpty { null }
            }
        }
    }

    /** Real-time purchase updates (new purchases, renewals). */
    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        _isPurchasing.value = false
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> container.appScope.launch {
                purchases.orEmpty()
                    .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    .forEach { purchase ->
                        applySubscriptionActive(_expiresAt.value)
                        acknowledgeIfNeeded(purchase)
                        notifyBackend(purchase)
                    }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> _purchaseError.value = result.debugMessage.ifEmpty { null }
        }
    }

    suspend fun restorePurchases() {
        _isRestoring.value = true
        _purchaseError.value = null
        try {
            checkLocalEntitlements()
            syncWithBackend()
        } finally {
            _isRestoring.value = false
        }
    }

    // endregion

    // region Backend

    /** Re-checks entitlements when the app comes to the foreground, at most every 30 min. */
    fun checkOnForeground() {
        if (System.currentTimeMillis() - lastBackendSyncAt < FOREGROUND_COOLDOWN_MS) return
        container.appScope.launch {
            checkLocalEntitlements()
            syncWithBackend()
        }
    }

    /** Refreshes the subscription screen: entitlements plus backend display info. */
    suspend fun checkSubscriptionStatus() {
        checkLocalEntitlements()
        syncWithBackend()
    }

    /** Updates expiry/auto-renew from the backend; may grant (never revoke) access. */
    suspend fun syncWithBackend() {
        if (!container.authManager.isSignedIn) return
        try {
            val dto = apiCall { container.syncApi.getSubscription() }
            _autoRenewStatus.value = dto.autoRenewStatus
            val expiry = dto.expiresAt
            if (expiry != null) {
                if (_hasSubscription.value) {
                    _expiresAt.value = expiry
                    prefs.subscriptionExpiresAt = expiry
                } else if (dto.active && expiry > System.currentTimeMillis()) {
                    // No local Play purchase (e.g. bought on iOS) but the backend confirms it.
                    applySubscriptionActive(expiry)
                }
            }
            lastBackendSyncAt = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.w(TAG, "syncWithBackend failed", e)
        }
    }

    /** Lets the backend verify the purchase so sync access is granted to this uid. */
    private suspend fun notifyBackend(purchase: Purchase) {
        if (!container.authManager.isSignedIn) return
        val productId = purchase.products.firstOrNull { it in SyncConfig.ALL_PRODUCT_IDS } ?: return
        try {
            val dto = apiCall {
                container.syncApi.verifyAndroidSubscription(AndroidVerifyRequest(productId, purchase.purchaseToken))
            }
            dto.expiresAt?.let {
                _expiresAt.value = it
                prefs.subscriptionExpiresAt = it
            }
        } catch (e: Exception) {
            // Local entitlement already grants access; the backend is retried on next foreground.
            Log.w(TAG, "verifyAndroidSubscription failed", e)
        }
    }

    // endregion

    private fun applySubscriptionActive(expiresAt: Long?) {
        _hasSubscription.value = true
        prefs.hasSubscription = true
        _expiresAt.value = expiresAt
        if (expiresAt != null) prefs.subscriptionExpiresAt = expiresAt
    }

    private fun applySubscriptionInactive() {
        _hasSubscription.value = false
        _expiresAt.value = null
        prefs.hasSubscription = false
        prefs.subscriptionExpiresAt = null
    }

    private companion object {
        const val TAG = "Subscription"
        const val FOREGROUND_COOLDOWN_MS = 30 * 60 * 1000L
    }
}

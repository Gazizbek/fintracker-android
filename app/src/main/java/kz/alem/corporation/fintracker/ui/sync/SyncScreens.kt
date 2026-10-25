package kz.alem.corporation.fintracker.ui.sync

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.billingclient.api.ProductDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kz.alem.corporation.fintracker.R
import kz.alem.corporation.fintracker.sync.SignInCancelledException
import kz.alem.corporation.fintracker.sync.SyncConfig
import kz.alem.corporation.fintracker.ui.common.Formats
import kz.alem.corporation.fintracker.ui.common.LocalAppContainer
import kz.alem.corporation.fintracker.ui.common.appLocale
import kz.alem.corporation.fintracker.ui.navigation.AuthRoute
import kz.alem.corporation.fintracker.ui.navigation.LocalNavigator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloseTopBar(title: String) {
    val navigator = LocalNavigator.current
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = { navigator.back() }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close)) }
        },
    )
}

// region Auth

/** Sign in / sign up (iOS AuthScreen; Sign in with Apple has no Android counterpart). */
@Composable
fun AuthScreen() {
    val auth = LocalAppContainer.current.authManager
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user by auth.currentUser.collectAsState()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var isSignUp by rememberSaveable { mutableStateOf(false) }
    var isLoading by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val signInFailed = stringResource(R.string.signInFailed)

    fun run(action: suspend () -> Unit) {
        error = null
        isLoading = true
        scope.launch {
            try {
                action()
                navigator.back()
            } catch (e: CancellationException) {
                throw e
            } catch (_: SignInCancelledException) {
                // User closed the Google sheet; stay silent like iOS does for Apple.
            } catch (e: Exception) {
                error = e.localizedMessage ?: signInFailed
            }
            isLoading = false
        }
    }

    Scaffold(topBar = { CloseTopBar(stringResource(if (isSignUp) R.string.createAccount else R.string.signIn)) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { run { auth.signInWithGoogle(context) } },
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.signInWithGoogle), fontWeight = FontWeight.SemiBold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f))
                Text(stringResource(R.string.orDivider), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp))
                HorizontalDivider(Modifier.weight(1f))
            }
            if (user?.isAnonymous == true) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(12.dp)) {
                        Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.anonymousSignInNote), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it.trim() },
                label = { Text(stringResource(R.string.email)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Button(
                onClick = { run { if (isSignUp) auth.signUp(email, password) else auth.signIn(email, password) } },
                enabled = email.isNotEmpty() && password.isNotEmpty() && !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(if (isSignUp) R.string.createAccount else R.string.signIn), fontWeight = FontWeight.Bold)
                }
            }
            TextButton(
                onClick = {
                    isSignUp = !isSignUp
                    error = null
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(if (isSignUp) R.string.alreadyHaveAccount else R.string.noAccountYet)) }
        }
    }
}

// endregion

// region Subscription

/** "$4.99/month" from the product's base plan. */
@Composable
private fun priceWithPeriod(product: ProductDetails): String {
    val phase = product.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull() ?: return ""
    val period = when {
        'Y' in phase.billingPeriod -> R.string.subscriptionPeriodYear
        'M' in phase.billingPeriod -> R.string.subscriptionPeriodMonth
        'W' in phase.billingPeriod -> R.string.subscriptionPeriodWeek
        else -> R.string.subscriptionPeriodDay
    }
    return "${phase.formattedPrice}/${stringResource(period)}"
}

@Composable
fun SubscriptionScreen() {
    val container = LocalAppContainer.current
    val subscriptions = container.subscriptionManager
    val navigator = LocalNavigator.current
    val activity = checkNotNull(LocalActivity.current)
    val locale = appLocale
    val scope = rememberCoroutineScope()
    val hasSubscription by subscriptions.hasSubscription.collectAsState()
    val expiresAt by subscriptions.expiresAt.collectAsState()
    val autoRenew by subscriptions.autoRenewStatus.collectAsState()
    val monthly by subscriptions.monthlyProduct.collectAsState()
    val yearly by subscriptions.yearlyProduct.collectAsState()
    val selected by subscriptions.selectedProductId.collectAsState()
    val isPurchasing by subscriptions.isPurchasing.collectAsState()
    val isRestoring by subscriptions.isRestoring.collectAsState()
    val purchaseError by subscriptions.purchaseError.collectAsState()
    val billingUnavailable by subscriptions.billingUnavailable.collectAsState()
    val syncEnabled by container.syncEngine.isSyncEnabled.collectAsState()
    val user by container.authManager.currentUser.collectAsState()

    // Returning to this screen always refreshes the truth.
    LaunchedEffect(Unit) {
        if (monthly == null && yearly == null) subscriptions.loadProducts()
        subscriptions.checkSubscriptionStatus()
    }

    Scaffold(topBar = { CloseTopBar(stringResource(R.string.cloudSync)) }) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Icon(Icons.Filled.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp).size(56.dp))
            Text(stringResource(R.string.cloudSyncPro), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Text(
                stringResource(R.string.cloudSyncProDescription),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.syncEnabled), fontWeight = FontWeight.Medium) },
                supportingContent = { Text(stringResource(R.string.syncEnabledDescription)) },
                trailingContent = { Switch(checked = syncEnabled, onCheckedChange = container.syncEngine::setSyncEnabled) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )

            Benefit(Icons.Filled.CloudSync, R.string.benefitSyncTitle, R.string.benefitSyncSubtitle)
            Benefit(Icons.Filled.Devices, R.string.benefitMultiDeviceTitle, R.string.benefitMultiDeviceSubtitle)
            Benefit(Icons.Filled.History, R.string.benefitHistoryTitle, R.string.benefitHistorySubtitle)
            Benefit(Icons.Filled.Security, R.string.benefitBackupTitle, R.string.benefitBackupSubtitle)
            Spacer(Modifier.height(16.dp))

            if (hasSubscription) {
                Surface(color = Color(0x1A34C759), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Verified, contentDescription = null, tint = Color(0xFF34C759))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(stringResource(R.string.subscriptionActive), fontWeight = FontWeight.Medium)
                            expiresAt?.let {
                                val label = stringResource(if (autoRenew == false) R.string.subscriptionExpires else R.string.subscriptionNextBill)
                                Text("$label ${Formats.longDate(it, locale)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (user == null) {
                    Surface(color = Color(0x1AFF9500), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row {
                                Icon(Icons.Filled.CloudOff, contentDescription = null, tint = Color(0xFFFF9500))
                                Column(Modifier.padding(start = 12.dp)) {
                                    Text(stringResource(R.string.syncPaused), fontWeight = FontWeight.Medium)
                                    Text(stringResource(R.string.syncPausedSignInDescription), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Button(onClick = { navigator.navigate(AuthRoute) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                                Text(stringResource(R.string.signIn))
                            }
                        }
                    }
                }
            } else {
                val products = listOfNotNull(yearly, monthly)
                if (products.isEmpty()) {
                    if (billingUnavailable) {
                        Text(stringResource(R.string.storeUnavailable), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                    } else {
                        CircularProgressIndicator(Modifier.padding(16.dp))
                    }
                }
                products.forEach { product ->
                    val isYearly = product.productId == SyncConfig.YEARLY_PRODUCT_ID
                    ProductCard(
                        name = product.name.ifEmpty { stringResource(if (isYearly) R.string.yearly else R.string.monthly) },
                        description = product.description.ifEmpty {
                            stringResource(if (isYearly) R.string.subscriptionYearlyDescription else R.string.subscriptionMonthlyDescription)
                        },
                        price = priceWithPeriod(product),
                        selected = product.productId == selected,
                        onClick = { subscriptions.selectProduct(product.productId) },
                    )
                }
                Button(
                    onClick = { scope.launch { subscriptions.purchase(activity) } },
                    enabled = products.isNotEmpty() && !isPurchasing && !isRestoring,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(52.dp),
                ) {
                    if (isPurchasing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.subscribeNow), fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = { scope.launch { subscriptions.restorePurchases() } }, enabled = !isPurchasing && !isRestoring) {
                    if (isRestoring) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.restorePurchases))
                }
                Text(
                    stringResource(R.string.subscriptionLegalNote),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }
            purchaseError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
private fun Benefit(icon: ImageVector, title: Int, subtitle: Int) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(stringResource(title), fontWeight = FontWeight.Medium) },
        supportingContent = { Text(stringResource(subtitle)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun ProductCard(name: String, description: String, price: String, selected: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) BorderStroke(1.5.dp, primary.copy(alpha = 0.4f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(price, fontWeight = FontWeight.Bold, color = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// endregion

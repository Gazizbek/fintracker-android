package kz.alem.corporation.fintracker.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kz.alem.corporation.fintracker.AppContainer
import kz.alem.corporation.fintracker.FinTrackerApplication
import java.util.concurrent.TimeUnit

/** FCM token registration with the backend (port of the iOS PushManager). */
class PushManager(private val container: AppContainer) {

    @Volatile
    var fcmToken: String? = null
        private set

    fun configure() {
        container.appScope.launch {
            runCatching { FirebaseMessaging.getInstance().token.await() }
                .onSuccess { onNewToken(it) }
                .onFailure { Log.w(TAG, "FCM token unavailable", it) }
        }
    }

    suspend fun onNewToken(token: String) {
        fcmToken = token
        registerIfPossible()
    }

    /** Registers only when both a token and a signed-in user exist. */
    suspend fun registerIfPossible() {
        val token = fcmToken ?: return
        if (!container.authManager.isSignedIn) return
        try {
            apiCall { container.syncApi.registerDevice(RegisterDeviceRequest(token, SyncConfig.PUSH_PLATFORM)) }
        } catch (e: Exception) {
            Log.w(TAG, "registerDevice failed", e)
        }
    }

    /** Best effort; sign-out must never be blocked by the network. */
    suspend fun unregisterCurrentToken() {
        val token = fcmToken ?: return
        runCatching { apiCall { container.syncApi.unregisterDevice(UnregisterDeviceRequest(token)) } }
    }

    private companion object {
        const val TAG = "Push"
    }
}

/**
 * Receives the backend's silent data message (`{type: "sync"}`) sent when another device
 * uploaded changes, and hands the sync to WorkManager since this callback is short-lived.
 */
class FinTrackerMessagingService : FirebaseMessagingService() {

    private val container: AppContainer get() = (application as FinTrackerApplication).container

    override fun onNewToken(token: String) {
        container.appScope.launch { container.pushManager.onNewToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["type"] == "sync") SyncWorker.enqueueNow(this)
    }
}

/** Background sync, the Android counterpart of the iOS background app refresh task. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as FinTrackerApplication).container
        container.syncEngine.sync()
        return if (container.syncEngine.state.value is SyncState.Error) Result.retry() else Result.success()
    }

    companion object {
        private const val PERIODIC_NAME = "periodic-sync"
        private const val IMMEDIATE_NAME = "push-sync"

        private val networkConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(networkConstraint)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun enqueueNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(networkConstraint)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(IMMEDIATE_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}

/** Syncs as soon as connectivity comes back. */
class NetworkMonitor(context: Context, private val onAvailable: () -> Unit) {

    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    fun start() {
        connectivityManager?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = onAvailable()
        })
    }
}

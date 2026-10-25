package kz.alem.corporation.fintracker

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kz.alem.corporation.fintracker.data.AppPreferences
import kz.alem.corporation.fintracker.data.FinanceStore
import kz.alem.corporation.fintracker.sync.AuthManager
import kz.alem.corporation.fintracker.sync.PushManager
import kz.alem.corporation.fintracker.sync.SubscriptionManager
import kz.alem.corporation.fintracker.sync.SyncApi
import kz.alem.corporation.fintracker.sync.SyncEngine
import kz.alem.corporation.fintracker.sync.createSyncApi

/**
 * Manual dependency container. The sync managers reference each other (like the iOS
 * singletons), so they are created lazily and resolve their peers through the container.
 */
class AppContainer(val context: Context) {

    /** Lives as long as the process; used for work that must outlive a screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val prefs = AppPreferences(context)
    val store = FinanceStore(context)

    val authManager: AuthManager by lazy { AuthManager(this) }
    val syncApi: SyncApi by lazy { createSyncApi { force -> authManager.getIdTokenBlocking(force) } }
    val subscriptionManager: SubscriptionManager by lazy { SubscriptionManager(this) }
    val syncEngine: SyncEngine by lazy { SyncEngine(this) }
    val pushManager: PushManager by lazy { PushManager(this) }
}

package kz.alem.corporation.fintracker

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kz.alem.corporation.fintracker.data.backfillMissingOperationSums
import kz.alem.corporation.fintracker.sync.NetworkMonitor
import kz.alem.corporation.fintracker.sync.SyncWorker

class FinTrackerApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        container.appScope.launch { container.store.backfillMissingOperationSums() }

        // Instantiate the auth listener and subscription cache early so the UI shows the
        // right state immediately.
        container.authManager
        container.subscriptionManager
        container.pushManager.configure()

        // Every local write schedules a debounced sync.
        container.store.localChanges
            .onEach { container.syncEngine.scheduleDebounced() }
            .launchIn(container.appScope)

        NetworkMonitor(this) { container.syncEngine.scheduleImmediate() }.start()
        SyncWorker.schedulePeriodic(this)

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                container.syncEngine.scheduleImmediate()
                container.subscriptionManager.checkOnForeground()
            }
        })
    }
}

package kz.alem.corporation.fintracker

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kz.alem.corporation.fintracker.data.backfillMissingOperationSums
import kz.alem.corporation.fintracker.sync.NetworkMonitor
import kz.alem.corporation.fintracker.sync.SyncWorker
import kz.alem.corporation.fintracker.widget.updateDataWidgets

@OptIn(FlowPreview::class)
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

        // Home screen widgets follow every data change (local edits and sync), batched.
        container.store.changes
            .drop(1)
            .debounce(WIDGET_UPDATE_DEBOUNCE_MS)
            .onEach { updateDataWidgets(this) }
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

    private companion object {
        const val WIDGET_UPDATE_DEBOUNCE_MS = 1_000L
    }
}

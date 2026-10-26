package kz.alem.corporation.fintracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import kotlinx.serialization.Serializable
import kz.alem.corporation.fintracker.data.model.CategoryType
import kz.alem.corporation.fintracker.data.model.EditType
import kz.alem.corporation.fintracker.data.model.OperationType
import kz.alem.corporation.fintracker.data.model.OperationsFilter

/** Root destination holding the tabs and the bottom bar. */
@Serializable object MainRoute

// Tab graphs
@Serializable object OperationsGraph
@Serializable object AccountsGraph
@Serializable object PlanningGraph
@Serializable object CatalogGraph
@Serializable object SettingsGraph

// Tab roots
@Serializable object OperationsRoute
@Serializable object AccountsRoute
@Serializable object PlanningRoute
@Serializable object CatalogRoute
@Serializable object SettingsRoute

// Catalog
@Serializable object CategoriesRoute
@Serializable object CategoryGroupsRoute
@Serializable object CurrenciesRoute
@Serializable object LinearReportRoute
@Serializable object PieChartRoute

// Settings
@Serializable object InterfaceSettingsRoute
@Serializable object DatabaseRoute
@Serializable object ImportRoute
@Serializable object ExportRoute
@Serializable object PrivacyRoute
@Serializable object AuthRoute
@Serializable object SubscriptionRoute

// Full-screen pages opened above the tabs (root host)
@Serializable data class OperationEditRoute(val editType: EditType, val type: OperationType, val id: Long = 0)
@Serializable data class AccountEditRoute(val editType: EditType, val id: Long = 0)
/** [type] presets the category type of a new category (e.g. income when created from an income operation). */
@Serializable data class CategoryEditRoute(val editType: EditType, val id: Long = 0, val type: CategoryType = CategoryType.EXPENSE)
@Serializable data class CategoryGroupEditRoute(val editType: EditType, val id: Long = 0)
@Serializable data class CurrencyEditRoute(val editType: EditType, val id: Long = 0)
@Serializable data class PlanEditRoute(val editType: EditType, val id: Long = 0)

/** Keys under which editors hand the id of a newly created record back to the caller. */
object NavResults {
    const val CREATED_ACCOUNT = "createdAccount"
    const val CREATED_CATEGORY = "createdCategory"
    const val CREATED_CATEGORY_GROUP = "createdCategoryGroup"
    const val CREATED_CURRENCY = "createdCurrency"
}

/**
 * Routes navigation between the root host (full-screen pages above the tabs) and the host
 * inside the tabs. [tabs] is set while the tabbed screen is composed.
 */
class AppNavigator(
    private val root: NavHostController,
    private val onShowOperations: (OperationsFilter) -> Unit,
) {
    var tabs: NavHostController? = null
        set(value) {
            field = value
            // A tab requested before the tab host existed (cold start from a widget).
            if (value != null) pendingTab?.let {
                pendingTab = null
                selectTab(value, it)
            }
        }

    private var pendingTab: Any? = null

    fun navigate(route: Any) {
        if (isFullScreen(route)) root.navigate(route) else tabs?.navigate(route)
    }

    fun back() {
        if (root.previousBackStackEntry != null) root.popBackStack() else tabs?.popBackStack()
    }

    /** Opens the Operations tab at its root with [filter] applied (catalog items, reports). */
    fun showOperations(filter: OperationsFilter) {
        onShowOperations(filter)
        root.popBackStack(MainRoute, inclusive = false)
        tabs?.let { tabs ->
            tabs.navigate(OperationsGraph) {
                popUpTo(tabs.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
            }
        }
    }

    /** Closes any full-screen page and switches to the tab owning [graph], keeping its state. */
    fun openTab(graph: Any) {
        root.popBackStack(MainRoute, inclusive = false)
        val tabs = tabs ?: run {
            pendingTab = graph
            return
        }
        selectTab(tabs, graph)
    }

    private fun selectTab(tabs: NavHostController, graph: Any) {
        tabs.navigate(graph) {
            popUpTo(tabs.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    /** Hands [value] to the full-screen page below the current one. */
    fun setResult(key: String, value: Long) {
        root.previousBackStackEntry?.savedStateHandle?.set(key, value)
    }

    private fun isFullScreen(route: Any) = when (route) {
        is OperationEditRoute, is AccountEditRoute, is CategoryEditRoute, is CategoryGroupEditRoute,
        is CurrencyEditRoute, is PlanEditRoute, AuthRoute, SubscriptionRoute -> true
        else -> false
    }
}

val LocalNavigator = staticCompositionLocalOf<AppNavigator> { error("AppNavigator not provided") }

/** Delivers (once) an id another screen returned via [AppNavigator.setResult]. */
@Composable
fun NavResultEffect(key: String, onResult: (Long) -> Unit) {
    val entry = LocalViewModelStoreOwner.current as? NavBackStackEntry ?: return
    val callback = rememberUpdatedState(onResult)
    LaunchedEffect(entry, key) {
        entry.savedStateHandle.getStateFlow(key, 0L).collect { id ->
            if (id != 0L) {
                callback.value(id)
                entry.savedStateHandle[key] = 0L
            }
        }
    }
}

/** Emits a tab's graph object when its already-selected tab is tapped again. */
val LocalTabReselect = staticCompositionLocalOf<kotlinx.coroutines.flow.SharedFlow<Any>> { kotlinx.coroutines.flow.MutableSharedFlow() }

/** Scrolls [state] to the top whenever the tab owning [graph] is re-selected. */
@Composable
fun ScrollToTopOnReselect(graph: Any, state: androidx.compose.foundation.lazy.LazyListState) {
    val reselect = LocalTabReselect.current
    LaunchedEffect(reselect, state) {
        reselect.collect { if (it == graph) state.animateScrollToItem(0) }
    }
}

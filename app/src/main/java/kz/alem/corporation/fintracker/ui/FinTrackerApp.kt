package kz.alem.corporation.fintracker.ui

import androidx.activity.compose.LocalActivity
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.MutableSharedFlow
import kz.alem.corporation.fintracker.R
import kz.alem.corporation.fintracker.ui.accounts.AccountEditScreen
import kz.alem.corporation.fintracker.ui.accounts.AccountsScreen
import kz.alem.corporation.fintracker.ui.categories.CategoriesScreen
import kz.alem.corporation.fintracker.ui.categories.CategoryEditScreen
import kz.alem.corporation.fintracker.ui.categories.CategoryGroupEditScreen
import kz.alem.corporation.fintracker.ui.categories.CategoryGroupsScreen
import kz.alem.corporation.fintracker.ui.common.LocalAppContainer
import kz.alem.corporation.fintracker.ui.common.appViewModel
import kz.alem.corporation.fintracker.ui.currencies.CurrenciesScreen
import kz.alem.corporation.fintracker.ui.currencies.CurrencyEditScreen
import kz.alem.corporation.fintracker.ui.database.DatabaseScreen
import kz.alem.corporation.fintracker.ui.database.ExportScreen
import kz.alem.corporation.fintracker.ui.database.ImportScreen
import kz.alem.corporation.fintracker.ui.navigation.AccountEditRoute
import kz.alem.corporation.fintracker.ui.navigation.AccountsGraph
import kz.alem.corporation.fintracker.ui.navigation.AccountsRoute
import kz.alem.corporation.fintracker.ui.navigation.AppNavigator
import kz.alem.corporation.fintracker.ui.navigation.AuthRoute
import kz.alem.corporation.fintracker.ui.navigation.CatalogGraph
import kz.alem.corporation.fintracker.ui.navigation.CatalogRoute
import kz.alem.corporation.fintracker.ui.navigation.CategoriesRoute
import kz.alem.corporation.fintracker.ui.navigation.CategoryEditRoute
import kz.alem.corporation.fintracker.ui.navigation.CategoryGroupEditRoute
import kz.alem.corporation.fintracker.ui.navigation.CategoryGroupsRoute
import kz.alem.corporation.fintracker.ui.navigation.CurrenciesRoute
import kz.alem.corporation.fintracker.ui.navigation.CurrencyEditRoute
import kz.alem.corporation.fintracker.ui.navigation.DatabaseRoute
import kz.alem.corporation.fintracker.ui.navigation.ExportRoute
import kz.alem.corporation.fintracker.ui.navigation.ImportRoute
import kz.alem.corporation.fintracker.ui.navigation.InterfaceSettingsRoute
import kz.alem.corporation.fintracker.ui.navigation.LinearReportRoute
import kz.alem.corporation.fintracker.ui.navigation.LocalNavigator
import kz.alem.corporation.fintracker.ui.navigation.LocalTabReselect
import kz.alem.corporation.fintracker.ui.navigation.MainRoute
import kz.alem.corporation.fintracker.ui.navigation.OperationEditRoute
import kz.alem.corporation.fintracker.ui.navigation.OperationsGraph
import kz.alem.corporation.fintracker.ui.navigation.OperationsRoute
import kz.alem.corporation.fintracker.ui.navigation.PieChartRoute
import kz.alem.corporation.fintracker.ui.navigation.PlanEditRoute
import kz.alem.corporation.fintracker.ui.navigation.PlanningGraph
import kz.alem.corporation.fintracker.ui.navigation.PlanningRoute
import kz.alem.corporation.fintracker.ui.navigation.PrivacyRoute
import kz.alem.corporation.fintracker.ui.navigation.SettingsGraph
import kz.alem.corporation.fintracker.ui.navigation.SettingsRoute
import kz.alem.corporation.fintracker.ui.navigation.SubscriptionRoute
import kz.alem.corporation.fintracker.ui.operations.OperationEditScreen
import kz.alem.corporation.fintracker.ui.operations.OperationsScreen
import kz.alem.corporation.fintracker.ui.operations.OperationsViewModel
import kz.alem.corporation.fintracker.ui.planning.PlanEditScreen
import kz.alem.corporation.fintracker.ui.planning.PlanningScreen
import kz.alem.corporation.fintracker.ui.reports.LinearReportScreen
import kz.alem.corporation.fintracker.ui.reports.PieChartScreen
import kz.alem.corporation.fintracker.ui.settings.CatalogScreen
import kz.alem.corporation.fintracker.ui.settings.InterfaceSettingsScreen
import kz.alem.corporation.fintracker.ui.settings.PrivacyScreen
import kz.alem.corporation.fintracker.ui.settings.SettingsScreen
import kz.alem.corporation.fintracker.ui.sync.AuthScreen
import kz.alem.corporation.fintracker.ui.sync.SubscriptionScreen

private enum class Tab(val graph: Any, val icon: ImageVector, val label: Int) {
    OPERATIONS(OperationsGraph, Icons.AutoMirrored.Outlined.ReceiptLong, R.string.operations),
    ACCOUNTS(AccountsGraph, Icons.Outlined.CreditCard, R.string.accounts),
    PLANNING(PlanningGraph, Icons.Outlined.TrackChanges, R.string.planning),
    CATALOG(CatalogGraph, Icons.AutoMirrored.Outlined.MenuBook, R.string.catalog),
    SETTINGS(SettingsGraph, Icons.Outlined.Settings, R.string.settings),
}

/** Root: the tabbed app, plus the account-switch confirmation. */
@Composable
fun FinTrackerRoot() {
    MainScreen()
    AccountSwitchDialog()
}

@Composable
private fun AccountSwitchDialog() {
    val auth = LocalAppContainer.current.authManager
    val pending by auth.pendingAccountSwitch.collectAsState()
    if (pending == null) return
    AlertDialog(
        // Both buttons resolve the switch; dismissing must not silently pick one.
        onDismissRequest = {},
        title = { Text(stringResource(R.string.switchAccountTitle)) },
        text = { Text(stringResource(R.string.switchAccountMessage)) },
        confirmButton = {
            TextButton(onClick = auth::confirmAccountSwitch) {
                Text(stringResource(R.string.switchAccountConfirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = auth::cancelAccountSwitch) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Two navigation levels: the tabbed screens live in [MainRoute] together with the bottom bar,
 * and full-screen pages (editors, sign-in, subscription) open above it in the root host. The
 * tab screen therefore stays untouched while a form opens over it.
 */
@Composable
private fun MainScreen() {
    val rootController = rememberNavController()
    val activity = checkNotNull(LocalActivity.current) as ComponentActivity
    val operationsViewModel = appViewModel(owner = activity) { container, _ -> OperationsViewModel(container) }
    val navigator = remember(rootController) {
        AppNavigator(rootController) { filter -> operationsViewModel.filter.value = filter }
    }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        NavHost(navController = rootController, startDestination = MainRoute) {
            composable<MainRoute> { TabsScreen(navigator, operationsViewModel) }
            composable<AuthRoute> { AuthScreen() }
            composable<SubscriptionRoute> { SubscriptionScreen() }
            composable<OperationEditRoute> { OperationEditScreen(it.toRoute()) }
            composable<AccountEditRoute> { AccountEditScreen(it.toRoute()) }
            composable<CategoryEditRoute> { CategoryEditScreen(it.toRoute()) }
            composable<CategoryGroupEditRoute> { CategoryGroupEditScreen(it.toRoute()) }
            composable<CurrencyEditRoute> { CurrencyEditScreen(it.toRoute()) }
            composable<PlanEditRoute> { PlanEditScreen(it.toRoute()) }
        }
    }
}

@Composable
private fun TabsScreen(navigator: AppNavigator, operationsViewModel: OperationsViewModel) {
    val navController = rememberNavController()
    val reselect = remember { MutableSharedFlow<Any>(extraBufferCapacity = 1) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    DisposableEffect(navController) {
        navigator.tabs = navController
        onDispose { if (navigator.tabs === navController) navigator.tabs = null }
    }

    fun selectTab(tab: Tab) {
        navController.navigate(tab.graph) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    CompositionLocalProvider(LocalTabReselect provides reselect) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        val selected = destination?.hierarchy?.any { it.hasRoute(tab.graph::class) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (selected) {
                                    // Re-selecting a tab pops to its root and scrolls to top.
                                    val rootPopped = navController.popBackStack(tabRoot(tab)::class, inclusive = false)
                                    if (!rootPopped) selectTab(tab)
                                    reselect.tryEmit(tab.graph)
                                } else {
                                    selectTab(tab)
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label), maxLines = 1) },
                        )
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = OperationsGraph,
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding),
            ) {
                navigation<OperationsGraph>(startDestination = OperationsRoute) {
                    composable<OperationsRoute> { OperationsScreen(operationsViewModel) }
                }
                navigation<AccountsGraph>(startDestination = AccountsRoute) {
                    composable<AccountsRoute> { AccountsScreen() }
                }
                navigation<PlanningGraph>(startDestination = PlanningRoute) {
                    composable<PlanningRoute> { PlanningScreen() }
                }
                navigation<CatalogGraph>(startDestination = CatalogRoute) {
                    composable<CatalogRoute> { CatalogScreen() }
                    composable<CategoriesRoute> { CategoriesScreen() }
                    composable<CategoryGroupsRoute> { CategoryGroupsScreen() }
                    composable<CurrenciesRoute> { CurrenciesScreen() }
                    composable<LinearReportRoute> { LinearReportScreen() }
                    composable<PieChartRoute> { PieChartScreen() }
                }
                navigation<SettingsGraph>(startDestination = SettingsRoute) {
                    composable<SettingsRoute> { SettingsScreen() }
                    composable<InterfaceSettingsRoute> { InterfaceSettingsScreen() }
                    composable<DatabaseRoute> { DatabaseScreen() }
                    composable<ImportRoute> { ImportScreen() }
                    composable<ExportRoute> { ExportScreen() }
                    composable<PrivacyRoute> { PrivacyScreen() }
                }
            }
        }
    }
}

private fun tabRoot(tab: Tab): Any = when (tab) {
    Tab.OPERATIONS -> OperationsRoute
    Tab.ACCOUNTS -> AccountsRoute
    Tab.PLANNING -> PlanningRoute
    Tab.CATALOG -> CatalogRoute
    Tab.SETTINGS -> SettingsRoute
}

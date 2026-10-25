package kz.alem.corporation.fintracker.ui.settings

import androidx.compose.ui.platform.LocalResources
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.WebView
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.launch
import kz.alem.corporation.fintracker.BuildConfig
import kz.alem.corporation.fintracker.R
import kz.alem.corporation.fintracker.data.Language
import kz.alem.corporation.fintracker.data.Theme
import kz.alem.corporation.fintracker.sync.SyncApiException
import kz.alem.corporation.fintracker.sync.SyncState
import kz.alem.corporation.fintracker.ui.common.BackButton
import kz.alem.corporation.fintracker.ui.common.ListPreferenceRow
import kz.alem.corporation.fintracker.ui.common.FormDivider
import kz.alem.corporation.fintracker.ui.common.FormScaffold
import kz.alem.corporation.fintracker.ui.common.FormSection
import kz.alem.corporation.fintracker.ui.common.Formats
import kz.alem.corporation.fintracker.ui.common.LocalAppContainer
import kz.alem.corporation.fintracker.ui.common.MenuRowTitleInset
import kz.alem.corporation.fintracker.ui.common.SwitchRow
import kz.alem.corporation.fintracker.ui.common.appLocale
import kz.alem.corporation.fintracker.ui.navigation.AuthRoute
import kz.alem.corporation.fintracker.ui.navigation.CategoriesRoute
import kz.alem.corporation.fintracker.ui.navigation.CurrenciesRoute
import kz.alem.corporation.fintracker.ui.navigation.DatabaseRoute
import kz.alem.corporation.fintracker.ui.navigation.InterfaceSettingsRoute
import kz.alem.corporation.fintracker.ui.navigation.LinearReportRoute
import kz.alem.corporation.fintracker.ui.navigation.LocalNavigator
import kz.alem.corporation.fintracker.ui.navigation.PieChartRoute
import kz.alem.corporation.fintracker.ui.navigation.PrivacyRoute
import kz.alem.corporation.fintracker.ui.navigation.SubscriptionRoute

@Composable
fun MenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    tint: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) = MenuRow(rememberVectorPainter(icon), title, subtitle, enabled, tint, trailing, onClick)

/** Settings row with a plain monochrome leading icon, as in the system Settings app. */
@Composable
fun MenuRow(
    painter: Painter,
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    tint: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val iconTint = (tint ?: MaterialTheme.colorScheme.onSurfaceVariant).let { if (enabled) it else it.copy(alpha = 0.38f) }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(painter, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp)) },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier,
    )
}

// region Catalog tab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen() {
    val navigator = LocalNavigator.current
    FormScaffold(stringResource(R.string.catalog), showBack = false) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            FormSection(title = stringResource(R.string.directories)) {
                MenuRow(Icons.Filled.Category, stringResource(R.string.categories)) { navigator.navigate(CategoriesRoute) }
                FormDivider(MenuRowTitleInset)
                MenuRow(Icons.Filled.CurrencyExchange, stringResource(R.string.currencies)) { navigator.navigate(CurrenciesRoute) }
            }
            FormSection(title = stringResource(R.string.reports)) {
                MenuRow(Icons.AutoMirrored.Filled.ViewList, stringResource(R.string.linearReport)) { navigator.navigate(LinearReportRoute) }
                FormDivider(MenuRowTitleInset)
                MenuRow(Icons.Filled.PieChart, stringResource(R.string.circleDiagram)) { navigator.navigate(PieChartRoute) }
            }
        }
    }
}

// endregion

// region Settings tab

private fun Context.openUrl(url: String): Boolean = try {
    startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val locale = appLocale
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val auth = container.authManager
    val user by auth.currentUser.collectAsState()
    val hasSubscription by container.subscriptionManager.hasSubscription.collectAsState()
    val lastSyncedAt by container.syncEngine.lastSyncedAt.collectAsState()
    val syncState by container.syncEngine.state.collectAsState()
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf<String?>(null) }
    var actionError by rememberSaveable { mutableStateOf<String?>(null) }
    val signedIn = user != null
    val anonymous = user?.isAnonymous == true

    fun errorText(e: Exception): String =
        (e as? SyncApiException)?.messageRes?.let { resources.getString(it) } ?: e.localizedMessage.orEmpty()

    FormScaffold(stringResource(R.string.settings), showBack = false) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            FormSection(title = stringResource(R.string.cloudSync)) {
                when {
                    signedIn && !anonymous -> MenuRow(
                        icon = Icons.Filled.AccountCircle,
                        title = user?.email.orEmpty(),
                        trailing = {
                            TextButton(onClick = { dialog = "signOut" }) {
                                Text(stringResource(R.string.signOut), color = MaterialTheme.colorScheme.error)
                            }
                        },
                    )
                    else -> MenuRow(
                        icon = Icons.Filled.PersonAdd,
                        title = stringResource(if (anonymous) R.string.signInWithEmail else R.string.signIn),
                        subtitle = stringResource(R.string.signInToRestoreData),
                    ) { navigator.navigate(AuthRoute) }
                }
                FormDivider(MenuRowTitleInset)
                val subtitle = when {
                    hasSubscription && !signedIn -> stringResource(R.string.syncPaused)
                    syncState is SyncState.Syncing -> stringResource(R.string.syncing)
                    syncState is SyncState.Error && hasSubscription -> (syncState as SyncState.Error).message
                    lastSyncedAt != null -> "${stringResource(R.string.lastSynced)}: ${Formats.dateTime(lastSyncedAt!!, locale)}"
                    else -> null
                }
                MenuRow(
                    painter = rememberVectorPainter(if (hasSubscription) Icons.Filled.Verified else Icons.Outlined.WorkspacePremium),
                    tint = if (hasSubscription) MaterialTheme.colorScheme.primary else null,
                    title = stringResource(R.string.cloudSync),
                    subtitle = subtitle,
                    trailing = if (!hasSubscription) {
                        {
                            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(50)) {
                                Text("PRO", color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                        }
                    } else null,
                ) { navigator.navigate(SubscriptionRoute) }
                if (signedIn && !anonymous) {
                    FormDivider(MenuRowTitleInset)
                    MenuRow(
                        icon = Icons.Filled.CloudDownload,
                                                title = stringResource(R.string.restoreFromCloud),
                        enabled = busy == null,
                        trailing = if (busy == "restore") ({ CircularProgressIndicator(Modifier.size(20.dp)) }) else null,
                    ) { if (hasSubscription) dialog = "restore" else navigator.navigate(SubscriptionRoute) }
                    FormDivider(MenuRowTitleInset)
                    MenuRow(
                        icon = Icons.Filled.DeleteForever,
                        tint = if (hasSubscription) MaterialTheme.colorScheme.error else null,
                        title = stringResource(R.string.deleteCloudData),
                        enabled = busy == null,
                        trailing = if (busy == "delete") ({ CircularProgressIndicator(Modifier.size(20.dp)) }) else null,
                    ) { if (hasSubscription) dialog = "delete" else navigator.navigate(SubscriptionRoute) }
                }
                actionError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
                }
            }

            FormSection(title = stringResource(R.string.settings)) {
                MenuRow(Icons.Filled.Palette, stringResource(R.string.interface_)) { navigator.navigate(InterfaceSettingsRoute) }
                FormDivider(MenuRowTitleInset)
                MenuRow(Icons.Filled.Storage, stringResource(R.string.database)) { navigator.navigate(DatabaseRoute) }
            }

            FormSection(title = stringResource(R.string.contactUs)) {
                MenuRow(painterResource(R.drawable.ic_whatsapp), stringResource(R.string.onWhatsapp)) {
                    context.openUrl("https://wa.me/77756120521")
                }
                FormDivider(MenuRowTitleInset)
                MenuRow(painterResource(R.drawable.ic_telegram), stringResource(R.string.onTelegram)) {
                    if (!context.openUrl("tg://resolve?domain=alemcorp")) context.openUrl("https://t.me/alemcorp")
                }
            }

            FormSection(title = stringResource(R.string.aboutApp)) {
                MenuRow(Icons.Filled.Shield, stringResource(R.string.privacyPolicy)) { navigator.navigate(PrivacyRoute) }
                FormDivider(MenuRowTitleInset)
                MenuRow(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.version),
                    trailing = { Text(BuildConfig.VERSION_NAME, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                )
            }
        }
    }

    when (dialog) {
        "signOut" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.signOut)) },
            text = { Text(stringResource(R.string.signOutConfirmation)) },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    auth.signOut()
                }) { Text(stringResource(R.string.signOut), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } },
        )
        "restore" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.restoreFromCloud)) },
            text = { Text(stringResource(R.string.restoreFromCloudWarning)) },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    busy = "restore"
                    actionError = null
                    scope.launch {
                        try {
                            container.syncEngine.restoreFromCloud()
                        } catch (e: Exception) {
                            actionError = errorText(e)
                        }
                        busy = null
                    }
                }) { Text(stringResource(R.string.restoreFromCloud), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } },
        )
        "delete" -> DeleteCloudDataDialog(
            onDismiss = { dialog = null },
            onConfirm = {
                dialog = null
                busy = "delete"
                actionError = null
                scope.launch {
                    try {
                        container.syncEngine.deleteCloudData()
                    } catch (e: Exception) {
                        actionError = errorText(e)
                    }
                    busy = null
                }
            },
        )
    }
}

/** Requires typing DELETE before wiping the cloud copy. */
@Composable
private fun DeleteCloudDataDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.deleteCloudData)) },
        text = {
            Column {
                Text(stringResource(R.string.deleteCloudDataWarning))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("DELETE") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = text == "DELETE",
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.deleteCloudData)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// endregion

// region Interface

private fun currentLanguage(): Language {
    val tag = AppCompatDelegate.getApplicationLocales()[0]?.language
        ?: java.util.Locale.getDefault().language
    return Language.entries.firstOrNull { it.tag == tag } ?: Language.ENGLISH
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterfaceSettingsScreen() {
    val prefs = LocalAppContainer.current.prefs
    val theme by prefs.theme.collectAsState()
    val useOnlyDate by prefs.useOnlyDate.collectAsState()
    val openFiltered by prefs.openFilteredOperation.collectAsState()
    val language = remember { currentLanguage() }

    FormScaffold(stringResource(R.string.settingInterface)) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            FormSection {
                ListPreferenceRow(
                    label = stringResource(R.string.language),
                    options = Language.entries,
                    selected = language,
                    optionLabel = { it.displayName },
                    // Recreates the activity with the new locale (per-app language).
                    onSelect = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(it.tag)) },
                )
                FormDivider()
                ListPreferenceRow(
                    label = stringResource(R.string.theme),
                    options = Theme.entries,
                    selected = theme,
                    optionLabel = {
                        stringResource(
                            when (it) {
                                Theme.SYSTEM -> R.string.system
                                Theme.LIGHT -> R.string.light
                                Theme.DARK -> R.string.dark
                            }
                        )
                    },
                    onSelect = prefs::setTheme,
                )
            }
            FormSection(footer = stringResource(R.string.enterOnlyDateForOperationDescription)) {
                SwitchRow(stringResource(R.string.enterOnlyDateForOperation), useOnlyDate, prefs::setUseOnlyDate)
            }
            FormSection(footer = stringResource(R.string.openFilteredOperationDescription)) {
                SwitchRow(stringResource(R.string.openFilteredOperation), openFiltered, prefs::setOpenFilteredOperation)
            }
        }
    }
}

// endregion

// region Privacy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen() {
    val context = LocalContext.current
    val language = remember { currentLanguage() }
    val textColor = MaterialTheme.colorScheme.onSurface
    val background = MaterialTheme.colorScheme.surface
    val html = remember(textColor, background, language) {
        fun hex(color: Color) = String.format("#%06X", color.toArgb() and 0xFFFFFF)
        context.assets.open("privacy-${language.tag}.html").bufferedReader().use { it.readText() }
            .replace("labelColor", hex(textColor))
            .replace("backgroundColor", hex(background))
    }
    FormScaffold(stringResource(R.string.privacyPolicy)) { padding ->
        AndroidView(
            factory = {
                WebView(it).apply {
                    // The bundled policy pages build their styles with inline JavaScript.
                    @SuppressLint("SetJavaScriptEnabled")
                    settings.javaScriptEnabled = true
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            },
            update = { it.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null) },
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        )
    }
}

// endregion

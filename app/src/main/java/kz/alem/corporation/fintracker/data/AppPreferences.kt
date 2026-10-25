package kz.alem.corporation.fintracker.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class Theme(val raw: String) { SYSTEM("system"), LIGHT("light"), DARK("dark") }

enum class Language(val tag: String, val displayName: String) {
    ENGLISH("en", "English"),
    KAZAKH("kk", "Қазақша"),
    RUSSIAN("ru", "Русский"),
}

@Serializable
data class ImportColumn(val id: Int, val name: String, val isEnabled: Boolean = true)

/** App settings, the Android counterpart of the iOS @AppStorage / UserDefaults keys. */
class AppPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("fintracker_settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    init {
        // The app lock was removed; drop its leftover settings, including the stored passcode.
        prefs.edit { remove(LEGACY_KEY_USE_PASSCODE); remove(LEGACY_KEY_BIOMETRIC); remove(LEGACY_KEY_PASSCODE) }
    }

    private val _theme = MutableStateFlow(Theme.entries.firstOrNull { it.raw == prefs.getString(KEY_THEME, null) } ?: Theme.SYSTEM)
    val theme: StateFlow<Theme> = _theme.asStateFlow()

    private val _useOnlyDate = MutableStateFlow(prefs.getBoolean(KEY_USE_ONLY_DATE, false))
    val useOnlyDate: StateFlow<Boolean> = _useOnlyDate.asStateFlow()

    private val _openFilteredOperation = MutableStateFlow(prefs.getBoolean(KEY_OPEN_FILTERED, false))
    val openFilteredOperation: StateFlow<Boolean> = _openFilteredOperation.asStateFlow()

    fun setTheme(value: Theme) {
        prefs.edit { putString(KEY_THEME, value.raw) }
        _theme.value = value
    }

    fun setUseOnlyDate(value: Boolean) {
        prefs.edit { putBoolean(KEY_USE_ONLY_DATE, value) }
        _useOnlyDate.value = value
    }

    fun setOpenFilteredOperation(value: Boolean) {
        prefs.edit { putBoolean(KEY_OPEN_FILTERED, value) }
        _openFilteredOperation.value = value
    }

    // Import
    var importColumns: List<ImportColumn>?
        get() = prefs.getString(KEY_IMPORT_COLUMNS, null)?.let {
            runCatching { json.decodeFromString<List<ImportColumn>>(it) }.getOrNull()
        }
        set(value) = prefs.edit {
            if (value == null) remove(KEY_IMPORT_COLUMNS) else putString(KEY_IMPORT_COLUMNS, json.encodeToString(value))
        }

    // Sync bookkeeping
    var lastKnownUid: String?
        get() = prefs.getString(KEY_LAST_UID, null)
        set(value) = prefs.edit { putString(KEY_LAST_UID, value) }

    var lastSyncAt: Long
        get() = prefs.getLong(KEY_LAST_SYNC_AT, 0)
        set(value) = prefs.edit { putLong(KEY_LAST_SYNC_AT, value) }

    var isSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_SYNC_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_SYNC_ENABLED, value) }

    var hasSubscription: Boolean
        get() = prefs.getBoolean(KEY_HAS_SUBSCRIPTION, false)
        set(value) = prefs.edit { putBoolean(KEY_HAS_SUBSCRIPTION, value) }

    var subscriptionExpiresAt: Long?
        get() = prefs.getLong(KEY_SUBSCRIPTION_EXPIRES, 0).takeIf { it > 0 }
        set(value) = prefs.edit {
            if (value == null) remove(KEY_SUBSCRIPTION_EXPIRES) else putLong(KEY_SUBSCRIPTION_EXPIRES, value)
        }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_USE_ONLY_DATE = "useOnlyDate"
        const val KEY_OPEN_FILTERED = "openFilteredOperation"
        const val KEY_IMPORT_COLUMNS = "importColumns"
        const val KEY_LAST_UID = "lastKnownUID"
        const val KEY_LAST_SYNC_AT = "lastSyncAt"
        const val KEY_SYNC_ENABLED = "isSyncEnabled"
        const val KEY_HAS_SUBSCRIPTION = "hasSubscription"
        const val KEY_SUBSCRIPTION_EXPIRES = "subscriptionExpiresAt"
        const val LEGACY_KEY_USE_PASSCODE = "usePasscode"
        const val LEGACY_KEY_BIOMETRIC = "biometricEnabled"
        const val LEGACY_KEY_PASSCODE = "passcode"
    }
}

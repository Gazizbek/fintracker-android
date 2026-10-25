package kz.alem.corporation.fintracker.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kz.alem.corporation.fintracker.AppContainer
import kz.alem.corporation.fintracker.data.formatNumber
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

/** Creates (or reuses) a ViewModel that gets the app container and a SavedStateHandle. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    owner: ViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current),
    crossinline create: (AppContainer, SavedStateHandle) -> VM,
): VM {
    val container = LocalAppContainer.current
    return viewModel(viewModelStoreOwner = owner, key = key) { create(container, createSavedStateHandle()) }
}

/** Locale of the in-app language (may differ from the device language). */
val appLocale: Locale
    @Composable @ReadOnlyComposable
    get() = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

object Formats {
    /** "January 2025" style section header, capitalized like iOS. */
    fun monthYear(millis: Long, locale: Locale): String =
        SimpleDateFormat("LLLL yyyy", locale).format(Date(millis)).replaceFirstChar { it.titlecase(locale) }

    fun dayOfMonth(millis: Long): String = SimpleDateFormat("dd", Locale.ROOT).format(Date(millis))

    fun mediumDate(millis: Long, locale: Locale): String =
        DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(millis))

    fun longDate(millis: Long, locale: Locale): String =
        DateFormat.getDateInstance(DateFormat.LONG, locale).format(Date(millis))

    fun time(millis: Long, locale: Locale): String =
        DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date(millis))

    fun dateTime(millis: Long, locale: Locale): String =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale).format(Date(millis))

    fun number(value: Double?, locale: Locale): String = formatNumber(value, locale)

    fun money(value: Double, symbol: String, locale: Locale): String = "${formatNumber(value, locale)} $symbol".trim()
}

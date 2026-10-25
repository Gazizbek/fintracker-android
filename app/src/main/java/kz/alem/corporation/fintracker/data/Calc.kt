package kz.alem.corporation.fintracker.data

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Converts [sum] with an exchange [rate]. mainRate 1 means "1 source = rate destination"
 * (multiply), anything else means "1 destination = rate source" (divide).
 */
fun calculateSum(sum: Double, rate: Double?, mainRate: Int?): Double {
    if (rate == null) return sum
    return if (mainRate == 1) sum * rate else sum / rate
}

/** Rounds to cents and turns -0.0 into 0.0, like the iOS balance code. */
fun Double.roundCents(): Double {
    val rounded = (this * 100).roundToLong() / 100.0
    return if (rounded == 0.0) 0.0 else rounded
}

fun Double.roundTo(places: Int): Double {
    val factor = 10.0.pow(places)
    return (this * factor).roundToLong() / factor
}

/** Decimal format with grouping and up to two fraction digits (iOS NumberFormatter .decimal). */
fun formatNumber(value: Double?, locale: Locale = Locale.getDefault()): String {
    if (value == null) return ""
    val format = DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(locale))
    format.roundingMode = RoundingMode.HALF_EVEN
    val normalized = if (value == 0.0) 0.0 else value
    return format.format(normalized)
}

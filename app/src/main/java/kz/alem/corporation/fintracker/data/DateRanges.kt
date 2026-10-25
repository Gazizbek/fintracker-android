package kz.alem.corporation.fintracker.data

import kz.alem.corporation.fintracker.data.model.Period
import java.util.Calendar

// Period boundaries, ported from the iOS Date extension. The end of a range is the last
// millisecond before the next period starts.

private fun calendarAt(millis: Long): Calendar = Calendar.getInstance().apply { timeInMillis = millis }

private fun Calendar.startOfDay(): Calendar = apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

fun startOfDay(millis: Long): Long = calendarAt(millis).startOfDay().timeInMillis

fun endOfDay(millis: Long): Long =
    calendarAt(millis).startOfDay().apply { add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis - 1

fun firstDayOfCurrentWeek(now: Long): Long = calendarAt(now).startOfDay().apply {
    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
    if (timeInMillis > now) add(Calendar.WEEK_OF_YEAR, -1)
}.timeInMillis

fun lastDayOfCurrentWeek(now: Long): Long =
    calendarAt(firstDayOfCurrentWeek(now)).apply { add(Calendar.DAY_OF_MONTH, 7) }.timeInMillis - 1

fun firstDayOfLastWeek(now: Long): Long =
    calendarAt(firstDayOfCurrentWeek(now)).apply { add(Calendar.DAY_OF_MONTH, -7) }.timeInMillis

fun lastDayOfLastWeek(now: Long): Long = firstDayOfCurrentWeek(now) - 1

fun firstDayOfCurrentMonth(now: Long): Long =
    calendarAt(now).startOfDay().apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis

fun lastDayOfCurrentMonth(now: Long): Long =
    calendarAt(firstDayOfCurrentMonth(now)).apply { add(Calendar.MONTH, 1) }.timeInMillis - 1

fun firstDayOfLastMonth(now: Long): Long =
    calendarAt(firstDayOfCurrentMonth(now)).apply { add(Calendar.MONTH, -1) }.timeInMillis

fun lastDayOfLastMonth(now: Long): Long = firstDayOfCurrentMonth(now) - 1

fun firstDayOfCurrentYear(now: Long): Long =
    calendarAt(now).startOfDay().apply { set(Calendar.DAY_OF_YEAR, 1) }.timeInMillis

fun lastDayOfCurrentYear(now: Long): Long =
    calendarAt(firstDayOfCurrentYear(now)).apply { add(Calendar.YEAR, 1) }.timeInMillis - 1

fun firstDayOfLastYear(now: Long): Long =
    calendarAt(firstDayOfCurrentYear(now)).apply { add(Calendar.YEAR, -1) }.timeInMillis

fun lastDayOfLastYear(now: Long): Long = firstDayOfCurrentYear(now) - 1

/** Date range a period resolves to today, or null for "all time" / arbitrary periods. */
fun Period.range(now: Long = System.currentTimeMillis()): Pair<Long, Long>? = when (this) {
    Period.CURRENT_WEEK -> firstDayOfCurrentWeek(now) to lastDayOfCurrentWeek(now)
    Period.LAST_WEEK -> firstDayOfLastWeek(now) to lastDayOfLastWeek(now)
    Period.CURRENT_MONTH -> firstDayOfCurrentMonth(now) to lastDayOfCurrentMonth(now)
    Period.LAST_MONTH -> firstDayOfLastMonth(now) to lastDayOfLastMonth(now)
    Period.CURRENT_YEAR -> firstDayOfCurrentYear(now) to lastDayOfCurrentYear(now)
    Period.LAST_YEAR -> firstDayOfLastYear(now) to lastDayOfLastYear(now)
    Period.ALL_TIME, Period.ARBITRARY -> null
}

/**
 * Which period button matches a stored date range (plans store only dates). Falls back
 * to [Period.ARBITRARY] when nothing matches exactly.
 */
fun inferPeriod(beginDate: Long, endDate: Long, now: Long = System.currentTimeMillis()): Period =
    Period.entries.firstOrNull { it.range(now) == (beginDate to endDate) } ?: Period.ARBITRARY

/** "yyyyMM" month key used to group operations and report rows. */
fun monthKey(millis: Long): Int {
    val cal = calendarAt(millis)
    return cal.get(Calendar.YEAR) * 100 + cal.get(Calendar.MONTH) + 1
}

fun monthKeyStart(key: Int): Long = Calendar.getInstance().apply {
    clear()
    set(key / 100, key % 100 - 1, 1)
}.timeInMillis

package kz.alem.corporation.fintracker

import kz.alem.corporation.fintracker.data.calculateSum
import kz.alem.corporation.fintracker.data.endOfDay
import kz.alem.corporation.fintracker.data.firstDayOfCurrentMonth
import kz.alem.corporation.fintracker.data.formatNumber
import kz.alem.corporation.fintracker.data.inferPeriod
import kz.alem.corporation.fintracker.data.lastDayOfCurrentMonth
import kz.alem.corporation.fintracker.data.lastDayOfLastWeek
import kz.alem.corporation.fintracker.data.firstDayOfCurrentWeek
import kz.alem.corporation.fintracker.data.model.Period
import kz.alem.corporation.fintracker.data.monthKey
import kz.alem.corporation.fintracker.data.monthKeyStart
import kz.alem.corporation.fintracker.data.range
import kz.alem.corporation.fintracker.data.roundCents
import kz.alem.corporation.fintracker.data.startOfDay
import kz.alem.corporation.fintracker.ui.common.CalculatorEngine
import kz.alem.corporation.fintracker.ui.database.CsvParser
import kz.alem.corporation.fintracker.ui.database.Separator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class LogicTest {

    @Test
    fun calculateSumUsesRateDirection() {
        assertEquals(500.0, calculateSum(100.0, 5.0, 1), 1e-9)
        assertEquals(20.0, calculateSum(100.0, 5.0, 2), 1e-9)
        assertEquals(100.0, calculateSum(100.0, null, 1), 1e-9)
    }

    @Test
    fun roundCentsNormalizesNegativeZero() {
        assertEquals(10.13, 10.125001.roundCents(), 1e-9)
        assertEquals("0.0", (-0.001).roundCents().toString())
    }

    @Test
    fun formatNumberGroupsAndTrimsDecimals() {
        assertEquals("1,234.5", formatNumber(1234.5, Locale.US))
        assertEquals("12", formatNumber(12.0, Locale.US))
        assertEquals("0", formatNumber(-0.0, Locale.US))
    }

    @Test
    fun periodRangesAreContiguous() {
        val now = System.currentTimeMillis()
        val (monthStart, monthEnd) = Period.CURRENT_MONTH.range(now)!!
        assertEquals(firstDayOfCurrentMonth(now), monthStart)
        assertEquals(lastDayOfCurrentMonth(now), monthEnd)
        assertEquals(firstDayOfCurrentWeek(now) - 1, lastDayOfLastWeek(now))
        // The day ends one millisecond before the next day starts.
        val end = endOfDay(now)
        assertEquals(end + 1, startOfDay(end + 1))
        assertEquals(true, end >= now)
    }

    @Test
    fun inferPeriodRecognizesStoredRanges() {
        val now = System.currentTimeMillis()
        val (begin, end) = Period.LAST_MONTH.range(now)!!
        assertEquals(Period.LAST_MONTH, inferPeriod(begin, end, now))
        assertEquals(Period.ARBITRARY, inferPeriod(begin + 1, end, now))
    }

    @Test
    fun monthKeyRoundTrips() {
        val date = Calendar.getInstance().apply { set(2024, Calendar.FEBRUARY, 17, 10, 30) }.timeInMillis
        assertEquals(202402, monthKey(date))
        assertEquals(202402, monthKey(monthKeyStart(202402)))
    }

    @Test
    fun calculatorUsesOperatorPrecedence() {
        val calc = CalculatorEngine(0.0)
        "2".forEach(calc::digit)
        calc.operator('+')
        "3".forEach(calc::digit)
        calc.operator('*')
        "4".forEach(calc::digit)
        assertEquals(14.0, calc.preview()!!, 1e-9)
        assertEquals(14.0, calc.result(), 1e-9)
        assertEquals("2 + 3 × 4", calc.expression('.'))
    }

    @Test
    fun calculatorReplacesInitialValueOnFirstDigit() {
        val calc = CalculatorEngine(100.0)
        assertEquals("100", calc.current)
        calc.digit('7')
        assertEquals("7", calc.current)
    }

    @Test
    fun calculatorBackspaceRemovesDigitsAndOperators() {
        val calc = CalculatorEngine(0.0)
        "125".forEach(calc::digit)
        calc.backspace()
        assertEquals("12", calc.current)
        calc.operator('+')
        calc.backspace()
        // The operator is gone and the previous number is editable again.
        assertEquals("12", calc.current)
        assertEquals(null, calc.preview())
        calc.backspace()
        assertEquals("1", calc.current)

        val opened = CalculatorEngine(50.0)
        opened.backspace()
        assertEquals("", opened.current)
        assertEquals(0.0, opened.result(), 1e-9)
    }

    @Test
    fun calculatorDecimalsAfterOperatorAndDivisionByZero() {
        val calc = CalculatorEngine(0.0)
        "5".forEach(calc::digit)
        calc.operator('/')
        calc.digit('.')
        calc.digit('5')
        assertEquals(10.0, calc.result(), 1e-9)

        val zero = CalculatorEngine(0.0)
        zero.digit('7')
        zero.operator('/')
        zero.digit('0')
        assertEquals(0.0, zero.result(), 1e-9)
    }

    @Test
    fun csvParserHandlesQuotesAndFirstRow() {
        val text = "header,row\n01.02.24 10:00,\"Food, drinks\",\"multi\nline\"\r\n\n02.02.24 11:00,x,y\n"
        val rows = CsvParser.parse(text, Separator.COMMA, firstRow = 2)
        assertEquals(2, rows.size)
        assertEquals(listOf("01.02.24 10:00", "Food, drinks", "multi line"), rows[0])
        assertEquals(listOf("02.02.24 11:00", "x", "y"), rows[1])
        assertEquals(listOf("a", "b;c"), CsvParser.parse("a;\"b;c\"", Separator.SEMICOLON, 1)[0])
    }
}

package kz.alem.corporation.fintracker.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kz.alem.corporation.fintracker.R
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols

/**
 * Expression calculator ported from the previous Android app: numbers and operators are
 * collected as tokens and evaluated with the usual precedence (× ÷ before + −). The value
 * shown on open is replaced by the first digit typed.
 */
class CalculatorEngine(initial: Double) {
    /** Completed numbers (Double) and operators (Char: + - * /). */
    private val parts = mutableListOf<Any>()

    /** Number being typed, '.' as decimal separator. */
    var current: String = if (initial != 0.0) plain(initial) else ""
        private set
    private var freshOpen = true

    fun digit(d: Char) {
        if (freshOpen) {
            current = if (d == '.') "0." else d.toString()
            freshOpen = false
            return
        }
        current = when {
            d == '.' && current.contains('.') -> current
            d == '.' && current.isEmpty() -> "0."
            d == '.' && current == "-" -> "-0."
            current == "0" -> d.toString()
            current == "-0" -> "-$d"
            else -> current + d
        }
    }

    fun operator(op: Char) {
        freshOpen = false
        when {
            current.isNotEmpty() && current != "-" -> {
                parts += current.toDoubleOrNull() ?: 0.0
                parts += op
                current = ""
            }
            // Pressing another operator replaces the pending one.
            parts.isNotEmpty() && parts.last() is Char -> parts[parts.lastIndex] = op
        }
    }

    fun equals() {
        freshOpen = false
        evaluate(tokens())?.let {
            parts.clear()
            current = plain(it)
        }
    }

    fun negate() {
        freshOpen = false
        current = when {
            current.isEmpty() -> "-"
            current == "-" -> ""
            current.startsWith("-") -> current.drop(1)
            else -> "-$current"
        }
    }

    fun percent() {
        freshOpen = false
        current.toDoubleOrNull()?.let { current = plain(it / 100.0) }
    }

    fun clear() {
        freshOpen = false
        parts.clear()
        current = ""
    }

    /** Deletes the last digit; on an operator, removes it and resumes the number before it. */
    fun backspace() {
        if (freshOpen) {
            freshOpen = false
            current = ""
            return
        }
        if (current.isNotEmpty()) {
            current = current.dropLast(1)
            return
        }
        if (parts.lastOrNull() is Char) {
            parts.removeAt(parts.lastIndex)
            val number = parts.removeLastOrNull() as? Double
            current = number?.let(::plain) ?: ""
        }
    }

    /** The expression as typed, e.g. "12 + 3 ×". */
    fun expression(separator: Char): String = buildString {
        parts.forEach { part ->
            if (part is Char) append(" ${glyph(part)} ") else append(plain(part as Double).replace('.', separator))
        }
        append(current.replace('.', separator))
    }.ifBlank { "0" }

    /** Live result while an expression is pending, or null. */
    fun preview(): Double? = if (parts.isEmpty()) null else evaluate(tokens())

    /** Final value for the ✓ key. */
    fun result(): Double = evaluate(tokens()) ?: 0.0

    private fun tokens(): List<Any> {
        val tokens = parts.toMutableList()
        if (current.isNotEmpty() && current != "-") tokens += current.toDoubleOrNull() ?: 0.0
        while (tokens.lastOrNull() is Char) tokens.removeAt(tokens.lastIndex)
        return tokens
    }

    companion object {
        private const val MAX_FRACTION = 6

        fun plain(value: Double): String {
            if (value.isNaN() || value.isInfinite()) return "0"
            val text = BigDecimal.valueOf(value).setScale(MAX_FRACTION, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
            return if (text == "-0") "0" else text
        }

        private fun glyph(op: Char) = when (op) {
            '-' -> "−"
            '*' -> "×"
            '/' -> "÷"
            else -> op.toString()
        }

        /** × and ÷ first, then + and − left to right; null on division by zero. */
        private fun evaluate(input: List<Any>): Double? {
            if (input.isEmpty()) return null
            val tokens = input.toMutableList()
            var i = 1
            while (i < tokens.size) {
                val op = tokens[i]
                if (op == '*' || op == '/') {
                    val a = tokens[i - 1] as? Double ?: return null
                    val b = tokens[i + 1] as? Double ?: return null
                    tokens[i - 1] = if (op == '*') a * b else if (b == 0.0) return null else a / b
                    tokens.removeAt(i)
                    tokens.removeAt(i)
                } else {
                    i += 2
                }
            }
            var result = tokens[0] as? Double ?: return null
            var j = 1
            while (j < tokens.size) {
                val op = tokens[j] as? Char ?: return null
                val b = tokens[j + 1] as? Double ?: return null
                result = if (op == '+') result + b else result - b
                j += 2
            }
            return result
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorSheet(initial: Double, onDone: (Double) -> Unit, onDismiss: () -> Unit) {
    val engine = remember { CalculatorEngine(initial) }
    // Bumped after every key so the display re-reads the engine.
    var tick by remember { mutableIntStateOf(0) }
    val press: (CalculatorEngine.() -> Unit) -> Unit = { action ->
        engine.action()
        tick++
    }
    val separator = DecimalFormatSymbols.getInstance(appLocale).decimalSeparator
    val locale = appLocale

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val expression = remember(tick) { engine.expression(separator) }
            val preview = remember(tick) { engine.preview() }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        expression,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.StartEllipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (preview != null) {
                        Text(
                            "= ${Formats.number(preview, locale)}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                IconButton(onClick = { press { backspace() } }) {
                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = stringResource(R.string.backspace))
                }
            }

            KeyRow {
                Key("AC") { press { clear() } }
                Key("±") { press { negate() } }
                Key("%") { press { percent() } }
                Key("÷", accent = true) { press { operator('/') } }
            }
            KeyRow {
                Key("7") { press { digit('7') } }
                Key("8") { press { digit('8') } }
                Key("9") { press { digit('9') } }
                Key("×", accent = true) { press { operator('*') } }
            }
            KeyRow {
                Key("4") { press { digit('4') } }
                Key("5") { press { digit('5') } }
                Key("6") { press { digit('6') } }
                Key("−", accent = true) { press { operator('-') } }
            }
            KeyRow {
                Key("1") { press { digit('1') } }
                Key("2") { press { digit('2') } }
                Key("3") { press { digit('3') } }
                Key("+", accent = true) { press { operator('+') } }
            }
            KeyRow {
                Key("=", accent = true) { press { equals() } }
                Key("0") { press { digit('0') } }
                Key(separator.toString()) { press { digit('.') } }
                Key("✓", accent = true) { onDone(engine.result()) }
            }
        }
    }
}

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) =
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)

@Composable
private fun RowScope.Key(text: String, accent: Boolean = false, onClick: () -> Unit) {
    val modifier = Modifier
        .weight(1f)
        .height(56.dp)
    if (accent) {
        Button(onClick = onClick, modifier = modifier) { Text(text, style = MaterialTheme.typography.titleLarge) }
    } else {
        FilledTonalButton(onClick = onClick, modifier = modifier) { Text(text, style = MaterialTheme.typography.titleLarge) }
    }
}

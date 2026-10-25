package kz.alem.corporation.fintracker.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Amount input: a read-only outlined field showing the formatted value; tapping it opens
 * the calculator sheet. A zero value shows as empty unless [showZero]. [contentPadding] is the spacing around the field inside a form.
 */
@Composable
fun AmountField(
    value: Double,
    onValueChange: (Double) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    suffix: String? = null,
    placeholder: String = "0",
    errorText: String? = null,
    borderless: Boolean = false,
    isError: Boolean = errorText != null,
    showZero: Boolean = false,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val interactionSource = rememberTapInteractionSource { open = true }
    FormTextField(
        value = if (value == 0.0 && !showZero) "" else Formats.number(value, appLocale),
        onValueChange = {},
        borderless = borderless,
        readOnly = true,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        suffix = suffix?.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
        isError = isError,
        supportingText = errorText?.let { { Text(it) } },
        interactionSource = interactionSource,
        modifier = modifier
            .padding(if (borderless) PaddingValues() else contentPadding),
    )
    if (open) {
        CalculatorSheet(
            initial = value,
            onDone = {
                onValueChange(it)
                open = false
            },
            onDismiss = { open = false },
        )
    }
}

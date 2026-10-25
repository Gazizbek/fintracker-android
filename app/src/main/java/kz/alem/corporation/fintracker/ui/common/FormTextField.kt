package kz.alem.corporation.fintracker.ui.common

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

/**
 * Text field used by form components. Outlined by default; [borderless] gives a filled field
 * with no container or indicator, so it reads as a list row inside a card (separate rows with
 * dividers). Its content starts 16dp in, in line with list-item labels and inset dividers.
 * [padded] adds the usual form spacing around an outlined field.
 */
@Composable
internal fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    borderless: Boolean,
    modifier: Modifier = Modifier,
    padded: Boolean = true,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    interactionSource: MutableInteractionSource? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    // A read-only field is a button (opens a picker, menu or calculator), not text: the inner
    // text field is disabled so its value can't be focused or selected (no Copy / Read aloud
    // menu on long press), it keeps the enabled colors, and a clickable on top reports taps to
    // [source] and draws the ripple.
    val asButton = readOnly && enabled
    val tap = if (asButton) Modifier.clickable(interactionSource = source, indication = LocalIndication.current, role = Role.Button) {} else Modifier
    val textEnabled = enabled && !asButton
    if (borderless) {
        TextField(
            value = value, onValueChange = onValueChange, modifier = modifier.fillMaxWidth().then(tap),
            readOnly = readOnly, enabled = textEnabled, singleLine = singleLine, minLines = minLines, maxLines = maxLines,
            label = label, placeholder = placeholder, suffix = suffix, trailingIcon = trailingIcon,
            supportingText = supportingText, isError = isError, interactionSource = if (asButton) null else source,
            keyboardOptions = keyboardOptions,
            colors = borderlessFieldColors(enabledLook = asButton, isError = isError),
        )
    } else {
        OutlinedTextField(
            value = value, onValueChange = onValueChange,
            modifier = (if (padded) modifier.padding(horizontal = 16.dp, vertical = 8.dp) else modifier).fillMaxWidth().then(tap),
            readOnly = readOnly, enabled = textEnabled, singleLine = singleLine, minLines = minLines, maxLines = maxLines,
            label = label, placeholder = placeholder, suffix = suffix, trailingIcon = trailingIcon,
            supportingText = supportingText, isError = isError, interactionSource = if (asButton) null else source,
            keyboardOptions = keyboardOptions,
            colors = if (asButton) outlinedButtonFieldColors(isError) else OutlinedTextFieldDefaults.colors(),
        )
    }
}

/** Disabled-state colors that match an enabled, unfocused field (red when [isError]). */
@Composable
private fun enabledLookColors(isError: Boolean): EnabledLook {
    val scheme = MaterialTheme.colorScheme
    return EnabledLook(
        text = scheme.onSurface,
        label = if (isError) scheme.error else scheme.onSurfaceVariant,
        icon = if (isError) scheme.error else scheme.onSurfaceVariant,
        secondary = scheme.onSurfaceVariant,
        supporting = if (isError) scheme.error else scheme.onSurfaceVariant,
        border = if (isError) scheme.error else scheme.outline,
    )
}

private class EnabledLook(val text: Color, val label: Color, val icon: Color, val secondary: Color, val supporting: Color, val border: Color)

@Composable
private fun outlinedButtonFieldColors(isError: Boolean): TextFieldColors {
    val look = enabledLookColors(isError)
    return OutlinedTextFieldDefaults.colors(
        disabledTextColor = look.text,
        disabledLabelColor = look.label,
        disabledTrailingIconColor = look.icon,
        disabledSuffixColor = look.secondary,
        disabledPlaceholderColor = look.secondary,
        disabledSupportingTextColor = look.supporting,
        disabledBorderColor = look.border,
    )
}

@Composable
internal fun borderlessFieldColors(enabledLook: Boolean = false, isError: Boolean = false): TextFieldColors {
    val look = enabledLookColors(isError)
    val disabled = if (enabledLook) {
        TextFieldDefaults.colors(
            disabledTextColor = look.text,
            disabledLabelColor = look.label,
            disabledTrailingIconColor = look.icon,
            disabledSuffixColor = look.secondary,
            disabledPlaceholderColor = look.secondary,
            disabledSupportingTextColor = look.supporting,
        )
    } else {
        TextFieldDefaults.colors()
    }
    return disabled.copy(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        errorContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
    )
}

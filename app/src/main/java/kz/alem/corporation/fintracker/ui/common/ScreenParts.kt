package kz.alem.corporation.fintracker.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kz.alem.corporation.fintracker.R
import kz.alem.corporation.fintracker.ui.navigation.LocalNavigator

@Composable
fun BackButton() {
    val navigator = LocalNavigator.current
    IconButton(onClick = { navigator.back() }) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
    }
}

/** Outlined text field with an inline validation message. */
@Composable
fun RequiredField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    trailing: (@Composable () -> Unit)? = null,
    borderless: Boolean = false,
    isError: Boolean = error != null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
) {
    FormTextField(
        value = value,
        onValueChange = onValueChange,
        borderless = borderless,
        label = { Text(label) },
        isError = isError,
        supportingText = error?.let { { Text(it) } },
        trailingIcon = trailing,
        keyboardOptions = keyboardOptions,
    )
}

/** Always-visible search field under the top bar (iOS .searchable). */
@Composable
fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.search)) },
        leadingIcon = { Icon(androidx.compose.material.icons.Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(androidx.compose.material.icons.Icons.Filled.Clear, contentDescription = null)
                }
            }
        } else null,
        singleLine = true,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        colors = androidx.compose.material3.TextFieldDefaults.colors(
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * Interaction source that reports taps, for read-only fields that open a picker. The tap
 * would also focus the field (and it would stay highlighted after the picker closes), so
 * focus is cleared before [onTap] runs.
 */
@Composable
fun rememberTapInteractionSource(onTap: () -> Unit): androidx.compose.foundation.interaction.MutableInteractionSource {
    val source = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val latest = androidx.compose.runtime.rememberUpdatedState(onTap)
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    androidx.compose.runtime.LaunchedEffect(source) {
        source.interactions.collect {
            if (it is androidx.compose.foundation.interaction.PressInteraction.Release) {
                focusManager.clearFocus()
                latest.value()
            }
        }
    }
    return source
}

/**
 * Read-only field (outlined, or [borderless]) that opens a picker when tapped. [suffix] shows extra info at
 * the right end of the field (e.g. an account balance); [supporting] shows it below;
 * [onAdd] adds a "+" to create a new record.
 */
@Composable
fun PickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
    errorText: String? = null,
    supporting: String? = null,
    suffix: String? = null,
    onAdd: (() -> Unit)? = null,
    borderless: Boolean = false,
    isError: Boolean = errorText != null,
    /** False lets a long value (e.g. several chosen items) wrap instead of being cut off. */
    singleLine: Boolean = true,
) {
    FormTextField(
        value = value,
        onValueChange = {},
        borderless = borderless,
        readOnly = true,
        singleLine = singleLine,
        label = { Text(label) },
        isError = isError,
        supportingText = (errorText ?: supporting)?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        trailingIcon = {
            if (onAdd != null) {
                IconButton(onClick = onAdd) { Icon(androidx.compose.material.icons.Icons.Filled.Add, contentDescription = stringResource(R.string.add)) }
            } else {
                Icon(androidx.compose.material.icons.Icons.Filled.ArrowDropDown, contentDescription = null)
            }
        },
        interactionSource = rememberTapInteractionSource(onClick),
    )
}

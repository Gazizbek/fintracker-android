package kz.alem.corporation.fintracker.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kz.alem.corporation.fintracker.R

/** Grid of icons; tapping one selects it and closes the sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> IconPickerSheet(
    icons: List<T>,
    selected: T,
    imageVector: (T) -> ImageVector,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.chooseIcon),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            items(icons) { icon ->
                val isSelected = icon == selected
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .border(
                            width = if (isSelected) 2.dp else 0.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = CircleShape,
                        )
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable {
                            onSelect(icon)
                            onDismiss()
                        },
                ) {
                    Icon(imageVector(icon), contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private val palette = listOf(
    0xFF007AFF, 0xFF34C759, 0xFFFF3B30, 0xFFFF9500, 0xFFFFCC00, 0xFFAF52DE,
    0xFF5856D6, 0xFFFF2D55, 0xFF5AC8FA, 0xFF00C7BE, 0xFFA2845E, 0xFF8E8E93,
    0xFF1B5E20, 0xFF0D47A1, 0xFFB71C1C, 0xFFE65100, 0xFF4A148C, 0xFF263238,
).map { Color(it) }

/** Palette plus HSV sliders, the counterpart of the iOS ColorPicker. */
@Composable
fun ColorPickerDialog(initial: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    val color = Color.hsv(hue, saturation, value)

    fun setFrom(c: Color) {
        val out = FloatArray(3)
        android.graphics.Color.colorToHSV(c.toArgb(), out)
        hue = out[0]
        saturation = out[1]
        value = out[2]
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.color)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(color),
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(140.dp),
                ) {
                    items(palette) { swatch ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(swatch)
                                .clickable { setFrom(swatch) },
                        ) {
                            if (swatch.toArgb() == color.toArgb()) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                            }
                        }
                    }
                }
                Text(stringResource(R.string.hue), style = MaterialTheme.typography.labelMedium)
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                Text(stringResource(R.string.saturation), style = MaterialTheme.typography.labelMedium)
                Slider(value = saturation, onValueChange = { saturation = it })
                Text(stringResource(R.string.brightness), style = MaterialTheme.typography.labelMedium)
                Slider(value = value, onValueChange = { value = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSelect(color.toArgb())
                onDismiss()
            }) { Text(stringResource(R.string.choose)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Form row showing the current color; opens [ColorPickerDialog]. */
@Composable
fun ColorRow(color: Int, onChange: (Int) -> Unit) {
    var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.material3.ListItem(
        headlineContent = { Text(stringResource(R.string.color)) },
        trailingContent = {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(color)),
            )
        },
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { open = true },
    )
    if (open) ColorPickerDialog(initial = color, onSelect = onChange, onDismiss = { open = false })
}

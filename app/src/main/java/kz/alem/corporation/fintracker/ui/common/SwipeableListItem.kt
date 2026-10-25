package kz.alem.corporation.fintracker.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kz.alem.corporation.fintracker.R
import kz.alem.corporation.fintracker.ui.theme.BrandBlue
import kotlin.math.roundToInt

/**
 * List row that slides left to reveal two actions (primary, Edit by default, and Delete),
 * ported from the previous Android app. Delete asks for confirmation. Tapping the row runs
 * [onTap] (or [onEdit]); tapping while revealed closes it.
 */
@Composable
fun SwipeableListItem(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    primaryIcon: ImageVector = Icons.Filled.Edit,
    primaryContentDescription: String = stringResource(R.string.edit),
    onTap: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val actionsWidth = 112.dp
    val actionsWidthPx = with(density) { actionsWidth.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val revealed = offsetX.value <= -actionsWidthPx / 2f
    var confirmDelete by remember { mutableStateOf(false) }

    Box(modifier = modifier.clipToBounds()) {
        Row(
            modifier = Modifier
                .matchParentSize()
                .background(BrandBlue)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = {
                scope.launch { offsetX.animateTo(0f) }
                onEdit()
            }) {
                Icon(primaryIcon, contentDescription = primaryContentDescription, tint = Color.White)
            }
            IconButton(onClick = {
                scope.launch { offsetX.animateTo(0f) }
                confirmDelete = true
            }) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete), tint = Color.White)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(actionsWidthPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val target = if (offsetX.value <= -actionsWidthPx / 2f) -actionsWidthPx else 0f
                            scope.launch { offsetX.animateTo(target) }
                        },
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            val newX = (offsetX.value + delta).coerceIn(-actionsWidthPx, 0f)
                            scope.launch { offsetX.snapTo(newX) }
                        },
                    )
                }
                .clickable {
                    if (revealed) scope.launch { offsetX.animateTo(0f) } else (onTap ?: onEdit)()
                },
        ) {
            content()
        }
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(onConfirm = onDelete, onDismiss = { confirmDelete = false })
    }
}

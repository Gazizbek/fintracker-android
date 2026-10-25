package kz.alem.corporation.fintracker.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Shakes [content] sideways when [trigger] changes while [active] is true: the iOS `.shake`
 * modifier used on required fields (two back-and-forth swings of 8pt over 0.4 s).
 */
@Composable
fun ShakeBox(trigger: Int, active: Boolean, content: @Composable () -> Unit) {
    val offset = remember { Animatable(0f) }
    val distance = with(LocalDensity.current) { 8.dp.toPx() }
    val isActive by rememberUpdatedState(active)
    LaunchedEffect(trigger) {
        if (trigger == 0 || !isActive) return@LaunchedEffect
        repeat(2) {
            offset.animateTo(distance, tween(90, easing = LinearEasing))
            offset.animateTo(-distance, tween(90, easing = LinearEasing))
        }
        offset.animateTo(0f, tween(40, easing = LinearEasing))
    }
    Box(Modifier.graphicsLayer { translationX = offset.value }) { content() }
}

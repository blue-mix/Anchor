package com.example.anchor.core.util

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

// ── Swipe thresholds ──────────────────────────────────────────
private const val SWIPE_THRESHOLD_PX = 50f
private const val VELOCITY_THRESHOLD = 100f   // reserved for future velocity-based detection

/**
 * Detects a horizontal swipe and fires [onSwipe] with the total drag delta
 * (positive = right, negative = left) when the gesture exceeds the threshold.
 *
 * Typical use: scrubbing seek position in the player.
 */
fun Modifier.horizontalSwipeGesture(
    threshold: Float = SWIPE_THRESHOLD_PX,
    onSwipe: (deltaX: Float) -> Unit
): Modifier = this.pointerInput(Unit) {
    var accumulated = 0f
    detectHorizontalDragGestures(
        onDragStart = { accumulated = 0f },
        onDragEnd = {
            if (abs(accumulated) >= threshold) onSwipe(accumulated)
        },
        onHorizontalDrag = { _, delta -> accumulated += delta }
    )
}

/**
 * Detects vertical swipes and routes them to the correct callback based on
 * which horizontal half of the component the gesture started in.
 *
 * - Left half  → [onLeftSideSwipe]  (conventionally: brightness)
 * - Right half → [onRightSideSwipe] (conventionally: volume)
 *
 * Delta is inverted so that swiping UP produces a positive value (increase).
 */
fun Modifier.verticalSwipeGesture(
    threshold: Float = SWIPE_THRESHOLD_PX,
    onLeftSideSwipe: (delta: Float) -> Unit,
    onRightSideSwipe: (delta: Float) -> Unit
): Modifier = this.pointerInput(Unit) {
    var startX = 0f
    var accumulated = 0f
    detectVerticalDragGestures(
        onDragStart = { offset ->
            startX = offset.x
            accumulated = 0f
        },
        onDragEnd = {
            if (abs(accumulated) >= threshold) {
                val inverted = -accumulated    // up = positive
                if (startX < size.width / 2f) onLeftSideSwipe(inverted)
                else onRightSideSwipe(inverted)
            }
        },
        onVerticalDrag = { _, delta -> accumulated += delta }
    )
}

/**
 * Detects a single tap anywhere on the component.
 * Lighter than [detectTapGestures] when you only need the tap event.
 */
fun Modifier.onSingleTap(
    onTap: () -> Unit
): Modifier = this.pointerInput(Unit) {
    detectTapGestures(onTap = { onTap() })
}

/**
 * Detects both single tap and double tap.
 *
 * @param onTap       fired on a single tap (after the double-tap window passes)
 * @param onDoubleTap fired on a double tap
 */
fun Modifier.onTapGestures(
    onTap: (() -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null
): Modifier = this.pointerInput(Unit) {
    detectTapGestures(
        onTap = onTap?.let { cb -> { cb() } },
        onDoubleTap = onDoubleTap?.let { cb -> { cb() } }
    )
}
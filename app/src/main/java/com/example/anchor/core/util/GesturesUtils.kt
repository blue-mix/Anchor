// app/src/main/java/com/example/anchor/utils/GestureUtils.kt

package com.example.anchor.core.util

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

/**
 * Modifier for detecting horizontal swipe gestures for seeking.
 */
fun Modifier.horizontalSwipeGesture(
    onSwipe: (Float) -> Unit
): Modifier = this.pointerInput(Unit) {
    var totalDragAmount = 0f

    detectHorizontalDragGestures(
        onDragStart = { totalDragAmount = 0f },
        onDragEnd = {
            if (abs(totalDragAmount) > 50) {
                onSwipe(totalDragAmount)
            }
        },
        onHorizontalDrag = { _, dragAmount ->
            totalDragAmount += dragAmount
        }
    )
}

/**
 * Modifier for detecting vertical swipe gestures for volume/brightness.
 */
fun Modifier.verticalSwipeGesture(
    onLeftSideSwipe: (Float) -> Unit,  // For brightness
    onRightSideSwipe: (Float) -> Unit  // For volume
): Modifier = this.pointerInput(Unit) {
    var startX = 0f
    var totalDragAmount = 0f

    detectVerticalDragGestures(
        onDragStart = { offset ->
            startX = offset.x
            totalDragAmount = 0f
        },
        onDragEnd = {
            if (abs(totalDragAmount) > 50) {
                val isLeftSide = startX < size.width / 2
                if (isLeftSide) {
                    onLeftSideSwipe(-totalDragAmount) // Inverted for natural feel
                } else {
                    onRightSideSwipe(-totalDragAmount)
                }
            }
        },
        onVerticalDrag = { _, dragAmount ->
            totalDragAmount += dragAmount
        }
    )
}

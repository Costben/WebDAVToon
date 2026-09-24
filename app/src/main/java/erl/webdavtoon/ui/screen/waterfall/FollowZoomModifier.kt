// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.waterfall

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged

/**
 * Two-finger pinch detector that reports the raw zoom factor and the centroid.
 *
 * Unlike a fixed-step detector this keeps every intermediate zoom value, which is
 * what lets [FollowZoomWaterfallLayout] continuously reflow the visible window
 * while the fingers are moving.
 */
suspend fun PointerInputScope.detectFollowZoomGestures(
    onGestureStart: (centroid: Offset) -> Unit,
    onGesture: (centroid: Offset, zoomChange: Float) -> Unit,
    onGestureEnd: () -> Unit
) {
    var isZooming = false
    try {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)

            do {
                val event = awaitPointerEvent()
                val pressedPointers = event.changes.filter { it.pressed }
                val pointerCount = pressedPointers.size

                if (pointerCount >= 2) {
                    val zoomChange = event.calculateZoom()
                    val centroid = event.calculateCentroid(useCurrent = false)

                    if (!isZooming) {
                        if (zoomChange != 1.0f) {
                            isZooming = true
                            onGestureStart(centroid)
                            onGesture(centroid, zoomChange)
                            event.changes.forEach { change ->
                                if (change.positionChanged()) {
                                    change.consume()
                                }
                            }
                        }
                    } else {
                        if (zoomChange != 1.0f) {
                            onGesture(centroid, zoomChange)
                        }
                        event.changes.forEach { change ->
                            if (change.positionChanged()) {
                                change.consume()
                            }
                        }
                    }
                } else {
                    if (isZooming) {
                        isZooming = false
                        onGestureEnd()
                    }
                }
            } while (event.changes.any { it.pressed })

            if (isZooming) {
                isZooming = false
                onGestureEnd()
            }
        }
    } finally {
        if (isZooming) {
            isZooming = false
            onGestureEnd()
        }
    }
}

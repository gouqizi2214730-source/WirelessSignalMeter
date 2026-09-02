package com.craxiom.networksurvey.ui.kernelsu

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange

/**
 * 拖动手势检查器：支持监听按压、拖动开始、拖动中、拖动结束/取消。
 */
suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: ((Offset) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    onDragCancel: (() -> Unit)? = null,
    onPress: ((Offset) -> Unit)? = null,
    onRelease: (() -> Unit)? = null,
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onPress?.invoke(down.position)
        var dragStarted = false
        var isCanceled = false

        try {
            val dragSuccess = drag(down.id) { change ->
                if (!dragStarted) {
                    dragStarted = true
                    onDragStart?.invoke(change.position)
                }
                val dragAmount = change.positionChange()
                change.consume()
                onDrag(change, dragAmount)
            }
            if (dragStarted) {
                if (dragSuccess) {
                    onDragEnd?.invoke()
                } else {
                    onDragCancel?.invoke()
                }
            }
        } catch (e: Exception) {
            isCanceled = true
            if (dragStarted) onDragCancel?.invoke()
        } finally {
            onRelease?.invoke()
        }
    }
}

fun Modifier.inspectDragGestures(
    onDragStart: ((Offset) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    onDragCancel: (() -> Unit)? = null,
    onPress: ((Offset) -> Unit)? = null,
    onRelease: (() -> Unit)? = null,
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
): Modifier = pointerInput(Unit) {
    inspectDragGestures(
        onDragStart = onDragStart,
        onDragEnd = onDragEnd,
        onDragCancel = onDragCancel,
        onPress = onPress,
        onRelease = onRelease,
        onDrag = onDrag
    )
}

package com.craxiom.networksurvey.ui.kernelsu

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 阻尼拖动动画控制器（KernelSU Liquid 气泡物理引擎）
 */
class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    private val initialValue: Float,
    private val valueRange: ClosedFloatingPointRange<Float>,
    private val visibilityThreshold: Float = 0.001f,
    private val initialScale: Float = 1.0f,
    private val pressedScale: Float = 0.95f,
    private val canDrag: () -> Boolean = { true },
    private val onDragStarted: (Offset) -> Unit = {},
    private val onDragStopped: (Float) -> Unit = {},
    private val onDrag: (dragAmount: Offset) -> Unit = {}
) {
    var isDragging: Boolean = false
        private set

    private val mutatorMutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()

    private val valueAnimationSpec: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = visibilityThreshold
    )

    private val scaleXAnimationSpec: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = visibilityThreshold
    )

    private val scaleYAnimationSpec: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = visibilityThreshold
    )

    private val pressProgressAnimationSpec: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
        visibilityThreshold = visibilityThreshold
    )

    private val velocityAnimationSpec: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
        visibilityThreshold = visibilityThreshold
    )

    val valueAnimation: Animatable<Float, AnimationVector1D> = Animatable(initialValue, visibilityThreshold)
    val scaleXAnimation: Animatable<Float, AnimationVector1D> = Animatable(initialScale, visibilityThreshold)
    val scaleYAnimation: Animatable<Float, AnimationVector1D> = Animatable(initialScale, visibilityThreshold)
    val pressProgressAnimation: Animatable<Float, AnimationVector1D> = Animatable(0f, visibilityThreshold)
    val velocityAnimation: Animatable<Float, AnimationVector1D> = Animatable(0f, visibilityThreshold)

    val value: Float get() = valueAnimation.value
    val targetValue: Float get() = valueAnimation.targetValue
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val pressProgress: Float get() = pressProgressAnimation.value
    val velocity: Float get() = velocityAnimation.value

    fun snapTo(target: Float) {
        animationScope.launch {
            valueAnimation.snapTo(target.coerceIn(valueRange))
        }
    }

    fun animateToValue(target: Float) {
        animationScope.launch {
            valueAnimation.animateTo(
                targetValue = target.coerceIn(valueRange),
                animationSpec = valueAnimationSpec
            )
        }
    }

    fun updateValue(target: Float) {
        animateToValue(target)
    }

    private fun press(position: Offset) {
        animationScope.launch {
            pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec)
        }
        animationScope.launch {
            scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec)
            scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec)
        }
    }

    private fun release() {
        animationScope.launch {
            pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec)
        }
        animationScope.launch {
            scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec)
            scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec)
        }
    }

    val modifier: Modifier = Modifier.inspectDragGestures(
        onPress = { pos ->
            if (canDrag()) {
                velocityTracker.resetTracking()
                press(pos)
            }
        },
        onRelease = {
            if (canDrag()) {
                release()
            }
        },
        onDragStart = { pos ->
            if (canDrag()) {
                isDragging = true
                onDragStarted(pos)
            }
        },
        onDragEnd = {
            if (canDrag()) {
                isDragging = false
                val v = velocityTracker.calculateVelocity().x
                onDragStopped(v)
            }
        },
        onDragCancel = {
            if (canDrag()) {
                isDragging = false
                onDragStopped(0f)
            }
        },
        onDrag = { change, dragAmount ->
            if (canDrag()) {
                velocityTracker.addPointerInputChange(change)
                onDrag(dragAmount)
            }
        }
    )
}

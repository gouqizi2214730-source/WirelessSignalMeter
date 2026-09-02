package com.craxiom.networksurvey.ui.kernelsu

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 交互式高光效果（基于 AGSL RuntimeShader）
 */
class InteractiveHighlight(
    private val animationScope: CoroutineScope,
    private val position: () -> Offset = { Offset.Zero }
) {
    private val pressProgressAnimationSpec: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
        visibilityThreshold = 0.001f
    )

    private val positionAnimationSpec: SpringSpec<Offset> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
        visibilityThreshold = Offset(1f, 1f)
    )

    private val pressProgressAnimation: Animatable<Float, AnimationVector1D> = Animatable(0f, 0.001f)
    private val positionAnimation: Animatable<Offset, AnimationVector2D> = Animatable(Offset.Zero, Offset.VectorConverter, Offset(1f, 1f))

    private val shader: RuntimeShader? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        RuntimeShader(
            """
            uniform float2 size;
            layout(color) uniform half4 color;
            uniform float radius;
            uniform float2 position;
            
            half4 main(float2 coord) {
                float dist = distance(coord, position);
                float intensity = smoothstep(radius, radius * 0.5, dist);
                return color * intensity;
            }
            """.trimIndent()
        )
    } else null

    val gestureModifier: Modifier = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            animationScope.launch {
                positionAnimation.snapTo(down.position)
                pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec)
            }
            try {
                drag(down.id) { change ->
                    val pos = change.position
                    change.consume()
                    animationScope.launch {
                        positionAnimation.snapTo(pos)
                    }
                }
            } finally {
                animationScope.launch {
                    pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec)
                }
            }
        }
    }

    val modifier: Modifier = Modifier.drawWithCache {
        val s = shader
        if (s != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val brush = ShaderBrush(s)
            val currentPos = if (positionAnimation.value != Offset.Zero) positionAnimation.value else position()
            onDrawWithContent {
                drawContent()
                val progress = pressProgressAnimation.value
                if (progress > 0.001f) {
                    s.setFloatUniform("size", size.width, size.height)
                    s.setColorUniform("color", android.graphics.Color.valueOf(1f, 1f, 1f, 0.3f * progress))
                    s.setFloatUniform("radius", size.height * 1.5f)
                    s.setFloatUniform("position", currentPos.x, currentPos.y)
                    drawRect(brush)
                }
            }
        } else {
            onDrawWithContent { drawContent() }
        }
    }
}

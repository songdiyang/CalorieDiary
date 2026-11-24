package com.calorie.diary.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.random.Random

private const val EFFECT_DURATION_MS = 2600

private data class RainGrain(
    val xRatio: Float,
    val startRatio: Float,
    val delay: Float,
    val fallDuration: Float,
    val size: Float,
    val phase: Float,
    val spin: Float,
    val rotation: Float,
    val grain: Boolean
)

private fun buildRainGrains(count: Int): List<RainGrain> {
    val random = Random(seed = 20261123)
    return List(count) { index ->
        val grain = index < count * 0.72f
        RainGrain(
            xRatio = random.nextFloat(),
            startRatio = -0.12f + random.nextFloat() * 0.28f,
            delay = random.nextFloat() * 0.5f,
            fallDuration = 0.95f + random.nextFloat() * 0.55f,
            size = 0.7f + random.nextFloat() * 0.8f,
            phase = random.nextFloat() * 6.28f,
            spin = -220f + random.nextFloat() * 440f,
            rotation = random.nextFloat() * 360f,
            grain = grain
        )
    }
}

/**
 * 全屏米饭特效：米粒从屏幕上方陆续“复制”出现并旋转落下，持续约 2.6 秒后自动结束。
 * 纯绘制、不拦截触摸事件，覆盖在聊天页最上层。
 */
@Composable
fun RiceRainEffect(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }
    val items = remember { buildRainGrains(88) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(EFFECT_DURATION_MS, easing = LinearEasing)
        )
        onFinished()
    }

    Canvas(modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        items.forEach { item ->
            val local = (progress.value - item.delay) / item.fallDuration
            if (local <= 0f) return@forEach
            val t = local.coerceIn(0f, 1f)
            val appear = (t / 0.1f).coerceIn(0f, 1f)
            val alpha = if (t > 0.86f) ((1f - t) / 0.14f).coerceIn(0f, 1f) else 1f
            val sway = sin(item.phase + t * 6.5f) * width * 0.035f
            val x = item.xRatio * width + sway
            val y = (item.startRatio + t * 1.18f) * height
            val base = width * 0.016f * item.size * appear
            rotate(degrees = item.rotation + t * item.spin, pivot = Offset(x, y)) {
                if (item.grain) {
                    val grainWidth = base * 1.7f
                    val grainHeight = base * 3.1f
                    drawOval(
                        color = RiceGrainColor.copy(alpha = alpha),
                        topLeft = Offset(x - grainWidth / 2f, y - grainHeight / 2f),
                        size = Size(grainWidth, grainHeight)
                    )
                    drawOval(
                        color = RiceEdgeColor.copy(alpha = alpha * 0.85f),
                        topLeft = Offset(x - grainWidth / 2f, y - grainHeight / 2f),
                        size = Size(grainWidth, grainHeight),
                        style = Stroke(width = 1.dp.toPx())
                    )
                } else {
                    drawCircle(
                        color = SparkleColor.copy(alpha = alpha * 0.9f),
                        radius = base * 0.55f,
                        center = Offset(x, y)
                    )
                }
            }
        }
    }
}

private val RiceGrainColor = Color(0xFFFFF9E8)
private val RiceEdgeColor = Color(0xFFE7C98F)
private val SparkleColor = Color(0xFFFFD54F)
package com.calorie.diary.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.calorie.diary.ui.theme.InkFaint
import com.calorie.diary.ui.theme.Orange
import kotlin.math.cos
import kotlin.math.sin

/**
 * 点状进度圆环：一整圈小圆点代表 100%，按进度从 12 点方向顺时针点亮。
 * 轨道点与进度点共用同一半径，只有一圈，避免"外圈点 + 内圈粗环"的双环杂乱感。
 */
@Composable
fun CuteProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    ringColor: Color = Orange,
    trackColor: Color = InkFaint,
    dotColor: Color = ringColor,
    dotTrackColor: Color = trackColor.copy(alpha = 0.4f),
    dots: Int = 28,
    content: @Composable () -> Unit = {}
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val clamped = progress.coerceIn(0f, 1f)
            val step = 360f / dots
            // 点大小随环长自适应：约为相邻点间距的 1/3，限制在 3~7dp
            val circumference = Math.PI * this.size.minDimension
            val dotRadius = (circumference / dots * 0.33).toFloat()
                .coerceIn(3.dp.toPx(), 7.dp.toPx())
            val radius = this.size.minDimension / 2 - dotRadius
            val center = Offset(this.size.width / 2, this.size.height / 2)

            for (i in 0 until dots) {
                val angle = Math.toRadians((-90 + i * step).toDouble())
                val p = Offset(
                    center.x + radius * cos(angle).toFloat(),
                    center.y + radius * sin(angle).toFloat()
                )
                val active = clamped > 0f && i * step < clamped * 360f
                drawCircle(
                    color = if (active) dotColor else dotTrackColor,
                    radius = dotRadius,
                    center = p
                )
            }
        }
        content()
    }
}

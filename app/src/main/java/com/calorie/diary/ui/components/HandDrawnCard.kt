package com.calorie.diary.ui.components

import android.graphics.PathMeasure
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.Ink
import kotlin.math.cos
import kotlin.math.sin

/**
 * 生成带轻微起伏的手绘风圆角矩形路径。
 */
fun buildWobblyPath(
    width: Float,
    height: Float,
    cornerRadius: Float,
    wobblePx: Float,
    seed: Float
): Path {
    val base = android.graphics.Path().apply {
        addRoundRect(
            android.graphics.RectF(0f, 0f, width, height),
            cornerRadius,
            cornerRadius,
            android.graphics.Path.Direction.CW
        )
    }
    val measure = PathMeasure(base, true)
    val length = measure.length
    val step = length / 120f
    val pos = FloatArray(2)
    val tan = FloatArray(2)
    val out = android.graphics.Path()
    var d = 0f
    var first = true
    while (d <= length) {
        measure.getPosTan(d, pos, tan)
        // 法线方向
        val nx = -tan[1]
        val ny = tan[0]
        val w = (wobblePx * (
            0.75 * sin(d / 26.0 + seed.toDouble()) + 0.25 * sin(d / 11.0 + seed * 1.7)
        )).toFloat()
        val x = pos[0] + nx * w
        val y = pos[1] + ny * w
        if (first) {
            out.moveTo(x, y)
            first = false
        } else {
            out.lineTo(x, y)
        }
        d += step
    }
    out.close()
    return out.asComposePath()
}

@Composable
fun HandDrawnCard(
    modifier: Modifier = Modifier,
    fill: Color = CardWhite,
    borderColor: Color = Ink,
    borderWidth: Dp = 2.2.dp,
    cornerRadius: Dp = 24.dp,
    wobble: Dp = 1.6.dp,
    seed: Float = 1.3f,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val strokePx = with(density) { borderWidth.toPx() }
    val radiusPx = with(density) { cornerRadius.toPx() }
    val wobblePx = with(density) { wobble.toPx() }

    Box(
        modifier = modifier
            .background(fill, RoundedCornerShape(cornerRadius))
            .drawWithContent {
                drawContent()
                val p = buildWobblyPath(size.width, size.height, radiusPx, wobblePx, seed)
                drawPath(p, color = borderColor, style = Stroke(width = strokePx))
            }
            .padding(contentPadding)
    ) {
        content()
    }
}

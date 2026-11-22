package com.calorie.diary.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.Yellow

@Composable
fun MascotChick(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    bodyColor: Color = Yellow,
    outline: Color = Ink
) {
    Box(modifier = modifier.size(size)) {
        androidx.compose.foundation.Canvas(Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2
            val cy = h / 2

            // 头顶小毛
            val hairStroke = Stroke(width = w * 0.028f, cap = StrokeCap.Round)
            drawLine(
                outline.copy(alpha = 0.7f),
                Offset(cx - w * 0.05f, h * 0.26f),
                Offset(cx - w * 0.13f, h * 0.16f),
                strokeWidth = w * 0.028f, cap = StrokeCap.Round
            )
            drawLine(
                outline.copy(alpha = 0.7f),
                Offset(cx, h * 0.25f),
                Offset(cx, h * 0.13f),
                strokeWidth = w * 0.028f, cap = StrokeCap.Round
            )
            drawLine(
                outline.copy(alpha = 0.7f),
                Offset(cx + w * 0.05f, h * 0.26f),
                Offset(cx + w * 0.13f, h * 0.16f),
                strokeWidth = w * 0.028f, cap = StrokeCap.Round
            )

            // 身体
            drawOval(
                color = bodyColor,
                topLeft = Offset(w * 0.16f, h * 0.24f),
                size = androidx.compose.ui.geometry.Size(w * 0.68f, h * 0.66f)
            )

            // 肚皮
            drawOval(
                color = Color(0xFFFFF1C9),
                topLeft = Offset(w * 0.27f, h * 0.46f),
                size = androidx.compose.ui.geometry.Size(w * 0.46f, h * 0.4f)
            )

            // 翅膀
            val wing = Path().apply {
                moveTo(cx + w * 0.26f, h * 0.52f)
                cubicTo(
                    cx + w * 0.4f, h * 0.54f,
                    cx + w * 0.4f, h * 0.7f,
                    cx + w * 0.24f, h * 0.68f
                )
                close()
            }
            drawPath(wing, color = Color(0xFFF2BE4E))

            // 眼睛
            drawCircle(Color(0xFF5C4A3D), w * 0.035f, Offset(cx - w * 0.12f, h * 0.45f))
            drawCircle(Color(0xFF5C4A3D), w * 0.035f, Offset(cx + w * 0.12f, h * 0.45f))
            drawCircle(Color.White, w * 0.012f, Offset(cx - w * 0.11f, h * 0.44f))
            drawCircle(Color.White, w * 0.012f, Offset(cx + w * 0.13f, h * 0.44f))

            // 腮红
            drawCircle(Color(0xFFFFB7C5), w * 0.04f, Offset(cx - w * 0.2f, h * 0.52f))
            drawCircle(Color(0xFFFFB7C5), w * 0.04f, Offset(cx + w * 0.2f, h * 0.52f))

            // 小嘴
            val beak = Path().apply {
                moveTo(cx - w * 0.04f, h * 0.52f)
                lineTo(cx + w * 0.04f, h * 0.52f)
                lineTo(cx, h * 0.58f)
                close()
            }
            drawPath(beak, color = Color(0xFFFF8A5C))

            // 小脚
            drawLine(
                Color(0xFFFF8A5C),
                Offset(cx - w * 0.08f, h * 0.9f),
                Offset(cx - w * 0.1f, h * 0.96f),
                strokeWidth = w * 0.03f, cap = StrokeCap.Round
            )
            drawLine(
                Color(0xFFFF8A5C),
                Offset(cx + w * 0.08f, h * 0.9f),
                Offset(cx + w * 0.1f, h * 0.96f),
                strokeWidth = w * 0.03f, cap = StrokeCap.Round
            )
        }
    }
}

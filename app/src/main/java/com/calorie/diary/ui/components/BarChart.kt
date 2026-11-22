package com.calorie.diary.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.Orange

data class BarItem(
    val label: String,
    val value: Int,
    val color: Color = Orange
)

/**
 * 简洁圆角柱状图：柱子高度按数值比例，底部绘制标签。
 */
@Composable
fun SimpleBarChart(
    items: List<BarItem>,
    modifier: Modifier = Modifier,
    height: Dp = 132.dp
) {
    val textMeasurer = rememberTextMeasurer()
    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            val labelSpace = 26.dp.toPx()
            val chartTop = 0f
            val chartBottom = size.height - labelSpace
            val chartHeight = chartBottom - chartTop
            val maxValue = (items.maxOfOrNull { it.value } ?: 0).coerceAtLeast(1)
            // 间隙收紧，保证窄卡片里柱子仍有足够宽度，不被圆角挤成"圆点"
            val gap = 10.dp.toPx()
            val barWidth = (size.width - gap * (items.size + 1)) / items.size

            val minBar = 14.dp.toPx()
            items.forEachIndexed { index, item ->
                val isEmpty = item.value <= 0
                val fraction = item.value.toFloat() / maxValue.toFloat()
                // 零值也保留一个浅色矮柱，避免空状态大片留白
                val barHeight = if (isEmpty) minBar
                    else (chartHeight * fraction).coerceAtLeast(minBar)
                val topLeft = Offset(
                    x = gap + index * (barWidth + gap),
                    y = chartBottom - barHeight
                )
                drawRoundRect(
                    color = if (isEmpty) item.color.copy(alpha = 0.22f) else item.color,
                    topLeft = topLeft,
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
                val measured = textMeasurer.measure(
                    item.label,
                    style = TextStyle(
                        fontFamily = CuteFont,
                        fontSize = 12.sp,
                        color = InkLight
                    )
                )
                drawText(
                    measured,
                    topLeft = Offset(
                        x = topLeft.x + barWidth / 2 - measured.size.width / 2,
                        y = chartBottom + 6.dp.toPx()
                    )
                )
            }
        }
    }
}

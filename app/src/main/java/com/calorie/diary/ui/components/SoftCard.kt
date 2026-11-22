package com.calorie.diary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.Elevation
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.Radius

/**
 * 专业卡片：纯白/浅色填充 + 柔和暖调阴影 + 统一圆角，无深色描边。
 * 签名与旧 HandDrawnCard 对齐，便于逐页替换。
 */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    fill: Color = CardWhite,
    cornerRadius: Dp = Radius.xl,
    elevation: Dp = Elevation.card,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                clip = false,
                ambientColor = Ink.copy(alpha = 0.18f),
                spotColor = Ink.copy(alpha = 0.14f)
            )
            .background(fill, shape)
            .padding(contentPadding)
    ) {
        content()
    }
}

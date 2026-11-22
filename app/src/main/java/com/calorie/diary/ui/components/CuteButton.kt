package com.calorie.diary.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.Orange

@Composable
fun CuteButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Orange,
    shadowColor: Color = Ink,
    contentColor: Color = Color.White,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 28.dp, vertical = 12.dp)
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = if (pressed) 0.96f else 1f
                scaleY = if (pressed) 0.96f else 1f
            }
            .clip(shape)
            .drawBehind {
                val cr = CornerRadius(20.dp.toPx())
                if (enabled) {
                    drawRoundRect(
                        color = shadowColor.copy(alpha = 0.85f),
                        cornerRadius = cr,
                        topLeft = Offset(0f, 5.dp.toPx()),
                        size = size
                    )
                }
                drawRoundRect(
                    color = if (enabled) containerColor else containerColor.copy(alpha = 0.5f),
                    cornerRadius = cr
                )
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = contentColor,
            fontFamily = CuteFont,
            fontSize = 17.sp
        )
    }
}

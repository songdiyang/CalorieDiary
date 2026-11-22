package com.calorie.diary.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.calorie.diary.ui.theme.OrangeDeep

/** 手绘风格的小火焰，用于好友火花、群火花的展示。 */
@Composable
fun SparkFlame(modifier: Modifier = Modifier, color: Color = OrangeDeep) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val flame = Path().apply {
            moveTo(w * 0.54f, h * 0.02f)
            cubicTo(w * 0.58f, h * 0.29f, w * 0.88f, h * 0.37f, w * 0.88f, h * 0.64f)
            cubicTo(w * 0.88f, h * 0.86f, w * 0.72f, h * 0.98f, w * 0.49f, h * 0.98f)
            cubicTo(w * 0.23f, h * 0.98f, w * 0.08f, h * 0.82f, w * 0.10f, h * 0.60f)
            cubicTo(w * 0.12f, h * 0.41f, w * 0.30f, h * 0.31f, w * 0.36f, h * 0.16f)
            cubicTo(w * 0.39f, h * 0.35f, w * 0.49f, h * 0.38f, w * 0.54f, h * 0.02f)
            close()
        }
        drawPath(flame, color)
    }
}

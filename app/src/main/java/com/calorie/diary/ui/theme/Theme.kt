package com.calorie.diary.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

private val appTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = CuteFont,
        color = Ink,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = CuteFont,
        color = Ink,
        fontSize = 14.sp
    ),
    titleLarge = TextStyle(
        fontFamily = CuteFont,
        color = Ink,
        fontSize = 22.sp
    ),
    titleMedium = TextStyle(
        fontFamily = CuteFont,
        color = Ink,
        fontSize = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = CuteFont,
        color = Ink,
        fontSize = 15.sp
    ),
    labelSmall = TextStyle(
        fontFamily = CuteFont,
        color = InkLight,
        fontSize = 12.sp
    )
)

@Composable
fun CalorieDiaryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Orange,
            onPrimary = CardWhite,
            secondary = Yellow,
            background = ScreenBg,
            onBackground = Ink,
            surface = CardWhite,
            onSurface = Ink,
            outline = Hairline
        ),
        typography = appTypography,
        shapes = CuteShapes,
        content = content
    )
}

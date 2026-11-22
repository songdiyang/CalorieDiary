@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.calorie.diary.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.calorie.diary.R

val CuteFont = FontFamily(
    Font(R.font.zcool_kuaile, FontWeight.Normal)
)

val NumberFont = FontFamily(
    Font(
        R.font.baloo2,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
    Font(
        R.font.baloo2,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    ),
    Font(
        R.font.baloo2,
        weight = FontWeight.ExtraBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(800))
    )
)

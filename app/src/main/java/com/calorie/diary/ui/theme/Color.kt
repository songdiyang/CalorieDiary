package com.calorie.diary.ui.theme

import androidx.compose.ui.graphics.Color

/** 当前主题调色板；组合中读取会随主题切换自动重组。 */
internal val Palette: CaloriePalette
    get() = AppThemePalettes.of(ThemeController.theme)

val Cream: Color get() = Palette.cream
val CardWhite: Color get() = Palette.cardWhite
val ScreenBg: Color get() = Palette.screenBg
val Ink: Color get() = Palette.ink
val InkLight: Color get() = Palette.inkLight
val InkFaint: Color get() = Palette.inkFaint
val Hairline: Color get() = Palette.hairline
val Danger: Color get() = Palette.danger
val Success: Color get() = Palette.success

val Orange: Color get() = Palette.primary
val OrangeDeep: Color get() = Palette.primaryDeep
val Yellow: Color get() = Palette.yellow
val YellowSoft: Color get() = Palette.yellowSoft
val Pink: Color get() = Palette.pink
val PinkSoft: Color get() = Palette.pinkSoft
val Blue: Color get() = Palette.blue
val BlueSoft: Color get() = Palette.blueSoft
val Mint: Color get() = Palette.mint
val MintSoft: Color get() = Palette.mintSoft
val Lavender: Color get() = Palette.lavender
val LavenderSoft: Color get() = Palette.lavenderSoft

val mealColors: List<Color>
    get() = listOf(YellowSoft, PinkSoft, BlueSoft, MintSoft)

val mealAccents: List<Color>
    get() = listOf(Orange, Pink, Blue, Mint)

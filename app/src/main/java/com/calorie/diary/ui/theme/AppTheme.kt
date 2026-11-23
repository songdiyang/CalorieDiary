package com.calorie.diary.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

/** 应用全局主题：薄荷绿 / 天空蓝 / 暖阳橙。 */
enum class AppTheme(val storageKey: String) {
    MINT("mint"),
    SKY("sky"),
    SUNSET("sunset");

    companion object {
        fun fromKey(key: String?): AppTheme =
            values().firstOrNull { it.storageKey == key } ?: SUNSET
    }
}

/** 一套完整调色板，字段与界面里使用的颜色标识一一对应。 */
data class CaloriePalette(
    val cream: Color,
    val cardWhite: Color,
    val screenBg: Color,
    val ink: Color,
    val inkLight: Color,
    val inkFaint: Color,
    val hairline: Color,
    val danger: Color,
    val success: Color,
    val primary: Color,
    val primaryDeep: Color,
    val yellow: Color,
    val yellowSoft: Color,
    val pink: Color,
    val pinkSoft: Color,
    val blue: Color,
    val blueSoft: Color,
    val mint: Color,
    val mintSoft: Color,
    val lavender: Color,
    val lavenderSoft: Color
)

object AppThemePalettes {

    /** 暖阳橙：沿用原版暖色体系，作为默认主题。 */
    val sunset = CaloriePalette(
        cream = Color(0xFFFFF6E0),
        cardWhite = Color(0xFFFFFDF7),
        screenBg = Color(0xFFFAF5EF),
        ink = Color(0xFF5C4A3D),
        inkLight = Color(0xFF9A8B7E),
        inkFaint = Color(0xFFC9BBAE),
        hairline = Color(0xFFECE4DA),
        danger = Color(0xFFEF6C6C),
        success = Color(0xFF5FB878),
        primary = Color(0xFFFF9F68),
        primaryDeep = Color(0xFFFF8A5C),
        yellow = Color(0xFFFFD76A),
        yellowSoft = Color(0xFFFFE9B8),
        pink = Color(0xFFFFB7C5),
        pinkSoft = Color(0xFFFFD6E0),
        blue = Color(0xFFA8D8EA),
        blueSoft = Color(0xFFC7ECF5),
        mint = Color(0xFFB5EAD7),
        mintSoft = Color(0xFFD7F0E2),
        lavender = Color(0xFFD9C7F0),
        lavenderSoft = Color(0xFFEAE1F8)
    )

    /** 薄荷绿：清透薄荷主色 + 浅青、奶绿辅助色。 */
    val mint = CaloriePalette(
        cream = Color(0xFFEAF8F0),
        cardWhite = Color(0xFFFBFEFC),
        screenBg = Color(0xFFF1FAF5),
        ink = Color(0xFF335247),
        inkLight = Color(0xFF77958A),
        inkFaint = Color(0xFFB9D4C9),
        hairline = Color(0xFFDFF0E8),
        danger = Color(0xFFEF6C6C),
        success = Color(0xFF46A97E),
        primary = Color(0xFF63CDA8),
        primaryDeep = Color(0xFF34A67D),
        yellow = Color(0xFFAEE8CB),
        yellowSoft = Color(0xFFE1F7EB),
        pink = Color(0xFFF3B6C4),
        pinkSoft = Color(0xFFFBE3E9),
        blue = Color(0xFF9BD9D2),
        blueSoft = Color(0xFFD0F0EA),
        mint = Color(0xFF9BE7C9),
        mintSoft = Color(0xFFD8F5E7),
        lavender = Color(0xFFC5E9D7),
        lavenderSoft = Color(0xFFE2F5EC)
    )

    /** 天空蓝：澄澈天蓝主色 + 暖阳黄、薄荷青点缀。 */
    val sky = CaloriePalette(
        cream = Color(0xFFEAF5FC),
        cardWhite = Color(0xFFFCFEFF),
        screenBg = Color(0xFFF2F8FD),
        ink = Color(0xFF33485B),
        inkLight = Color(0xFF7890A4),
        inkFaint = Color(0xFFB9CEE0),
        hairline = Color(0xFFDEEBF6),
        danger = Color(0xFFEF6C6C),
        success = Color(0xFF46A97E),
        primary = Color(0xFF69B8EF),
        primaryDeep = Color(0xFF3D93D9),
        yellow = Color(0xFFFFDF90),
        yellowSoft = Color(0xFFFFF0CC),
        pink = Color(0xFFF4B7C6),
        pinkSoft = Color(0xFFFCE3EA),
        blue = Color(0xFF9FD3F4),
        blueSoft = Color(0xFFCFE9FB),
        mint = Color(0xFFADE6D4),
        mintSoft = Color(0xFFDCF4EC),
        lavender = Color(0xFFC6D4F2),
        lavenderSoft = Color(0xFFE4EBFB)
    )

    fun of(theme: AppTheme): CaloriePalette = when (theme) {
        AppTheme.MINT -> mint
        AppTheme.SKY -> sky
        AppTheme.SUNSET -> sunset
    }
}

/** 全局主题状态：快照状态驱动界面重组，启动时同步恢复本地选择。 */
object ThemeController {

    private const val PREFS_NAME = "app_theme"
    private const val KEY_THEME = "theme_id"

    private val current = mutableStateOf(AppTheme.SUNSET)
    private var prefs: SharedPreferences? = null

    val theme: AppTheme get() = current.value

    fun init(context: Context) {
        val store = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = store
        current.value = AppTheme.fromKey(store.getString(KEY_THEME, null))
    }

    fun select(theme: AppTheme) {
        if (current.value == theme) return
        current.value = theme
        prefs?.edit()?.putString(KEY_THEME, theme.storageKey)?.apply()
    }
}
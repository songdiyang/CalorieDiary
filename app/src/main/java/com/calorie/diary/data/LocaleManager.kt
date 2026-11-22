package com.calorie.diary.data

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import java.util.Locale

/** 应用固定使用简体中文。 */
object LocaleManager {
    private val APP_LOCALE: Locale = Locale.SIMPLIFIED_CHINESE

    fun wrap(context: Context): ContextWrapper {
        Locale.setDefault(APP_LOCALE)
        val config = Configuration(context.resources.configuration).apply {
            setLocale(APP_LOCALE)
        }
        return ContextWrapper(context.createConfigurationContext(config))
    }
}
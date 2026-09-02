package com.craxiom.networksurvey.util

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager

/**
 * 主题偏好：浅色 / 深色 / 跟随系统。
 * 存储于默认 SharedPreferences（key=theme_mode），与 preferences.xml 的 ListPreference 打通。
 */
object ThemePref {
    const val MODE_LIGHT = 0
    const val MODE_DARK = 1
    const val MODE_SYSTEM = 2
    const val KEY_THEME = "theme_mode"

    @JvmStatic
    fun get(context: Context): Int = prefs(context).getInt(KEY_THEME, MODE_SYSTEM)

    @JvmStatic
    fun set(context: Context, mode: Int) {
        prefs(context).edit().putInt(KEY_THEME, mode).apply()
    }

    /** 应用夜间模式到 AppCompatDelegate（影响 XML 资源 + Compose 跟随）。 */
    @JvmStatic
    fun apply(context: Context) {
        when (get(context)) {
            MODE_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            MODE_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
}

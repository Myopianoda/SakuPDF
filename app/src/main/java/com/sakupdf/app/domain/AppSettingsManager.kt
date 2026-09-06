package com.sakupdf.app.domain

import android.content.Context
import android.content.SharedPreferences
import com.sakupdf.app.model.ThemeOption

object AppSettingsManager {

    private const val PREFS_NAME = "sakupdf_preferences"
    private const val KEY_THEME = "key_theme_option"
    private const val KEY_DEFAULT_QUALITY = "key_default_quality"
    private const val KEY_DEFAULT_PAGE_SIZE = "key_default_page_size"
    private const val KEY_DEFAULT_MARGIN = "key_default_margin"
    private const val KEY_DEFAULT_COMPRESSION = "key_default_compression"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getThemeOption(context: Context): ThemeOption {
        val name = getPrefs(context).getString(KEY_THEME, ThemeOption.SYSTEM.name) ?: ThemeOption.SYSTEM.name
        return try {
            ThemeOption.valueOf(name)
        } catch (_: Exception) {
            ThemeOption.SYSTEM
        }
    }

    fun saveThemeOption(context: Context, option: ThemeOption) {
        getPrefs(context).edit().putString(KEY_THEME, option.name).apply()
    }

    fun getDefaultQuality(context: Context): String {
        return getPrefs(context).getString(KEY_DEFAULT_QUALITY, "Tinggi (Disarankan)") ?: "Tinggi (Disarankan)"
    }

    fun saveDefaultQuality(context: Context, quality: String) {
        getPrefs(context).edit().putString(KEY_DEFAULT_QUALITY, quality).apply()
    }

    fun getDefaultPageSize(context: Context): String {
        return getPrefs(context).getString(KEY_DEFAULT_PAGE_SIZE, "A4") ?: "A4"
    }

    fun saveDefaultPageSize(context: Context, size: String) {
        getPrefs(context).edit().putString(KEY_DEFAULT_PAGE_SIZE, size).apply()
    }

    fun getDefaultMargin(context: Context): String {
        return getPrefs(context).getString(KEY_DEFAULT_MARGIN, "Tanpa margin") ?: "Tanpa margin"
    }

    fun saveDefaultMargin(context: Context, margin: String) {
        getPrefs(context).edit().putString(KEY_DEFAULT_MARGIN, margin).apply()
    }

    fun getDefaultCompression(context: Context): String {
        return getPrefs(context).getString(KEY_DEFAULT_COMPRESSION, "Standar") ?: "Standar"
    }

    fun saveDefaultCompression(context: Context, comp: String) {
        getPrefs(context).edit().putString(KEY_DEFAULT_COMPRESSION, comp).apply()
    }
}

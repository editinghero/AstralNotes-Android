package com.astralquarks.notes.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class AppColorPalette(val displayName: String, val primaryColor: Color) {
    DYNAMIC_MATERIAL_YOU("Dynamic (Material You)", Color(0xFF6750A4)),
    EXPRESSIVE_PURPLE("Expressive Violet", Color(0xFF6750A4)),
    OCEAN_BREEZE("Ocean Sapphire", Color(0xFF0061A4)),
    EMERALD_MINT("Emerald Mint", Color(0xFF006C4C)),
    SUNSET_CORAL("Sunset Amber", Color(0xFF984715)),
    SAKURA_ROSE("Sakura Blossom", Color(0xFF904A75)),
    MONOCHROME("Slate Monochrome", Color(0xFF475569))
}

enum class TonalStyle(val displayName: String, val description: String) {
    TONAL_SPOT("Tonal Spot", "Default balanced Material You dynamic palette"),
    VIBRANT("Vibrant", "High chroma, saturated and energetic colors"),
    EXPRESSIVE("Expressive", "Creative shifted hues with distinct colorful accents"),
    RAINBOW("Rainbow", "Playful multi-hue spectrum with cheerful accents"),
    FRUIT_SALAD("Fruit Salad", "Fresh two-tone berry and citrus accents"),
    SPRITZ("Spritz", "Soft, muted desaturated pastel tones"),
    MONOCHROME("Monochrome", "Clean minimal grayscale and slate palette")
}

data class ThemeSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val colorPalette: AppColorPalette = AppColorPalette.DYNAMIC_MATERIAL_YOU,
    val tonalStyle: TonalStyle = TonalStyle.TONAL_SPOT
)

class ThemeSettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("astral_theme_preferences", Context.MODE_PRIVATE)

    private val _themeSettings = MutableStateFlow(loadSettings())
    val themeSettings: StateFlow<ThemeSettings> = _themeSettings.asStateFlow()

    private fun loadSettings(): ThemeSettings {
        val modeStr = prefs.getString("theme_mode", AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
        val paletteStr = prefs.getString("color_palette", AppColorPalette.DYNAMIC_MATERIAL_YOU.name)
            ?: AppColorPalette.DYNAMIC_MATERIAL_YOU.name
        val styleStr = prefs.getString("tonal_style", TonalStyle.TONAL_SPOT.name) ?: TonalStyle.TONAL_SPOT.name

        val mode = runCatching { AppThemeMode.valueOf(modeStr) }.getOrDefault(AppThemeMode.SYSTEM)
        val palette = runCatching { AppColorPalette.valueOf(paletteStr) }.getOrDefault(AppColorPalette.DYNAMIC_MATERIAL_YOU)
        val style = runCatching { TonalStyle.valueOf(styleStr) }.getOrDefault(TonalStyle.TONAL_SPOT)

        return ThemeSettings(mode, palette, style)
    }

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _themeSettings.value = _themeSettings.value.copy(themeMode = mode)
    }

    fun setColorPalette(palette: AppColorPalette) {
        prefs.edit().putString("color_palette", palette.name).apply()
        _themeSettings.value = _themeSettings.value.copy(colorPalette = palette)
    }

    fun setTonalStyle(style: TonalStyle) {
        prefs.edit().putString("tonal_style", style.name).apply()
        _themeSettings.value = _themeSettings.value.copy(tonalStyle = style)
    }

    companion object {
        @Volatile
        private var INSTANCE: ThemeSettingsManager? = null

        fun getInstance(context: Context): ThemeSettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThemeSettingsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

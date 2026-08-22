package com.astralquarks.notes.model

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.core.graphics.ColorUtils

data class NoteColor(
    val name: String,
    val hex: String,
    val lightColor: Color,
    val darkColor: Color
)

object NoteColorPalette {
    val presets = listOf(
        NoteColor("Default", "#DEFAULT", Color.Unspecified, Color.Unspecified),
        NoteColor("Buttercream Yellow", "#FEF9C3", Color(0xFFFEF9C3), Color(0xFF383214)),
        NoteColor("Peach Glow", "#FFEDD5", Color(0xFFFFEDD5), Color(0xFF3D271B)),
        NoteColor("Blush Rose", "#FFE4E6", Color(0xFFFFE4E6), Color(0xFF3C1F25)),
        NoteColor("Sakura Pink", "#FCE7F3", Color(0xFFFCE7F3), Color(0xFF391D2F)),
        NoteColor("Pastel Lavender", "#F3E8FF", Color(0xFFF3E8FF), Color(0xFF2C203F)),
        NoteColor("Periwinkle Mist", "#EDE9FE", Color(0xFFEDE9FE), Color(0xFF232545)),
        NoteColor("Sky Breeze", "#E0F2FE", Color(0xFFE0F2FE), Color(0xFF172C3E)),
        NoteColor("Aqua Cloud", "#CCFBF1", Color(0xFFCCFBF1), Color(0xFF133633)),
        NoteColor("Mint Sorbet", "#DCFCE7", Color(0xFFDCFCE7), Color(0xFF163522)),
        NoteColor("Matcha Sage", "#ECFCCB", Color(0xFFECFCCB), Color(0xFF26351B)),
        NoteColor("Pistachio Leaf", "#F7FEE7", Color(0xFFF7FEE7), Color(0xFF2D3519)),
        NoteColor("Honey Vanilla", "#FEF3C7", Color(0xFFFEF3C7), Color(0xFF3A2F17)),
        NoteColor("Warm Linen", "#F5F5F4", Color(0xFFF5F5F4), Color(0xFF2B2A29)),
        NoteColor("Cool Slate", "#F1F5F9", Color(0xFFF1F5F9), Color(0xFF202631))
    )

    private val legacyAliases = mapOf(
        "#FFF3A3" to "#FEF9C3",
        "#FFD4B2" to "#FFEDD5",
        "#FFC0BE" to "#FFE4E6",
        "#FFC6E0" to "#FCE7F3",
        "#F8BBD0" to "#FCE7F3",
        "#E2C9FF" to "#F3E8FF",
        "#CAD7FF" to "#EDE9FE",
        "#BCE8FF" to "#E0F2FE",
        "#B6F4D0" to "#DCFCE7",
        "#CCE8BD" to "#ECFCCB",
        "#E4F3AA" to "#F7FEE7",
        "#FFE0B2" to "#FEF3C7",
        "#EAE2D8" to "#F5F5F4",
        "#DEE4EB" to "#F1F5F9",
        "#FFF8E1" to "#FEF9C3",
        "#E8F5E9" to "#DCFCE7",
        "#E3F2FD" to "#E0F2FE",
        "#F3E5F5" to "#F3E8FF",
        "#FFEBEE" to "#FFE4E6"
    )

    fun isDefaultColor(hex: String): Boolean {
        return hex.isBlank() || hex.equals("#DEFAULT", ignoreCase = true) || hex.equals("#FFFFFF", ignoreCase = true)
    }

    /**
     * Converts any color or hex into a true pastel for light mode, or a deep subtle tint for dark mode.
     * If default, returns the Theme surfaceContainer.
     */
    fun getNoteContainerColor(hex: String, colorScheme: ColorScheme, isDark: Boolean = false): Color {
        val isDarkTheme = isDark || colorScheme.surface.luminance() < 0.5f

        if (isDefaultColor(hex)) {
            return colorScheme.surfaceContainer
        }

        val canonicalHex = legacyAliases[hex.uppercase()] ?: hex
        val preset = presets.find { it.hex.equals(canonicalHex, ignoreCase = true) }
        if (preset != null && preset.hex != "#DEFAULT") {
            return if (isDarkTheme) preset.darkColor else preset.lightColor
        }

        return try {
            val parsedInt = android.graphics.Color.parseColor(hex)
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(parsedInt, hsl)
            if (isDarkTheme) {
                // Dark mode: Deep, subtle, legible container tint
                hsl[1] = hsl[1].coerceIn(0.25f, 0.50f)
                hsl[2] = 0.18f
            } else {
                // Light mode: Ultra-clean soft pastel luminance
                hsl[1] = hsl[1].coerceIn(0.30f, 0.65f)
                hsl[2] = 0.93f
            }
            val adjustedInt = ColorUtils.HSLToColor(hsl)
            Color(adjustedInt)
        } catch (e: Exception) {
            colorScheme.surfaceContainer
        }
    }

    fun getColorForHex(hex: String, isDark: Boolean = false): Color {
        if (isDefaultColor(hex)) {
            return if (isDark) Color(0xFF211F26) else Color(0xFFF3EDF7)
        }
        val canonicalHex = legacyAliases[hex.uppercase()] ?: hex
        val preset = presets.find { it.hex.equals(canonicalHex, ignoreCase = true) }
        if (preset != null && preset.hex != "#DEFAULT") {
            return if (isDark) preset.darkColor else preset.lightColor
        }
        return try {
            val parsedInt = android.graphics.Color.parseColor(hex)
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(parsedInt, hsl)
            if (isDark) {
                hsl[1] = hsl[1].coerceIn(0.25f, 0.50f)
                hsl[2] = 0.18f
            } else {
                hsl[1] = hsl[1].coerceIn(0.30f, 0.65f)
                hsl[2] = 0.93f
            }
            val adjustedInt = ColorUtils.HSLToColor(hsl)
            Color(adjustedInt)
        } catch (e: Exception) {
            if (isDark) Color(0xFF211F26) else Color(0xFFF3EDF7)
        }
    }

    fun getNoteTextColor(containerColor: Color): Color {
        return if (containerColor.luminance() > 0.45f) {
            Color(0xFF1B1B1F) // Crisp charcoal for pastels
        } else {
            Color(0xFFF4EFF4) // Crisp high-contrast light text on dark containers
        }
    }

    fun getNoteSecondaryTextColor(containerColor: Color): Color {
        return if (containerColor.luminance() > 0.45f) {
            Color(0xFF45464F) // Slate for secondary text on pastels
        } else {
            Color(0xFFCAC4D0) // Crisp light secondary text
        }
    }
}





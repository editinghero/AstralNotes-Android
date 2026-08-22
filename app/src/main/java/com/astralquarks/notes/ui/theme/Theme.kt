package com.astralquarks.notes.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// --- 1. EXPRESSIVE PURPLE ---
private val ExpressivePurpleLight = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    background = Color(0xFFFDF7FF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFDF7FF),
    onSurface = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceVariant = Color(0xFFE7E0EB),
    onSurfaceVariant = Color(0xFF49454E),
    outline = Color(0xFF7A757F)
)

private val ExpressivePurpleDark = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E0E9),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF948F99)
)

// --- 2. OCEAN SAPPHIRE ---
private val OceanSapphireLight = lightColorScheme(
    primary = Color(0xFF0061A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF535F70),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD7E3F7),
    onSecondaryContainer = Color(0xFF101C2B),
    tertiary = Color(0xFF6B5778),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF2DAFF),
    onTertiaryContainer = Color(0xFF251431),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF191C20),
    surfaceContainer = Color(0xFFECF0F8),
    surfaceContainerLow = Color(0xFFF2F4FB),
    surfaceContainerHigh = Color(0xFFE6EAF2),
    surfaceVariant = Color(0xFFDFE2EB),
    onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF73777F)
)

private val OceanSapphireDark = darkColorScheme(
    primary = Color(0xFF9ECAFF),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF00497D),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFBBC7DB),
    onSecondary = Color(0xFF253140),
    secondaryContainer = Color(0xFF3B4858),
    onSecondaryContainer = Color(0xFFD7E3F7),
    tertiary = Color(0xFFD6BEE4),
    onTertiary = Color(0xFF3B2948),
    tertiaryContainer = Color(0xFF523F5F),
    onTertiaryContainer = Color(0xFFF2DAFF),
    background = Color(0xFF111418),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111418),
    onSurface = Color(0xFFE2E2E9),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainerHigh = Color(0xFF282B2F),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C7CF),
    outline = Color(0xFF8D9199)
)

// --- 3. EMERALD MINT ---
private val EmeraldMintLight = lightColorScheme(
    primary = Color(0xFF006C4C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF89F8C7),
    onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF4D6356),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFE9D8),
    onSecondaryContainer = Color(0xFF0A1F16),
    tertiary = Color(0xFF3D6373),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC1E8FB),
    onTertiaryContainer = Color(0xFF001F29),
    background = Color(0xFFF5FAF4),
    onBackground = Color(0xFF171D19),
    surface = Color(0xFFF5FAF4),
    onSurface = Color(0xFF171D19),
    surfaceContainer = Color(0xFFEAF1EB),
    surfaceContainerLow = Color(0xFFF0F6F0),
    surfaceContainerHigh = Color(0xFFE4EBE5),
    surfaceVariant = Color(0xFFDBE5DE),
    onSurfaceVariant = Color(0xFF404944),
    outline = Color(0xFF707973)
)

private val EmeraldMintDark = darkColorScheme(
    primary = Color(0xFF6CDBAC),
    onPrimary = Color(0xFF003825),
    primaryContainer = Color(0xFF005138),
    onPrimaryContainer = Color(0xFF89F8C7),
    secondary = Color(0xFFB4CCBC),
    onSecondary = Color(0xFF20352A),
    secondaryContainer = Color(0xFF364B3F),
    onSecondaryContainer = Color(0xFFCFE9D8),
    tertiary = Color(0xFFA5CCDE),
    onTertiary = Color(0xFF073543),
    tertiaryContainer = Color(0xFF244C5B),
    onTertiaryContainer = Color(0xFFC1E8FB),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFDFE4DF),
    surface = Color(0xFF0F1512),
    onSurface = Color(0xFFDFE4DF),
    surfaceContainer = Color(0xFF1B211E),
    surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainerHigh = Color(0xFF262C28),
    surfaceVariant = Color(0xFF404944),
    onSurfaceVariant = Color(0xFFBFC9C2),
    outline = Color(0xFF8A938D)
)

// --- 4. SUNSET CORAL ---
private val SunsetCoralLight = lightColorScheme(
    primary = Color(0xFF984715),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCC),
    onPrimaryContainer = Color(0xFF351000),
    secondary = Color(0xFF77574B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBCE),
    onSecondaryContainer = Color(0xFF2C160D),
    tertiary = Color(0xFF6A5E2F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF3E2A7),
    onTertiaryContainer = Color(0xFF221B00),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF221A15),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF221A15),
    surfaceContainer = Color(0xFFF7ECE6),
    surfaceContainerLow = Color(0xFFFDF1EA),
    surfaceContainerHigh = Color(0xFFF1E6E0),
    surfaceVariant = Color(0xFFF5DED5),
    onSurfaceVariant = Color(0xFF53433C),
    outline = Color(0xFF85736B)
)

private val SunsetCoralDark = darkColorScheme(
    primary = Color(0xFFFFB596),
    onPrimary = Color(0xFF561F00),
    primaryContainer = Color(0xFF7A3000),
    onPrimaryContainer = Color(0xFFFFDBCC),
    secondary = Color(0xFFE7BEAF),
    onSecondary = Color(0xFF442A20),
    secondaryContainer = Color(0xFF5D3F35),
    onSecondaryContainer = Color(0xFFFFDBCE),
    tertiary = Color(0xFFD6C68E),
    onTertiary = Color(0xFF393005),
    tertiaryContainer = Color(0xFF51461A),
    onTertiaryContainer = Color(0xFFF3E2A7),
    background = Color(0xFF1A120E),
    onBackground = Color(0xFFEDE0DB),
    surface = Color(0xFF1A120E),
    onSurface = Color(0xFFEDE0DB),
    surfaceContainer = Color(0xFF271E19),
    surfaceContainerLow = Color(0xFF221A15),
    surfaceContainerHigh = Color(0xFF322823),
    surfaceVariant = Color(0xFF53433C),
    onSurfaceVariant = Color(0xFFD8C2B9),
    outline = Color(0xFFA08D85)
)

// --- 5. SAKURA BLOSSOM ---
private val SakuraRoseLight = lightColorScheme(
    primary = Color(0xFF904A75),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD8EC),
    onPrimaryContainer = Color(0xFF3B072F),
    secondary = Color(0xFF705765),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFBD7E9),
    onSecondaryContainer = Color(0xFF281622),
    tertiary = Color(0xFF81533B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDBCF),
    onTertiaryContainer = Color(0xFF321203),
    background = Color(0xFFFFF8F8),
    onBackground = Color(0xFF211A1E),
    surface = Color(0xFFFFF8F8),
    onSurface = Color(0xFF211A1E),
    surfaceContainer = Color(0xFFF8ECF2),
    surfaceContainerLow = Color(0xFFFDF1F7),
    surfaceContainerHigh = Color(0xFFF2E6EC),
    surfaceVariant = Color(0xFFEFDEE6),
    onSurfaceVariant = Color(0xFF4F434A),
    outline = Color(0xFF81737B)
)

private val SakuraRoseDark = darkColorScheme(
    primary = Color(0xFFFFAFD6),
    onPrimary = Color(0xFF581D45),
    primaryContainer = Color(0xFF74335D),
    onPrimaryContainer = Color(0xFFFFD8EC),
    secondary = Color(0xFFDEBDCD),
    onSecondary = Color(0xFF3F2A37),
    secondaryContainer = Color(0xFF57404E),
    onSecondaryContainer = Color(0xFFFBD7E9),
    tertiary = Color(0xFFF5B899),
    onTertiary = Color(0xFF4C2612),
    tertiaryContainer = Color(0xFF663C26),
    onTertiaryContainer = Color(0xFFFFDBCF),
    background = Color(0xFF191116),
    onBackground = Color(0xFFEFE0E6),
    surface = Color(0xFF191116),
    onSurface = Color(0xFFEFE0E6),
    surfaceContainer = Color(0xFF261D22),
    surfaceContainerLow = Color(0xFF211A1E),
    surfaceContainerHigh = Color(0xFF31282D),
    surfaceVariant = Color(0xFF4F434A),
    onSurfaceVariant = Color(0xFFD2C2CB),
    outline = Color(0xFF9B8C95)
)

// --- 6. MONOCHROME ---
private val MonochromeLight = lightColorScheme(
    primary = Color(0xFF1E293B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2E8F0),
    onPrimaryContainer = Color(0xFF0F172A),
    secondary = Color(0xFF475569),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF1F5F9),
    onSecondaryContainer = Color(0xFF1E293B),
    tertiary = Color(0xFF64748B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE2E8F0),
    onTertiaryContainer = Color(0xFF0F172A),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainerHigh = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFF94A3B8)
)

private val MonochromeDark = darkColorScheme(
    primary = Color(0xFFF1F5F9),
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF334155),
    onPrimaryContainer = Color(0xFFF8FAFC),
    secondary = Color(0xFFCBD5E1),
    onSecondary = Color(0xFF1E293B),
    secondaryContainer = Color(0xFF334155),
    onSecondaryContainer = Color(0xFFF1F5F9),
    tertiary = Color(0xFF94A3B8),
    onTertiary = Color(0xFF0F172A),
    tertiaryContainer = Color(0xFF475569),
    onTertiaryContainer = Color(0xFFF8FAFC),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF0F172A),
    onSurface = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFF1E293B),
    surfaceContainerLow = Color(0xFF141E2E),
    surfaceContainerHigh = Color(0xFF334155),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF64748B)
)

// --- TONAL STYLES ---

// 1. TONAL SPOT
private val TonalSpotLight = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    background = Color(0xFFFEF7FF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFEF7FF),
    onSurface = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceVariant = Color(0xFFE7E0EB),
    onSurfaceVariant = Color(0xFF49454E),
    outline = Color(0xFF7A757F)
)

private val TonalSpotDark = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E0E9),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF948F99)
)

// 2. VIBRANT
private val VibrantLight = lightColorScheme(
    primary = Color(0xFF6200EE),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF006A6A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF6FF7F7),
    onSecondaryContainer = Color(0xFF002020),
    tertiary = Color(0xFFB00020),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDAD6),
    onTertiaryContainer = Color(0xFF410002),
    background = Color(0xFFFDF7FF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFDF7FF),
    onSurface = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFFF1E6FF),
    surfaceContainerLow = Color(0xFFF7EEFF),
    surfaceContainerHigh = Color(0xFFE8DBFA),
    surfaceVariant = Color(0xFFE7E0EB),
    onSurfaceVariant = Color(0xFF49454E),
    outline = Color(0xFF7A757F)
)

private val VibrantDark = darkColorScheme(
    primary = Color(0xFFBB86FC),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFF4DDADA),
    onSecondary = Color(0xFF003737),
    secondaryContainer = Color(0xFF004F4F),
    onSecondaryContainer = Color(0xFF6FF7F7),
    tertiary = Color(0xFFFFB4AB),
    onTertiary = Color(0xFF690005),
    tertiaryContainer = Color(0xFF93000A),
    onTertiaryContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE6E0E9),
    surfaceContainer = Color(0xFF221A2C),
    surfaceContainerLow = Color(0xFF1B1324),
    surfaceContainerHigh = Color(0xFF2C2236),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF948F99)
)

// 3. EXPRESSIVE (Gold / Amber / Sage contrast)
private val ExpressiveStyleLight = lightColorScheme(
    primary = Color(0xFF825500),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDDB3),
    onPrimaryContainer = Color(0xFF291800),
    secondary = Color(0xFF6F5B40),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFADEBC),
    onSecondaryContainer = Color(0xFF271904),
    tertiary = Color(0xFF516440),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD4EAB8),
    onTertiaryContainer = Color(0xFF102004),
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF221A14),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF221A14),
    surfaceContainer = Color(0xFFF7ECE4),
    surfaceContainerLow = Color(0xFFFDF2EA),
    surfaceContainerHigh = Color(0xFFF1E6DE),
    surfaceVariant = Color(0xFFF0E0D0),
    onSurfaceVariant = Color(0xFF4F4539),
    outline = Color(0xFF817567)
)

private val ExpressiveStyleDark = darkColorScheme(
    primary = Color(0xFFFFB951),
    onPrimary = Color(0xFF452B00),
    primaryContainer = Color(0xFF633F00),
    onPrimaryContainer = Color(0xFFFFDDB3),
    secondary = Color(0xFFDDC3A1),
    onSecondary = Color(0xFF3E2D16),
    secondaryContainer = Color(0xFF56442B),
    onSecondaryContainer = Color(0xFFFADEBC),
    tertiary = Color(0xFFB8CEA2),
    onTertiary = Color(0xFF243516),
    tertiaryContainer = Color(0xFF3A4C2A),
    onTertiaryContainer = Color(0xFFD4EAB8),
    background = Color(0xFF19120C),
    onBackground = Color(0xFFEFE0D5),
    surface = Color(0xFF19120C),
    onSurface = Color(0xFFEFE0D5),
    surfaceContainer = Color(0xFF261D15),
    surfaceContainerLow = Color(0xFF221A14),
    surfaceContainerHigh = Color(0xFF31281F),
    surfaceVariant = Color(0xFF4F4539),
    onSurfaceVariant = Color(0xFFD3C4B4),
    outline = Color(0xFF9C8F80)
)

// 4. RAINBOW
private val RainbowLight = lightColorScheme(
    primary = Color(0xFF9C4146),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDADA),
    onPrimaryContainer = Color(0xFF410009),
    secondary = Color(0xFF775656),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDADA),
    onSecondaryContainer = Color(0xFF2C1516),
    tertiary = Color(0xFF755A2F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDAE),
    onTertiaryContainer = Color(0xFF281800),
    background = Color(0xFFFFF8F7),
    onBackground = Color(0xFF221919),
    surface = Color(0xFFFFF8F7),
    onSurface = Color(0xFF221919),
    surfaceContainer = Color(0xFFFAECEB),
    surfaceContainerLow = Color(0xFFFFF0F0),
    surfaceContainerHigh = Color(0xFFF4E6E5),
    surfaceVariant = Color(0xFFF4DDDD),
    onSurfaceVariant = Color(0xFF534343),
    outline = Color(0xFF857373)
)

private val RainbowDark = darkColorScheme(
    primary = Color(0xFFFFB3B4),
    onPrimary = Color(0xFF5F121C),
    primaryContainer = Color(0xFF7E2930),
    onPrimaryContainer = Color(0xFFFFDADA),
    secondary = Color(0xFFE6BDBC),
    onSecondary = Color(0xFF44292A),
    secondaryContainer = Color(0xFF5D3F3F),
    onSecondaryContainer = Color(0xFFFFDADA),
    tertiary = Color(0xFFE5C18D),
    onTertiary = Color(0xFF422C05),
    tertiaryContainer = Color(0xFF5B421A),
    onTertiaryContainer = Color(0xFFFFDDAE),
    background = Color(0xFF1A1111),
    onBackground = Color(0xFFEFE0E0),
    surface = Color(0xFF1A1111),
    onSurface = Color(0xFFEFE0E0),
    surfaceContainer = Color(0xFF271D1D),
    surfaceContainerLow = Color(0xFF221919),
    surfaceContainerHigh = Color(0xFF322727),
    surfaceVariant = Color(0xFF534343),
    onSurfaceVariant = Color(0xFFD7C1C1),
    outline = Color(0xFFA08C8C)
)

// 5. FRUIT SALAD
private val FruitSaladLight = lightColorScheme(
    primary = Color(0xFF006D35),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF8EF7AB),
    onPrimaryContainer = Color(0xFF00210C),
    secondary = Color(0xFF4E6352),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD1E8D3),
    onSecondaryContainer = Color(0xFF0C1F12),
    tertiary = Color(0xFFB81D4C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9DF),
    onTertiaryContainer = Color(0xFF3F0013),
    background = Color(0xFFF5FBF3),
    onBackground = Color(0xFF171D18),
    surface = Color(0xFFF5FBF3),
    onSurface = Color(0xFF171D18),
    surfaceContainer = Color(0xFFE9F2E7),
    surfaceContainerLow = Color(0xFFEFF8EE),
    surfaceContainerHigh = Color(0xFFE3ECE2),
    surfaceVariant = Color(0xFFDCE5DC),
    onSurfaceVariant = Color(0xFF414942),
    outline = Color(0xFF717972)
)

private val FruitSaladDark = darkColorScheme(
    primary = Color(0xFF69DD8E),
    onPrimary = Color(0xFF003919),
    primaryContainer = Color(0xFF005326),
    onPrimaryContainer = Color(0xFF8EF7AB),
    secondary = Color(0xFFB5CCB9),
    onSecondary = Color(0xFF213526),
    secondaryContainer = Color(0xFF374B3C),
    onSecondaryContainer = Color(0xFFD1E8D3),
    tertiary = Color(0xFFFFB1C1),
    onTertiary = Color(0xFF650024),
    tertiaryContainer = Color(0xFF8F0038),
    onTertiaryContainer = Color(0xFFFFD9DF),
    background = Color(0xFF0F1511),
    onBackground = Color(0xFFDFE4DE),
    surface = Color(0xFF0F1511),
    onSurface = Color(0xFFDFE4DE),
    surfaceContainer = Color(0xFF1B211D),
    surfaceContainerLow = Color(0xFF171D18),
    surfaceContainerHigh = Color(0xFF262C27),
    surfaceVariant = Color(0xFF414942),
    onSurfaceVariant = Color(0xFFC0C9C0),
    outline = Color(0xFF8B938B)
)

// 6. SPRITZ
private val SpritzLight = lightColorScheme(
    primary = Color(0xFF5C5D72),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE1E0F9),
    onPrimaryContainer = Color(0xFF191A2C),
    secondary = Color(0xFF5C5D62),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2E2E9),
    onSecondaryContainer = Color(0xFF191B1F),
    tertiary = Color(0xFF665A6F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEDDEF6),
    onTertiaryContainer = Color(0xFF22182A),
    background = Color(0xFFFAF8FD),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFAF8FD),
    onSurface = Color(0xFF1B1B1F),
    surfaceContainer = Color(0xFFEFEDF2),
    surfaceContainerLow = Color(0xFFF5F3F8),
    surfaceContainerHigh = Color(0xFFE9E7EC),
    surfaceVariant = Color(0xFFE4E1EC),
    onSurfaceVariant = Color(0xFF47464F),
    outline = Color(0xFF777680)
)

private val SpritzDark = darkColorScheme(
    primary = Color(0xFFC5C4DD),
    onPrimary = Color(0xFF2E2F42),
    primaryContainer = Color(0xFF44455A),
    onPrimaryContainer = Color(0xFFE1E0F9),
    secondary = Color(0xFFC5C5CB),
    onSecondary = Color(0xFF2E3034),
    secondaryContainer = Color(0xFF44464B),
    onSecondaryContainer = Color(0xFFE2E2E9),
    tertiary = Color(0xFFD0C1DA),
    onTertiary = Color(0xFF372D40),
    tertiaryContainer = Color(0xFF4E4357),
    onTertiaryContainer = Color(0xFFEDDEF6),
    background = Color(0xFF131316),
    onBackground = Color(0xFFE4E1E6),
    surface = Color(0xFF131316),
    onSurface = Color(0xFFE4E1E6),
    surfaceContainer = Color(0xFF1F1F23),
    surfaceContainerLow = Color(0xFF1B1B1F),
    surfaceContainerHigh = Color(0xFF2A292D),
    surfaceVariant = Color(0xFF47464F),
    onSurfaceVariant = Color(0xFFC8C5D0),
    outline = Color(0xFF918F9A)
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val themeManager = remember { ThemeSettingsManager.getInstance(context) }
    val settings by themeManager.themeSettings.collectAsState()

    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (settings.themeMode) {
        AppThemeMode.SYSTEM -> isSystemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val colorScheme: ColorScheme = if (settings.colorPalette != AppColorPalette.DYNAMIC_MATERIAL_YOU) {
        when (settings.colorPalette) {
            AppColorPalette.EXPRESSIVE_PURPLE -> if (isDark) ExpressivePurpleDark else ExpressivePurpleLight
            AppColorPalette.OCEAN_BREEZE -> if (isDark) OceanSapphireDark else OceanSapphireLight
            AppColorPalette.EMERALD_MINT -> if (isDark) EmeraldMintDark else EmeraldMintLight
            AppColorPalette.SUNSET_CORAL -> if (isDark) SunsetCoralDark else SunsetCoralLight
            AppColorPalette.SAKURA_ROSE -> if (isDark) SakuraRoseDark else SakuraRoseLight
            AppColorPalette.MONOCHROME -> if (isDark) MonochromeDark else MonochromeLight
            else -> if (isDark) TonalSpotDark else TonalSpotLight
        }
    } else {
        // Dynamic / Tonal Style Mode
        when (settings.tonalStyle) {
            TonalStyle.TONAL_SPOT -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                } else {
                    if (isDark) TonalSpotDark else TonalSpotLight
                }
            }
            TonalStyle.VIBRANT -> if (isDark) VibrantDark else VibrantLight
            TonalStyle.EXPRESSIVE -> if (isDark) ExpressiveStyleDark else ExpressiveStyleLight
            TonalStyle.RAINBOW -> if (isDark) RainbowDark else RainbowLight
            TonalStyle.FRUIT_SALAD -> if (isDark) FruitSaladDark else FruitSaladLight
            TonalStyle.SPRITZ -> if (isDark) SpritzDark else SpritzLight
            TonalStyle.MONOCHROME -> if (isDark) MonochromeDark else MonochromeLight
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

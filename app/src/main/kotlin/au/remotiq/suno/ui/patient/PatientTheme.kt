// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.ui.patient

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Colour themes for the patient layout (arch.md section 11.3). */
enum class PatientThemeName {
    STANDARD,
    DARK,
    YELLOW,
    ;

    companion object {
        fun fromName(name: String?): PatientThemeName = entries.firstOrNull { it.name == name } ?: STANDARD
    }
}

/**
 * Patient colour tokens, based on the Okabe–Ito colour-blind-safe palette. Every state also has
 * an icon, a word and a shape, so colour is never the only signal.
 */
@Immutable
data class PatientColors(
    val background: Color,
    val surface: Color,
    val surfaceSoft: Color,
    val ink: Color,
    val muted: Color,
    val hairline: Color,
    val on: Color,
    val onInk: Color,
    val onSoft: Color,
    val onSofter: Color,
    val help: Color,
    val helpInk: Color,
    val warn: Color,
    val warnInk: Color,
    /** SOS: dark vermillion with white text (8:1), the same in every theme and app. */
    val sos: Color = SosVermillion,
    val sosInk: Color = Color.White,
    /** True for the yellow-on-black theme, which relies on borders instead of shadows. */
    val outlined: Boolean,
)

private val SosVermillion = Color(0xFFA84A00)

private val Standard =
    PatientColors(
        background = Color(0xFFF1F5F8),
        surface = Color(0xFFFFFFFF),
        surfaceSoft = Color(0xFFE3EBF1),
        ink = Color(0xFF0E1B26),
        muted = Color(0xFF4A5B69),
        hairline = Color(0xFF8A99A6),
        on = Color(0xFF0A5A8F),
        onInk = Color(0xFFFFFFFF),
        onSoft = Color(0xFFD3E6F3),
        onSofter = Color(0xFFE7F1F8),
        help = Color(0xFF00765A),
        helpInk = Color(0xFFFFFFFF),
        warn = Color(0xFFE69F00),
        warnInk = Color(0xFF111111),
        outlined = false,
    )

private val Dark =
    PatientColors(
        background = Color(0xFF0A1117),
        surface = Color(0xFF141E27),
        surfaceSoft = Color(0xFF1C2934),
        ink = Color(0xFFEEF3F7),
        muted = Color(0xFFA9B7C3),
        hairline = Color(0xFF5B6B78),
        on = Color(0xFF6CC3F0),
        onInk = Color(0xFF04121C),
        onSoft = Color(0xFF173A52),
        onSofter = Color(0xFF112C3E),
        help = Color(0xFF34C9A0),
        helpInk = Color(0xFF03140E),
        warn = Color(0xFFF0B030),
        warnInk = Color(0xFF111111),
        outlined = false,
    )

private val Yellow =
    PatientColors(
        background = Color(0xFF000000),
        surface = Color(0xFF000000),
        surfaceSoft = Color(0xFF000000),
        ink = Color(0xFFFFE600),
        muted = Color(0xFFFFE600),
        hairline = Color(0xFFFFE600),
        on = Color(0xFFFFE600),
        onInk = Color(0xFF000000),
        onSoft = Color(0xFF4D4500),
        onSofter = Color(0xFF2B2700),
        help = Color(0xFFFFE600),
        helpInk = Color(0xFF000000),
        warn = Color(0xFFFFE600),
        warnInk = Color(0xFF000000),
        outlined = true,
    )

val LocalPatientColors = staticCompositionLocalOf { Standard }

fun patientColors(theme: PatientThemeName): PatientColors =
    when (theme) {
        PatientThemeName.STANDARD -> Standard
        PatientThemeName.DARK -> Dark
        PatientThemeName.YELLOW -> Yellow
    }

/** Large type: body 22 sp and up. It also follows the system font size. */
private val PatientTypography =
    Typography(
        displaySmall =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                lineHeight = 40.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                lineHeight = 36.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                lineHeight = 30.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
            ),
        bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 22.sp, lineHeight = 30.sp),
        bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 18.sp, lineHeight = 26.sp),
        labelLarge =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 26.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                lineHeight = 22.sp,
            ),
    )

@Composable
fun PatientTheme(
    theme: PatientThemeName,
    content: @Composable () -> Unit,
) {
    val c = patientColors(theme)
    val scheme =
        if (theme == PatientThemeName.STANDARD) {
            lightColorScheme(
                primary = c.on,
                onPrimary = c.onInk,
                background = c.background,
                surface = c.surface,
                onSurface = c.ink,
                onBackground = c.ink,
            )
        } else {
            darkColorScheme(
                primary = c.on,
                onPrimary = c.onInk,
                background = c.background,
                surface = c.surface,
                onSurface = c.ink,
                onBackground = c.ink,
            )
        }
    CompositionLocalProvider(LocalPatientColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = PatientTypography, content = content)
    }
}

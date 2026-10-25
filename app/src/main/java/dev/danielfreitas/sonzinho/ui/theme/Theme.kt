package dev.danielfreitas.sonzinho.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.danielfreitas.sonzinho.R

/**
 * Identidade "Jardim ao entardecer": fundo índigo-ameixa profundo (descansa os olhos e faz as
 * cores brilharem sem clarões) e oito cores vivas de mesma luminosidade aproximada, para que
 * nenhuma "grite" mais que as outras.
 */
object SonzinhoPalette {
    val Night = Color(0xFF231A4F)
    val NightDeep = Color(0xFF17113A)
    val Plum = Color(0xFF4A2775)
    val Dusk = Color(0xFF6B3A8C)
    val Cream = Color(0xFFFFF7EC)
    val Ink = Color(0xFF2E2066)

    val Coral = Color(0xFFFF7B6B)
    val Tangerine = Color(0xFFFFA552)
    val Sun = Color(0xFFFFD45C)
    val Lime = Color(0xFFB8E35A)
    val Mint = Color(0xFF52D6A8)
    val Sky = Color(0xFF55C2F2)
    val Lavender = Color(0xFF9D8BFF)
    val Bubblegum = Color(0xFFFF8CC6)

    /** Cores das notas, do grave (quente) ao agudo (frio), dando sentido visual à altura. */
    val notes = listOf(Coral, Tangerine, Sun, Lime, Mint, Sky, Lavender, Bubblegum)

    fun note(index: Int): Color = notes[Math.floorMod(index, notes.size)]
}

private val nunito =
    FontFamily(
        listOf(400, 600, 700, 800, 900).map { weight ->
            Font(
                R.font.nunito,
                weight = FontWeight(weight),
                variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
            )
        },
    )

private val typography =
    Typography().let { base ->
        Typography(
            displayLarge = base.displayLarge.copy(fontFamily = nunito, fontWeight = FontWeight.Black),
            displayMedium = base.displayMedium.copy(fontFamily = nunito, fontWeight = FontWeight.Black),
            displaySmall = base.displaySmall.copy(fontFamily = nunito, fontWeight = FontWeight.ExtraBold),
            headlineLarge = base.headlineLarge.copy(fontFamily = nunito, fontWeight = FontWeight.ExtraBold),
            headlineMedium = base.headlineMedium.copy(fontFamily = nunito, fontWeight = FontWeight.ExtraBold),
            headlineSmall = base.headlineSmall.copy(fontFamily = nunito, fontWeight = FontWeight.Bold),
            titleLarge = base.titleLarge.copy(fontFamily = nunito, fontWeight = FontWeight.ExtraBold),
            titleMedium = base.titleMedium.copy(fontFamily = nunito, fontWeight = FontWeight.Bold),
            titleSmall = base.titleSmall.copy(fontFamily = nunito, fontWeight = FontWeight.Bold),
            bodyLarge = base.bodyLarge.copy(fontFamily = nunito, fontSize = 17.sp),
            bodyMedium = base.bodyMedium.copy(fontFamily = nunito, fontSize = 15.sp),
            bodySmall = base.bodySmall.copy(fontFamily = nunito),
            labelLarge = base.labelLarge.copy(fontFamily = nunito, fontWeight = FontWeight.Bold, fontSize = 16.sp),
            labelMedium = base.labelMedium.copy(fontFamily = nunito, fontWeight = FontWeight.Bold),
            labelSmall = base.labelSmall.copy(fontFamily = nunito, fontWeight = FontWeight.Bold),
        )
    }

private val parentColors: ColorScheme =
    lightColorScheme(
        primary = Color(0xFF5B3FD0),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE6DEFF),
        onPrimaryContainer = SonzinhoPalette.Ink,
        secondary = Color(0xFFD9485F),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFDDE2),
        onSecondaryContainer = Color(0xFF5A1324),
        tertiary = Color(0xFF00796B),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFC9F2E6),
        onTertiaryContainer = Color(0xFF00382F),
        background = SonzinhoPalette.Cream,
        onBackground = SonzinhoPalette.Ink,
        surface = Color(0xFFFFFBF6),
        onSurface = SonzinhoPalette.Ink,
        surfaceVariant = Color(0xFFF1E8F7),
        onSurfaceVariant = Color(0xFF4B4363),
        surfaceContainer = Color(0xFFF7EEF8),
        surfaceContainerHigh = Color(0xFFF1E6F4),
        outline = Color(0xFF7B7294),
        outlineVariant = Color(0xFFD5CBE3),
        error = Color(0xFFB3261E),
    )

private val shapes =
    Shapes(
        extraSmall = RoundedCornerShape(10.dp),
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(36.dp),
    )

@Immutable
data class SonzinhoColors(
    val babyBackgroundTop: Color = SonzinhoPalette.Night,
    val babyBackgroundBottom: Color = SonzinhoPalette.Plum,
)

val LocalSonzinhoColors = staticCompositionLocalOf { SonzinhoColors() }

@Composable
fun SonzinhoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = parentColors, typography = typography, shapes = shapes, content = content)
}

val BabyLabelStyle = TextStyle(fontFamily = nunito, fontWeight = FontWeight.Black, fontSize = 22.sp)

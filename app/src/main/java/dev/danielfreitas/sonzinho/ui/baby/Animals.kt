package dev.danielfreitas.sonzinho.ui.baby

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import dev.danielfreitas.sonzinho.domain.model.Instrument
import dev.danielfreitas.sonzinho.ui.theme.SonzinhoPalette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** Bichinhos desenhados com formas simples e arredondadas (arte própria, sem licenças externas). */
enum class Animal(
    val displayName: String,
    val voice: Instrument,
    val body: Color,
    val accent: Color,
) {
    BIRD("Passarinho", Instrument.BIRD, SonzinhoPalette.Coral, SonzinhoPalette.Sun),
    CAT("Gatinho", Instrument.CAT, SonzinhoPalette.Lavender, SonzinhoPalette.Bubblegum),
    DUCK("Patinho", Instrument.DUCK, SonzinhoPalette.Sun, SonzinhoPalette.Tangerine),
    FROG("Sapinho", Instrument.FROG, SonzinhoPalette.Lime, SonzinhoPalette.Mint),
    BEAR("Ursinho", Instrument.BEAR, Color(0xFFD99A6C), Color(0xFFF3CFA8)),
    WHALE("Baleia", Instrument.WHALE, SonzinhoPalette.Sky, Color(0xFFBFEAFF)),
}

private val Ink = Color(0xFF2B1B4E)
private val Cheek = Color(0x66FF6F91)

/**
 * Desenha um bichinho centrado em [center] com raio [r].
 * @param sinceTap segundos desde o último toque (anima pulo, boca e "achatamento").
 */
@Suppress("CyclomaticComplexMethod")
fun DrawScope.drawAnimal(
    animal: Animal,
    center: Offset,
    r: Float,
    time: Float,
    sinceTap: Float,
    phase: Float,
    motion: Float,
) {
    val bounce = if (sinceTap < 2f) exp(-sinceTap * 4.5f) * sin(sinceTap * 15f) else 0f
    val breathe = 1f + 0.025f * sin(time * 1.7f + phase) * motion
    val sx = breathe * (1f + 0.12f * bounce * motion)
    val sy = breathe * (1f - 0.16f * bounce * motion)
    val hop = -r * 0.28f * abs(bounce) * motion
    val blinkCycle = (time + phase * 3.1f) % (4.2f + phase)
    val blink = blinkCycle < 0.14f
    val mouthOpen = sinceTap < 0.45f

    // Sombra suave no chão.
    drawOval(
        Color.Black.copy(alpha = 0.16f),
        topLeft = Offset(center.x - r * 0.75f, center.y + r * 0.92f),
        size = Size(r * 1.5f, r * 0.26f),
    )
    withTransform({
        translate(0f, hop)
        scale(sx, sy, pivot = Offset(center.x, center.y + r))
    }) {
        when (animal) {
            Animal.BIRD -> bird(center, r, animal, time, mouthOpen)
            Animal.CAT -> cat(center, r, animal, mouthOpen)
            Animal.DUCK -> duck(center, r, animal, mouthOpen)
            Animal.FROG -> frog(center, r, animal, mouthOpen)
            Animal.BEAR -> bear(center, r, animal, mouthOpen)
            Animal.WHALE -> whale(center, r, animal, time, sinceTap, mouthOpen)
        }
        val eyeY = if (animal == Animal.FROG) center.y - r * 0.72f else center.y - r * 0.12f
        val eyeDx = if (animal == Animal.FROG) r * 0.42f else r * 0.32f
        if (animal != Animal.WHALE) eyes(Offset(center.x, eyeY), eyeDx, r, blink)
    }
}

private fun DrawScope.eyes(
    at: Offset,
    dx: Float,
    r: Float,
    blink: Boolean,
) {
    for (side in listOf(-1f, 1f)) {
        val c = Offset(at.x + side * dx, at.y)
        if (blink) {
            drawLine(Ink, Offset(c.x - r * 0.1f, c.y), Offset(c.x + r * 0.1f, c.y), strokeWidth = r * 0.05f, cap = StrokeCap.Round)
        } else {
            drawCircle(Ink, radius = r * 0.11f, center = c)
            drawCircle(Color.White, radius = r * 0.04f, center = Offset(c.x + r * 0.035f, c.y - r * 0.04f))
        }
        drawCircle(Cheek, radius = r * 0.11f, center = Offset(c.x + side * r * 0.12f, c.y + r * 0.22f))
    }
}

private fun DrawScope.mouth(
    at: Offset,
    r: Float,
    open: Boolean,
) {
    if (open) {
        drawOval(Ink, topLeft = Offset(at.x - r * 0.12f, at.y - r * 0.06f), size = Size(r * 0.24f, r * 0.2f))
        drawOval(Color(0xFFFF8FA3), topLeft = Offset(at.x - r * 0.07f, at.y + r * 0.04f), size = Size(r * 0.14f, r * 0.08f))
    } else {
        drawArc(
            Ink,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(at.x - r * 0.14f, at.y - r * 0.12f),
            size = Size(r * 0.28f, r * 0.2f),
            style = Stroke(width = r * 0.045f, cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.bird(
    c: Offset,
    r: Float,
    a: Animal,
    time: Float,
    open: Boolean,
) {
    for (k in -1..1) drawCircle(a.body, radius = r * 0.14f, center = Offset(c.x + k * r * 0.16f, c.y - r * 0.92f))
    val flap = sin(time * 3f) * 0.08f
    drawOval(a.body.copy(alpha = 0.85f), topLeft = Offset(c.x - r * 1.18f, c.y - r * (0.1f + flap)), size = Size(r * 0.5f, r * 0.7f))
    drawOval(a.body.copy(alpha = 0.85f), topLeft = Offset(c.x + r * 0.68f, c.y - r * (0.1f - flap)), size = Size(r * 0.5f, r * 0.7f))
    drawCircle(a.body, radius = r * 0.95f, center = c)
    drawOval(Color.White.copy(alpha = 0.35f), topLeft = Offset(c.x - r * 0.5f, c.y + r * 0.15f), size = Size(r, r * 0.65f))
    val beak =
        Path().apply {
            moveTo(c.x - r * 0.16f, c.y + r * 0.12f)
            lineTo(c.x + r * 0.16f, c.y + r * 0.12f)
            lineTo(c.x, c.y + (if (open) r * 0.45f else r * 0.36f))
            close()
        }
    drawPath(beak, a.accent)
}

private fun DrawScope.cat(
    c: Offset,
    r: Float,
    a: Animal,
    open: Boolean,
) {
    for (side in listOf(-1f, 1f)) {
        val ear =
            Path().apply {
                moveTo(c.x + side * r * 0.25f, c.y - r * 0.8f)
                lineTo(c.x + side * r * 0.85f, c.y - r * 1.15f)
                lineTo(c.x + side * r * 0.8f, c.y - r * 0.4f)
                close()
            }
        drawPath(ear, a.body)
        val inner =
            Path().apply {
                moveTo(c.x + side * r * 0.38f, c.y - r * 0.75f)
                lineTo(c.x + side * r * 0.76f, c.y - r * 0.98f)
                lineTo(c.x + side * r * 0.72f, c.y - r * 0.55f)
                close()
            }
        drawPath(inner, a.accent.copy(alpha = 0.8f))
    }
    drawCircle(a.body, radius = r * 0.95f, center = c)
    val nose =
        Path().apply {
            moveTo(c.x - r * 0.07f, c.y + r * 0.1f)
            lineTo(c.x + r * 0.07f, c.y + r * 0.1f)
            lineTo(c.x, c.y + r * 0.18f)
            close()
        }
    drawPath(nose, a.accent)
    for (side in listOf(-1f, 1f)) {
        for (k in 0..1) {
            val y = c.y + r * (0.16f + k * 0.1f)
            drawLine(Ink.copy(alpha = 0.55f), Offset(c.x + side * r * 0.3f, y), Offset(c.x + side * r * 0.75f, y - r * 0.05f + k * r * 0.1f), strokeWidth = r * 0.025f, cap = StrokeCap.Round)
        }
    }
    mouth(Offset(c.x, c.y + r * 0.3f), r, open)
}

private fun DrawScope.duck(
    c: Offset,
    r: Float,
    a: Animal,
    open: Boolean,
) {
    drawCircle(a.body, radius = r * 0.16f, center = Offset(c.x + r * 0.05f, c.y - r * 0.98f))
    drawCircle(a.body, radius = r * 0.95f, center = c)
    drawOval(Color.White.copy(alpha = 0.3f), topLeft = Offset(c.x - r * 0.55f, c.y + r * 0.2f), size = Size(r * 1.1f, r * 0.6f))
    val beakH = if (open) r * 0.34f else r * 0.24f
    drawOval(a.accent, topLeft = Offset(c.x - r * 0.34f, c.y + r * 0.08f), size = Size(r * 0.68f, beakH))
    drawLine(Ink.copy(alpha = 0.35f), Offset(c.x - r * 0.26f, c.y + r * 0.08f + beakH / 2), Offset(c.x + r * 0.26f, c.y + r * 0.08f + beakH / 2), strokeWidth = r * 0.02f)
}

private fun DrawScope.frog(
    c: Offset,
    r: Float,
    a: Animal,
    open: Boolean,
) {
    for (side in listOf(-1f, 1f)) {
        drawCircle(a.body, radius = r * 0.32f, center = Offset(c.x + side * r * 0.42f, c.y - r * 0.72f))
        drawCircle(Color.White, radius = r * 0.2f, center = Offset(c.x + side * r * 0.42f, c.y - r * 0.72f))
    }
    drawOval(a.body, topLeft = Offset(c.x - r * 1.05f, c.y - r * 0.75f), size = Size(r * 2.1f, r * 1.7f))
    drawOval(a.accent.copy(alpha = 0.55f), topLeft = Offset(c.x - r * 0.6f, c.y + r * 0.15f), size = Size(r * 1.2f, r * 0.7f))
    if (open) {
        drawOval(Ink, topLeft = Offset(c.x - r * 0.4f, c.y - r * 0.15f), size = Size(r * 0.8f, r * 0.3f))
    } else {
        drawArc(
            Ink,
            startAngle = 15f,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = Offset(c.x - r * 0.5f, c.y - r * 0.35f),
            size = Size(r, r * 0.45f),
            style = Stroke(width = r * 0.05f, cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.bear(
    c: Offset,
    r: Float,
    a: Animal,
    open: Boolean,
) {
    for (side in listOf(-1f, 1f)) {
        drawCircle(a.body, radius = r * 0.32f, center = Offset(c.x + side * r * 0.68f, c.y - r * 0.72f))
        drawCircle(a.accent, radius = r * 0.17f, center = Offset(c.x + side * r * 0.68f, c.y - r * 0.72f))
    }
    drawCircle(a.body, radius = r * 0.95f, center = c)
    drawOval(a.accent, topLeft = Offset(c.x - r * 0.36f, c.y + r * 0.02f), size = Size(r * 0.72f, r * 0.55f))
    drawOval(Ink, topLeft = Offset(c.x - r * 0.12f, c.y + r * 0.06f), size = Size(r * 0.24f, r * 0.15f))
    mouth(Offset(c.x, c.y + r * 0.38f), r * 0.9f, open)
}

private fun DrawScope.whale(
    c: Offset,
    r: Float,
    a: Animal,
    time: Float,
    sinceTap: Float,
    open: Boolean,
) {
    val tailSwing = sin(time * 1.6f) * r * 0.08f
    val tail =
        Path().apply {
            moveTo(c.x + r * 0.85f, c.y + r * 0.05f)
            quadraticTo(c.x + r * 1.2f, c.y - r * 0.2f + tailSwing, c.x + r * 1.35f, c.y - r * 0.55f + tailSwing)
            quadraticTo(c.x + r * 1.1f, c.y - r * 0.3f, c.x + r * 0.95f, c.y - r * 0.35f + tailSwing)
            close()
        }
    drawPath(tail, a.body)
    drawOval(a.body, topLeft = Offset(c.x - r * 1.1f, c.y - r * 0.7f), size = Size(r * 2.1f, r * 1.55f))
    drawOval(a.accent, topLeft = Offset(c.x - r * 0.8f, c.y + r * 0.2f), size = Size(r * 1.4f, r * 0.6f))
    eyes(Offset(c.x - r * 0.35f, c.y - r * 0.15f), r * 0.28f, r, blink = false)
    mouth(Offset(c.x - r * 0.35f, c.y + r * 0.22f), r, open)
    // Esguicho de água logo depois do toque.
    if (sinceTap < 1.2f) {
        val t = sinceTap / 1.2f
        for (k in 0 until 5) {
            val ang = (-PI / 2 + (k - 2) * 0.35).toFloat()
            val d = r * (0.3f + 0.9f * t)
            drawCircle(
                Color(0xFFD9F4FF).copy(alpha = 0.8f * (1f - t)),
                radius = r * 0.07f,
                center = Offset(c.x - r * 0.2f + cos(ang) * d * 0.5f, c.y - r * 0.75f + sin(ang) * d),
            )
        }
    }
}

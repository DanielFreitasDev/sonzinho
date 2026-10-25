package dev.danielfreitas.sonzinho.ui.baby

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import dev.danielfreitas.sonzinho.domain.model.AppSettings
import dev.danielfreitas.sonzinho.domain.model.Instrument
import dev.danielfreitas.sonzinho.domain.music.MusicTheory
import dev.danielfreitas.sonzinho.ui.theme.SonzinhoPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

private enum class KeyShape { CIRCLE, STAR, HEART, FLOWER, SQUARE, TRIANGLE, DROP, MOON }

/** Teclado colorido: de 5 a 8 teclas gigantes, cada uma com cor, forma e nota próprias. */
@Composable
fun KeyboardScreen(
    settings: AppSettings,
    effects: EffectsState,
    haptics: BabyHaptics,
    onPlay: (instrumentId: Int, midi: Float, velocity: Float, pan: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = settings.keyboardKeys.coerceIn(AppSettings.keyboardKeyRange)
    val instrument = Instrument.fromId(settings.instrumentId) ?: Instrument.MARIMBA
    val notes =
        remember(settings.scale, settings.key, instrument, count) {
            val all = MusicTheory.scaleNotes(settings.scale, settings.key, instrument.lowestMidi, instrument.highestMidi)
            // Começa perto do centro da região do instrumento, subindo da esquerda para a direita.
            val start = ((all.size - count) / 2).coerceAtLeast(0)
            List(count) { i -> all.getOrElse(start + i) { all.last() } }
        }
    val state = remember { KeyboardState() }
    val pressedAt = remember(count) { FloatArray(count) { -10f } }
    val time = rememberAmbientTime()
    val motion = settings.animationLevel.motionScale

    fun press(
        key: Int,
        pos: Offset,
        pressure: Float,
    ) {
        onPlay(
            instrument.id,
            notes[key].toFloat(),
            MusicTheory.velocityForTouch(pressure, 0.6f),
            MusicTheory.panFor(pos.x, state.size.width.toFloat()),
        )
        pressedAt[key] = time()
        val color = SonzinhoPalette.note(key)
        effects.ripple(pos.x, pos.y, color, state.size.height * 0.35f)
        effects.burst(pos.x, pos.y, color, baseCount = 10)
        haptics.tap()
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { state.size = it }
            .semantics { contentDescription = "Teclado colorido com $count teclas gigantes" }
            .multiTouch(
                onDown = { id, pos, pressure, time ->
                    val key = state.keyAt(pos, count)
                    state.pointers[id] = key to time
                    press(key, pos, pressure)
                },
                onMove = { id, pos, now ->
                    val (current, at) = state.pointers[id] ?: return@multiTouch
                    val key = state.keyAt(pos, count)
                    if (key != current && now - at > 60) {
                        state.pointers[id] = key to now
                        press(key, pos, 0.5f)
                    }
                },
                onUp = { state.pointers.remove(it) },
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = time()
            drawRect(Brush.verticalGradient(listOf(SonzinhoPalette.NightDeep, SonzinhoPalette.Night)))
            val gap = size.width * 0.012f
            val keyW = (size.width - gap * (count + 1)) / count
            val keyH = size.height - gap * 2
            for (i in 0 until count) {
                val since = t - pressedAt[i]
                val press = if (since < 1.5f) exp(-since * 6f) else 0f
                val scale = 1f - 0.05f * press * motion
                val left = gap + i * (keyW + gap)
                val color = SonzinhoPalette.note(i)
                val center = Offset(left + keyW / 2, gap + keyH / 2)
                withTransform({ scale(scale, scale, pivot = center) }) {
                    drawRoundRect(
                        Brush.verticalGradient(listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.7f))),
                        topLeft = Offset(left, gap),
                        size = Size(keyW, keyH),
                        cornerRadius = CornerRadius(keyW * 0.28f),
                    )
                    drawRoundRect(
                        Color.White.copy(alpha = 0.18f + 0.2f * press),
                        topLeft = Offset(left + keyW * 0.12f, gap + keyH * 0.04f),
                        size = Size(keyW * 0.76f, keyH * 0.18f),
                        cornerRadius = CornerRadius(keyW * 0.2f),
                    )
                    val wobble = sin(t * 1.4f + i) * 0.04f * motion + press * 0.15f
                    val iconSize = min(keyW * 0.36f, keyH * 0.2f) * (1f + wobble)
                    drawKeyShape(
                        KeyShape.entries[i % KeyShape.entries.size],
                        Offset(center.x, gap + keyH * 0.62f),
                        iconSize,
                        Color.White.copy(alpha = 0.9f),
                        color,
                    )
                }
            }
        }
        EffectsLayer(effects, Modifier.fillMaxSize())
    }
}

private class KeyboardState {
    var size: IntSize = IntSize.Zero
    val pointers = HashMap<Long, Pair<Int, Long>>()

    fun keyAt(
        p: Offset,
        count: Int,
    ): Int = ((p.x / size.width.coerceAtLeast(1)) * count).toInt().coerceIn(0, count - 1)
}

@Suppress("CyclomaticComplexMethod")
private fun DrawScope.drawKeyShape(
    shape: KeyShape,
    c: Offset,
    r: Float,
    color: Color,
    background: Color,
) {
    when (shape) {
        KeyShape.CIRCLE -> {
            drawCircle(color, r, c)
        }

        KeyShape.STAR -> {
            withTransform({
                translate(c.x, c.y)
                scale(r, r, pivot = Offset.Zero)
            }) { drawPath(EffectsState.unitStar, color) }
        }

        KeyShape.HEART -> {
            val p =
                Path().apply {
                    moveTo(c.x, c.y + r * 0.9f)
                    cubicTo(c.x - r * 1.6f, c.y - r * 0.2f, c.x - r * 0.6f, c.y - r * 1.3f, c.x, c.y - r * 0.4f)
                    cubicTo(c.x + r * 0.6f, c.y - r * 1.3f, c.x + r * 1.6f, c.y - r * 0.2f, c.x, c.y + r * 0.9f)
                    close()
                }
            drawPath(p, color)
        }

        KeyShape.FLOWER -> {
            for (k in 0 until 6) {
                val a = k * PI.toFloat() / 3f
                drawCircle(color, r * 0.42f, Offset(c.x + cos(a) * r * 0.55f, c.y + sin(a) * r * 0.55f))
            }
            drawCircle(SonzinhoPalette.Sun, r * 0.35f, c)
        }

        KeyShape.SQUARE -> {
            drawRoundRect(
                color,
                topLeft = Offset(c.x - r * 0.85f, c.y - r * 0.85f),
                size = Size(r * 1.7f, r * 1.7f),
                cornerRadius = CornerRadius(r * 0.4f),
            )
        }

        KeyShape.TRIANGLE -> {
            val p =
                Path().apply {
                    moveTo(c.x, c.y - r)
                    lineTo(c.x + r, c.y + r * 0.8f)
                    lineTo(c.x - r, c.y + r * 0.8f)
                    close()
                }
            drawPath(p, color)
        }

        KeyShape.DROP -> {
            val p =
                Path().apply {
                    moveTo(c.x, c.y - r * 1.1f)
                    cubicTo(c.x + r * 0.9f, c.y - r * 0.1f, c.x + r * 0.8f, c.y + r * 0.9f, c.x, c.y + r * 0.9f)
                    cubicTo(c.x - r * 0.8f, c.y + r * 0.9f, c.x - r * 0.9f, c.y - r * 0.1f, c.x, c.y - r * 1.1f)
                    close()
                }
            drawPath(p, color)
        }

        KeyShape.MOON -> {
            drawCircle(color, r, c)
            drawCircle(background, r * 0.8f, Offset(c.x + r * 0.45f, c.y - r * 0.2f))
        }
    }
}

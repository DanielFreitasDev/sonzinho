package dev.danielfreitas.sonzinho.ui.baby

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import dev.danielfreitas.sonzinho.domain.model.AppSettings
import dev.danielfreitas.sonzinho.domain.model.Instrument
import dev.danielfreitas.sonzinho.domain.music.MusicTheory
import dev.danielfreitas.sonzinho.ui.theme.SonzinhoPalette
import kotlin.math.max
import kotlin.math.sin

/**
 * Tela mágica: a tela inteira é um instrumento. Faixas diagonais coloridas vão do grave
 * (embaixo, à esquerda, cores quentes) ao agudo (em cima, à direita, cores frias). Arrastar o
 * dedo passa por várias notas, como um glissando.
 */
@Composable
fun MagicScreen(
    settings: AppSettings,
    effects: EffectsState,
    haptics: BabyHaptics,
    onPlay: (instrumentId: Int, midi: Float, velocity: Float, pan: Float) -> Unit,
    modifier: Modifier = Modifier,
    scaleOverride: dev.danielfreitas.sonzinho.domain.model.Scale? = null,
    keyOverride: dev.danielfreitas.sonzinho.domain.model.MusicKey? = null,
) {
    val instrument = Instrument.fromId(settings.instrumentId) ?: Instrument.MARIMBA
    val scale = scaleOverride ?: settings.scale
    val key = keyOverride ?: settings.key
    val notes = remember(scale, key, instrument) { MusicTheory.playableNotes(scale, key, instrument, MAX_REGIONS) }
    val state = remember { MagicTouchState() }
    val glow = remember(notes.size) { FloatArray(notes.size) }
    val time = rememberAmbientTime()

    fun trigger(
        region: Int,
        position: Offset,
        pressure: Float,
    ) {
        val size = state.size
        val midi = notes[region].toFloat()
        val velocity = MusicTheory.velocityForTouch(pressure, 0.5f)
        onPlay(instrument.id, midi, velocity, MusicTheory.panFor(position.x, size.width.toFloat()))
        val color = SonzinhoPalette.note(region)
        effects.ripple(position.x, position.y, color, max(size.width, size.height) * 0.22f)
        effects.burst(position.x, position.y, color, baseCount = 9)
        glow[region] = 1f
        haptics.tap()
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { state.size = it }
            .semantics { contentDescription = "Tela mágica: toque em qualquer lugar para fazer música" }
            .multiTouch(
                onDown = { id, pos, pressure ->
                    val region = state.regionAt(pos, notes.size)
                    state.pointers[id] = PointerTrack(region, SystemClock.uptimeMillis())
                    trigger(region, pos, pressure)
                },
                onMove = { id, pos ->
                    val track = state.pointers[id] ?: return@multiTouch
                    val region = state.regionAt(pos, notes.size)
                    val now = SystemClock.uptimeMillis()
                    if (region != track.region && now - track.lastNoteAt >= GLISSANDO_MS) {
                        state.pointers[id] = PointerTrack(region, now)
                        trigger(region, pos, 0.5f)
                    }
                },
                onUp = { id -> state.pointers.remove(id) },
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = time()
            drawRect(
                Brush.verticalGradient(listOf(SonzinhoPalette.NightDeep, SonzinhoPalette.Night, SonzinhoPalette.Plum)),
            )
            val w = size.width
            val h = size.height
            val n = notes.size
            val a = HORIZONTAL / w
            val b = (1f - HORIZONTAL) / h
            val ext = h * 2f
            for (i in 0 until n) {
                val v0 = i / n.toFloat()
                val v1 = (i + 1) / n.toFloat()

                // Bordas da faixa: pontos onde a*x + b*(h - y) = v.
                fun xAt(
                    v: Float,
                    yUp: Float,
                ) = (v - b * yUp) / a
                val path =
                    Path().apply {
                        moveTo(xAt(v0, -ext), h + ext)
                        lineTo(xAt(v0, h + ext), -ext)
                        lineTo(xAt(v1, h + ext), -ext)
                        lineTo(xAt(v1, -ext), h + ext)
                        close()
                    }
                // Respiração lenta (período ~7 s) e brilho suave na faixa tocada.
                val breathe = 0.5f + 0.5f * sin(t * 0.9f + i * 0.55f)
                val alpha = 0.10f + 0.06f * breathe + 0.22f * glow[i]
                drawPath(path, SonzinhoPalette.note(i).copy(alpha = alpha))
                glow[i] = (glow[i] - 0.016f).coerceAtLeast(0f)
            }
        }
        EffectsLayer(effects, Modifier.fillMaxSize())
    }
}

private data class PointerTrack(
    val region: Int,
    val lastNoteAt: Long,
)

private class MagicTouchState {
    var size: IntSize = IntSize.Zero
    val pointers = HashMap<Long, PointerTrack>()

    fun regionAt(
        pos: Offset,
        regions: Int,
    ): Int = MusicTheory.magicRegion(pos.x, pos.y, size.width.toFloat(), size.height.toFloat(), regions)
}

private const val MAX_REGIONS = 13
private const val GLISSANDO_MS = 70L
private const val HORIZONTAL = 0.68f

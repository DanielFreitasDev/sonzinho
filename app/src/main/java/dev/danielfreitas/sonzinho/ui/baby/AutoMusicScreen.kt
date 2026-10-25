package dev.danielfreitas.sonzinho.ui.baby

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import dev.danielfreitas.sonzinho.domain.audio.SequenceData
import dev.danielfreitas.sonzinho.domain.model.AppSettings
import dev.danielfreitas.sonzinho.ui.theme.SonzinhoPalette
import kotlin.math.exp
import kotlin.math.sin

/**
 * Música que toca sozinha: as músicas e gravações dos pais tocam em sequência com animação.
 * Por baixo há uma tela mágica na mesma escala e tonalidade da música, então o bebê pode
 * tocar junto e tudo combina.
 */
@Composable
fun AutoMusicScreen(
    settings: AppSettings,
    item: AutoMusicItem?,
    effects: EffectsState,
    haptics: BabyHaptics,
    positionProvider: () -> Double,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPlay: (instrumentId: Int, midi: Float, velocity: Float, pan: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(Unit) {
        onStart()
        onDispose { onStop() }
    }
    val time = rememberAmbientTime()
    val tracker = remember(item?.sequence) { EventTracker(item?.sequence) }

    Box(modifier.fillMaxSize()) {
        MagicScreen(
            settings = settings,
            effects = effects,
            haptics = haptics,
            onPlay = onPlay,
            scaleOverride = item?.scale,
            keyOverride = item?.key,
        )
        Canvas(Modifier.fillMaxSize()) {
            val t = time()
            val seq = item?.sequence
            val position = positionProvider()
            if (seq != null) {
                tracker.advance(position) { event ->
                    val progress = (event.timeSeconds / seq.lengthSeconds).toFloat()
                    val pitch = ((event.midi - 30f) / 66f).coerceIn(0f, 1f)
                    val x = size.width * (0.08f + 0.84f * progress)
                    val y = size.height * (0.88f - 0.76f * pitch)
                    val color = SonzinhoPalette.note(event.midi.toInt())
                    effects.ripple(x, y, color, size.minDimension * 0.12f)
                    effects.burst(x, y, color, baseCount = 5, spread = 0.7f)
                    tracker.lastBeatAt = t
                }
                // Linha do tempo suave: uma lua que atravessa o céu acompanhando a música.
                val progress = (position / seq.lengthSeconds).toFloat().coerceIn(0f, 1f)
                val moonX = size.width * (0.08f + 0.84f * progress)
                val pulse = exp(-(t - tracker.lastBeatAt) * 5f)
                val moonY = size.height * 0.12f + sin(t * 1.3f) * size.height * 0.01f
                val r = size.minDimension * (0.045f + 0.008f * pulse)
                drawCircle(SonzinhoPalette.Sun.copy(alpha = 0.18f + 0.12f * pulse), r * 2.2f, Offset(moonX, moonY))
                drawCircle(SonzinhoPalette.Sun, r, Offset(moonX, moonY))
                drawCircle(Color(0x332E2066), r * 0.18f, Offset(moonX - r * 0.35f, moonY - r * 0.1f))
                drawCircle(Color(0x332E2066), r * 0.12f, Offset(moonX + r * 0.3f, moonY + r * 0.3f))
                drawLine(
                    Color.White.copy(alpha = 0.12f),
                    Offset(size.width * 0.08f, moonY),
                    Offset(size.width * 0.92f, moonY),
                    strokeWidth = 4f,
                )
                drawCircle(Color.White.copy(alpha = 0.10f), r * 3f, Offset(moonX, moonY), style = Stroke(2f))
            }
        }
    }
}

/** Detecta quais eventos passaram desde o último quadro (inclusive na volta do loop). */
private class EventTracker(
    private val sequence: SequenceData?,
) {
    private var last = -1.0
    var lastBeatAt = -10f

    fun advance(
        position: Double,
        onEvent: (dev.danielfreitas.sonzinho.domain.audio.SequenceEvent) -> Unit,
    ) {
        val seq = sequence ?: return
        if (last < 0) {
            last = position
            return
        }
        if (position >= last) {
            for (e in seq.events) if (e.timeSeconds > last && e.timeSeconds <= position) onEvent(e)
        } else {
            for (e in seq.events) if (e.timeSeconds > last || e.timeSeconds <= position) onEvent(e)
        }
        last = position
    }
}

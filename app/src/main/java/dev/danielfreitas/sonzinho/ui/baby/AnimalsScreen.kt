package dev.danielfreitas.sonzinho.ui.baby

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import dev.danielfreitas.sonzinho.domain.model.AnimalSoundMode
import dev.danielfreitas.sonzinho.domain.model.AppSettings
import dev.danielfreitas.sonzinho.domain.model.Instrument
import dev.danielfreitas.sonzinho.domain.music.MusicTheory
import dev.danielfreitas.sonzinho.ui.theme.SonzinhoPalette
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Bichinhos musicais: seis bichinhos grandes cobrem a tela toda (cada um "dono" da sua área),
 * então qualquer toque faz um deles cantar e pular. Os sons criados pelos pais que estão
 * visíveis no modo bebê passeiam como bolhinhas mágicas.
 */
@Composable
fun AnimalsScreen(
    settings: AppSettings,
    sounds: List<BabySound>,
    effects: EffectsState,
    haptics: BabyHaptics,
    onPlay: (instrumentId: Int, midi: Float, velocity: Float, pan: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val animals = Animal.entries
    val state = remember { AnimalsState(animals.size) }
    val time = rememberAmbientTime()
    val instrument = Instrument.fromId(settings.instrumentId) ?: Instrument.MARIMBA
    val motion = settings.animationLevel.motionScale
    val notesByAnimal =
        remember(settings.scale, settings.key, instrument, settings.animalSoundMode) {
            animals.map { animal ->
                val voice = animal.voice
                val (low, high) =
                    if (settings.animalSoundMode == AnimalSoundMode.VOICES) {
                        voice.lowestMidi to voice.highestMidi
                    } else {
                        // Mesma ordem grave→agudo dos bichinhos, dentro da região do instrumento.
                        val span = instrument.highestMidi - instrument.lowestMidi
                        val center = instrument.lowestMidi + span * (0.15f + 0.7f * animalPitchRank(animal))
                        (center - 6).toInt() to (center + 6).toInt()
                    }
                MusicTheory.scaleNotes(settings.scale, settings.key, low, high).ifEmpty { listOf(low) }
            }
        }

    fun playAnimal(
        index: Int,
        position: Offset,
    ) {
        val animal = animals[index]
        val notes = notesByAnimal[index]
        val midi = notes[state.nextNote[index] % notes.size].toFloat()
        state.nextNote[index] = (state.nextNote[index] + 1 + (index % 2)) % (notes.size * 4)
        val id = if (settings.animalSoundMode == AnimalSoundMode.VOICES) animal.voice.id else instrument.id
        onPlay(id, midi, 0.8f, MusicTheory.panFor(position.x, state.size.width.toFloat()))
        state.tapTime[index] = time()
        val center = state.centerOf(index, animals.size)
        effects.burst(center.x, center.y - state.radius(animals.size) * 0.8f, animal.body, baseCount = 12, spread = 1.2f)
        effects.ripple(center.x, center.y, animal.accent, state.radius(animals.size) * 1.1f)
        haptics.tap()
    }

    fun playBubble(
        bubble: Int,
        position: Offset,
    ) {
        val sound = sounds[bubble]
        val notes = MusicTheory.scaleNotes(settings.scale, settings.key, 55, 79)
        val midi = notes[(state.bubbleNote++ * 2) % notes.size].toFloat()
        onPlay(sound.slot, midi, 0.8f, MusicTheory.panFor(position.x, state.size.width.toFloat()))
        state.bubblePopped[bubble % state.bubblePopped.size] = time()
        effects.burst(position.x, position.y, SonzinhoPalette.note(sound.sound.colorIndex), baseCount = 16, spread = 1.4f)
        haptics.tap()
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { state.size = it }
            .semantics { contentDescription = "Bichinhos musicais: toque nos bichinhos para ouvir" }
            .multiTouch(onDown = { _, pos, _, _ ->
                val bubble = state.bubbleAt(pos, sounds.size, time())
                if (bubble >= 0) playBubble(bubble, pos) else playAnimal(state.cellAt(pos, animals.size), pos)
            }),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = time()
            drawRect(Brush.verticalGradient(listOf(SonzinhoPalette.NightDeep, SonzinhoPalette.Night, SonzinhoPalette.Dusk)))
            // Colinas suaves ao fundo.
            drawCircle(Color(0xFF2F2A6E), radius = size.width * 0.6f, center = Offset(size.width * 0.2f, size.height * 1.35f))
            drawCircle(Color(0xFF3A2F7A), radius = size.width * 0.55f, center = Offset(size.width * 0.85f, size.height * 1.4f))
            val r = state.radius(animals.size)
            animals.forEachIndexed { i, animal ->
                drawAnimal(animal, state.centerOf(i, animals.size), r, t, t - state.tapTime[i], i * 0.9f, motion)
            }
            sounds.forEachIndexed { i, sound ->
                val p = state.bubblePosition(i, t)
                val popped = t - state.bubblePopped[i % state.bubblePopped.size] < BUBBLE_RESPAWN
                if (!popped) {
                    val c = SonzinhoPalette.note(sound.sound.colorIndex)
                    val br = state.bubbleRadius()
                    drawCircle(c.copy(alpha = 0.35f), radius = br, center = p)
                    drawCircle(
                        c.copy(alpha = 0.8f),
                        radius = br,
                        center = p,
                        style =
                            androidx.compose.ui.graphics.drawscope.Stroke(
                                width =
                                    br * 0.12f,
                            ),
                    )
                    drawCircle(Color.White.copy(alpha = 0.6f), radius = br * 0.2f, center = Offset(p.x - br * 0.35f, p.y - br * 0.35f))
                }
            }
        }
        EffectsLayer(effects, Modifier.fillMaxSize())
    }
}

private fun animalPitchRank(animal: Animal): Float =
    when (animal) {
        Animal.WHALE -> 0f
        Animal.BEAR -> 0.2f
        Animal.FROG -> 0.4f
        Animal.DUCK -> 0.6f
        Animal.CAT -> 0.8f
        Animal.BIRD -> 1f
    }

private class AnimalsState(
    count: Int,
) {
    var size: IntSize = IntSize.Zero
    val tapTime = FloatArray(count) { -10f }
    val nextNote = IntArray(count)
    val bubblePopped = FloatArray(64) { -10f }
    var bubbleNote = 0

    private val landscape get() = size.width >= size.height

    private fun cols(count: Int) = if (landscape) (count + 1) / 2 else 2

    private fun rows(count: Int) = (count + cols(count) - 1) / cols(count)

    fun cellAt(
        p: Offset,
        count: Int,
    ): Int {
        val c = cols(count)
        val r = rows(count)
        val col = ((p.x / size.width) * c).toInt().coerceIn(0, c - 1)
        val row = ((p.y / size.height) * r).toInt().coerceIn(0, r - 1)
        return (row * c + col).coerceIn(0, count - 1)
    }

    fun centerOf(
        i: Int,
        count: Int,
    ): Offset {
        val c = cols(count)
        val r = rows(count)
        val cw = size.width / c.toFloat()
        val ch = size.height / r.toFloat()
        val col = i % c
        val row = i / c
        return Offset(cw * (col + 0.5f), ch * (row + 0.55f))
    }

    fun radius(count: Int): Float {
        val cw = size.width / cols(count).toFloat()
        val ch = size.height / rows(count).toFloat()
        return min(cw, ch) * 0.3f
    }

    fun bubbleRadius(): Float = min(size.width, size.height) * 0.065f

    fun bubblePosition(
        i: Int,
        t: Float,
    ): Offset {
        val speed = 0.035f + 0.01f * (i % 3)
        val u = ((t * speed + i * 0.37f) % 1.2f) - 0.1f
        val y = size.height * (0.1f + 0.05f * (i % 2)) + sin(t * 0.8f + i) * size.height * 0.03f
        return Offset(u * size.width, y)
    }

    fun bubbleAt(
        p: Offset,
        count: Int,
        t: Float,
    ): Int {
        val r = bubbleRadius() * 1.3f
        for (i in 0 until count) {
            if (t - bubblePopped[i % bubblePopped.size] < BUBBLE_RESPAWN) continue
            val b = bubblePosition(i, t)
            if (hypot(p.x - b.x, p.y - b.y) <= r) return i
        }
        return -1
    }
}

private const val BUBBLE_RESPAWN = 2.5f

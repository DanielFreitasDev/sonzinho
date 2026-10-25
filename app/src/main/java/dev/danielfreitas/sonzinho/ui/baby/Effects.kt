package dev.danielfreitas.sonzinho.ui.baby

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Partículas e ondas em vetores pré-alocados: nenhum objeto é criado por quadro, então o
 * coletor de lixo não interrompe a animação (60–120 fps) nem compete com a thread de áudio.
 * Tudo some devagar (sem flashes): opacidade máxima limitada e fades longos.
 */
class EffectsState(
    private val capacity: Int = 420,
    private val rippleCapacity: Int = 48,
) {
    var frame by mutableLongStateOf(0L)
        private set

    var intensity = 1f
    var motion = 1f

    private val px = FloatArray(capacity)
    private val py = FloatArray(capacity)
    private val vx = FloatArray(capacity)
    private val vy = FloatArray(capacity)
    private val life = FloatArray(capacity)
    private val maxLife = FloatArray(capacity)
    private val pSize = FloatArray(capacity)
    private val spin = FloatArray(capacity)
    private val color = IntArray(capacity)
    private val kind = IntArray(capacity)
    private var cursor = 0

    private val rx = FloatArray(rippleCapacity)
    private val ry = FloatArray(rippleCapacity)
    private val rLife = FloatArray(rippleCapacity)
    private val rMax = FloatArray(rippleCapacity)
    private val rColor = IntArray(rippleCapacity)
    private var rCursor = 0

    private val random = Random(7)

    fun burst(
        x: Float,
        y: Float,
        tint: Color,
        baseCount: Int = 10,
        spread: Float = 1f,
    ) {
        val count = (baseCount * intensity).toInt().coerceAtLeast(2)
        val argb = tint.toArgb()
        repeat(count) {
            val i = cursor
            cursor = (cursor + 1) % capacity
            val angle = random.nextFloat() * 2f * PI.toFloat()
            val speed = (60f + random.nextFloat() * 170f) * spread * motion
            px[i] = x
            py[i] = y
            vx[i] = cos(angle) * speed
            vy[i] = sin(angle) * speed - 90f * motion
            maxLife[i] = 1.1f + random.nextFloat() * 1.0f
            life[i] = maxLife[i]
            pSize[i] = 6f + random.nextFloat() * 14f
            spin[i] = random.nextFloat() * 6f - 3f
            color[i] = argb
            kind[i] =
                when (random.nextInt(10)) {
                    in 0..4 -> KIND_DOT
                    in 5..7 -> KIND_STAR
                    else -> KIND_NOTE
                }
        }
    }

    fun ripple(
        x: Float,
        y: Float,
        tint: Color,
        maxRadius: Float,
    ) {
        val i = rCursor
        rCursor = (rCursor + 1) % rippleCapacity
        rx[i] = x
        ry[i] = y
        rMax[i] = maxRadius
        rLife[i] = RIPPLE_SECONDS
        rColor[i] = tint.toArgb()
    }

    fun step(dt: Float) {
        val d = dt.coerceIn(0f, 0.05f)
        for (i in 0 until capacity) {
            if (life[i] <= 0f) continue
            life[i] -= d
            vx[i] *= 1f - 1.6f * d
            vy[i] = vy[i] * (1f - 1.6f * d) - 20f * d * motion
            px[i] += vx[i] * d
            py[i] += vy[i] * d
        }
        for (i in 0 until rippleCapacity) if (rLife[i] > 0f) rLife[i] -= d
        frame++
    }

    fun draw(scope: DrawScope) {
        with(scope) {
            for (i in 0 until rippleCapacity) {
                if (rLife[i] <= 0f) continue
                val t = 1f - rLife[i] / RIPPLE_SECONDS
                val eased = 1f - (1f - t) * (1f - t) * (1f - t)
                val alpha = 0.55f * (1f - t)
                val c = Color(rColor[i]).copy(alpha = alpha)
                drawCircle(c, radius = 18f + rMax[i] * eased, center = Offset(rx[i], ry[i]), style = Stroke(width = 10f * (1f - t) + 2f))
                drawCircle(c.copy(alpha = alpha * 0.25f), radius = 12f + rMax[i] * 0.6f * eased, center = Offset(rx[i], ry[i]))
            }
            for (i in 0 until capacity) {
                if (life[i] <= 0f) continue
                val t = life[i] / maxLife[i]
                val alpha = (if (t > 0.8f) (1f - t) * 5f else t / 0.8f).coerceIn(0f, 1f) * 0.85f
                val c = Color(color[i]).copy(alpha = alpha)
                val s = pSize[i] * (0.6f + 0.4f * t)
                when (kind[i]) {
                    KIND_DOT -> {
                        drawCircle(c, radius = s * 0.6f, center = Offset(px[i], py[i]))
                    }

                    KIND_STAR -> {
                        withTransform({
                            translate(px[i], py[i])
                            rotate(degrees = spin[i] * (1f - t) * 60f, pivot = Offset.Zero)
                            scale(s, s, pivot = Offset.Zero)
                        }) { drawPath(unitStar, c) }
                    }

                    else -> {
                        drawNote(Offset(px[i], py[i]), s, c)
                    }
                }
            }
        }
    }

    private fun DrawScope.drawNote(
        at: Offset,
        s: Float,
        c: Color,
    ) {
        translate(at.x, at.y) {
            drawCircle(c, radius = s * 0.55f, center = Offset(0f, s * 0.6f))
            drawRect(c, topLeft = Offset(s * 0.4f, -s * 0.9f), size = Size(s * 0.18f, s * 1.5f))
            drawRect(c, topLeft = Offset(s * 0.4f, -s * 0.9f), size = Size(s * 0.6f, s * 0.22f))
        }
    }

    companion object {
        private const val KIND_DOT = 0
        private const val KIND_STAR = 1
        private const val KIND_NOTE = 2
        private const val RIPPLE_SECONDS = 1.3f

        /** Estrela de cinco pontas arredondada, raio 1, centrada na origem. */
        val unitStar: Path =
            Path().apply {
                for (k in 0 until 10) {
                    val r = if (k % 2 == 0) 1f else 0.48f
                    val a = -PI / 2 + k * PI / 5
                    val x = (cos(a) * r).toFloat()
                    val y = (sin(a) * r).toFloat()
                    if (k == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
    }
}

@Composable
fun rememberEffectsState(
    intensity: Float,
    motion: Float,
): EffectsState {
    val state = remember { EffectsState() }
    state.intensity = intensity
    state.motion = motion
    LaunchedEffect(state) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) state.step((now - last) / 1_000_000_000f)
                last = now
            }
        }
    }
    return state
}

@Composable
fun EffectsLayer(
    state: EffectsState,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        state.frame // lido aqui para redesenhar a cada quadro
        state.draw(this)
    }
}

/** Relógio de animação ambiente (segundos), compartilhado pelas telas do bebê. */
@Composable
fun rememberAmbientTime(): () -> Float {
    var time by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time = it - start }
    }
    return { time / 1_000_000_000f }
}

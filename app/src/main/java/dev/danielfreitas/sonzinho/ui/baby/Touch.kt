package dev.danielfreitas.sonzinho.ui.baby

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView

/**
 * Multitoque bruto: cada dedo (ou pedaço da palma) é acompanhado pelo seu id. Não há gestos a
 * reconhecer: encostar já toca. Funciona com dezenas de ponteiros ao mesmo tempo.
 */
fun Modifier.multiTouch(
    onDown: (id: Long, position: Offset, pressure: Float) -> Unit,
    onMove: (id: Long, position: Offset) -> Unit = { _, _ -> },
    onUp: (id: Long) -> Unit = {},
): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                for (change in event.changes) {
                    val id = change.id.toLong()
                    when {
                        change.pressed && !change.previousPressed -> onDown(id, change.position, change.pressure)
                        change.pressed -> if (change.position != change.previousPosition) onMove(id, change.position)
                        change.previousPressed -> onUp(id)
                    }
                    change.consume()
                }
            }
        }
    }

private fun PointerId.toLong(): Long = value

/** Retorno háptico leve e espaçado (sem zumbido contínuo), usando a própria View: sem permissão. */
class BabyHaptics(
    private val view: android.view.View,
    private val enabled: Boolean,
) {
    private var last = 0L

    fun tap() {
        if (!enabled) return
        val now = SystemClock.uptimeMillis()
        if (now - last < MIN_INTERVAL_MS) return
        last = now
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private companion object {
        const val MIN_INTERVAL_MS = 90L
    }
}

@Composable
fun rememberBabyHaptics(enabled: Boolean): BabyHaptics {
    val view = LocalView.current
    return remember(view, enabled) { BabyHaptics(view, enabled) }
}

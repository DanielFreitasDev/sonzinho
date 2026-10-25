package dev.danielfreitas.sonzinho.ui.parent

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.danielfreitas.sonzinho.domain.usecase.UnlockGesture
import dev.danielfreitas.sonzinho.ui.GateState
import dev.danielfreitas.sonzinho.ui.baby.EffectsState
import dev.danielfreitas.sonzinho.ui.theme.SonzinhoPalette

/**
 * Observa todos os toques (sem consumi-los, então o canto também toca música) e detecta o
 * gesto dos pais: um único dedo parado no canto superior esquerdo por 3 segundos. A palma de
 * um bebê encosta vários dedos ao mesmo tempo e cancela o gesto.
 */
@Composable
fun UnlockCornerDetector(
    onHeld: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val cornerPx = with(LocalDensity.current) { UnlockGesture.CORNER_DP.dp.toPx() }
    var holding by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(holding) {
        progress = 0f
        if (!holding) return@LaunchedEffect
        // Tempo dos quadros (e não o relógio do sistema): testável com o relógio do Compose.
        val start = withFrameMillis { it }
        while (true) {
            val held = withFrameMillis { it } - start
            progress = UnlockGesture.progress(held)
            if (held >= UnlockGesture.HOLD_MILLIS) {
                holding = false
                onHeld()
                break
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .pointerInput(cornerPx) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val down = event.changes.filter { it.pressed }
                        val inCorner =
                            UnlockGesture.isValid(down.size) &&
                                down[0].position.x <= cornerPx &&
                                down[0].position.y <= cornerPx
                        if (inCorner != holding) holding = inCorner
                    }
                }
            },
    ) {
        content()
        Canvas(
            Modifier
                .size(UnlockGesture.CORNER_DP.dp)
                .testTag("cantinho_dos_pais")
                .semantics { contentDescription = "Canto dos pais: segure por 3 segundos com um dedo" },
        ) {
            val c = Offset(size.width * 0.42f, size.height * 0.42f)
            val r = size.minDimension * 0.16f
            drawCircle(Color.White.copy(alpha = 0.10f), r * 1.9f, c)
            withTransform({
                translate(c.x, c.y)
                scale(r, r, pivot = Offset.Zero)
            }) { drawPath(EffectsState.unitStar, Color.White.copy(alpha = 0.32f)) }
            if (progress > 0.05f) {
                drawArc(
                    SonzinhoPalette.Sun,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(c.x - r * 2.4f, c.y - r * 2.4f),
                    size = androidx.compose.ui.geometry.Size(r * 4.8f, r * 4.8f),
                    style = Stroke(width = r * 0.5f, cap = StrokeCap.Round),
                )
            }
        }
    }
}

/** Desafio para adultos: conta simples ou PIN, com teclado numérico grande. */
@Composable
fun ParentChallengeOverlay(
    gate: GateState,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (gate == GateState.Hidden) return
    var input by remember(gate) { mutableStateOf("") }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC17113A))
            // Absorve os toques: o bebê não toca música através do desafio.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .testTag("desafio_pais"),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(16.dp).widthIn(max = 560.dp),
        ) {
            Column(
                Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (gate) {
                    is GateState.CoolingDown -> {
                        Text("Muitas tentativas", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(8.dp))
                        Text("Tente de novo em ${gate.secondsLeft} s", style = MaterialTheme.typography.bodyLarge)
                    }
                    is GateState.Math, is GateState.Pin -> {
                        val title = if (gate is GateState.Math) gate.challenge.question else "Digite o PIN dos pais"
                        val wrong = (gate as? GateState.Math)?.wrong == true || (gate as? GateState.Pin)?.wrong == true
                        Text("Área dos pais", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                        if (wrong) {
                            Text("Ops, não foi dessa vez. Tente de novo.", color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(8.dp))
                        val shown = if (gate is GateState.Pin) "●".repeat(input.length) else input
                        Text(
                            shown.ifEmpty { " " },
                            style = MaterialTheme.typography.displaySmall,
                            modifier = Modifier.testTag("resposta"),
                        )
                        Spacer(Modifier.height(8.dp))
                        NumericKeypad(
                            onDigit = { d -> if (input.length < 8) input += d },
                            onDelete = { input = input.dropLast(1) },
                            onConfirm = {
                                onSubmit(input)
                                input = ""
                            },
                        )
                    }
                    GateState.Hidden -> Unit
                }
                TextButton(onClick = onDismiss) { Text("Voltar a brincar") }
            }
        }
    }
}

@Composable
private fun NumericKeypad(
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    onConfirm: () -> Unit,
) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { d -> KeypadButton(d) { onDigit(d) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onDelete, modifier = Modifier.size(width = 76.dp, height = 56.dp)) {
                Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = "Apagar")
            }
            KeypadButton("0") { onDigit("0") }
            Button(onClick = onConfirm, modifier = Modifier.size(width = 76.dp, height = 56.dp).testTag("confirmar")) { Text("OK") }
        }
    }
}

@Composable
private fun KeypadButton(
    label: String,
    onClick: () -> Unit,
) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.size(width = 76.dp, height = 56.dp).testTag("tecla_$label")) {
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
}

/** Aviso de tempo de uso: uma lua dormindo e estrelas lentas. O bebê não toca mais até os pais virem. */
@Composable
fun RestOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xF0110C2E))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }.semantics { contentDescription = "Hora de descansar" },
        contentAlignment = Alignment.Center,
    ) {
        val time = dev.danielfreitas.sonzinho.ui.baby.rememberAmbientTime()
        Canvas(Modifier.fillMaxSize()) {
            val t = time()
            for (k in 0 until 24) {
                val x = size.width * ((k * 0.137f) % 1f)
                val y = size.height * ((k * 0.291f) % 0.8f)
                val a = 0.25f + 0.2f * kotlin.math.sin(t * 0.6f + k)
                drawCircle(Color.White.copy(alpha = a), 3f + (k % 3), Offset(x, y))
            }
            val c = Offset(size.width / 2, size.height * 0.42f)
            val r = size.minDimension * 0.16f
            drawCircle(SonzinhoPalette.Sun.copy(alpha = 0.15f), r * 1.6f, c)
            drawCircle(SonzinhoPalette.Sun, r, c)
            drawCircle(Color(0xF0110C2E), r * 0.85f, Offset(c.x + r * 0.5f, c.y - r * 0.25f))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 260.dp)) {
            Text("Hora de descansar", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Spacer(Modifier.width(8.dp))
            Text(
                "Para continuar, um adulto segura o cantinho com a estrela.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )
        }
    }
}

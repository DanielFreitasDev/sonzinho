package dev.danielfreitas.sonzinho.ui.parent

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.danielfreitas.sonzinho.domain.audio.AudioEngine
import dev.danielfreitas.sonzinho.domain.model.CustomSound
import dev.danielfreitas.sonzinho.domain.model.Instrument
import dev.danielfreitas.sonzinho.domain.model.SoundParams
import dev.danielfreitas.sonzinho.domain.model.SoundShape
import dev.danielfreitas.sonzinho.domain.music.SoundDesigner
import dev.danielfreitas.sonzinho.domain.repository.LibraryRepository
import dev.danielfreitas.sonzinho.ui.common.ChoiceChips
import dev.danielfreitas.sonzinho.ui.common.LabeledSlider
import dev.danielfreitas.sonzinho.ui.common.ParentScaffold
import dev.danielfreitas.sonzinho.ui.theme.SonzinhoPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreatorState(
    val id: Long = 0,
    val name: String = "",
    val params: SoundParams = SoundParams(),
    val colorIndex: Int = 0,
    val saved: Boolean = false,
)

@HiltViewModel
class CreatorViewModel
    @Inject
    constructor(
        private val engine: AudioEngine,
        private val library: LibraryRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(CreatorState())
        val state: StateFlow<CreatorState> = _state.asStateFlow()
        private var voice = 0
        private var loaded = false

        fun load(soundId: Long?) {
            if (loaded) return
            loaded = true
            viewModelScope.launch {
                val existing = soundId?.let { id -> library.sounds.first().firstOrNull { it.id == id } }
                if (existing != null) {
                    _state.value = CreatorState(existing.id, existing.name, existing.params, existing.colorIndex, saved = true)
                }
                applyPatch()
            }
        }

        fun update(transform: (SoundParams) -> SoundParams) {
            _state.value = _state.value.copy(params = transform(_state.value.params), saved = false)
            applyPatch()
        }

        fun setColor(index: Int) {
            _state.value = _state.value.copy(colorIndex = index, saved = false)
        }

        /** Pad X/Y: X = macio → brilhante, Y = grave (embaixo) → agudo (em cima). */
        fun padDown(
            x: Float,
            y: Float,
        ) {
            update { it.copy(brightness = x, pitch = y) }
            val p = _state.value.params
            if (voice != 0) engine.noteOff(voice)
            voice = engine.noteOn(Instrument.CREATOR_PREVIEW_SLOT, SoundDesigner.midiFor(p), 0.8f, 0f, 0f)
        }

        fun padMove(
            x: Float,
            y: Float,
        ) {
            _state.value = _state.value.copy(params = _state.value.params.copy(brightness = x, pitch = y), saved = false)
            val p = _state.value.params
            // Brilho relativo ao patch atual: o filtro acompanha o dedo sem reiniciar a nota.
            engine.setVoiceTarget(voice, SoundDesigner.midiFor(p), 0.6f + 0.8f * x)
        }

        fun padUp() {
            applyPatch()
            if (voice != 0) engine.noteOff(voice)
            voice = 0
        }

        fun preview() {
            val p = _state.value.params
            engine.noteOn(Instrument.CREATOR_PREVIEW_SLOT, SoundDesigner.midiFor(p), 0.8f, 0f, 0.6f)
        }

        fun save(name: String) {
            viewModelScope.launch {
                val s = _state.value
                val id = library.saveSound(CustomSound(s.id, name.ifBlank { "Meu som" }.take(40), s.params, s.colorIndex))
                _state.value = s.copy(id = id, name = name, saved = true)
            }
        }

        private fun applyPatch() {
            engine.definePatch(Instrument.CREATOR_PREVIEW_SLOT, SoundDesigner.toPatch(_state.value.params).toFloatArray())
        }

        override fun onCleared() {
            if (voice != 0) engine.noteOff(voice)
        }
    }

@Composable
fun CreatorScreen(
    soundId: Long?,
    onBack: () -> Unit,
    viewModel: CreatorViewModel = hiltViewModel(),
) {
    LaunchedEffect(soundId) { viewModel.load(soundId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var askName by remember { mutableStateOf(false) }
    val p = state.params

    ParentScaffold(
        title = "Criador de sons",
        onBack = onBack,
        actions = {
            Button(onClick = { askName = true }, modifier = Modifier.testTag("salvar_som")) {
                Icon(Icons.Rounded.Save, contentDescription = null)
                Text(if (state.saved) "Salvo" else "Salvar")
            }
        },
    ) {
        Text("Arraste o dedo no quadro: para cima fica mais agudo, para a direita mais brilhante.", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            XYPad(
                x = p.brightness,
                y = p.pitch,
                color = SonzinhoPalette.note(state.colorIndex),
                onDown = viewModel::padDown,
                onMove = viewModel::padMove,
                onUp = viewModel::padUp,
                modifier = Modifier.weight(1f).widthIn(max = 420.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Forma do som", style = MaterialTheme.typography.titleMedium)
                ChoiceChips(SoundShape.entries, p.shape, { it.displayName }, { shape -> viewModel.update { it.copy(shape = shape) } })
                LabeledSlider("Duração", p.length, { v -> viewModel.update { it.copy(length = v) } }, startLabel = "Curtinho", endLabel = "Longo", onValueChangeFinished = viewModel::preview)
                LabeledSlider("Eco", p.echo, { v -> viewModel.update { it.copy(echo = v) } }, startLabel = "Sem eco", endLabel = "Muito eco", onValueChangeFinished = viewModel::preview)
                LabeledSlider("Balanço", p.wobble, { v -> viewModel.update { it.copy(wobble = v) } }, startLabel = "Parado", endLabel = "Balança", onValueChangeFinished = viewModel::preview)
                Text("Cor da bolhinha no modo bebê", style = MaterialTheme.typography.titleMedium)
                ChoiceChips((0 until SonzinhoPalette.notes.size).toList(), state.colorIndex, { colorName(it) }, viewModel::setColor)
            }
        }
    }

    if (askName) {
        var name by remember { mutableStateOf(state.name.ifBlank { "Meu som" }) }
        AlertDialog(
            onDismissRequest = { askName = false },
            title = { Text("Nome do som") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, singleLine = true, modifier = Modifier.testTag("nome_som")) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.save(name)
                    askName = false
                }) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { askName = false }) { Text("Cancelar") } },
        )
    }
}

private fun colorName(i: Int): String = listOf("Coral", "Tangerina", "Sol", "Limão", "Menta", "Céu", "Lavanda", "Chiclete")[i]

@Composable
private fun XYPad(
    x: Float,
    y: Float,
    color: Color,
    onDown: (Float, Float) -> Unit,
    onMove: (Float, Float) -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .testTag("pad_xy")
            .semantics { contentDescription = "Quadro de som: para cima mais agudo, para a direita mais brilhante" }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        val nx = (change.position.x / size.width).coerceIn(0f, 1f)
                        val ny = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                        when {
                            change.pressed && !change.previousPressed -> onDown(nx, ny)
                            change.pressed -> onMove(nx, ny)
                            change.previousPressed -> onUp()
                        }
                        change.consume()
                    }
                }
            },
    ) {
        drawRoundRect(
            Brush.linearGradient(listOf(SonzinhoPalette.Night, SonzinhoPalette.Plum, SonzinhoPalette.Dusk)),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(48f),
        )
        for (k in 1..3) {
            val f = k / 4f
            drawLine(Color.White.copy(alpha = 0.08f), Offset(size.width * f, 0f), Offset(size.width * f, size.height), 2f)
            drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, size.height * f), Offset(size.width, size.height * f), 2f)
        }
        val c = Offset(x * size.width, (1f - y) * size.height)
        drawCircle(color.copy(alpha = 0.25f), radius = size.minDimension * 0.12f, center = c)
        drawCircle(color, radius = size.minDimension * 0.06f, center = c)
        drawCircle(Color.White.copy(alpha = 0.6f), radius = size.minDimension * 0.09f, center = c, style = Stroke(4f))
    }
}

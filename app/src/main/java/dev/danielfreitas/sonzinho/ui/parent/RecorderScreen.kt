package dev.danielfreitas.sonzinho.ui.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.danielfreitas.sonzinho.domain.model.AppSettings
import dev.danielfreitas.sonzinho.domain.model.RecordedNote
import dev.danielfreitas.sonzinho.domain.model.Recording
import dev.danielfreitas.sonzinho.domain.repository.LibraryRepository
import dev.danielfreitas.sonzinho.domain.repository.SettingsRepository
import dev.danielfreitas.sonzinho.domain.usecase.NotePlayer
import dev.danielfreitas.sonzinho.ui.baby.MagicScreen
import dev.danielfreitas.sonzinho.ui.baby.rememberBabyHaptics
import dev.danielfreitas.sonzinho.ui.baby.rememberEffectsState
import dev.danielfreitas.sonzinho.ui.common.ParentScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecorderViewModel
    @Inject
    constructor(
        private val notePlayer: NotePlayer,
        private val library: LibraryRepository,
        settingsRepository: SettingsRepository,
    ) : ViewModel() {
        val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
        val recording: StateFlow<Boolean> = notePlayer.recorder.recording
        private var pending: Pair<List<RecordedNote>, Long>? = null

        fun play(
            id: Int,
            midi: Float,
            velocity: Float,
            pan: Float,
        ) {
            notePlayer.play(id, midi, velocity, pan)
        }

        fun start() = notePlayer.recorder.start()

        /** Para a gravação; devolve true se há notas para salvar. */
        fun stop(): Boolean {
            val result = notePlayer.recorder.stop()
            pending = result
            return result.first.isNotEmpty()
        }

        fun elapsed(): Long = notePlayer.recorder.elapsedMillis()

        fun save(name: String) {
            val (notes, duration) = pending ?: return
            pending = null
            viewModelScope.launch {
                library.saveRecording(Recording(name = name.ifBlank { "Minha gravação" }.take(40), durationMs = duration, notes = notes))
            }
        }

        fun discard() {
            pending = null
        }

        override fun onCleared() {
            if (recording.value) notePlayer.recorder.stop()
        }
    }

@Composable
fun RecorderScreen(
    onBack: () -> Unit,
    viewModel: RecorderViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    var askName by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(recording) {
        while (recording) {
            elapsed = viewModel.elapsed()
            delay(200)
        }
    }
    val effects = rememberEffectsState(settings.animationLevel.particleScale, settings.animationLevel.motionScale)
    val haptics = rememberBabyHaptics(false)

    ParentScaffold(title = "Gravar", onBack = onBack, scrollable = false) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = {
                    if (recording) {
                        askName = viewModel.stop()
                    } else {
                        viewModel.start()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.height(56.dp).testTag("botao_gravar"),
            ) {
                Icon(if (recording) Icons.Rounded.Stop else Icons.Rounded.FiberManualRecord, contentDescription = null)
                Text(if (recording) "Parar" else "Gravar")
            }
            Text(
                if (recording) "Gravando… %d:%02d".format(elapsed / 60_000, (elapsed / 1000) % 60) else "Toque o quadro abaixo. O que você tocar será gravado.",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(28.dp))) {
            MagicScreen(settings, effects, haptics, onPlay = viewModel::play)
        }
    }

    if (askName) {
        var name by remember { mutableStateOf("Minha gravação") }
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Salvar gravação") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.save(name)
                    askName = false
                }) { Text("Salvar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.discard()
                    askName = false
                }) { Text("Descartar") }
            },
        )
    }
}

package dev.danielfreitas.sonzinho.ui.parent

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import dev.danielfreitas.sonzinho.domain.model.Cell
import dev.danielfreitas.sonzinho.domain.model.Instrument
import dev.danielfreitas.sonzinho.domain.model.MusicKey
import dev.danielfreitas.sonzinho.domain.model.Scale
import dev.danielfreitas.sonzinho.domain.model.Song
import dev.danielfreitas.sonzinho.domain.model.TrackKind
import dev.danielfreitas.sonzinho.domain.music.SongEditor
import dev.danielfreitas.sonzinho.domain.music.SongScheduler
import dev.danielfreitas.sonzinho.domain.repository.LibraryRepository
import dev.danielfreitas.sonzinho.domain.repository.SettingsRepository
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

data class SequencerState(
    val song: Song = SongEditor.demoSong(),
    val track: TrackKind = TrackKind.MELODY,
    val playing: Boolean = false,
    val saved: Boolean = true,
    val loaded: Boolean = false,
)

@HiltViewModel
@Suppress("TooManyFunctions")
class SequencerViewModel
    @Inject
    constructor(
        private val engine: AudioEngine,
        private val library: LibraryRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(SequencerState())
        val state: StateFlow<SequencerState> = _state.asStateFlow()

        fun load(songId: Long?) {
            if (_state.value.loaded) return
            viewModelScope.launch {
                val settings = settingsRepository.settings.first()
                val existing = songId?.let { id -> library.songs.first().firstOrNull { it.id == id } }
                val song = existing ?: SongEditor.demoSong("Música nova").copy(scale = settings.scale, key = settings.key)
                _state.value = SequencerState(song = song, saved = existing != null, loaded = true)
            }
        }

        fun selectTrack(kind: TrackKind) = _state.update { it.copy(track = kind) }

        fun toggle(
            step: Int,
            row: Int,
        ) {
            val s = _state.value
            val updated = SongEditor.toggleCell(s.song, s.track, step, row)
            val track = updated.tracks.first { it.kind == s.track }
            if (Cell(step, row) in track.cells) {
                // Prévia da nota ao acender a célula.
                engine.noteOn(track.instrumentId, SongScheduler.midiFor(updated, track, row).toFloat(), 0.7f, 0f, 0.5f)
            }
            change(updated)
        }

        fun setTempo(bpm: Int) = change(SongEditor.setTempo(_state.value.song, bpm))

        fun setSteps(steps: Int) = change(SongEditor.setSteps(_state.value.song, steps))

        fun setInstrument(id: Int) = change(SongEditor.setInstrument(_state.value.song, _state.value.track, id))

        fun toggleMute() = change(SongEditor.toggleMute(_state.value.song, _state.value.track))

        fun clearTrack() = change(SongEditor.clearTrack(_state.value.song, _state.value.track))

        fun setScale(scale: Scale) = change(_state.value.song.copy(scale = scale))

        fun setKey(key: MusicKey) = change(_state.value.song.copy(key = key))

        fun rename(name: String) = change(_state.value.song.copy(name = name.take(40)))

        fun togglePlay() {
            val s = _state.value
            if (s.playing) {
                engine.stopSequence()
                engine.allNotesOff()
            } else {
                engine.setSequence(SongScheduler.toSequence(s.song, EDITOR_ID))
                engine.playSequence(fromStart = true)
            }
            _state.update { it.copy(playing = !s.playing) }
        }

        fun save() {
            viewModelScope.launch {
                val song = _state.value.song
                val id = library.saveSong(song.copy(name = song.name.ifBlank { "Minha música" }))
                _state.update { it.copy(song = it.song.copy(id = id), saved = true) }
            }
        }

        fun position(): Double = engine.sequencePositionSeconds()

        private fun change(song: Song) {
            _state.update { it.copy(song = song, saved = false) }
            // Tocando: troca a sequência sem perder o compasso (mesmo id preserva a fase).
            if (_state.value.playing) engine.setSequence(SongScheduler.toSequence(song, EDITOR_ID))
        }

        override fun onCleared() {
            engine.stopSequence()
        }

        private inline fun MutableStateFlow<SequencerState>.update(block: (SequencerState) -> SequencerState) {
            value = block(value)
        }

        private companion object {
            const val EDITOR_ID = 777_000
        }
    }

@Composable
fun SequencerScreen(
    songId: Long?,
    onBack: () -> Unit,
    viewModel: SequencerViewModel = hiltViewModel(),
) {
    LaunchedEffect(songId) { viewModel.load(songId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val song = state.song
    val track = song.tracks.first { it.kind == state.track }
    var playStep by remember { mutableIntStateOf(-1) }
    LaunchedEffect(state.playing, song.tempoBpm, song.steps) {
        if (!state.playing) {
            playStep = -1
            return@LaunchedEffect
        }
        while (true) withFrameNanos { playStep = SongScheduler.currentStep(viewModel.position(), song) }
    }

    ParentScaffold(
        title = "Fazer música",
        onBack = onBack,
        actions = {
            FilledTonalButton(onClick = viewModel::togglePlay, modifier = Modifier.testTag("tocar_musica")) {
                Icon(if (state.playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, contentDescription = null)
                Text(if (state.playing) "Parar" else "Tocar")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = viewModel::save, enabled = !state.saved, modifier = Modifier.testTag("salvar_musica")) {
                Icon(Icons.Rounded.Save, contentDescription = null)
                Text(if (state.saved) "Salva" else "Salvar")
            }
        },
    ) {
        OutlinedTextField(
            value = song.name,
            onValueChange = viewModel::rename,
            label = { Text("Nome da música") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        PrimaryTabRow(selectedTabIndex = state.track.ordinal) {
            TrackKind.entries.forEach { kind ->
                Tab(
                    selected = kind == state.track,
                    onClick = { viewModel.selectTrack(kind) },
                    text = { Text(kind.displayName) },
                )
            }
        }
        StepGrid(
            song = song,
            selected = state.track,
            playStep = playStep,
            onToggle = viewModel::toggle,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = viewModel::toggleMute) {
                Icon(Icons.AutoMirrored.Rounded.VolumeOff, contentDescription = null)
                Text(if (track.muted) "Linha calada" else "Calar linha")
            }
            TextButton(onClick = viewModel::clearTrack) {
                Icon(Icons.Rounded.Delete, contentDescription = null)
                Text("Limpar linha")
            }
        }
        Text("Instrumento da linha", style = MaterialTheme.typography.titleMedium)
        val options =
            when (state.track) {
                TrackKind.BASS -> listOf(Instrument.BASS, Instrument.PIANO, Instrument.HARP, Instrument.MARIMBA, Instrument.PAD)
                TrackKind.PAD -> listOf(Instrument.PAD, Instrument.VIBRAPHONE, Instrument.CHIMES, Instrument.FLUTE)
                else -> Instrument.melodic
            }
        ChoiceChips(
            options,
            Instrument.fromId(track.instrumentId) ?: options.first(),
            { it.displayName },
            { viewModel.setInstrument(it.id) },
        )
        var tempo by remember(song.tempoBpm) { mutableFloatStateOf(song.tempoBpm.toFloat()) }
        LabeledSlider(
            label = "Andamento",
            value = tempo,
            onValueChange = { tempo = it },
            onValueChangeFinished = { viewModel.setTempo(tempo.toInt()) },
            range = Song.tempoRange.first.toFloat()..Song.tempoRange.last.toFloat(),
            valueText = "${tempo.toInt()} batidas/min",
            startLabel = "Devagar",
            endLabel = "Rápido",
        )
        Text("Passos", style = MaterialTheme.typography.titleMedium)
        ChoiceChips(Song.stepOptions, song.steps, { "$it passos" }, viewModel::setSteps)
        Text("Escala e tonalidade", style = MaterialTheme.typography.titleMedium)
        ChoiceChips(Scale.entries, song.scale, { it.displayName }, viewModel::setScale)
        ChoiceChips(MusicKey.entries, song.key, { it.displayName }, viewModel::setKey)
    }
}

/** Grade de notas: linhas são alturas (grave embaixo), colunas são passos. As outras linhas aparecem bem clarinhas. */
@Composable
private fun StepGrid(
    song: Song,
    selected: TrackKind,
    playStep: Int,
    onToggle: (step: Int, row: Int) -> Unit,
) {
    val rows = Song.ROWS
    val steps = song.steps
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(steps / rows.toFloat() * 0.9f)
            .heightIn(max = 520.dp)
            .testTag("grade_sequenciador")
            .semantics { contentDescription = "Grade de notas: toque para acender ou apagar" }
            .pointerInput(steps, selected) {
                detectTapGestures { p ->
                    val step = (p.x / size.width * steps).toInt().coerceIn(0, steps - 1)
                    val row = rows - 1 - (p.y / size.height * rows).toInt().coerceIn(0, rows - 1)
                    onToggle(step, row)
                }
            },
    ) {
        val cw = size.width / steps
        val ch = size.height / rows
        val pad = minOf(cw, ch) * 0.08f
        for (step in 0 until steps) {
            if (step == playStep) {
                drawRect(SonzinhoPalette.Sun.copy(alpha = 0.25f), topLeft = Offset(step * cw, 0f), size = Size(cw, size.height))
            } else if (step % 4 == 0) {
                drawRect(Color.Black.copy(alpha = 0.04f), topLeft = Offset(step * cw, 0f), size = Size(cw, size.height))
            }
        }
        song.tracks.forEach { t ->
            val isSelected = t.kind == selected
            for (step in 0 until steps) {
                for (row in 0 until rows) {
                    val on = Cell(step, row) in t.cells
                    val topLeft = Offset(step * cw + pad, (rows - 1 - row) * ch + pad)
                    val cell = Size(cw - 2 * pad, ch - 2 * pad)
                    val color = SonzinhoPalette.note(row)
                    if (isSelected) {
                        drawRoundRect(
                            if (on) color else color.copy(alpha = 0.14f),
                            topLeft = topLeft,
                            size = cell,
                            cornerRadius = CornerRadius(cell.minDimension * 0.3f),
                        )
                    } else if (on) {
                        drawCircle(
                            color.copy(alpha = 0.35f),
                            radius = cell.minDimension * 0.18f,
                            center =
                                topLeft + Offset(cell.width / 2, cell.height / 2),
                        )
                    }
                }
            }
        }
    }
}

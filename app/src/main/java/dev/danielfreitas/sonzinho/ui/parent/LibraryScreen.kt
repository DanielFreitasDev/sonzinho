package dev.danielfreitas.sonzinho.ui.parent

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.danielfreitas.sonzinho.ui.common.ParentScaffold
import dev.danielfreitas.sonzinho.ui.theme.SonzinhoPalette

private data class LibraryRow(
    val key: String,
    val title: String,
    val subtitle: String,
    val colorIndex: Int,
    val visible: Boolean,
    val onPlay: () -> Unit,
    val onEdit: (() -> Unit)?,
    val onRename: (String) -> Unit,
    val onDelete: () -> Unit,
    val onVisible: (Boolean) -> Unit,
)

/** Minhas músicas e sons: tocar, renomear, apagar e escolher o que aparece no modo bebê. */
@Composable
fun LibraryScreen(
    onBack: () -> Unit,
    onEditSong: (Long) -> Unit,
    onEditSound: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playing by viewModel.playingKey.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var renaming by remember { mutableStateOf<LibraryRow?>(null) }
    var deleting by remember { mutableStateOf<LibraryRow?>(null) }

    val rows: List<LibraryRow> =
        when (tab) {
            0 ->
                state.songs.map { s ->
                    LibraryRow(
                        "song-${s.id}", s.name, "${s.steps} passos · ${s.tempoBpm} batidas/min", s.colorIndex, s.showInBaby,
                        { viewModel.playSong(s) }, { onEditSong(s.id) }, { viewModel.renameSong(s.id, it) },
                        { viewModel.deleteSong(s.id) }, { viewModel.setSongVisible(s.id, it) },
                    )
                }
            1 ->
                state.sounds.map { s ->
                    LibraryRow(
                        "sound-${s.id}", s.name, s.params.shape.displayName, s.colorIndex, s.showInBaby,
                        { viewModel.playSound(s) }, { onEditSound(s.id) }, { viewModel.renameSound(s.id, it) },
                        { viewModel.deleteSound(s.id) }, { viewModel.setSoundVisible(s.id, it) },
                    )
                }
            else ->
                state.recordings.map { r ->
                    LibraryRow(
                        "rec-${r.id}", r.name, "%d:%02d · %d notas".format(r.durationMs / 60_000, (r.durationMs / 1000) % 60, r.notes.size),
                        (r.id % 8).toInt(), r.showInBaby,
                        { viewModel.playRecording(r) }, null, { viewModel.renameRecording(r.id, it) },
                        { viewModel.deleteRecording(r.id) }, { viewModel.setRecordingVisible(r.id, it) },
                    )
                }
        }

    ParentScaffold(title = "Minhas músicas e sons", onBack = {
        viewModel.stop()
        onBack()
    }) {
        PrimaryTabRow(selectedTabIndex = tab) {
            listOf("Músicas", "Sons", "Gravações").forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
            }
        }
        Text(
            when (tab) {
                0 -> "Músicas marcadas aparecem em “Música que toca sozinha”."
                1 -> "Sons marcados aparecem como bolhinhas mágicas nos Bichinhos."
                else -> "Gravações marcadas também tocam sozinhas para o bebê."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        if (rows.isEmpty()) {
            Text("Nada por aqui ainda. Que tal criar algo?", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
        }
        rows.forEach { row ->
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = SonzinhoPalette.note(row.colorIndex), modifier = Modifier.size(20.dp)) {}
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(row.title, style = MaterialTheme.typography.titleMedium)
                        Text(row.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val isPlaying = playing == row.key
                    IconButton(onClick = { if (isPlaying) viewModel.stop() else row.onPlay() }) {
                        Icon(if (isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, contentDescription = if (isPlaying) "Parar" else "Tocar")
                    }
                    row.onEdit?.let { edit ->
                        IconButton(onClick = edit) { Icon(Icons.Rounded.Tune, contentDescription = "Editar") }
                    }
                    IconButton(onClick = { renaming = row }) { Icon(Icons.Rounded.Edit, contentDescription = "Renomear") }
                    IconButton(onClick = { deleting = row }) { Icon(Icons.Rounded.Delete, contentDescription = "Apagar") }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Switch(
                            checked = row.visible,
                            onCheckedChange = row.onVisible,
                            modifier = Modifier.semantics { contentDescription = "Mostrar no modo bebê" },
                        )
                        Text("No bebê", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    renaming?.let { row ->
        var name by remember(row.key) { mutableStateOf(row.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Renomear") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) row.onRename(name)
                    renaming = null
                }) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancelar") } },
        )
    }
    deleting?.let { row ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Apagar “${row.title}”?") },
            text = { Text("Isso não pode ser desfeito.") },
            confirmButton = {
                TextButton(onClick = {
                    row.onDelete()
                    deleting = null
                }) { Text("Apagar") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}

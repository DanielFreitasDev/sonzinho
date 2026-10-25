package dev.danielfreitas.sonzinho.ui.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.danielfreitas.sonzinho.BuildConfig
import dev.danielfreitas.sonzinho.domain.model.AnimalSoundMode
import dev.danielfreitas.sonzinho.domain.model.AnimationLevel
import dev.danielfreitas.sonzinho.domain.model.AppSettings
import dev.danielfreitas.sonzinho.domain.model.Instrument
import dev.danielfreitas.sonzinho.domain.model.MusicKey
import dev.danielfreitas.sonzinho.domain.model.Scale
import dev.danielfreitas.sonzinho.domain.model.ScreenOrientation
import dev.danielfreitas.sonzinho.ui.common.ChoiceChips
import dev.danielfreitas.sonzinho.ui.common.LabeledSlider
import dev.danielfreitas.sonzinho.ui.common.ParentScaffold
import dev.danielfreitas.sonzinho.ui.common.SectionCard
import dev.danielfreitas.sonzinho.ui.common.SwitchRow
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLockGuide: () -> Unit,
    onCredits: () -> Unit,
    isDeviceOwner: Boolean,
    onRemoveDeviceOwner: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    MessageSnackbar(viewModel)
    var showPinDialog by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var diagnostics by remember { mutableStateOf<String?>(null) }

    ParentScaffold(title = "Configurações", onBack = onBack, modifier = Modifier.testTag("configuracoes")) {
        SectionCard("Som", subtitle = "O volume máximo é um teto: o Sonzinho nunca passa dele.") {
            var volume by remember(s.maxVolume) { mutableFloatStateOf(s.maxVolume) }
            LabeledSlider(
                label = "Volume máximo",
                value = volume,
                onValueChange = { volume = it },
                onValueChangeFinished = { viewModel.setMaxVolume(volume) },
                range = AppSettings.MIN_VOLUME..1f,
                valueText = "${(volume * 100).roundToInt()}%",
                startLabel = "Baixinho",
                endLabel = "Alto",
            )
            var reverb by remember(s.reverb) { mutableFloatStateOf(s.reverb) }
            LabeledSlider(
                label = "Ambiente (eco da sala)",
                value = reverb,
                onValueChange = { reverb = it },
                onValueChangeFinished = { viewModel.setReverb(reverb) },
                range = 0f..1.6f,
                startLabel = "Seco",
                endLabel = "Catedral",
            )
            OutlinedButton(onClick = viewModel::testSound) { Text("Testar som") }
        }

        SectionCard("Instrumento e escala", subtitle = "A pentatônica faz qualquer combinação de notas soar bonita.") {
            Text("Instrumento padrão", style = MaterialTheme.typography.titleMedium)
            ChoiceChips(Instrument.melodic, Instrument.fromId(s.instrumentId) ?: Instrument.MARIMBA, {
                it.displayName
            }, { viewModel.setInstrument(it.id) })
            Text("Escala", style = MaterialTheme.typography.titleMedium)
            ChoiceChips(Scale.entries, s.scale, { it.displayName }, viewModel::setScale)
            Text(s.scale.description, style = MaterialTheme.typography.bodyMedium)
            Text("Tonalidade", style = MaterialTheme.typography.titleMedium)
            ChoiceChips(MusicKey.entries, s.key, { it.displayName }, viewModel::setKey)
        }

        SectionCard("Brincadeiras") {
            Text("Teclas do teclado colorido", style = MaterialTheme.typography.titleMedium)
            ChoiceChips(AppSettings.keyboardKeyRange.toList(), s.keyboardKeys, { "$it teclas" }, viewModel::setKeyboardKeys)
            Text("Som dos bichinhos", style = MaterialTheme.typography.titleMedium)
            ChoiceChips(AnimalSoundMode.entries, s.animalSoundMode, { it.displayName }, viewModel::setAnimalSoundMode)
        }

        SectionCard("Tela") {
            Text("Orientação (travada, não gira sozinha)", style = MaterialTheme.typography.titleMedium)
            ChoiceChips(ScreenOrientation.entries, s.orientation, { it.displayName }, viewModel::setOrientation)
            Text("Intensidade das animações", style = MaterialTheme.typography.titleMedium)
            ChoiceChips(AnimationLevel.entries, s.animationLevel, { it.displayName }, viewModel::setAnimationLevel)
            SwitchRow("Vibração ao tocar", s.vibration, viewModel::setVibration, description = "Um toque leve no aparelho a cada nota")
        }

        SectionCard("Tempo de uso") {
            Text("Aviso de descanso", style = MaterialTheme.typography.titleMedium)
            ChoiceChips(AppSettings.reminderOptions, s.usageReminderMinutes, {
                if (it ==
                    0
                ) {
                    "Desligado"
                } else {
                    "$it min"
                }
            }, viewModel::setUsageReminder)
            Text(
                "Depois do tempo escolhido, a tela escurece com uma lua dormindo e o som para até um adulto voltar.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        SectionCard("Segurança") {
            SwitchRow(
                "Modo travado",
                s.lockModeEnabled,
                viewModel::setLockMode,
                description = "Tela cheia, botão voltar bloqueado e fixação de tela no modo bebê",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onLockGuide) { Text("Como fixar a tela") }
                OutlinedButton(onClick = { showPinDialog = true }, modifier = Modifier.testTag("trocar_pin")) {
                    Text(if (s.hasPin) "Trocar PIN" else "Criar PIN")
                }
                if (s.hasPin) TextButton(onClick = viewModel::removePin) { Text("Remover PIN") }
            }
            Text(
                if (s.hasPin) "Para entrar na área dos pais é preciso o PIN." else "Sem PIN: para entrar, responda uma continha.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (isDeviceOwner) {
                Text("Bloqueio total (Device Owner) ativo.", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = onRemoveDeviceOwner) { Text("Remover Device Owner") }
            }
        }

        SectionCard("Sobre") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCredits) { Text("Créditos dos sons") }
                OutlinedButton(onClick = {
                    val d = viewModel.diagnostics()
                    diagnostics =
                        "Latência estimada: ${if (d.latencyMillis >= 0) "%.1f ms".format(d.latencyMillis) else "indisponível"}\n" +
                        "Taxa: ${d.sampleRate} Hz · bloco: ${d.framesPerBurst} quadros · MMAP: ${if (d.mmap) "sim" else "não"}"
                }) { Text("Diagnóstico de áudio") }
            }
            diagnostics?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(
                "Versão ${BuildConfig.VERSION_NAME} · sem internet, sem anúncios, sem coleta de dados.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        SectionCard("Apagar dados") {
            Text("Apaga músicas, sons, gravações, PIN e configurações.", style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = { confirmClear = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("Apagar tudo") }
        }
    }

    if (showPinDialog) {
        PinDialog(onDismiss = { showPinDialog = false }, onSave = { pin -> if (viewModel.setPin(pin)) showPinDialog = false })
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Apagar tudo?") },
            text = { Text("Isso não pode ser desfeito.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clearAllData()
                }) { Text("Apagar") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun PinDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val valid = pin.length in 4..8 && pin.all { it.isDigit() } && pin == confirm
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PIN dos pais") },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("De 4 a 8 números. Ele será pedido para sair do modo bebê.")
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(8) },
                    label = { Text("Novo PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.testTag("novo_pin"),
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it.filter(Char::isDigit).take(8) },
                    label = { Text("Repita o PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.testTag("repita_pin"),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(pin) }, enabled = valid) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

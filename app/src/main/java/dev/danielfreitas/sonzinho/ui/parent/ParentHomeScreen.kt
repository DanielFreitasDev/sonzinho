package dev.danielfreitas.sonzinho.ui.parent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.danielfreitas.sonzinho.domain.model.BabyActivity
import dev.danielfreitas.sonzinho.ui.Screen

@Composable
fun ParentHomeScreen(
    onNavigate: (Screen) -> Unit,
    onBackToBaby: () -> Unit,
    onExitApp: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    BackHandler(onBack = onBackToBaby)
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val recording by viewModel.babyRecording.collectAsStateWithLifecycle()
    MessageSnackbar(viewModel)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("area_dos_pais"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Área dos pais", style = MaterialTheme.typography.headlineLarge)
                Text("Escolha a brincadeira, crie sons e músicas.", style = MaterialTheme.typography.bodyLarge)
            }
            Button(
                onClick = onBackToBaby,
                modifier = Modifier.height(64.dp).testTag("voltar_bebe"),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Icon(Icons.Rounded.ChildCare, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Voltar para o bebê", style = MaterialTheme.typography.titleMedium)
            }
        }

        Text("Brincadeira do bebê", style = MaterialTheme.typography.titleLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BabyActivity.entries.forEach { activity ->
                val selected = settings.babyActivity == activity
                ActivityCard(
                    title = activity.displayName,
                    description = activity.description,
                    icon = activityIcon(activity),
                    selected = selected,
                    onClick = { viewModel.setBabyActivity(activity) },
                )
            }
        }

        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (recording) Icons.Rounded.FiberManualRecord else Icons.Rounded.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (recording) "Gravando a brincadeira do bebê…" else "Gravar a brincadeira do bebê",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Tudo o que o bebê tocar vira uma gravação para ouvir depois.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (recording) {
                    Button(onClick = viewModel::stopBabyRecording) {
                        Icon(Icons.Rounded.Stop, contentDescription = null)
                        Text("Parar e salvar")
                    }
                } else {
                    OutlinedButton(onClick = {
                        viewModel.startBabyRecording()
                        onBackToBaby()
                    }) { Text("Gravar e voltar") }
                }
            }
        }

        Text("Criar", style = MaterialTheme.typography.titleLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MenuCard("Fazer música", "Sequenciador em grade", Icons.Rounded.GridView) { onNavigate(Screen.Sequencer()) }
            MenuCard("Criador de sons", "Grave, agudo, macio, brilhante", Icons.Rounded.GraphicEq) { onNavigate(Screen.Creator()) }
            MenuCard("Gravar", "Toque livre e grave", Icons.Rounded.Mic) { onNavigate(Screen.Recorder) }
            MenuCard("Minhas músicas e sons", "Ouvir, renomear, escolher", Icons.Rounded.LibraryMusic) { onNavigate(Screen.Library) }
        }

        Text("Ajustes", style = MaterialTheme.typography.titleLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MenuCard(
                "Configurações",
                "Volume, escala, tela, PIN…",
                Icons.Rounded.Settings,
                tag = "abrir_config",
            ) { onNavigate(Screen.Settings) }
            MenuCard("Travar a tela", "Como fixar o app na Samsung", Icons.Rounded.Lock) { onNavigate(Screen.LockGuide) }
            MenuCard("Créditos dos sons", "Origem e licenças", Icons.Rounded.MusicNote) { onNavigate(Screen.Credits) }
            MenuCard(
                "Sair do Sonzinho",
                "Desfixa a tela e fecha o app",
                Icons.AutoMirrored.Rounded.Logout,
                tag = "sair_app",
            ) { onExitApp() }
        }
    }
}

private fun activityIcon(activity: BabyActivity): ImageVector =
    when (activity) {
        BabyActivity.MAGIC_SCREEN -> Icons.Rounded.TouchApp
        BabyActivity.ANIMALS -> Icons.Rounded.Pets
        BabyActivity.KEYBOARD -> Icons.Rounded.Piano
        BabyActivity.AUTO_MUSIC -> Icons.Rounded.PlayCircle
    }

@Composable
private fun ActivityCard(
    title: String,
    description: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.widthIn(min = 200.dp, max = 260.dp).testTag("brincadeira_$title"),
        colors =
            CardDefaults.cardColors(
                containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(if (selected) Icons.Rounded.AutoAwesome else icon, contentDescription = null, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium)
            if (selected) Text("Escolhida", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun MenuCard(
    title: String,
    description: String,
    icon: ImageVector,
    tag: String = title,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.widthIn(min = 200.dp, max = 260.dp).testTag(tag),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Mostra mensagens curtas de confirmação vindas do ViewModel. */
@Composable
fun MessageSnackbar(viewModel: SettingsViewModel) {
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(message) {
        message?.let {
            android.widget.Toast
                .makeText(context, it, android.widget.Toast.LENGTH_SHORT)
                .show()
            viewModel.consumeMessage()
        }
    }
}

@Composable
fun WelcomeScreen(
    onStart: () -> Unit,
    onLockGuide: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 720.dp).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Bem-vindos ao Sonzinho!", style = MaterialTheme.typography.displaySmall)
            Text(
                "Um instrumento mágico para brincar com o seu bebê. Qualquer toque faz um som bonito, " +
                    "sem erros e sem menus para o bebê.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Hint(
                "Para entrar na área dos pais",
                "Segure o cantinho com a estrela (canto superior esquerdo) por 3 segundos com um dedo só e responda a continha.",
            )
            Hint("Volume", "O volume máximo começa na metade. Você pode ajustar em Configurações.")
            Hint("Tela travada", "Ative a fixação de apps da Samsung para o bebê não sair da brincadeira. Explicamos o passo a passo.")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onStart, modifier = Modifier.height(56.dp).testTag("comecar")) { Text("Começar a brincar") }
                OutlinedButton(onClick = onLockGuide, modifier = Modifier.height(56.dp)) { Text("Como travar a tela") }
            }
        }
    }
}

@Composable
private fun Hint(
    title: String,
    body: String,
) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

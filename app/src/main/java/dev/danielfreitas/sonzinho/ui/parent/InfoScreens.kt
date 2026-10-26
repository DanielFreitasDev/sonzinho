package dev.danielfreitas.sonzinho.ui.parent

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.danielfreitas.sonzinho.data.lock.LockLevel
import dev.danielfreitas.sonzinho.ui.common.ParentScaffold
import dev.danielfreitas.sonzinho.ui.common.SectionCard

/** Passo a passo da fixação de tela na One UI 9 (Galaxy S24 Ultra, Android 17). */
@Composable
fun LockGuideScreen(
    onBack: () -> Unit,
    lockLevel: LockLevel,
    isDeviceOwner: Boolean,
    onTestPin: () -> Unit,
) {
    val context = LocalContext.current
    ParentScaffold(title = "Travar a tela", onBack = onBack) {
        SectionCard(
            "Estado agora",
            subtitle =
                when (lockLevel) {
                    LockLevel.LOCKED -> "Bloqueio total ativo (Device Owner)."
                    LockLevel.PINNED -> "Tela fixada: o bebê não sai do Sonzinho."
                    LockLevel.NONE -> "Tela ainda não está fixada."
                },
        ) {}
        SectionCard("1. Ative “Fixar apps” na Samsung (uma vez só)") {
            Steps(
                "Abra Configurações do aparelho.",
                "Toque em Segurança e privacidade → Mais configurações de segurança.",
                "Ative Fixar apps (em algumas versões aparece como “Fixar janelas”).",
                "Dentro da opção, ligue “Pedir PIN antes de desafixar” (ou “Solicitar padrão/PIN”).",
            )
            Text(
                "Os nomes podem variar um pouco entre versões da One UI. Use a busca das Configurações e procure por “Fixar”.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }) { Text("Abrir configurações de segurança") }
        }
        SectionCard("2. Volte ao Sonzinho") {
            Steps(
                "Com o modo travado ligado, ao entrar no modo bebê o Android pergunta se deseja fixar o Sonzinho. Confirme.",
                "Para sair: use “Sair do Sonzinho” na área dos pais, ou o gesto do sistema " +
                    "(na navegação por botões, segure Voltar + Recentes; " +
                    "com gestos, deslize de baixo para cima e segure) e digite o PIN do aparelho.",
            )
            OutlinedButton(onClick = onTestPin) { Text("Fixar agora para testar") }
        }
        SectionCard(
            "Opcional: bloqueio total (Device Owner)",
            subtitle = "Para quem quer o máximo: Início, Recentes e notificações ficam bloqueados, sem pedir confirmação.",
        ) {
            Text(
                if (isDeviceOwner) {
                    "O Sonzinho já é Device Owner neste aparelho."
                } else {
                    "Requer um computador com adb. Veja o README do projeto."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            SelectionContainer {
                Text(
                    "adb shell dpm set-device-owner dev.danielfreitas.sonzinho/.data.lock.SonzinhoDeviceAdminReceiver",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                "Atenção: o Android só aceita esse comando sem contas cadastradas no aparelho (Google, Samsung…), " +
                    "então em geral é para um aparelho dedicado ao bebê. " +
                    "Para desfazer, use “Remover Device Owner” nas Configurações do Sonzinho.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun Steps(vararg steps: String) {
    steps.forEachIndexed { i, step -> Text("${i + 1}. $step", style = MaterialTheme.typography.bodyLarge) }
}

private data class Credit(
    val title: String,
    val author: String,
    val license: String,
    val details: String,
)

private val credits =
    listOf(
        Credit(
            "Piano, marimba, sininhos (glockenspiel), xilofone, vibrafone, kalimba, harpa e sinos",
            "Versilian Studios LLC — Versilian Community Sample Library (VCSL)",
            "CC0 1.0 (domínio público)",
            "github.com/sgossner/VCSL · amostras em camadas suaves, reamostradas para 48 kHz e convertidas para Opus.",
        ),
        Credit(
            "Kalimba doce, caixinha de música e flauta doce",
            "Daniel Freitas (Tenzen) — app Melimba, do mesmo autor do Sonzinho",
            "Uso autorizado pelo autor",
            "Amostras do Melimba reamostradas para 48 kHz, medidas, aparadas e convertidas para Opus.",
        ),
        Credit(
            "Fonte Nunito",
            "The Nunito Project Authors (Vernon Adams, Cyreal, Jacques Le Bailly)",
            "SIL Open Font License 1.1",
            "github.com/googlefonts/nunito",
        ),
        Credit(
            "Vozes dos bichinhos, nuvem, grave, celesta, caixinha mágica e sons criados",
            "Sintetizados em tempo real pelo próprio Sonzinho",
            "Parte do app",
            "Nenhuma gravação externa: osciladores, filtros e envelopes do motor de áudio.",
        ),
        Credit(
            "Motor de áudio",
            "Oboe (Google)",
            "Apache 2.0",
            "github.com/google/oboe",
        ),
    )

@Composable
fun CreditsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var license by remember { mutableStateOf<String?>(null) }
    ParentScaffold(title = "Créditos dos sons", onBack = onBack) {
        credits.forEach { c ->
            SectionCard(c.title) {
                Text(c.author, style = MaterialTheme.typography.titleMedium)
                Text("Licença: ${c.license}", style = MaterialTheme.typography.bodyLarge)
                Text(c.details, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { license = readAsset(context, "licenses/vcsl-CC0.txt") }) { Text("Ver licença CC0") }
            TextButton(onClick = { license = readAsset(context, "licenses/nunito-OFL.txt") }) { Text("Ver licença OFL") }
        }
        license?.let {
            SectionCard("Texto da licença") { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
        }
    }
}

private fun readAsset(
    context: android.content.Context,
    path: String,
): String = runCatching { context.assets.open(path).use { it.readBytes().decodeToString() } }.getOrDefault("Arquivo não encontrado")

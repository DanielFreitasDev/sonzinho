package dev.danielfreitas.sonzinho.ui.baby

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.danielfreitas.sonzinho.domain.model.AppSettings
import dev.danielfreitas.sonzinho.domain.model.BabyActivity
import dev.danielfreitas.sonzinho.ui.GateState
import dev.danielfreitas.sonzinho.ui.parent.ParentChallengeOverlay
import dev.danielfreitas.sonzinho.ui.parent.RestOverlay
import dev.danielfreitas.sonzinho.ui.parent.UnlockCornerDetector
import dev.danielfreitas.sonzinho.ui.theme.SonzinhoPalette

/** Modo bebê: só cores, formas e sons. Nenhum menu; os pais entram pelo cantinho da estrela. */
@Composable
fun BabyScreen(
    gate: GateState,
    resting: Boolean,
    onCornerHeld: () -> Unit,
    onSubmitAnswer: (String) -> Unit,
    onDismissGate: () -> Unit,
    viewModel: BabyViewModel = hiltViewModel(),
) {
    // Voltar (inclusive o gesto preditivo) não tira o bebê da brincadeira.
    BackHandler(enabled = true) {}
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val sounds by viewModel.babySounds.collectAsStateWithLifecycle()
    val autoItem by viewModel.autoItem.collectAsStateWithLifecycle()
    val s = settings ?: AppSettings()
    val effects = rememberEffectsState(s.animationLevel.particleScale, s.animationLevel.motionScale)
    val haptics = rememberBabyHaptics(s.vibration)
    val play: (Int, Float, Float, Float) -> Unit = { id, midi, vel, pan -> viewModel.play(id, midi, vel, pan) }

    Box(Modifier.fillMaxSize().background(SonzinhoPalette.Night).testTag("modo_bebe")) {
        UnlockCornerDetector(onHeld = onCornerHeld) {
            when (s.babyActivity) {
                BabyActivity.MAGIC_SCREEN -> {
                    MagicScreen(s, effects, haptics, play)
                }

                BabyActivity.ANIMALS -> {
                    AnimalsScreen(s, sounds, effects, haptics, play)
                }

                BabyActivity.KEYBOARD -> {
                    KeyboardScreen(s, effects, haptics, play)
                }

                BabyActivity.AUTO_MUSIC -> {
                    AutoMusicScreen(
                        settings = s,
                        item = autoItem,
                        effects = effects,
                        haptics = haptics,
                        positionProvider = viewModel::sequencePosition,
                        onStart = viewModel::startAutoMusic,
                        onStop = viewModel::stopAutoMusic,
                        onPlay = play,
                    )
                }
            }
            if (resting) RestOverlay()
        }
        ParentChallengeOverlay(gate = gate, onSubmit = onSubmitAnswer, onDismiss = onDismissGate)
    }
}

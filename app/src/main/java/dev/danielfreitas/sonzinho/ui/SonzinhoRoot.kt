package dev.danielfreitas.sonzinho.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.danielfreitas.sonzinho.data.lock.LockLevel
import dev.danielfreitas.sonzinho.ui.baby.BabyScreen
import dev.danielfreitas.sonzinho.ui.parent.CreatorScreen
import dev.danielfreitas.sonzinho.ui.parent.CreditsScreen
import dev.danielfreitas.sonzinho.ui.parent.LibraryScreen
import dev.danielfreitas.sonzinho.ui.parent.LockGuideScreen
import dev.danielfreitas.sonzinho.ui.parent.ParentHomeScreen
import dev.danielfreitas.sonzinho.ui.parent.RecorderScreen
import dev.danielfreitas.sonzinho.ui.parent.SequencerScreen
import dev.danielfreitas.sonzinho.ui.parent.SettingsScreen
import dev.danielfreitas.sonzinho.ui.parent.WelcomeScreen
import dev.danielfreitas.sonzinho.ui.theme.SonzinhoPalette
import dev.danielfreitas.sonzinho.ui.theme.SonzinhoTheme

/** Ações que dependem da Activity (fixação de tela, sair do app). */
interface ActivityActions {
    val isDeviceOwner: Boolean

    fun lockLevel(): LockLevel

    fun pinNow()

    fun exitApp()

    fun removeDeviceOwner()
}

@Composable
fun SonzinhoRoot(
    viewModel: MainViewModel,
    actions: ActivityActions,
) {
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val gate by viewModel.gate.collectAsStateWithLifecycle()
    val resting by viewModel.resting.collectAsStateWithLifecycle()

    SonzinhoTheme {
        when (val s = screen) {
            Screen.Loading -> Box(Modifier.fillMaxSize().background(SonzinhoPalette.Night))
            Screen.Welcome ->
                WelcomeScreen(onStart = viewModel::finishOnboarding, onLockGuide = { viewModel.navigate(Screen.LockGuide) })
            Screen.Baby ->
                BabyScreen(
                    gate = gate,
                    resting = resting,
                    onCornerHeld = viewModel::onCornerHeld,
                    onSubmitAnswer = viewModel::submitAnswer,
                    onDismissGate = viewModel::dismissGate,
                )
            Screen.ParentHome ->
                ParentHomeScreen(
                    onNavigate = viewModel::navigate,
                    onBackToBaby = { viewModel.navigate(Screen.Baby) },
                    onExitApp = actions::exitApp,
                )
            Screen.Settings ->
                SettingsScreen(
                    onBack = viewModel::back,
                    onLockGuide = { viewModel.navigate(Screen.LockGuide) },
                    onCredits = { viewModel.navigate(Screen.Credits) },
                    isDeviceOwner = actions.isDeviceOwner,
                    onRemoveDeviceOwner = actions::removeDeviceOwner,
                )
            Screen.LockGuide ->
                LockGuideScreen(
                    onBack = viewModel::back,
                    lockLevel = actions.lockLevel(),
                    isDeviceOwner = actions.isDeviceOwner,
                    onTestPin = actions::pinNow,
                )
            Screen.Credits -> CreditsScreen(onBack = viewModel::back)
            is Screen.Sequencer -> SequencerScreen(songId = s.songId, onBack = viewModel::back)
            is Screen.Creator -> CreatorScreen(soundId = s.soundId, onBack = viewModel::back)
            Screen.Recorder -> RecorderScreen(onBack = viewModel::back)
            Screen.Library ->
                LibraryScreen(
                    onBack = viewModel::back,
                    onEditSong = { viewModel.navigate(Screen.Sequencer(it)) },
                    onEditSound = { viewModel.navigate(Screen.Creator(it)) },
                )
        }
    }
}

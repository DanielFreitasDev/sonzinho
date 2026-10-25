package dev.danielfreitas.sonzinho.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.danielfreitas.sonzinho.domain.audio.AudioEngine
import dev.danielfreitas.sonzinho.domain.audio.EngineSettings
import dev.danielfreitas.sonzinho.domain.model.AppSettings
import dev.danielfreitas.sonzinho.domain.model.CustomSound
import dev.danielfreitas.sonzinho.domain.model.Instrument
import dev.danielfreitas.sonzinho.domain.music.SoundDesigner
import dev.danielfreitas.sonzinho.domain.repository.LibraryRepository
import dev.danielfreitas.sonzinho.domain.repository.SettingsRepository
import dev.danielfreitas.sonzinho.domain.usecase.MathChallenge
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface Screen {
    data object Loading : Screen

    data object Welcome : Screen

    data object Baby : Screen

    data object ParentHome : Screen

    data object Settings : Screen

    data object LockGuide : Screen

    data object Credits : Screen

    data class Sequencer(
        val songId: Long? = null,
    ) : Screen

    data class Creator(
        val soundId: Long? = null,
    ) : Screen

    data object Recorder : Screen

    data object Library : Screen
}

sealed interface GateState {
    data object Hidden : GateState

    data class Math(
        val challenge: MathChallenge,
        val wrong: Boolean = false,
    ) : GateState

    data class Pin(
        val wrong: Boolean = false,
    ) : GateState

    /** Erros demais seguidos: espera antes de tentar de novo (dificulta toques aleatórios). */
    data class CoolingDown(
        val secondsLeft: Int,
    ) : GateState
}

/** Sons criados pelos pais ocupam os slots 64..125 do motor, na ordem dos ids. */
object CustomSoundSlots {
    private const val LAST_SLOT = 125

    fun assign(sounds: List<CustomSound>): Map<Long, Int> =
        sounds
            .sortedBy { it.id }
            .take(LAST_SLOT - Instrument.FIRST_CUSTOM_SLOT + 1)
            .mapIndexed { index, sound -> sound.id to Instrument.FIRST_CUSTOM_SLOT + index }
            .toMap()
}

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val library: LibraryRepository,
        private val engine: AudioEngine,
    ) : ViewModel() {
        val settings: StateFlow<AppSettings?> =
            settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        private val _screen = MutableStateFlow<Screen>(Screen.Loading)
        val screen: StateFlow<Screen> = _screen.asStateFlow()

        private val _gate = MutableStateFlow<GateState>(GateState.Hidden)
        val gate: StateFlow<GateState> = _gate.asStateFlow()

        /** Hora de descansar (aviso de tempo de uso). */
        private val _resting = MutableStateFlow(false)
        val resting: StateFlow<Boolean> = _resting.asStateFlow()

        val soundSlots: StateFlow<Map<Long, Int>> =
            library.sounds
                .map { CustomSoundSlots.assign(it) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

        private var wrongAttempts = 0
        private var reminderJob: Job? = null
        private var gateTimeoutJob: Job? = null

        init {
            viewModelScope.launch {
                settingsRepository.settings
                    .map { EngineSettings(maxVolume = it.maxVolume, reverb = it.reverb) }
                    .distinctUntilChanged()
                    .collect { engine.configure(it) }
            }
            viewModelScope.launch {
                settingsRepository.settings.map { it.instrumentId }.distinctUntilChanged().collect { id ->
                    engine.preferInstrument(id)
                }
            }
            viewModelScope.launch {
                library.sounds.collect { sounds ->
                    val slots = CustomSoundSlots.assign(sounds)
                    sounds.forEach { sound ->
                        slots[sound.id]?.let { slot -> engine.definePatch(slot, SoundDesigner.toPatch(sound.params).toFloatArray()) }
                    }
                }
            }
            viewModelScope.launch {
                val first = settingsRepository.settings.first()
                _screen.value = if (first.onboardingDone) Screen.Baby else Screen.Welcome
                if (first.onboardingDone) startReminder(first.usageReminderMinutes)
            }
        }

        fun navigate(target: Screen) {
            if (target == Screen.Baby && _screen.value != Screen.Baby) {
                startReminder(settings.value?.usageReminderMinutes ?: 0)
            }
            if (target != Screen.Baby) {
                engine.stopSequence()
                engine.allNotesOff()
            }
            _screen.value = target
        }

        /** Volta um nível: subtelas → área dos pais → bebê. */
        fun back() {
            when (_screen.value) {
                Screen.Baby, Screen.Loading -> Unit

                // o bebê nunca sai pelo botão voltar
                Screen.ParentHome, Screen.Welcome -> navigate(Screen.Baby)

                else -> navigate(Screen.ParentHome)
            }
        }

        fun finishOnboarding() {
            viewModelScope.launch { settingsRepository.setOnboardingDone(true) }
            navigate(Screen.Baby)
        }

        // ------------------------------------------------------------ desbloqueio parental

        fun onCornerHeld() {
            if (_gate.value != GateState.Hidden) return
            _gate.value = if (settings.value?.hasPin == true) GateState.Pin() else GateState.Math(MathChallenge.generate())
            armGateTimeout()
        }

        fun submitAnswer(answer: String) {
            when (val g = _gate.value) {
                is GateState.Math -> {
                    if (g.challenge.isCorrect(answer)) unlocked() else wrong()
                }

                is GateState.Pin -> {
                    viewModelScope.launch {
                        if (settingsRepository.verifyPin(answer)) unlocked() else wrong()
                    }
                }

                else -> {
                    Unit
                }
            }
        }

        fun dismissGate() {
            gateTimeoutJob?.cancel()
            if (_gate.value !is GateState.CoolingDown) _gate.value = GateState.Hidden
        }

        private fun unlocked() {
            gateTimeoutJob?.cancel()
            wrongAttempts = 0
            _gate.value = GateState.Hidden
            _resting.value = false
            navigate(Screen.ParentHome)
        }

        private fun wrong() {
            wrongAttempts++
            armGateTimeout()
            if (wrongAttempts >= MAX_WRONG) {
                wrongAttempts = 0
                gateTimeoutJob?.cancel()
                viewModelScope.launch {
                    for (s in COOLDOWN_SECONDS downTo 1) {
                        _gate.value = GateState.CoolingDown(s)
                        delay(1000)
                    }
                    _gate.value = GateState.Hidden
                }
                return
            }
            _gate.update {
                when (it) {
                    is GateState.Math -> GateState.Math(MathChallenge.generate(), wrong = true)
                    is GateState.Pin -> GateState.Pin(wrong = true)
                    else -> it
                }
            }
        }

        /** Desafio esquecido aberto some sozinho e o bebê volta a brincar. */
        private fun armGateTimeout() {
            gateTimeoutJob?.cancel()
            gateTimeoutJob =
                viewModelScope.launch {
                    delay(GATE_TIMEOUT_MS)
                    if (_gate.value is GateState.Math || _gate.value is GateState.Pin) _gate.value = GateState.Hidden
                }
        }

        // ------------------------------------------------------------ aviso de descanso

        private fun startReminder(minutes: Int) {
            reminderJob?.cancel()
            _resting.value = false
            if (minutes <= 0) return
            reminderJob =
                viewModelScope.launch {
                    delay(minutes * 60_000L)
                    if (_screen.value == Screen.Baby) {
                        engine.stopSequence()
                        _resting.value = true
                    }
                }
        }

        private companion object {
            const val MAX_WRONG = 3
            const val COOLDOWN_SECONDS = 30
            const val GATE_TIMEOUT_MS = 25_000L
        }
    }

package dev.danielfreitas.sonzinho.data.audio

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.danielfreitas.sonzinho.di.ApplicationScope
import dev.danielfreitas.sonzinho.domain.audio.AudioDiagnostics
import dev.danielfreitas.sonzinho.domain.audio.AudioEngine
import dev.danielfreitas.sonzinho.domain.audio.EngineSettings
import dev.danielfreitas.sonzinho.domain.audio.EngineState
import dev.danielfreitas.sonzinho.domain.audio.SequenceData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** Implementação do [AudioEngine] sobre o motor C++ (Oboe/AAudio). */
@Singleton
@Suppress("TooManyFunctions")
class NativeAudioEngine
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) : AudioEngine {
        private val bridge = NativeAudioBridge()
        private val handle: Long = bridge.nativeCreate()
        private val loadStarted = AtomicBoolean(false)
        private val _state = MutableStateFlow(EngineState(totalInstruments = SampledSpec.entries.size))
        override val state: StateFlow<EngineState> = _state.asStateFlow()

        @Volatile
        private var priorityInstrument: Int = 1

        override fun preferInstrument(instrumentId: Int) {
            priorityInstrument = instrumentId
        }

        override fun start() {
            val ok = bridge.nativeStart(handle)
            _state.update { it.copy(running = ok) }
            if (!ok) Log.w(TAG, "Não foi possível abrir a saída de áudio")
            loadSamplesOnce()
        }

        override fun stop() {
            bridge.nativeStop(handle)
            _state.update { it.copy(running = false) }
        }

        private fun loadSamplesOnce() {
            if (!loadStarted.compareAndSet(false, true)) return
            scope.launch(Dispatchers.Default.limitedParallelism(2)) {
                val loader = SampleLoader(context.assets)
                val manifest = runCatching { loader.readManifest() }.getOrElse {
                    Log.e(TAG, "Manifesto de sons ausente", it)
                    return@launch
                }
                val specs = SampledSpec.entries.sortedBy { if (it.slot == priorityInstrument) -1 else it.slot }
                for (spec in specs) {
                    val inst = manifest.instruments.firstOrNull { it.id == spec.folder } ?: continue
                    runCatching {
                        val zones = inst.zones.map { zone -> async { loader.decode(zone) } }.awaitAll()
                        bridge.nativeLoadInstrument(
                            handle,
                            spec.slot,
                            spec.gain,
                            spec.releaseSeconds,
                            FloatArray(zones.size) { zones[it].root },
                            FloatArray(zones.size) { zones[it].sampleRate.toFloat() },
                            IntArray(zones.size) { zones[it].channels },
                            Array(zones.size) { zones[it].pcm },
                        )
                    }.onFailure { Log.e(TAG, "Falha ao carregar ${spec.folder}", it) }
                    _state.update { it.copy(loadedInstruments = it.loadedInstruments + 1) }
                }
            }
        }

        override fun noteOn(
            instrumentId: Int,
            midi: Float,
            velocity: Float,
            pan: Float,
            durationSeconds: Float,
        ): Int = bridge.nativeNoteOn(handle, instrumentId, midi, velocity, pan, durationSeconds)

        override fun noteOff(voice: Int) = bridge.nativeNoteOff(handle, voice)

        override fun setVoiceTarget(
            voice: Int,
            midi: Float,
            brightness: Float,
        ) = bridge.nativeSetVoiceTarget(handle, voice, midi, brightness)

        override fun allNotesOff() = bridge.nativeAllNotesOff(handle)

        override fun definePatch(
            slot: Int,
            params: FloatArray,
        ) = bridge.nativeDefinePatch(handle, slot, params)

        override fun configure(settings: EngineSettings) =
            bridge.nativeSetConfig(handle, settings.polyphony, settings.maxVolume, settings.reverb, settings.brightness)

        override fun setSequence(sequence: SequenceData) {
            val events = sequence.events
            bridge.nativeSetSequence(
                handle,
                sequence.id,
                sequence.lengthSeconds,
                sequence.loop,
                DoubleArray(events.size) { events[it].timeSeconds },
                IntArray(events.size) { events[it].instrumentId },
                FloatArray(events.size) { events[it].midi },
                FloatArray(events.size) { events[it].velocity },
                FloatArray(events.size) { events[it].durationSeconds },
            )
        }

        override fun playSequence(fromStart: Boolean) = bridge.nativePlaySequence(handle, fromStart)

        override fun stopSequence() = bridge.nativeStopSequence(handle)

        override fun sequencePositionSeconds(): Double = bridge.nativeSequencePosition(handle)

        override fun isSequencePlaying(): Boolean = bridge.nativeSequencePlaying(handle)

        override fun setMuted(muted: Boolean) = bridge.nativeSetMuted(handle, muted)

        override fun diagnostics(): AudioDiagnostics =
            AudioDiagnostics(
                latencyMillis = bridge.nativeLatencyMillis(handle),
                sampleRate = bridge.nativeSampleRate(handle),
                framesPerBurst = bridge.nativeFramesPerBurst(handle),
                mmap = bridge.nativeIsMmap(handle),
                activeVoices = bridge.nativeActiveVoices(handle),
            )

        private companion object {
            const val TAG = "SonzinhoAudio"
        }
    }

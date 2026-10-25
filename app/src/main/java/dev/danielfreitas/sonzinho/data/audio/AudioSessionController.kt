package dev.danielfreitas.sonzinho.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.danielfreitas.sonzinho.domain.audio.AudioEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Liga o motor quando o app está visível e desliga em segundo plano (o Android 17 bloqueia
 * áudio em segundo plano sem serviço em primeiro plano, e um app de bebê não precisa disso).
 * Também respeita o foco de áudio: ligação telefônica ou outro app tocando silenciam o Sonzinho.
 */
@Singleton
class AudioSessionController
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val engine: AudioEngine,
    ) : DefaultLifecycleObserver {
        private val audioManager = context.getSystemService(AudioManager::class.java)
        private var focusRequest: AudioFocusRequest? = null

        private val focusListener =
            AudioManager.OnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_GAIN -> engine.setMuted(false)
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                    -> {
                        engine.stopSequence()
                        engine.setMuted(true)
                    }
                }
            }

        override fun onStart(owner: LifecycleOwner) {
            engine.start()
            requestFocus()
        }

        override fun onStop(owner: LifecycleOwner) {
            engine.stopSequence()
            engine.allNotesOff()
            engine.stop()
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        }

        private fun requestFocus() {
            val attributes =
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            val request =
                AudioFocusRequest
                    .Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener(focusListener)
                    .setWillPauseWhenDucked(true)
                    .build()
            focusRequest = request
            val result = audioManager.requestAudioFocus(request)
            engine.setMuted(result == AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        }
    }

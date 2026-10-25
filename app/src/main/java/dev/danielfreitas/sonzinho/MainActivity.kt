package dev.danielfreitas.sonzinho

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.danielfreitas.sonzinho.data.audio.AudioSessionController
import dev.danielfreitas.sonzinho.data.lock.LockLevel
import dev.danielfreitas.sonzinho.data.lock.LockTaskController
import dev.danielfreitas.sonzinho.domain.model.AppSettings
import dev.danielfreitas.sonzinho.domain.model.ScreenOrientation
import dev.danielfreitas.sonzinho.ui.ActivityActions
import dev.danielfreitas.sonzinho.ui.MainViewModel
import dev.danielfreitas.sonzinho.ui.Screen
import dev.danielfreitas.sonzinho.ui.SonzinhoRoot
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Activity única. Responsável pelo "modo travado":
 * - tela cheia imersiva (barras escondidas e reescondidas sempre que o sistema as mostrar);
 * - fixação de tela / lock task (`startLockTask`) no modo bebê;
 * - orientação fixa (sem sensor), escolhida pelos pais;
 * - voltar e voltar preditivo interceptados (via `BackHandler` no Compose);
 * - teclas de volume ignoradas no modo bebê travado (o teto de volume vem das configurações).
 */
@AndroidEntryPoint
class MainActivity :
    ComponentActivity(),
    ActivityActions {
    @Inject lateinit var audioSession: AudioSessionController

    private val viewModel: MainViewModel by viewModels()
    private val lockController by lazy { LockTaskController(this) }
    private var babyLocked = false

    /** Tenta fixar uma vez por entrada no modo bebê: se os pais recusarem o aviso do sistema, não insiste. */
    private var lockAttempted = false
    private val rehideBars = Runnable { hideSystemBars() }

    override val isDeviceOwner: Boolean get() = lockController.isDeviceOwner

    override fun onCreate(savedInstanceState: Bundle?) {
        // Barras transparentes com ícones claros sobre o fundo noturno (edge-to-edge obrigatório no Android 15+).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(audioSession)
        lockController.prepareDeviceOwnerPolicies()
        setupImmersive()

        setContent { SonzinhoRoot(viewModel, this) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(viewModel.settings.filterNotNull(), viewModel.screen) { s, screen -> s to screen }
                    .collect { (settings, screen) -> applyMode(settings, screen) }
            }
        }
    }

    private fun applyMode(
        settings: AppSettings,
        screen: Screen,
    ) {
        requestedOrientation =
            when (settings.orientation) {
                ScreenOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                ScreenOrientation.REVERSE_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                ScreenOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        val baby = screen == Screen.Baby
        if (baby) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        babyLocked = baby && settings.lockModeEnabled
        // A fixação fica ativa também na área dos pais; só "Sair" ou desligar o modo travado a encerra.
        if (babyLocked && !lockAttempted) {
            lockAttempted = true
            lockController.lock(this)
        } else if (!settings.lockModeEnabled) {
            lockController.unlock(this)
        }
        if (!baby) lockAttempted = false
        hideSystemBars()
    }

    private fun setupImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view, insets ->
            // O sistema mostrou as barras (deslize da borda): esconde de novo logo em seguida.
            if (insets.isVisible(WindowInsetsCompat.Type.systemBars())) {
                view.removeCallbacks(rehideBars)
                view.postDelayed(rehideBars, if (babyLocked) REHIDE_BABY_MS else REHIDE_PARENT_MS)
            }
            ViewCompat.onApplyWindowInsets(view, insets)
        }
        hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (babyLocked) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
                KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SEARCH,
                -> return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // ---------------------------------------------------------------- ActivityActions

    override fun lockLevel(): LockLevel = lockController.currentLevel()

    override fun pinNow() = lockController.lock(this)

    override fun exitApp() {
        babyLocked = false
        lockController.unlock(this)
        finishAndRemoveTask()
    }

    override fun removeDeviceOwner() {
        lockController.removeDeviceOwner(this)
        recreate()
    }

    private companion object {
        const val REHIDE_BABY_MS = 1200L
        const val REHIDE_PARENT_MS = 3500L
    }
}

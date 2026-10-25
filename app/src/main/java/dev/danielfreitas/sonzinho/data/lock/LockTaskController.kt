package dev.danielfreitas.sonzinho.data.lock

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log

/** Receptor de administração usado apenas quando os pais tornam o app Device Owner via adb. */
class SonzinhoDeviceAdminReceiver : DeviceAdminReceiver()

enum class LockLevel {
    /** Nada travado. */
    NONE,

    /** Fixação de tela do sistema ("Fixar apps"): sair exige o gesto do sistema + PIN do aparelho. */
    PINNED,

    /** Lock task completo (Device Owner): Início, Recentes e notificações ficam bloqueados. */
    LOCKED,
}

/**
 * Estratégia de bloqueio:
 * - Sem Device Owner, `startLockTask()` ativa a fixação de tela do sistema (o Android pede
 *   confirmação e só funciona com "Fixar apps" ligado nas configurações da Samsung).
 * - Com Device Owner, o app se coloca na lista de lock task e o bloqueio é total, sem
 *   confirmação e sem botões do sistema.
 */
class LockTaskController(
    private val context: Context,
) {
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val activityManager = context.getSystemService(ActivityManager::class.java)
    private val admin = ComponentName(context, SonzinhoDeviceAdminReceiver::class.java)

    val isDeviceOwner: Boolean get() = dpm?.isDeviceOwnerApp(context.packageName) == true

    fun currentLevel(): LockLevel =
        when (activityManager?.lockTaskModeState) {
            ActivityManager.LOCK_TASK_MODE_LOCKED -> LockLevel.LOCKED
            ActivityManager.LOCK_TASK_MODE_PINNED -> LockLevel.PINNED
            else -> LockLevel.NONE
        }

    /** Garante a lista de lock task e as restrições quando somos Device Owner. */
    fun prepareDeviceOwnerPolicies() {
        if (!isDeviceOwner) return
        runCatching {
            dpm.setLockTaskPackages(admin, arrayOf(context.packageName))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Sem Início, Recentes, notificações nem menu de desligar: o bebê não sai.
                dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
            }
        }.onFailure { Log.w(TAG, "Não foi possível aplicar políticas de lock task", it) }
    }

    fun lock(activity: Activity) {
        if (currentLevel() != LockLevel.NONE) return
        prepareDeviceOwnerPolicies()
        runCatching { activity.startLockTask() }
            .onFailure { Log.w(TAG, "startLockTask falhou (fixação de tela desligada?)", it) }
    }

    fun unlock(activity: Activity) {
        if (currentLevel() == LockLevel.NONE) return
        runCatching { activity.stopLockTask() }.onFailure { Log.w(TAG, "stopLockTask falhou", it) }
    }

    /**
     * Remove o Device Owner pelo próprio app (o caminho documentado no README). Depois disso o
     * aparelho volta ao normal e o Sonzinho passa a usar a fixação de tela comum.
     */
    @Suppress("DEPRECATION")
    fun removeDeviceOwner(activity: Activity): Boolean {
        if (!isDeviceOwner) return false
        unlock(activity)
        return runCatching {
            dpm.setLockTaskPackages(admin, emptyArray())
            dpm.clearDeviceOwnerApp(context.packageName)
            true
        }.getOrElse {
            Log.w(TAG, "Falha ao remover Device Owner", it)
            false
        }
    }

    private companion object {
        const val TAG = "SonzinhoLock"
    }
}

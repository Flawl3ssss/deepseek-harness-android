package ai.deepseek.dsh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Приёмник событий задач/виджета (ARCH-MOD-01/05).
 * MY_PACKAGE_REPLACED: мягкий рестарт процессов после обновления APK, ФС не трогаем (DEC-004).
 */
class TaskReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                InstallLog.w(c, "update", "package replaced, soft-restarting processes")
                if (!Paths.isInstalled(c)) return
                val prefs = Prefs(c)
                if (!prefs.autoStart() || prefs.zenKey().isBlank()) return
                val i = Intent(c, DshService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= 26) c.startForegroundService(i)
                else c.startService(i)
            }
        }
    }

    companion object {
        fun pingUi(c: Context) {
            // Обновить виджет: статус running.
            try {
                val i = Intent(c, DshWidget::class.java).setAction(DshWidget.ACTION_UPDATE)
                c.sendBroadcast(i)
            } catch (_: Exception) {
            }
        }
    }
}

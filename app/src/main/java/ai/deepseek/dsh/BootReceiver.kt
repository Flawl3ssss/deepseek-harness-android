package ai.deepseek.dsh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Автозапуск после ребута (ARCH-MOD-01): только если autoStart && installed. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = Prefs(c)
        if (!prefs.autoStart()) return
        if (!Paths.isInstalled(c)) return
        if (prefs.zenKey().isBlank()) return
        InstallLog.w(c, "boot", "BOOT_COMPLETED, starting service")
        val i = Intent(c, DshService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) c.startForegroundService(i)
        else c.startService(i)
    }
}

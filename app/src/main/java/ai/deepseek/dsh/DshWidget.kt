package ai.deepseek.dsh

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Виджет статуса DSH (ARCH-MOD-05, R-11): статус + тап открывает приложение. */
class DshWidget : AppWidgetProvider() {
    companion object {
        const val ACTION_UPDATE = "ai.deepseek.dsh.WIDGET_UPDATE"
    }

    override fun onReceive(c: Context, intent: Intent?) {
        super.onReceive(c, intent)
        if (intent?.action == ACTION_UPDATE) refreshAll(c)
    }

    override fun onUpdate(c: Context, mgr: AppWidgetManager, ids: IntArray) {
        refreshAll(c)
    }

    private fun refreshAll(c: Context) {
        try {
            val mgr = AppWidgetManager.getInstance(c)
            val ids = mgr.getAppWidgetIds(android.content.ComponentName(c, DshWidget::class.java))
            val status = if (DshService.running) c.getString(R.string.svc_running) else "—"
            for (id in ids) {
                val v = RemoteViews(c.packageName, R.layout.widget_layout)
                v.setTextViewText(R.id.widget_status, status)
                v.setOnClickPendingIntent(
                    R.id.widget_title,
                    android.app.PendingIntent.getActivity(
                        c, 0, Intent(c, MainActivity::class.java),
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                )
                mgr.updateAppWidget(id, v)
            }
        } catch (_: Exception) {
        }
    }
}

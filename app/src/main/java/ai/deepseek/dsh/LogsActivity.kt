package ai.deepseek.dsh

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * Экран логов (ARCH-MOD-08, DEC-008, R-12, TASK-013):
 * список + сортировка + превью 200 КБ + Share + Refresh + Clear old.
 * Пакет наружу: install-лог + device-info + logcat-срез + dump-config без секретов.
 */
class LogsActivity : AppCompatActivity() {
    private lateinit var col: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        setContentView(ScrollView(this).apply { addView(col) })
        refresh()
    }

    private fun refresh() {
        col.removeAllViews()
        val logs = try {
            Paths.logsDir(this).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        for (f in logs.take(30)) {
            col.addView(TextView(this).apply {
                text = "${f.name} (${f.length() / 1024} КБ)"
                textSize = 14f
                setPadding(0, 8, 0, 8)
                setOnClickListener { preview(f) }
            })
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun btn(t: String, fn: () -> Unit) = android.widget.Button(this).apply {
            text = t
            setOnClickListener { fn() }
        }.also { row.addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        btn("Refresh") { refresh() }
        btn("Share") { InstallLog.share(this) }
        btn("Clear old") {
            Thread {
                try {
                    val files = Paths.logsDir(this).listFiles()
                        ?.sortedByDescending { it.lastModified() }?.drop(5) ?: emptyList()
                    files.forEach { it.delete() }
                } catch (_: Exception) {
                }
                runOnUiThread { refresh() }
            }.start()
        }
        col.addView(row)
    }

    private fun preview(f: File) {
        try {
            val text = f.inputStream().bufferedReader().use { r ->
                val buf = CharArray(200 * 1024)
                val n = r.read(buf)
                if (n < 0) "" else String(buf, 0, n)
            }
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(f.name)
                .setMessage(InstallLog.redact(text))
                .setPositiveButton("OK", null)
                .show()
        } catch (e: Exception) {
            InstallLog.w(this, "logs", "preview failed: ${e.message}")
        }
    }
}

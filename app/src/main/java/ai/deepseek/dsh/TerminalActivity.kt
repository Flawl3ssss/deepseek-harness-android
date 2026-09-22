package ai.deepseek.dsh

import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Терминал гостя (ARCH-MOD-07, R-08, TASK-012): прямой proot `/bin/bash -l`,
 * синглтон TerminalHolder переживает ротацию, pump-тред 4 КБ, моноширинный вывод.
 * Без Shizuku/a11y. Вне песочницы DSH, внутри гостя.
 * v1.1-долг (TASK-103): audit команд в InstallLog с redact.
 */
class TerminalActivity : AppCompatActivity() {
    private lateinit var out: TextView
    private lateinit var input: EditText

    object TerminalHolder {
        var proc: Process? = null
        var pump: Thread? = null
        val buf = StringBuilder()
        var listener: ((String) -> Unit)? = null

        @Synchronized
        fun append(s: String) {
            buf.append(s)
            if (buf.length > 200000) buf.delete(0, buf.length - 200000)
            listener?.invoke(s)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
        }
        out = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
        }
        val sv = ScrollView(this).apply { addView(out) }
        root.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
        input = EditText(this).apply {
            hint = "команда"
            typeface = Typeface.MONOSPACE
        }
        root.addView(input)
        root.addView(Button(this).apply {
            text = "Выполнить"
            setOnClickListener { send() }
        })
        setContentView(root)
        TerminalHolder.listener = { runOnUiThread { render() } }
        render()
        ensureShell()
    }

    override fun onDestroy() {
        TerminalHolder.listener = null
        super.onDestroy()
    }

    private fun render() {
        out.text = TerminalHolder.buf.toString()
    }

    private fun ensureShell() {
        if (TerminalHolder.proc != null) return
        Thread {
            try {
                val prefs = Prefs(this)
                val inner = Proot.full(this, prefs, listOf("/bin/bash", "-l"), "/root")
                TerminalHolder.append("$ proot bash -l\n")
                val p = ProcessBuilder(inner).redirectErrorStream(true).start()
                TerminalHolder.proc = p
                TerminalHolder.pump = Thread {
                    try {
                        val buf = ByteArray(4096)
                        while (true) {
                            val n = p.inputStream.read(buf)
                            if (n < 0) break
                            TerminalHolder.append(String(buf, 0, n))
                        }
                        TerminalHolder.append("\n[shell exited ${p.waitFor()}]\n")
                    } catch (e: Exception) {
                        TerminalHolder.append("\n[pump ended: ${e.message}]\n")
                    }
                    TerminalHolder.proc = null
                }.also { it.isDaemon = true; it.start() }
            } catch (e: Exception) {
                TerminalHolder.append("\n[shell start failed: ${e.message}]\n")
            }
        }.start()
    }

    private fun send() {
        val cmd = input.text.toString()
        if (cmd.isBlank()) return
        input.setText("")
        Thread {
            try {
                val p = TerminalHolder.proc
                if (p == null) {
                    TerminalHolder.append("\n[no shell, restarting…]\n")
                    ensureShell()
                    return@Thread
                }
                p.outputStream.write((cmd + "\n").toByteArray())
                p.outputStream.flush()
            } catch (e: Exception) {
                TerminalHolder.append("\n[send failed: ${e.message}]\n")
            }
        }.start()
    }
}

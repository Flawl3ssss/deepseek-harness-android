package ai.deepseek.dsh

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Единый LogBus (ARCH-MOD-08, DEC-008, R-12).
 *
 * Формат строк: ISO-8601 + тег компонента (+ rid/turn где есть):
 *   2026-09-22T12:00:00.123+07:00 [boot] message
 *   2026-09-22T12:00:01.456+07:00 [adapter rid=abc] message
 *
 * Redact (RISK-005): process-token из `dsh web:` URL, значения ключей по маске,
 * строки вида sk-... — никогда в лог. Проверяется secret-scan тестовых логов в CI.
 */
object InstallLog {
    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)

    // Маска секретов: sk-<20+>, github_pat_, gh[pousr]_, AKIA, xox... + process token (?token=...).
    private val secretRe = Regex("(sk-[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{22,}|gh[pousr]_[A-Za-z0-9]{36}|AKIA[0-9A-Z]{16}|xox[bap]-[A-Za-z0-9-]{10,})")
    private val tokenRe = Regex("([?&]token=)[^&\\s]+")

    fun redact(s: String): String =
        tokenRe.replace(secretRe.replace(s, "***"), "$1***")

    private fun stamp(): String = iso.format(Date())

    /** Текущий install-лог (30-мин окно): dsh-home/logs/install-<ts>.log */
    private fun currentFile(c: Context): File {
        val dir = Paths.logsDir(c)
        val name = "install-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.log"
        return File(dir, name)
    }

    @Synchronized
    fun w(c: Context, tag: String, msg: String) {
        try {
            val line = "${stamp()} [$tag] ${redact(msg)}\n"
            currentFile(c).appendText(line)
            android.util.Log.i("DSH", "[$tag] ${redact(msg)}")
        } catch (_: Exception) {
        }
    }

    /** Короткая форма: w(c, "boot", ...) */
    fun w(c: Context, msg: String) = w(c, "app", msg)

    fun deviceInfo(c: Context): String {
        val pm = c.packageManager
        val pi = try {
            pm.getPackageInfo(c.packageName, 0)
        } catch (_: Exception) {
            null
        }
        val pj = try {
            File(Paths.payloadDir(c), "payload.json").readText()
        } catch (_: Exception) {
            "{}"
        }
        val pageSize = try {
            // Os.getpagesize() доступен с API 21 через android.system.Os
            android.system.Os.getpagesize().toString()
        } catch (_: Exception) {
            "?"
        }
        return buildString {
            appendLine("device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("android: ${android.os.Build.VERSION.RELEASE} (sdk ${android.os.Build.VERSION.SDK_INT}), abi ${android.os.Build.SUPPORTED_ABIS.firstOrNull()}")
            appendLine("pagesize: $pageSize")
            appendLine("free: ${c.filesDir.freeSpace / 1048576}MB(filesDir) ${c.getExternalFilesDir(null)?.freeSpace?.div(1048576)}MB(ext)")
            appendLine("apk: ${pi?.versionName} (${pi?.versionCode})")
            appendLine("payload: $pj")
            appendLine("proot: ${if (Paths.hasBundledProot(c)) "bundled" else "fallback"}")
        }
    }

    fun share(c: Context) {
        try {
            val src = currentFile(c)
            val dst = File(File(c.cacheDir, "shared"), "dsh-install.log")
            dst.parentFile?.mkdirs()
            src.copyTo(dst, overwrite = true)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                c, "ai.deepseek.dsh.fileprovider", dst
            )
            val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            c.startActivity(android.content.Intent.createChooser(i, "Отправить лог"))
        } catch (e: Exception) {
            android.widget.Toast.makeText(c, "Share failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun exportToShared(c: Context) {
        try {
            val dst = File(Paths.sharedRoot(c), "logs/dsh-install.log")
            dst.parentFile?.mkdirs()
            currentFile(c).copyTo(dst, overwrite = true)
        } catch (_: Exception) {
        }
    }
}

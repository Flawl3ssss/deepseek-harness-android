package ai.deepseek.dsh

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Мастер первого запуска (ARCH-MOD-09/F-01, TASK-009).
 * Качает rootfs + proot + payload-1 + node-слой, проверяет sha256, распаковывает,
 * сидит dsh-home (zen-провайдер + дефолтная модель), smoke-тестит proot,
 * просит zen-ключ (gateKeyThenStart, R-06). Всё пишется в install-*.log (R-12).
 *
 * v1.0.1: ВЕСЬ лог виден прямо на экране (selectable, autoscroll) + кнопка
 * «Скопировать лог» (буфер обмена — вставка в чат текстом) + живой прогресс
 * скачивания в МБ (payload 76 МБ на медленной сети выглядит как «зависание»).
 */
class BootActivity : Activity() {
    private lateinit var stepViews: List<TextView>
    private lateinit var bar: ProgressBar
    private lateinit var pct: TextView
    private lateinit var status: TextView
    private lateinit var log: TextView
    private lateinit var scrollRoot: ScrollView
    private lateinit var retry: Button
    private lateinit var copyBtn: Button
    private lateinit var sendLog: Button

    private val steps = listOf(
        "Rootfs Ubuntu", "Node-слой", "proot", "Payload DSH",
        "Распаковка", "Проверка", "Запуск"
    )

    companion object {
        const val DL_BASE = "https://github.com/Flawl3ssss/deepseek-harness-android/releases/download"
        const val ROOTFS_TAG = "rootfs-ubuntu-1"
        const val NODE_TAG = "rootfs-ubuntu-1"
        const val PAYLOAD_TAG = "payload-1"
        const val EXPECTED_DSH = "0.1.7-alpha.1"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashCatcher()
        if (Paths.isInstalled(this)) {
            startMain()
            return
        }
        val bg = 0xFF0B0E14.toInt()
        val card = 0xFF151B26.toInt()
        scrollRoot = ScrollView(this).apply { setBackgroundColor(bg) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 32)
        }
        scrollRoot.addView(col, android.widget.FrameLayout.LayoutParams(-1, -2))
        val title = TextView(this).apply {
            text = "DSH"
            textSize = 34f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFFE8ECF3.toInt())
        }
        val sub = TextView(this).apply {
            text = getString(R.string.boot_subtitle)
            textSize = 15f
            setTextColor(0xFF9AA3B5.toInt())
        }
        col.addView(title)
        col.addView(sub)
        col.addView(gap(24))
        val cardBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(card)
            setPadding(32, 28, 32, 28)
        }
        stepViews = steps.map { name ->
            TextView(this).apply {
                text = "○  $name"
                textSize = 15f
                setTextColor(0xFF9AA3B5.toInt())
                setPadding(0, 8, 0, 8)
            }.also { cardBox.addView(it) }
        }
        col.addView(cardBox)
        col.addView(gap(20))
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        pct = TextView(this).apply {
            text = "0%"
            textSize = 13f
            setTextColor(0xFF9AA3B5.toInt())
            gravity = Gravity.END
        }
        status = TextView(this).apply {
            text = "Подготовка…"
            textSize = 13f
            setTextColor(0xFFE8ECF3.toInt())
            setPadding(0, 4, 0, 0)
        }
        col.addView(bar)
        col.addView(pct)
        col.addView(status)
        col.addView(gap(16))
        col.addView(TextView(this).apply {
            text = "Лог (всё пишется сюда):"
            textSize = 13f
            setTextColor(0xFF9AA3B5.toInt())
        })
        log = TextView(this).apply {
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(0xFF9AA3B5.toInt())
            setTextIsSelectable(true)
            minHeight = 600
            setPadding(16, 16, 16, 16)
            setBackgroundColor(card)
        }
        col.addView(log)
        col.addView(gap(16))
        retry = Button(this).apply {
            text = getString(R.string.action_retry)
            isEnabled = false
            setOnClickListener { Thread { runInstall() }.start() }
        }
        copyBtn = Button(this).apply {
            text = "СКОПИРОВАТЬ ЛОГ"
            setOnClickListener { copyLog() }
        }
        sendLog = Button(this).apply {
            text = getString(R.string.action_send_log)
            setOnClickListener { InstallLog.share(this@BootActivity) }
        }
        col.addView(retry, LinearLayout.LayoutParams(-1, -2))
        col.addView(gap(12, false))
        col.addView(copyBtn, LinearLayout.LayoutParams(-1, -2))
        col.addView(gap(12, false))
        col.addView(sendLog, LinearLayout.LayoutParams(-1, -2))
        col.addView(gap(24, false))
        setContentView(scrollRoot)
        Thread { runInstall() }.start()
    }

    private fun gap(h: Int, w: Boolean = false): android.view.View {
        return android.view.View(this).apply {
            layoutParams = if (w) LinearLayout.LayoutParams(h, -2) else LinearLayout.LayoutParams(-1, h)
        }
    }

    private fun ui(fn: () -> Unit) = runOnUiThread(fn)

    private fun mark(i: Int, ok: Boolean, detail: String = "") {
        ui {
            stepViews[i].text = (if (ok) "●  " else "✕  ") + steps[i] + if (detail.isNotEmpty()) " — $detail" else ""
            stepViews[i].setTextColor(if (ok) 0xFF4DA3FF.toInt() else 0xFFFF6B6B.toInt())
        }
        InstallLog.w(this, "boot", "step ${steps[i]}: ${if (ok) "OK" else "FAIL"} $detail")
    }

    private fun say(s: String) {
        InstallLog.w(this, "boot", s)
        ui {
            log.append(InstallLog.redact(s) + "\n")
            // Экранный буфер не растим бесконечно (память): держим хвост ~80К символов.
            if (log.length() > 120000) {
                log.text = log.text.takeLast(80000)
            }
            scrollRoot.post { scrollRoot.fullScroll(android.view.View.FOCUS_DOWN) }
        }
    }

    private fun setStatus(s: String) {
        ui { status.text = s }
        InstallLog.w(this, "boot", "status: $s")
    }

    private fun progress(p: Int) {
        ui { bar.progress = p; pct.text = "$p%" }
    }

    private fun runInstall() {
        ui { retry.isEnabled = false }
        // WakeLock на время установки: Doze при погашенном экране рвёт долгие скачивания.
        val wl = (getSystemService(POWER_SERVICE) as android.os.PowerManager)
            .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "dsh:install")
        try {
            wl.acquire((30 * 60 * 1000).toLong())
            runInstallInner()
        } catch (e: Exception) {
            say("FAILED: ${e.message}")
            setStatus("Ошибка: ${e.message}")
            ui { retry.isEnabled = true }
        } finally {
            try {
                if (wl.isHeld) wl.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun runInstallInner() {
        try {
            say(InstallLog.deviceInfo(this))
            // 1. rootfs Ubuntu (~17 МБ)
            setStatus("Скачивание Rootfs Ubuntu…")
            val rootfsTar = File(filesDir, "rootfs.tar.xz")
            download("$DL_BASE/$ROOTFS_TAG/ubuntu-24.04-arm64.tar.xz", rootfsTar, "$DL_BASE/$ROOTFS_TAG/SHA256SUMS", "ubuntu-24.04-arm64.tar.xz", 0, 20, "Rootfs")
            mark(0, true, "${rootfsTar.length() / 1048576} МБ"); progress(20)
            // 2. Node-слой (~31 МБ)
            setStatus("Скачивание Node-слоя…")
            val nodeTar = File(filesDir, "node.tar.xz")
            download("$DL_BASE/$NODE_TAG/node-v24-linux-arm64.tar.xz", nodeTar, "$DL_BASE/$NODE_TAG/SHA256SUMS", "node-v24-linux-arm64.tar.xz", 20, 12, "Node")
            mark(1, true, "${nodeTar.length() / 1048576} МБ"); progress(32)
            // 3. proot (fallback-бинарь; bundled lib едет в самом APK через jniLibs)
            setStatus("Установка proot…")
            installProotFallback()
            mark(2, true); progress(40)
            // 4. payload-1 (~76 МБ — самый долгий шаг!)
            setStatus("Скачивание Payload DSH (~76 МБ, самый долгий шаг)…")
            val payloadTar = File(filesDir, "payload.tar.xz")
            download("$DL_BASE/$PAYLOAD_TAG/payload-1.tar.xz", payloadTar, "$DL_BASE/$PAYLOAD_TAG/SHA256SUMS", "payload-1.tar.xz", 40, 15, "Payload")
            val pj = downloadText("$DL_BASE/$PAYLOAD_TAG/payload.json")
            if (!pj.contains(EXPECTED_DSH)) throw IllegalStateException("payload dshVersion mismatch (want $EXPECTED_DSH)")
            if (pj.contains("sk-")) throw IllegalStateException("payload.json looks like it contains a secret")
            mark(3, true, "${payloadTar.length() / 1048576} МБ"); progress(55)
            // 5. распаковка
            setStatus("Распаковка…")
            say("extract rootfs…")
            untar(rootfsTar, Paths.rootfsDir(this))
            say("extract node…")
            untar(nodeTar, Paths.nodeDir(this))
            say("extract payload…")
            untar(payloadTar, Paths.payloadDir(this))
            File(Paths.payloadDir(this), "payload.json").writeText(pj)
            mark(4, true); progress(75)
            // 6. проверка: proot smoke + node + payload.json
            setStatus("Проверка…")
            probeProot()
            checkNode()
            mark(5, true); progress(88)
            // 7. сид dsh-home + gate ключа
            setStatus("Запуск…")
            seedDshHome()
            mark(6, true); progress(100)
            say("done")
            ui { startMain() }
        } catch (e: Exception) {
            say("FAILED: ${e.message}")
            setStatus("Ошибка: ${e.message}")
            ui { retry.isEnabled = true }
        }
    }

    private fun startMain() {
        // gateKeyThenStart (R-06): без ключа — в настройки, иначе в Main.
        if (Prefs(this).zenKey().isBlank()) {
            startActivity(Intent(this, SettingsActivity::class.java))
        } else {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }

    private fun download(url: String, dst: File, sumsUrl: String, sumsName: String, base: Int, span: Int, label: String) {
        val expected = downloadText(sumsUrl).lines()
            .firstOrNull { it.trim().endsWith(sumsName) }
            ?.trim()?.split(Regex("\\s+"))?.firstOrNull()
            ?: throw IllegalStateException("no sha for $sumsName")
        // resume ×3
        var attempt = 0
        while (true) {
            try {
                httpToFile(url, dst, label, base, span)
                break
            } catch (e: Exception) {
                attempt++
                if (attempt >= 3) throw e
                say("retry $attempt/3: ${e.message}")
            }
        }
        val actual = sha256(dst)
        if (!actual.equals(expected, ignoreCase = true)) {
            dst.delete()
            throw IllegalStateException("sha256 mismatch for $sumsName")
        }
        say("$sumsName sha OK")
    }

    private fun downloadText(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 30000; c.readTimeout = 30000
        c.connect()
        if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode} for $url")
        return c.inputStream.bufferedReader().readText()
    }

    private fun httpToFile(url: String, dst: File, label: String, base: Int, span: Int) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 30000; c.readTimeout = 60000
        val have = if (dst.exists()) dst.length() else 0L
        if (have > 0) c.setRequestProperty("Range", "bytes=$have-")
        c.connect()
        if (c.responseCode !in listOf(200, 206)) throw IllegalStateException("HTTP ${c.responseCode} for $url")
        val total = (c.getHeaderField("Content-Length")?.toLongOrNull() ?: 0L) + have
        var lastUi = 0L
        FileOutputStream(dst, c.responseCode == 206).use { out ->
            val buf = ByteArray(65536)
            var done = have
            while (true) {
                val n = c.inputStream.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                done += n
                val now = System.currentTimeMillis()
                if (total > 0 && now - lastUi > 500) {
                    lastUi = now
                    val p = (base + (span * done / total).toInt()).coerceIn(0, 100)
                    val mb = "$label: ${done / 1048576}/${total / 1048576} МБ"
                    ui { bar.progress = p; pct.text = "$p%"; status.text = "Скачивание… $mb" }
                }
            }
            if (total > 0) {
                val mb = "$label: ${done / 1048576}/${total / 1048576} МБ — готово"
                say(mb)
            } else {
                say("$label: скачано ${done / 1048576} МБ (размер неизвестен)")
            }
        }
    }

    /** Копия лога в буфер обмена — вставка текстом прямо в чат. */
    private fun copyLog() {
        Thread {
            try {
                val f = Paths.logsDir(this).listFiles { x -> x.name.startsWith("install-") }
                    ?.maxByOrNull { it.lastModified() }
                    ?: throw IllegalStateException("лог-файл не найден")
                var text = f.readText()
                if (text.length > 150000) text = text.takeLast(150000)
                val clip = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val data = ClipData.newPlainText("dsh-log", InstallLog.redact(text))
                runOnUiThread {
                    clip.setPrimaryClip(data)
                    Toast.makeText(this, "Лог скопирован (${text.length / 1024} КБ) — вставь в чат", Toast.LENGTH_LONG).show()
                }
                InstallLog.w(this, "boot", "log copied to clipboard (${text.length} chars)")
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Копия не удалась: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun sha256(f: File): String {
        val d = MessageDigest.getInstance("SHA-256")
        FileInputStream(f).use { ins ->
            val buf = ByteArray(65536)
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                d.update(buf, 0, n)
            }
        }
        return d.digest().joinToString("") { "%02x".format(it) }
    }

    private fun untar(tar: File, dst: File) {
        dst.mkdirs()
        // commons-compress + xz (зависимости в build.gradle).
        FileInputStream(tar).use { fis ->
            org.apache.commons.compress.archivers.tar.TarArchiveInputStream(
                org.tukaani.xz.XZInputStream(fis)
            ).use { tis ->
                var e = tis.nextEntry
                while (e != null) {
                    val out = File(dst, e.name)
                    if (e.isDirectory) out.mkdirs()
                    else {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { tis.copyTo(it) }
                    }
                    e = tis.nextEntry
                }
            }
        }
    }

    private fun installProotFallback() {
        // Bundled libproot.so исполняется через linker64 прямо из nativeLibraryDir.
        // Копия в filesDir НЕработоспособна (noexec, error=13) — поэтому fallback качаем
        // обычным бинарём, а не копируем .so. Если bundled lib есть — fallback пропускаем.
        if (Paths.hasBundledProot(this)) {
            say("bundled libproot present, fallback skipped")
            return
        }
        val dst = File(filesDir, "proot")
        httpToFile("$DL_BASE/$ROOTFS_TAG/proot-aarch64", dst, "proot", 32, 8)
        dst.setExecutable(true)
        say("proot fallback installed")
    }

    private fun probeProot() {
        // smoke: proot --version через тот же инвок что боевой (linker64-head либо fallback).
        val head = if (Paths.hasBundledProot(this)) {
            listOf("/system/bin/linker64", Paths.prootLib(this).absolutePath)
        } else listOf(Paths.prootBin(this).absolutePath)
        val p = ProcessBuilder(head + listOf("--version")).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        val code = p.waitFor()
        say("proot smoke: exit=$code ${out.trim().take(120)}")
        if (code != 0) throw IllegalStateException("proot smoke failed")
    }

    private fun checkNode() {
        val node = File(Paths.nodeDir(this), "bin/node")
        if (!node.exists()) {
            // node-слой лежит как nodeDir/opt/node (см. rootfs.yml) — проверяем и его.
            val optNode = File(Paths.nodeDir(this), "opt/node/bin/node")
            if (optNode.exists()) {
                say("node present (opt/node)")
                return
            }
            val found = Paths.nodeDir(this).walkTopDown().firstOrNull { it.name == "node" && it.isFile }
                ?: throw IllegalStateException("node binary missing in node-layer")
            say("node at ${found.relativeTo(Paths.nodeDir(this))}")
        } else say("node present")
    }

    private fun seedDshHome() {
        val home = Paths.dshHome(this)
        home.mkdirs()
        File(home, "logs").mkdirs()
        Paths.sharedWorkspace(this).mkdirs()
        Paths.workspacePriv(this).mkdirs()
        // Сид settings.yaml: zen-провайдер openai-responses (DEC-007). Только референс ключа!
        val settings = File(home, "settings.yaml")
        if (!settings.exists()) {
            settings.writeText(
                "llm-pi-ai:\n" +
                    "  providers:\n" +
                    "    zen:\n" +
                    "      displayName: Zen (OpenCode)\n" +
                    "      api: openai-responses\n" +
                    "      baseURL: http://127.0.0.1:8787/v1\n" +
                    "      apiKeyEnv: ZEN_API_KEY\n" +
                    "      reasoning: xhigh\n" +
                    "      models:\n" +
                    "        - id: muse-spark-1.3-contributor-free\n"
            )
            say("settings.yaml seeded (zen provider, apiKeyEnv only)")
        }
        // home cordis.patch.yml: agent-default-model → zen (DEC-007).
        val patch = File(home, "cordis.patch.yml")
        if (!patch.exists()) {
            patch.writeText(
                "- path: agent-default-model\n" +
                    "  set:\n" +
                    "    provider: zen\n" +
                    "    model: muse-spark-1.3-contributor-free\n"
            )
            say("cordis.patch.yml seeded (default model zen)")
        }
    }

    private fun installCrashCatcher() {
        val h = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                File(Paths.logsDir(this), "CRASH.pending").writeText(
                    "${InstallLog.redact(e.stackTraceToString()).take(8000)}"
                )
            } catch (_: Exception) {
            }
            h?.uncaughtException(t, e)
        }
    }
}

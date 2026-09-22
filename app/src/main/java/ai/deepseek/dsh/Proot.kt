package ai.deepseek.dsh

import android.content.Context
import java.io.File

/**
 * Proot-harness (ARCH-MOD-02, DEC-002).
 * Инвок linker64+libproot.so, когда есть bundled lib; иначе прямой exec скачанного бинаря.
 * env -i (герметично, без утечек хоста), DSH_HOME=/root/dsh-home.
 * Порты: zen 8787, DSH 3080 (официальный дефолт web, FACT-DSH-02).
 */
object Proot {
    const val ZEN_PORT = 8787
    const val DSH_PORT = 3080

    private fun prefix(c: Context, workdir: String = "/root"): List<String> {
        val root = Paths.rootfsDir(c).absolutePath
        val shared = Paths.sharedRoot(c).absolutePath
        val home = Paths.dshHome(c).absolutePath
        val ws = Paths.sharedWorkspace(c).absolutePath
        val wsPriv = Paths.workspacePriv(c).absolutePath
        val payload = Paths.payloadDir(c).absolutePath
        // Node-слой распакован как nodeDir/opt/node (см. rootfs.yml + BootActivity.untar):
        // в госте должен быть ровно /opt/node, поэтому биндим внутренний каталог.
        val nodeSrc = File(Paths.nodeDir(c), "opt/node").takeIf { it.exists() }
            ?: Paths.nodeDir(c)
        // Bundled .so исполняется только через linker64 прямо из nativeLibraryDir.
        // Копия в filesDir неработоспособна (noexec) — см. BootActivity.installProot.
        val head = if (Paths.hasBundledProot(c)) {
            listOf("/system/bin/linker64", Paths.prootLib(c).absolutePath)
        } else listOf(Paths.prootBin(c).absolutePath)
        return head + listOf(
            "-r", root,
            "-b", "/dev", "-b", "/proc", "-b", "/sys",
            "-b", "$shared:/root/phone",
            "-b", "$ws:/root/workspace",
            "-b", "$wsPriv:/root/workspace-priv",
            "-b", "$home:/root/dsh-home",
            "-b", "$payload:/opt/dsh",
            "-b", "${nodeSrc.absolutePath}:/opt/node",
            "-w", workdir,
            // env -i идёт внутрь prefix: unset LD_PRELOAD обязателен (FACT-PROOT-01).
            "env", "-u", "LD_PRELOAD",
            "/usr/bin/env", "-i"
        )
    }

    private fun baseEnv(): List<String> = listOf(
        "HOME=/root", "TERM=xterm-256color", "LANG=C.UTF-8",
        "PATH=/opt/node/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        "DSH_HOME=/root/dsh-home"
    )

    /** Полная команда: proot ... env -i <env> <cmd>. Секреты — только env (FACT-DSH-06). */
    fun full(c: Context, prefs: Prefs, cmd: List<String>, workdir: String = "/root/workspace"): List<String> {
        val extra = listOf(
            "ZEN_API_KEY=${prefs.zenKey()}",
            "ZEN_ADAPTER_PORT=$ZEN_PORT",
            "NODE_OPTIONS=--max-old-space-size=${prefs.nodeRamMb()}"
        )
        return prefix(c, workdir) + baseEnv() + extra + cmd
    }

    /** argv запуска zen-адаптера (FACT-ZEN-03: sessions-file на устойчивом пути, не /tmp). */
    fun zenArgv(): List<String> = listOf(
        "/opt/node/bin/node",
        "/opt/dsh/bin/zen-adapter.mjs",
        "--port", ZEN_PORT.toString(),
        "--sessions-file", "/root/dsh-home/zen-sessions.json"
    )

    /** argv запуска DSH web (DEC-001: официальный дефолт 3080, loopback, no-open). */
    fun dshArgv(): List<String> = listOf(
        "/opt/node/bin/node",
        "/opt/dsh/node_modules/@deepseek-ai/dsh/lib/bin.js",
        "--profile", "web",
        "--port", DSH_PORT.toString(),
        "--no-open",
        "--trusted-host", "127.0.0.1:$DSH_PORT"
    )

    fun startDaemon(c: Context, tag: String, inner: List<String>, log: File): Process {
        log.parentFile?.mkdirs()
        val pb = ProcessBuilder(inner)
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log))
        pb.redirectError(ProcessBuilder.Redirect.appendTo(log))
        InstallLog.w(c, "proot", "start $tag: ${inner.joinToString(" ")}")
        return pb.start()
    }

    fun waitPort(port: Int, tries: Int = 15): Boolean {
        repeat(tries) {
            try {
                java.net.Socket("127.0.0.1", port).close()
                return true
            } catch (_: Exception) {
                Thread.sleep(1000)
            }
        }
        return false
    }
}

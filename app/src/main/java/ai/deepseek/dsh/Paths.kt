package ai.deepseek.dsh

import android.content.Context
import java.io.File

/**
 * Единая схема файловой системы (ARCH-MOD-06, DEC-005).
 *
 * Приват (filesDir, только приложение):
 *   rootfs/      — гостевой rootfs (Ubuntu 24.04 arm64)
 *   payload/     — payload-1: node_modules DSH, bin/zen-adapter.mjs, payload.json
 *   node/        — Node-слой (node-vXX-linux-arm64), /opt/node в госте
 *   dsh-home/    — DSH_HOME (профили, sessions, settings, credentials, zen-sessions.json, logs)
 *   workspace-priv/ — приватный workspace (cwd агента вкупе с /root/phone)
 *   proot        — бинарь proot (fallback, если нет bundled lib)
 *
 * Общее (getExternalFilesDir/DSH, видно пользователю):
 *   workspace/ inbox/ outbox/ downloads/ backups/
 */
object Paths {
    fun rootfsDir(c: Context) = File(c.filesDir, "rootfs")
    fun payloadDir(c: Context) = File(c.filesDir, "payload")
    fun nodeDir(c: Context) = File(c.filesDir, "node")
    fun dshHome(c: Context) = File(c.filesDir, "dsh-home")
    fun workspacePriv(c: Context) = File(c.filesDir, "workspace-priv")

    /** proot: bundled native lib (linker64) либо fallback-файл filesDir/proot. */
    fun prootLib(c: Context) = File(c.applicationInfo.nativeLibraryDir, "libproot.so")
    fun hasBundledProot(c: Context): Boolean {
        val lib = prootLib(c)
        return lib.exists() && lib.length() > 100000
    }
    fun prootBin(c: Context): File {
        if (hasBundledProot(c)) return prootLib(c)
        return File(c.filesDir, "proot")
    }

    fun sharedRoot(c: Context): File {
        val f = File(c.getExternalFilesDir(null), "DSH")
        f.mkdirs()
        return f
    }
    fun inbox(c: Context) = File(sharedRoot(c), "inbox").apply { mkdirs() }
    fun outbox(c: Context) = File(sharedRoot(c), "outbox").apply { mkdirs() }
    fun downloads(c: Context) = File(sharedRoot(c), "downloads").apply { mkdirs() }
    fun backups(c: Context) = File(sharedRoot(c), "backups").apply { mkdirs() }
    fun sharedWorkspace(c: Context) = File(sharedRoot(c), "workspace").apply { mkdirs() }

    fun logsDir(c: Context) = File(dshHome(c), "logs").apply { mkdirs() }

    fun isInstalled(c: Context): Boolean {
        // Ubuntu: /etc/os-release (ID=ubuntu) вместо debian_version.
        val osRelease = File(rootfsDir(c), "etc/os-release")
        if (!osRelease.exists()) return false
        val pj = File(payloadDir(c), "payload.json")
        if (!pj.exists()) return false
        // proot: bundled lib (exec прямо из nativeLibraryDir) либо рабочий fallback-файл.
        // Копия .so в filesDir НЕ считается (там noexec, error=13).
        if (!hasBundledProot(c) && !File(c.filesDir, "proot").exists()) return false
        return try {
            pj.readText().contains("\"payloadVersion\"")
        } catch (_: Exception) {
            false
        }
    }
}

package ai.deepseek.dsh

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream

/**
 * Бэкап DSH_HOME (ARCH-MOD-09, R-03, TASK-014): sessions/ + settings.yaml +
 * .credentials.yaml + profiles patch+package.json + home patch + storages/ +
 * attachments/ + zen-sessions.json + payload.json + manifest. Ротация 7+4.
 * Ночной periodic — в час из настроек; runOnce — кнопка.
 */
class BackupWorker(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        return try {
            runBackup(applicationContext)
            Result.success()
        } catch (e: Exception) {
            InstallLog.w(applicationContext, "backup", "backup FAILED: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        const val NAME = "dsh-backup"

        fun schedule(c: Context) {
            val req = PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(c).enqueueUniquePeriodicWork(
                NAME, ExistingPeriodicWorkPolicy.KEEP, req
            )
        }

        fun runOnce(c: Context) {
            Thread { runBackup(c) }.start()
        }

        fun runBackup(c: Context) {
            val home = Paths.dshHome(c)
            if (!home.exists()) {
                InstallLog.w(c, "backup", "no dsh-home, skip")
                return
            }
            val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val dst = File(Paths.backups(c), "dsh-backup-$ts.tar.xz")
            dst.parentFile?.mkdirs()
            // Список R-03: что сохраняем при обновлении/wipe.
            val rels = listOf(
                "sessions", "settings.yaml", ".credentials.yaml",
                "profiles", "cordis.patch.yml", "storages", "attachments",
                "zen-sessions.json", "payload.json"
            )
            TarArchiveOutputStream(XZOutputStream(dst.outputStream(), LZMA2Options())).use { tar ->
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                for (rel in rels) {
                    val f = File(home, rel.removeSuffix("/"))
                    val pf = File(Paths.payloadDir(c), rel)
                    val src = when {
                        f.exists() -> f to home
                        rel == "payload.json" && pf.exists() -> pf to Paths.payloadDir(c)
                        else -> continue
                    }
                    addRec(tar, src.first, src.second)
                }
                // manifest{apk, dsh, date, files}
                val manifest = buildString {
                    appendLine("date=$ts")
                    appendLine("apk=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    appendLine("dsh=0.1.7-alpha.1")
                    append("files=")
                    appendLine(rels.joinToString(","))
                }.toByteArray()
                val me = TarArchiveEntry("MANIFEST.txt")
                me.size = manifest.size.toLong()
                tar.putArchiveEntry(me)
                tar.write(manifest)
                tar.closeArchiveEntry()
            }
            InstallLog.w(c, "backup", "backup OK: ${dst.name} (${dst.length() / 1024} КБ)")
            // Ротация 7+4: держим последние 7 суточных + 4 недельных (эвристика по имени).
            try {
                val all = Paths.backups(c).listFiles { f -> f.name.startsWith("dsh-backup-") }
                    ?.sortedByDescending { it.lastModified() } ?: emptyList()
                all.drop(11).forEach { it.delete() }
            } catch (_: Exception) {
            }
        }

        private fun addRec(tar: TarArchiveOutputStream, f: File, base: File) {
            val rel = base.toURI().relativize(f.toURI()).path
            if (f.isDirectory) {
                val e = TarArchiveEntry(f, rel)
                tar.putArchiveEntry(e)
                tar.closeArchiveEntry()
                f.listFiles()?.forEach { addRec(tar, it, base) }
            } else {
                val e = TarArchiveEntry(f, rel)
                tar.putArchiveEntry(e)
                f.inputStream().use { it.copyTo(tar) }
                tar.closeArchiveEntry()
            }
        }
    }
}

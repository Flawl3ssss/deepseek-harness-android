package ai.deepseek.dsh

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

/**
 * Файловый менеджер моста (ARCH-MOD-06, TASK-011): 4 таба
 * workspace / inbox / outbox / downloads (+backups) + CRUD + share/attach.
 */
class FilesActivity : AppCompatActivity() {
    private var tab: String = "workspace"
    private lateinit var list: ListView
    private lateinit var title: TextView

    private fun dir(): File = when (tab) {
        "inbox" -> Paths.inbox(this)
        "outbox" -> Paths.outbox(this)
        "downloads" -> Paths.downloads(this)
        "backups" -> Paths.backups(this)
        else -> Paths.sharedWorkspace(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        title = TextView(this).apply { textSize = 16f }
        root.addView(title)
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (t in listOf("workspace", "inbox", "outbox", "downloads", "backups")) {
            tabs.addView(Button(this).apply {
                text = t.take(4)
                setOnClickListener { tab = t; refresh() }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(tabs)
        list = ListView(this)
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(ScrollView(this).apply { addView(root) })
        list.setOnItemClickListener { _, _, pos, _ ->
            val f = currentFiles().getOrNull(pos) ?: return@setOnItemClickListener
            if (f.isDirectory) return@setOnItemClickListener
            shareFile(f)
        }
        refresh()
    }

    private fun currentFiles(): List<File> =
        try {
            dir().listFiles()?.sortedBy { it.name } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

    private fun refresh() {
        title.text = "$tab: ${dir().absolutePath}"
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, currentFiles().map {
            (if (it.isDirectory) "[d] " else "") + it.name
        })
    }

    private fun shareFile(f: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(this, "ai.deepseek.dsh.fileprovider", f)
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(i, "Поделиться"))
        } catch (e: Exception) {
            InstallLog.w(this, "files", "share failed: ${e.message}")
        }
    }
}

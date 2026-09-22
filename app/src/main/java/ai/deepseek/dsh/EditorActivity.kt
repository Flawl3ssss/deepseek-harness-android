package ai.deepseek.dsh

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * Простой редактор файлов моста (TASK-011): открывает файл из inbox/workspace,
 * сохраняет обратно. Без подсветки — минимум v1.0.
 */
class EditorActivity : AppCompatActivity() {
    private lateinit var path: EditText
    private lateinit var body: EditText
    private var file: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        path = EditText(this).apply { hint = "имя файла (inbox/…)" }
        body = EditText(this).apply {
            hint = "текст"
            minLines = 20
            setSingleLine(false)
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Button(this).apply {
            text = "Открыть"
            setOnClickListener { open() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Button(this).apply {
            text = "Сохранить"
            setOnClickListener { save() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(path)
        root.addView(row)
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(ScrollView(this).apply { addView(root) })
        intent.getStringExtra("path")?.let { path.setText(it); open() }
    }

    private fun resolve(): File {
        val name = File(path.text.toString()).name
        val inbox = File(Paths.inbox(this), name)
        if (inbox.exists()) return inbox
        return File(Paths.sharedWorkspace(this), name)
    }

    private fun open() {
        try {
            file = resolve()
            body.setText(if (file!!.exists()) file!!.readText() else "")
        } catch (e: Exception) {
            Toast.makeText(this, "Open failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun save() {
        try {
            (file ?: resolve()).writeText(body.text.toString())
            Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

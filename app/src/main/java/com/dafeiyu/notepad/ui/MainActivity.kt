package com.dafeiyu.notepad.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.dafeiyu.notepad.data.MetaStore
import com.dafeiyu.notepad.data.NoteRepository
import com.dafeiyu.notepad.databinding.ActivityMainBinding
import com.dafeiyu.notepad.util.Formatters
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var repo: NoteRepository
    private lateinit var meta: MetaStore

    /** 当前等待配图的笔记名 */
    private var pendingCoverFor: String? = null

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val src = result.data?.data ?: return@registerForActivityResult
            val name = pendingCoverFor ?: return@registerForActivityResult
            copyImageToNote(src, name)
            pendingCoverFor = null
        }
    }

    private val requestPerm = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allGranted = grants.values.all { it }
        if (!allGranted) {
            Toast.makeText(this, "需要存储权限才能读写笔记", Toast.LENGTH_LONG).show()
        }
        refresh()
    }

    private fun ensurePermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            // Android 13+ 不再需要 WRITE_EXTERNAL_STORAGE，公共 Documents 目录可写
            return
        }
        val needed = mutableListOf<String>()
        if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            needed += android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (android.os.Build.VERSION.SDK_INT <= 29 &&
            checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            needed += android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
        if (needed.isNotEmpty()) requestPerm.launch(needed.toTypedArray())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        repo = NoteRepository(this)
        meta = MetaStore(this)

        ensurePermission()

        b.fabNew.setOnClickListener { dialogNewNote() }
        b.fabStats.setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val files = repo.listNotes()
        b.tvPath.text = repo.rootDir.absolutePath
        b.tvEmpty.visibility = if (files.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE

        // 用简单列表展示（不引 RecyclerView 也能跑，先保证稳定）
        val sb = StringBuilder()
        if (files.isEmpty()) {
            sb.append("")
        } else {
            files.forEachIndexed { idx, f ->
                val name = f.nameWithoutExtension
                val m = meta.get(name)
                val size = Formatters.size(f.length())
                val chars = runCatching { f.readText().length }.getOrDefault(0)
                val edits = m?.editCount ?: 0
                val created = m?.createdAt ?: f.lastModified()
                val hasImg = repo.imageOf(name) != null

                if (idx > 0) sb.append("\n\n")
                sb.append("📄 ").append(name).append("\n")
                sb.append("   ").append(size).append(" · ").append(chars).append(" 字")
                    .append(" · 编辑 ").append(edits).append(" 次")
                if (hasImg) sb.append(" · 🖼")
                sb.append("\n   ").append(Formatters.dateTime(created))
            }
        }
        b.tvList.text = sb.toString()

        // 点击列表项 → 需要知道点的是哪个，用位置映射
        b.tvList.setOnClickListener { showNotePicker(files) }

        b.tvList.setOnLongClickListener {
            showNotePicker(files, longPress = true)
            true
        }
    }

    private fun showNotePicker(files: List<File>, longPress: Boolean = false) {
        if (files.isEmpty()) return
        val names = files.map { it.nameWithoutExtension }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(if (longPress) "管理笔记" else "打开笔记")
            .setItems(names) { _, which ->
                val name = names[which]
                if (longPress) showManageDialog(name) else openEditor(name)
            }
            .show()
    }

    private fun showManageDialog(name: String) {
        val options = arrayOf("编辑内容", "设置配图", "重命名", "删除")
        AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openEditor(name)
                    1 -> {
                        pendingCoverFor = name
                        val i = Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = "image/*"
                            addCategory(Intent.CATEGORY_OPENABLE)
                        }
                        pickImage.launch(Intent.createChooser(i, "选择配图"))
                    }
                    2 -> dialogRename(name)
                    3 -> dialogDelete(name)
                }
            }
            .show()
    }

    private fun copyImageToNote(src: Uri, name: String) {
        runCatching {
            val ext = contentResolver.getType(src)?.substringAfterLast('/') ?: "png"
            val safeExt = if (ext == "jpeg") "jpg" else ext
            val target = File(repo.rootDir, "${name}_cover.$safeExt")
            contentResolver.openInputStream(src)!!.use { input ->
                target.outputStream().use { out -> input.copyTo(out) }
            }
            meta.setHasImage(name, true)
            Toast.makeText(this, "配图已设置", Toast.LENGTH_SHORT).show()
            refresh()
        }.onFailure {
            Toast.makeText(this, "设置失败：${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openEditor(name: String) {
        startActivity(Intent(this, EditorActivity::class.java).putExtra("noteName", name))
    }

    private fun dialogNewNote() {
        val input = android.widget.EditText(this).apply {
            hint = "笔记名称（不需要 .txt）"
        }
        AlertDialog.Builder(this)
            .setTitle("新建笔记")
            .setView(input)
            .setPositiveButton("创建") { _, _ ->
                val raw = input.text.toString().trim()
                if (raw.isEmpty()) return@setPositiveButton
                val name = raw.replace(Regex("[/\\\\:*?\"<>|]"), "_")
                if (repo.exists(name)) {
                    Toast.makeText(this, "同名笔记已存在", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                repo.create(name)
                meta.upsert(name, 0, isEdit = false)
                refresh()
                openEditor(name)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun dialogRename(old: String) {
        val input = android.widget.EditText(this).apply { setText(old) }
        AlertDialog.Builder(this)
            .setTitle("重命名")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val raw = input.text.toString().trim()
                if (raw.isEmpty() || raw == old) return@setPositiveButton
                val name = raw.replace(Regex("[/\\\\:*?\"<>|]"), "_")
                if (repo.rename(old, name)) {
                    // 元数据整体搬移
                    meta.get(old)?.let { m ->
                        val moved = m.copy(fileName = name)
                        meta.remove(old)
                        meta.upsert(name, moved.charCount, false)
                    }
                    refresh()
                } else {
                    Toast.makeText(this, "重命名失败（可能同名）", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun dialogDelete(name: String) {
        AlertDialog.Builder(this)
            .setTitle("删除「$name」")
            .setMessage("TXT 和配图都会被删除，无法恢复。")
            .setPositiveButton("删除") { _, _ ->
                repo.delete(name)
                meta.remove(name)
                refresh()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}

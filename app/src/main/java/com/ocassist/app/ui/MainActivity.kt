package com.ocassist.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ocassist.app.data.MetaStore
import com.ocassist.app.data.NoteRepository
import com.ocassist.app.data.TermStore
import com.ocassist.app.databinding.ActivityMainBinding
import com.ocassist.app.databinding.ItemNoteBinding
import com.ocassist.app.util.Formatters
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var repo: NoteRepository
    private lateinit var meta: MetaStore
    private lateinit var terms: TermStore
    private lateinit var adapter: NoteAdapter

    private var pendingCoverFor: String? = null

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val src = result.data?.data ?: return@registerForActivityResult
            val name = pendingCoverFor ?: return@registerForActivityResult
            applyCover(src, name)
            pendingCoverFor = null
        }
    }

    private val pickImport = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { doImport(it) }
        }
    }

    private val pickExportDir = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let { doExport(it) }
    }

    private val requestPerm = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        repo = NoteRepository(this)
        meta = MetaStore(this)
        terms = TermStore(this)

        ensurePermission()

        adapter = NoteAdapter(
            onClick = { openEditor(it) },
            onLongClick = { showManageDialog(it) }
        )
        b.rvNotes.layoutManager = LinearLayoutManager(this)
        b.rvNotes.adapter = adapter

        b.fabNew.setOnClickListener { dialogNewNote() }
        b.fabStats.setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
        }
        b.fabTool.setOnClickListener { showToolsMenu() }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun ensurePermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33) return
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

    private fun refresh() {
        val files = repo.listNotes()
        b.tvPath.text = repo.rootDir.absolutePath
        b.tvEmpty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        b.rvNotes.visibility = if (files.isEmpty()) View.GONE else View.VISIBLE

        val items = files.map { f ->
            val name = f.nameWithoutExtension
            val m = meta.get(name)
            NoteItem(
                name = name,
                size = Formatters.size(f.length()),
                chars = runCatching { f.readText().length }.getOrDefault(0),
                edits = m?.editCount ?: 0,
                created = m?.createdAt ?: f.lastModified(),
                hasImage = repo.imageOf(name) != null,
                preview = runCatching {
                    f.readText().take(60).replace("\n", " ")
                }.getOrDefault("")
            )
        }
        adapter.submit(items, repo)
    }

    // ─────────── 配图 ───────────

    private fun applyCover(uri: Uri, name: String) {
        runCatching {
            val mime = contentResolver.getType(uri) ?: "image/png"
            val ext = mime.substringAfterLast('/')
            contentResolver.openInputStream(uri)?.use { input ->
                repo.setCover(name, input, ext)
            } ?: throw IllegalStateException("无法读取图片")
            meta.setHasImage(name, true)
            Toast.makeText(this, "配图已设置", Toast.LENGTH_SHORT).show()
            refresh()
        }.onFailure {
            Toast.makeText(this, "设置失败：${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    /** 全屏查看配图 */
    private fun showCover(name: String) {
        val f = repo.imageOf(name)
        if (f == null) {
            Toast.makeText(this, "还没有配图", Toast.LENGTH_SHORT).show()
            return
        }
        val iv = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageURI(Uri.fromFile(f))
            setBackgroundColor(0xFF000000.toInt())
        }
        AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            .setView(iv)
            .setPositiveButton("关闭") { d, _ -> d.dismiss() }
            .show()
    }

    // ─────────── 菜单 ───────────

    private fun showToolsMenu() {
        val items = arrayOf(
            "导出全部笔记（打包）",
            "从备份导入",
            "管理自定义词库（${terms.count()} 词）",
            "打开笔记目录"
        )
        AlertDialog.Builder(this)
            .setTitle("工具")
            .setItems(items) { _, i ->
                when (i) {
                    0 -> exportAll()
                    1 -> pickImport.launch(
                        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            type = "*/*"
                            addCategory(Intent.CATEGORY_OPENABLE)
                        }
                    )
                    2 -> showTermManager()
                    3 -> openDir()
                }
            }
            .show()
    }

    private fun openDir() {
        runCatching {
            val i = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.fromFile(repo.rootDir), "resource/folder")
            }
            startActivity(i)
        }.onFailure {
            Toast.makeText(this, "目录：${repo.rootDir.absolutePath}", Toast.LENGTH_LONG).show()
        }
    }

    // ─────────── 词库管理 ───────────

    private fun showTermManager() {
        val list = terms.all().toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("自定义词库（${list.size} 个）\n长词优先，不会被拆碎统计")
            .setItems(list) { _, i ->
                terms.removeAt(i)
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("添加") { _, _ -> addTermDialog() }
            .setNeutralButton("批量导入") { _, _ -> batchImportTerms() }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun addTermDialog() {
        val input = EditText(this).apply { hint = "输入一个名词，如：基础宇宙" }
        AlertDialog.Builder(this)
            .setTitle("添加词条")
            .setView(input)
            .setPositiveButton("添加") { _, _ ->
                val t = input.text.toString().trim()
                if (terms.add(t)) {
                    Toast.makeText(this, "已添加「$t」", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "已存在或为空", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun batchImportTerms() {
        // 从剪贴板批量导入，一行一个
        val cm = getSystemService(android.content.ClipboardManager::class.java)
        val clip = cm?.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.text?.toString()?.trim()

        if (clip.isNullOrEmpty()) {
            Toast.makeText(this, "剪贴板没有内容（每行一个词）", Toast.LENGTH_LONG).show()
            return
        }
        val list = clip.split("\n", "，", ",", "、", " ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val n = terms.addAll(list)
        Toast.makeText(this, "导入了 $n 个词条", Toast.LENGTH_SHORT).show()
        showTermManager()
    }

    // ─────────── 导入 / 导出 ───────────

    /** 导出所有笔记 + 配图为一个 .zip（无依赖：手写 zip） */
    private fun exportAll() {
        pickExportDir.launch("OC辅助备份_${System.currentTimeMillis()}.zip")
    }

    private fun doExport(uri: Uri) {
        runCatching {
            contentResolver.openOutputStream(uri)?.use { os ->
                val notes = repo.listNotes()
                val entries = mutableListOf<Pair<String, ByteArray>>()
                notes.forEach { f ->
                    val name = f.nameWithoutExtension
                    entries += "notes/$name.txt" to f.readBytes()
                    repo.imageOf(name)?.let { img ->
                        entries += "notes/${name}_cover.${img.extension}" to img.readBytes()
                    }
                }
                // 元数据也一起导出
                val metaFile = File(filesDir, "notes_meta.json")
                if (metaFile.exists()) entries += "notes_meta.json" to metaFile.readBytes()
                val termFile = File(filesDir, "user_terms.json")
                if (termFile.exists()) entries += "user_terms.json" to termFile.readBytes()

                writeZip(os, entries)
            }
            Toast.makeText(this, "导出成功：${repo.listNotes().size} 篇笔记", Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(this, "导出失败：${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    /** 极简 ZIP 写入器（STORED 无压缩，纯手写，不依赖 java.util.zip） */
    private fun writeZip(out: java.io.OutputStream, entries: List<Pair<String, ByteArray>>) {
        val dos = java.io.DataOutputStream(out)
        val central = mutableListOf<ByteArray>()
        var offset = 0

        fun crc32(data: ByteArray): Long {
            val table = LongArray(256)
            for (i in 0 until 256) {
                var c = i.toLong()
                for (k in 0 until 8) c = if (c and 1L != 0L) 0xEDB88320L xor (c ushr 1) else c ushr 1
                table[i] = c
            }
            var crc = 0xFFFFFFFFL
            for (b in data) crc = table[((crc xor b.toLong()) and 0xFF).toInt()] xor (crc ushr 8)
            return crc xor 0xFFFFFFFFL
        }

        entries.forEach { (name, bytes) ->
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            val crc = crc32(bytes)
            val local = java.io.ByteArrayOutputStream()
            val ld = java.io.DataOutputStream(local)
            ld.writeInt(0x04034b50)
            ld.writeShort(20); ld.writeShort(0); ld.writeShort(0)   // version, flags, method=store
            ld.writeShort(0); ld.writeShort(0)                       // time, date
            ld.writeInt(crc.toInt())
            ld.writeInt(bytes.size)
            ld.writeInt(bytes.size)
            ld.writeShort(nameBytes.size)
            ld.writeShort(0)
            ld.write(nameBytes)
            ld.flush()
            val localBytes = local.toByteArray()

            dos.write(localBytes)
            dos.write(bytes)

            val cen = java.io.ByteArrayOutputStream()
            val cd = java.io.DataOutputStream(cen)
            cd.writeInt(0x02014b50)
            cd.writeShort(20); cd.writeShort(20); cd.writeShort(0); cd.writeShort(0)
            cd.writeShort(0); cd.writeShort(0)
            cd.writeInt(crc.toInt())
            cd.writeInt(bytes.size)
            cd.writeInt(bytes.size)
            cd.writeShort(nameBytes.size)
            cd.writeShort(0); cd.writeShort(0)
            cd.writeShort(0); cd.writeShort(0)
            cd.writeInt(0)
            cd.writeInt(offset)
            cd.write(nameBytes)
            cd.flush()
            central += cen.toByteArray()

            offset += localBytes.size + bytes.size
        }

        val cdStart = offset
        var cdSize = 0
        central.forEach { dos.write(it); cdSize += it.size }

        dos.writeInt(0x06054b50)
        dos.writeShort(0); dos.writeShort(0)
        dos.writeShort(central.size); dos.writeShort(central.size)
        dos.writeInt(cdSize)
        dos.writeInt(cdStart)
        dos.writeShort(0)
        dos.flush()
    }

    private fun doImport(uri: Uri) {
        runCatching {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("读取失败")

            // 判断是 zip 还是单个 txt
            val isZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

            if (!isZip) {
                // 单文件导入
                val name = "导入_${System.currentTimeMillis()}"
                File(repo.rootDir, "$name.txt").writeBytes(bytes)
                meta.upsert(name, String(bytes, Charsets.UTF_8).length, isEdit = false)
                Toast.makeText(this, "已导入 1 篇", Toast.LENGTH_SHORT).show()
                refresh()
                return@runCatching
            }

            // zip 导入：解到临时目录，再搬进笔记目录
            val tmp = File(cacheDir, "import_${System.currentTimeMillis()}")
            tmp.mkdirs()
            var count = 0
            // 用 java.util.zip 读（读比写好写，直接可用）
            java.util.zip.ZipInputStream(bytes.inputStream()).use { zis ->
                var e = zis.nextEntry
                while (e != null) {
                    val outFile = File(tmp, e.name)
                    if (!e.isDirectory && e.name.startsWith("notes/")) {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { zis.copyTo(it) }
                        count++
                    }
                    zis.closeEntry()
                    e = zis.nextEntry
                }
            }

            // 搬运
            val notesDir = File(tmp, "notes")
            var imported = 0
            notesDir.listFiles()?.forEach { f ->
                val target = File(repo.rootDir, f.name)
                if (!target.exists()) {
                    f.copyTo(target, overwrite = false)
                    imported++
                    if (f.name.endsWith(".txt")) {
                        val nm = f.nameWithoutExtension
                        meta.upsert(nm, runCatching { f.readText().length }.getOrDefault(0), isEdit = false)
                    }
                }
            }
            tmp.deleteRecursively()

            Toast.makeText(this, "导入了 $imported 个文件", Toast.LENGTH_LONG).show()
            refresh()
        }.onFailure {
            Toast.makeText(this, "导入失败：${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ─────────── 笔记操作 ───────────

    private fun openEditor(name: String) {
        startActivity(Intent(this, EditorActivity::class.java).putExtra("noteName", name))
    }

    private fun showManageDialog(name: String) {
        val items = arrayOf("编辑内容", "设置配图", "查看配图", "重命名", "删除")
        AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(items) { _, i ->
                when (i) {
                    0 -> openEditor(name)
                    1 -> {
                        pendingCoverFor = name
                        pickImage.launch(
                            Intent(Intent.ACTION_GET_CONTENT).apply {
                                type = "image/*"
                                addCategory(Intent.CATEGORY_OPENABLE)
                            }
                        )
                    }
                    2 -> showCover(name)
                    3 -> dialogRename(name)
                    4 -> dialogDelete(name)
                }
            }
            .show()
    }

    private fun dialogNewNote() {
        val input = EditText(this).apply { hint = "笔记名称（不需要 .txt）" }
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
        val input = EditText(this).apply { setText(old) }
        AlertDialog.Builder(this)
            .setTitle("重命名")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val raw = input.text.toString().trim()
                if (raw.isEmpty() || raw == old) return@setPositiveButton
                val name = raw.replace(Regex("[/\\\\:*?\"<>|]"), "_")
                if (repo.rename(old, name)) {
                    meta.get(old)?.let { m ->
                        meta.remove(old)
                        meta.upsert(name, m.charCount, false)
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

    // ─────────── 数据类与适配器 ───────────

    data class NoteItem(
        val name: String,
        val size: String,
        val chars: Int,
        val edits: Int,
        val created: Long,
        val hasImage: Boolean,
        val preview: String
    )

    private class NoteAdapter(
        private val onClick: (String) -> Unit,
        private val onLongClick: (String) -> Unit
    ) : RecyclerView.Adapter<NoteAdapter.VH>() {

        private var items: List<NoteItem> = emptyList()
        private var repo: NoteRepository? = null

        fun submit(list: List<NoteItem>, r: NoteRepository) {
            items = list
            repo = r
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ib = ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(ib)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val it = items[position]
            holder.b.tvName.text = it.name
            holder.b.tvMeta.text = buildString {
                append(it.size).append(" · ").append(it.chars).append(" 字")
                append(" · 编辑 ").append(it.edits).append(" 次")
                append(" · ").append(Formatters.date(it.created))
            }
            holder.b.tvPreview.text = it.preview

            if (it.hasImage) {
                holder.b.ivThumb.visibility = View.VISIBLE
                holder.b.tvNoThumb.visibility = View.GONE
                val bmp = repo?.loadThumbnail(it.name, 180)
                if (bmp != null) {
                    holder.b.ivThumb.setImageBitmap(bmp)
                } else {
                    holder.b.ivThumb.setImageDrawable(null)
                    holder.b.tvNoThumb.visibility = View.VISIBLE
                    holder.b.ivThumb.visibility = View.GONE
                }
            } else {
                holder.b.ivThumb.visibility = View.GONE
                holder.b.tvNoThumb.visibility = View.VISIBLE
            }

            holder.itemView.setOnClickListener { onClick(it.name) }
            holder.itemView.setOnLongClickListener { onLongClick(it.name); true }
        }

        override fun getItemCount() = items.size

        class VH(val b: ItemNoteBinding) : RecyclerView.ViewHolder(b.root)
    }
}

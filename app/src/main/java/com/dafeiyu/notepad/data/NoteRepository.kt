package com.dafeiyu.notepad.data

import android.content.Context
import java.io.File

/**
 * 笔记仓库：管理 /sdcard/Documents/大肥鱼笔记/ 下的 TXT 与同名图片
 *
 * 图片命名约定：笔记.txt 的配图存为 笔记_cover.png（或 .jpg）
 */
class NoteRepository(private val context: Context) {

    val rootDir: File by lazy {
        val dir = File(
            android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOCUMENTS
            ),
            "大肥鱼笔记"
        )
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    fun listNotes(): List<File> {
        val files = rootDir.listFiles { f ->
            f.isFile && f.name.endsWith(".txt", ignoreCase = true)
        } ?: emptyArray()
        return files.sortedByDescending { it.lastModified() }
    }

    fun noteFile(baseName: String): File = File(rootDir, "$baseName.txt")

    /** 寻找该笔记的配图（支持 png/jpg/jpeg/webp） */
    fun imageOf(baseName: String): File? {
        val exts = listOf("png", "jpg", "jpeg", "webp")
        for (e in exts) {
            val f = File(rootDir, "${baseName}_cover.$e")
            if (f.exists()) return f
        }
        return null
    }

    fun create(baseName: String, initial: String = ""): File {
        val f = noteFile(baseName)
        f.writeText(initial)
        return f
    }

    fun read(baseName: String): String = noteFile(baseName).let {
        if (it.exists()) it.readText() else ""
    }

    fun write(baseName: String, content: String) {
        noteFile(baseName).writeText(content)
    }

    fun delete(baseName: String) {
        noteFile(baseName).delete()
        imageOf(baseName)?.delete()
    }

    fun rename(oldName: String, newName: String): Boolean {
        val old = noteFile(oldName)
        val neu = noteFile(newName)
        if (neu.exists()) return false
        val ok = old.renameTo(neu)
        if (ok) {
            // 图片也跟着改名
            imageOf(oldName)?.let { img ->
                val ext = img.extension
                img.renameTo(File(rootDir, "${newName}_cover.$ext"))
            }
        }
        return ok
    }

    fun exists(baseName: String): Boolean = noteFile(baseName).exists()

    fun sizeOf(baseName: String): Long = noteFile(baseName).length()
}

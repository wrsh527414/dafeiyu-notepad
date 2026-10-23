package com.ocassist.app.data

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

    /** 设置配图：把选中的图片复制为该笔记的 _cover 文件，覆盖旧图 */
    fun setCover(baseName: String, input: java.io.InputStream, ext: String): File {
        // 先删掉所有旧配图，避免多格式共存
        listOf("png", "jpg", "jpeg", "webp").forEach {
            File(rootDir, "${baseName}_cover.$it").takeIf { f -> f.exists() }?.delete()
        }
        val safeExt = when (ext.lowercase()) {
            "jpeg", "jpg" -> "jpg"
            "webp" -> "webp"
            else -> "png"
        }
        val target = File(rootDir, "${baseName}_cover.$safeExt")
        target.outputStream().use { out -> input.copyTo(out) }
        return target
    }

    fun removeCover(baseName: String) {
        listOf("png", "jpg", "jpeg", "webp").forEach {
            File(rootDir, "${baseName}_cover.$it").takeIf { f -> f.exists() }?.delete()
        }
    }

    /** 用 BitmapFactory 解码缩略图（避免大图 OOM） */
    fun loadThumbnail(baseName: String, targetPx: Int = 256): android.graphics.Bitmap? {
        val f = imageOf(baseName) ?: return null
        if (!f.exists()) return null
        return runCatching {
            val opts = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeFile(f.absolutePath, opts)
            var scale = 1
            while (opts.outWidth / scale > targetPx * 2 || opts.outHeight / scale > targetPx * 2) {
                scale *= 2
            }
            val opts2 = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = scale
            }
            android.graphics.BitmapFactory.decodeFile(f.absolutePath, opts2)
        }.getOrNull()
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

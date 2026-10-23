package com.dafeiyu.notepad.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 文件元数据：创建时间、修改次数、图片关联等
 */
data class NoteMeta(
    val fileName: String,          // 文件名（不含扩展名）
    val createdAt: Long,           // 创建时间戳
    var modifiedAt: Long,          // 最后修改时间戳
    var editCount: Int,            // 修改次数
    var hasImage: Boolean = false, // 是否有关联图片
    var charCount: Int = 0         // 字数（冗余存储，避免每次重算）
)

/**
 * 元数据存储：用一个 JSON 文件保存所有笔记的元信息
 * 存于应用私有目录，不随用户删 TXT 而丢失统计
 */
class MetaStore(context: Context) {

    private val file = File(context.filesDir, "notes_meta.json")
    private val map = mutableMapOf<String, NoteMeta>()

    init {
        load()
    }

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val m = NoteMeta(
                    fileName = o.getString("fileName"),
                    createdAt = o.getLong("createdAt"),
                    modifiedAt = o.getLong("modifiedAt"),
                    editCount = o.getInt("editCount"),
                    hasImage = o.optBoolean("hasImage", false),
                    charCount = o.optInt("charCount", 0)
                )
                map[m.fileName] = m
            }
        }
    }

    fun save() {
        val arr = JSONArray()
        map.values.forEach { m ->
            arr.put(JSONObject().apply {
                put("fileName", m.fileName)
                put("createdAt", m.createdAt)
                put("modifiedAt", m.modifiedAt)
                put("editCount", m.editCount)
                put("hasImage", m.hasImage)
                put("charCount", m.charCount)
            })
        }
        runCatching { file.writeText(arr.toString()) }
    }

    fun get(fileName: String): NoteMeta? = map[fileName]

    /** 新建或更新元数据。isEdit=true 时累加修改次数 */
    fun upsert(fileName: String, charCount: Int, isEdit: Boolean): NoteMeta {
        val now = System.currentTimeMillis()
        val existing = map[fileName]
        val meta = if (existing == null) {
            NoteMeta(fileName, now, now, 0, false, charCount)
        } else {
            existing.apply {
                modifiedAt = now
                this.charCount = charCount
                if (isEdit) editCount++
            }
        }
        map[fileName] = meta
        save()
        return meta
    }

    fun setHasImage(fileName: String, has: Boolean) {
        map[fileName]?.let {
            it.hasImage = has
            save()
        }
    }

    fun remove(fileName: String) {
        map.remove(fileName)
        save()
    }

    fun all(): List<NoteMeta> = map.values.toList()
}

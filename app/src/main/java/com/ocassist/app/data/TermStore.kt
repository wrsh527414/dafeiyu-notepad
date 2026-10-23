package com.ocassist.app.data

import android.content.Context
import org.json.JSONArray
import java.io.File

/**
 * 用户自定义词库存储
 *
 * 用户可以把「基础宇宙」「墨影仆从」这类原创名词录进来，
 * 分词时会优先按整个词匹配，不会被拆碎成多个无意义片段。
 */
class TermStore(context: Context) {

    private val file = File(context.filesDir, "user_terms.json")
    private val terms = mutableListOf<String>()

    init { load() }

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val t = arr.getString(i).trim()
                if (t.isNotEmpty()) terms += t
            }
        }
    }

    fun save() {
        val arr = JSONArray()
        terms.forEach { arr.put(it) }
        runCatching { file.writeText(arr.toString()) }
    }

    fun all(): List<String> = terms.toList()

    /** 添加词条，已存在则返回 false */
    fun add(term: String): Boolean {
        val t = term.trim()
        if (t.isEmpty() || terms.contains(t)) return false
        terms += t
        save()
        return true
    }

    fun addAll(list: List<String>): Int {
        var n = 0
        list.forEach { if (add(it)) n++ }
        return n
    }

    fun remove(term: String) {
        if (terms.remove(term)) save()
    }

    fun removeAt(index: Int) {
        if (index in terms.indices) {
            terms.removeAt(index)
            save()
        }
    }

    fun clear() {
        terms.clear()
        save()
    }

    fun count(): Int = terms.size
}

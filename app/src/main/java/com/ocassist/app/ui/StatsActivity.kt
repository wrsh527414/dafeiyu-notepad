package com.ocassist.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ocassist.app.data.NoteRepository
import com.ocassist.app.data.TermStore
import com.ocassist.app.databinding.ActivityStatsBinding
import com.ocassist.app.databinding.ItemWordBinding
import com.ocassist.app.util.WordCounter

class StatsActivity : AppCompatActivity() {

    private lateinit var b: ActivityStatsBinding
    private lateinit var repo: NoteRepository
    private lateinit var terms: TermStore

    private var singleNote: String? = null
    private var includeStop = false
    private var nounsOnly = true
    private var termsOnly = false
    private var minCount = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(b.root)

        repo = NoteRepository(this)
        terms = TermStore(this)
        singleNote = intent.getStringExtra("noteName")

        supportActionBar?.title = if (singleNote != null) "词频 · $singleNote" else "全部词频"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        b.rvWords.layoutManager = LinearLayoutManager(this)
        b.rvWords.adapter = WordAdapter()

        b.switchStop.setOnCheckedChangeListener { _, c -> includeStop = c; compute() }
        b.switchNoun.setOnCheckedChangeListener { _, c -> nounsOnly = c; compute() }
        b.switchTermsOnly.setOnCheckedChangeListener { _, c ->
            if (c && terms.count() == 0) {
                // 词库为空时开白名单等于啥都不显示，先引导添加
                b.switchTermsOnly.isChecked = false
                Toast.makeText(this, "词库是空的，先添加几个词再开这个", Toast.LENGTH_LONG).show()
                showTermManager()
                return@setOnCheckedChangeListener
            }
            termsOnly = c
            compute()
        }
        b.chipMin2.setOnClickListener { minCount = 2; compute() }
        b.chipMin3.setOnClickListener { minCount = 3; compute() }
        b.chipMin5.setOnClickListener { minCount = 5; compute() }
        b.btnTerms.setOnClickListener { showTermManager() }

        b.switchNoun.isChecked = true
        updateTermButton()
        compute()
    }

    private fun updateTermButton() {
        b.btnTerms.text = "词库 (${terms.count()})"
    }

    // ─────────── 词库管理 ───────────

    private fun showTermManager() {
        val list = terms.all()
        if (list.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("自定义词库")
                .setMessage(
                    "词库用来告诉 App：哪些是你自己的专有名词。\n\n" +
                            "加入「基础宇宙」后，它就不会被拆成「基础」和「宇宙」分别统计了。\n\n" +
                            "打开「只统计词库」后，结果里只显示词库中的词。"
                )
                .setPositiveButton("添加") { _, _ -> addTermDialog() }
                .setNeutralButton("批量导入") { _, _ -> batchImportTerms() }
                .setNegativeButton("关闭", null)
                .show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("自定义词库（${list.size} 个）\n点击可删除")
            .setItems(list.toTypedArray()) { _, i ->
                val w = list[i]
                AlertDialog.Builder(this)
                    .setMessage("删除「$w」？")
                    .setPositiveButton("删除") { _, _ ->
                        terms.remove(w)
                        updateTermButton()
                        compute()
                        showTermManager()
                    }
                    .setNegativeButton("取消", null)
                    .show()
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
                if (t.isEmpty()) return@setPositiveButton
                if (terms.add(t)) {
                    Toast.makeText(this, "已添加「$t」", Toast.LENGTH_SHORT).show()
                    updateTermButton()
                    compute()
                } else {
                    Toast.makeText(this, "「$t」已存在", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 从剪贴板批量导入，支持换行/逗号/顿号/空格分隔 */
    private fun batchImportTerms() {
        val cm = getSystemService(android.content.ClipboardManager::class.java)
        val clip = cm?.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.text?.toString()?.trim()

        if (clip.isNullOrEmpty()) {
            Toast.makeText(this, "剪贴板是空的\n先把词复制好（每行一个）", Toast.LENGTH_LONG).show()
            return
        }
        val list = clip.split("\n", "\r", "，", ",", "、", "；", ";", " ")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.length >= 2 }
            .distinct()
        val n = terms.addAll(list)
        Toast.makeText(this, "导入 $n 个词条", Toast.LENGTH_SHORT).show()
        updateTermButton()
        compute()
    }

    // ─────────── 统计 ───────────

    private fun compute() {
        val text = if (singleNote != null) {
            repo.read(singleNote!!)
        } else {
            repo.listNotes().joinToString("\n\n") { runCatching { it.readText() }.getOrDefault("") }
        }

        if (text.isBlank()) {
            b.tvSummary.text = "没有可统计的文本。"
            (b.rvWords.adapter as WordAdapter).submit(emptyList())
            b.tvEmpty.visibility = View.VISIBLE
            return
        }

        val st = WordCounter.stats(text, terms.all())
        val sb = StringBuilder()
        sb.append("共 ${st.charCount} 字")
        sb.append(" · 中文 ${st.cjkCount}")
        sb.append(" · 去重 ${st.uniqueWords}")
        sb.append(" · 词库 ${terms.count()}")
        b.tvSummary.text = sb.toString()

        val words = WordCounter.count(
            text,
            minCount = minCount,
            includeStopWords = includeStop,
            userTerms = terms.all(),
            nounsOnly = nounsOnly,
            termsOnly = termsOnly
        )
        (b.rvWords.adapter as WordAdapter).submit(words)
        b.tvEmpty.visibility = if (words.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    private class WordAdapter : RecyclerView.Adapter<WordAdapter.VH>() {

        private var data: List<WordCounter.WordFreq> = emptyList()
        private var max = 1

        fun submit(list: List<WordCounter.WordFreq>) {
            data = list
            max = list.maxOfOrNull { it.count } ?: 1
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ib = ItemWordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(ib)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val w = data[position]
            holder.b.tvRank.text = "#${position + 1}"
            holder.b.tvWord.text = if (w.isTerm) "★ ${w.word}" else w.word
            holder.b.tvCount.text = "${w.count} 次"
            holder.b.bar.progress = (w.count * 100 / max).coerceIn(1, 100)
        }

        override fun getItemCount() = data.size

        class VH(val b: ItemWordBinding) : RecyclerView.ViewHolder(b.root)
    }
}

package com.dafeiyu.notepad.ui

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dafeiyu.notepad.data.MetaStore
import com.dafeiyu.notepad.data.NoteRepository
import com.dafeiyu.notepad.databinding.ActivityStatsBinding
import com.dafeiyu.notepad.databinding.ItemWordBinding
import com.dafeiyu.notepad.util.Formatters
import com.dafeiyu.notepad.util.WordCounter

class StatsActivity : AppCompatActivity() {

    private lateinit var b: ActivityStatsBinding
    private lateinit var repo: NoteRepository
    private lateinit var meta: MetaStore

    /** 指定笔记时只统计这一篇；否则统计全部 */
    private var singleNote: String? = null
    private var includeStop = false
    private var minCount = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(b.root)

        repo = NoteRepository(this)
        meta = MetaStore(this)
        singleNote = intent.getStringExtra("noteName")

        supportActionBar?.title = if (singleNote != null) "词频 · $singleNote" else "全部词频"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        b.rvWords.layoutManager = LinearLayoutManager(this)
        b.rvWords.adapter = WordAdapter()

        b.switchStop.setOnCheckedChangeListener { _, checked ->
            includeStop = checked
            compute()
        }
        b.chipMin2.setOnClickListener { minCount = 2; compute() }
        b.chipMin3.setOnClickListener { minCount = 3; compute() }
        b.chipMin5.setOnClickListener { minCount = 5; compute() }

        compute()
    }

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

        val st = WordCounter.stats(text)
        b.tvSummary.text = buildString {
            append("共 ${st.charCount} 字")
            append(" · 中文 ${st.cjkCount} 字")
            append(" · 英文 ${st.wordCount} 词")
            append(" · 去重 ${st.uniqueWords}")
        }

        val words = WordCounter.count(text, minCount = minCount, includeStopWords = includeStop)
        (b.rvWords.adapter as WordAdapter).submit(words)
        b.tvEmpty.visibility = if (words.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    // ── 列表适配器 ──
    private class WordAdapter : RecyclerView.Adapter<WordAdapter.VH>() {

        private var data: List<WordCounter.WordFreq> = emptyList()
        private var max = 1

        fun submit(list: List<WordCounter.WordFreq>) {
            data = list
            max = list.maxOfOrNull { it.count } ?: 1
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val ib = ItemWordBinding.inflate(
                android.view.LayoutInflater.from(parent.context), parent, false
            )
            return VH(ib)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val w = data[position]
            holder.b.tvRank.text = "#${position + 1}"
            holder.b.tvWord.text = w.word
            holder.b.tvCount.text = "${w.count} 次"
            holder.b.bar.progress = (w.count * 100 / max).coerceIn(1, 100)
        }

        override fun getItemCount() = data.size

        class VH(val b: ItemWordBinding) : RecyclerView.ViewHolder(b.root)
    }
}

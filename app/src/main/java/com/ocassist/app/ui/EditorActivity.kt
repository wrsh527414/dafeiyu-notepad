package com.ocassist.app.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ocassist.app.data.MetaStore
import com.ocassist.app.data.NoteRepository
import com.ocassist.app.databinding.ActivityEditorBinding
import com.ocassist.app.util.Formatters
import com.ocassist.app.util.WordCounter
import java.io.File

class EditorActivity : AppCompatActivity() {

    private lateinit var b: ActivityEditorBinding
    private lateinit var repo: NoteRepository
    private lateinit var meta: MetaStore
    private lateinit var noteName: String

    private var dirty = false
    private var autoSaveRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(b.root)

        noteName = intent.getStringExtra("noteName") ?: run {
            finish(); return
        }

        repo = NoteRepository(this)
        meta = MetaStore(this)

        supportActionBar?.title = noteName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        b.etContent.setText(repo.read(noteName))
        updateInfo()

        b.etContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                dirty = true
                updateInfo()
                scheduleAutoSave()
            }
        })
    }

    /** 停止输入 1.5 秒后自动保存，避免频繁写盘 */
    private fun scheduleAutoSave() {
        autoSaveRunnable?.let { b.etContent.removeCallbacks(it) }
        val r = Runnable { doSave(silent = true) }
        autoSaveRunnable = r
        b.etContent.postDelayed(r, 1500)
    }

    private fun updateInfo() {
        val text = b.etContent.text?.toString() ?: ""
        val st = WordCounter.stats(text)
        val m = meta.get(noteName)
        val sb = StringBuilder()
        sb.append("${st.charCount} 字")
        sb.append(" · ${st.lineCount} 行")
        sb.append(" · ${st.uniqueWords} 个词")
        if (m != null) {
            sb.append(" · 编辑 ${m.editCount} 次")
            sb.append(" · 创建于 ${Formatters.date(m.createdAt)}")
        }
        b.tvInfo.text = sb.toString()
        updateCover()
    }

    /** 刷新配图预览条 */
    private fun updateCover() {
        val bmp = repo.loadThumbnail(noteName, 120)
        if (bmp != null) {
            b.coverBar.visibility = android.view.View.VISIBLE
            b.ivCover.setImageBitmap(bmp)
            b.ivCover.setOnClickListener { showFullCover() }
            b.coverBar.setOnClickListener { showFullCover() }
        } else {
            b.coverBar.visibility = android.view.View.GONE
        }
    }

    private fun showFullCover() {
        val f = repo.imageOf(noteName) ?: return
        val iv = android.widget.ImageView(this).apply {
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            setImageURI(android.net.Uri.fromFile(f))
            setBackgroundColor(0xFF000000.toInt())
        }
        androidx.appcompat.app.AlertDialog.Builder(
            this, android.R.style.Theme_Black_NoTitleBar_Fullscreen
        )
            .setView(iv)
            .setPositiveButton("关闭") { d, _ -> d.dismiss() }
            .show()
    }

    private fun doSave(silent: Boolean) {
        val text = b.etContent.text?.toString() ?: ""
        if (!dirty) return
        repo.write(noteName, text)
        meta.upsert(noteName, text.length, isEdit = true)
        dirty = false
        updateInfo()
        if (!silent) {
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "保存").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        menu.add(0, 2, 1, "词频统计").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { saveAndFinish(); true }
        1 -> { doSave(silent = false); true }
        2 -> {
            doSave(silent = true)
            startActivity(
                android.content.Intent(this, StatsActivity::class.java)
                    .putExtra("noteName", noteName)
            )
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun saveAndFinish() {
        doSave(silent = true)
        finish()
    }

    override fun onBackPressed() {
        saveAndFinish()
        super.onBackPressed()
    }

    override fun onPause() {
        super.onPause()
        doSave(silent = true)
    }
}

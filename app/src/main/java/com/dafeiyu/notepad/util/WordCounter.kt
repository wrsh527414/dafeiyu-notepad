package com.dafeiyu.notepad.util

/**
 * 词频统计引擎
 *
 * 同时处理：
 *  - 英文单词（连续字母，长度 >= 2）
 *  - 中文 n-gram（2 字词 + 3 字词，滑动窗口）
 *
 * 中文没做真分词，用 n-gram 近似。停用词表可过滤高频噪声。
 */
object WordCounter {

    /** 常见中文停用词（2-gram / 3-gram 里最容易出噪声的） */
    private val CN_STOP = setOf(
        "的了", "是的", "我们", "你们", "他们", "这个", "那个", "什么", "可以", "没有",
        "就是", "因为", "所以", "但是", "而且", "如果", "虽然", "然后", "已经", "还是",
        "一个", "一些", "这些", "那些", "自己", "时候", "现在", "知道", "觉得", "应该",
        "或者", "不过", "只是", "这样", "那样", "怎么", "为什么", "不是", "不能", "不会",
        "的话", "一样", "起来", "出来", "下来", "过来", "上了", "里了", "了的", "是一"
    )

    /** 英文常见停用词 */
    private val EN_STOP = setOf(
        "the", "and", "for", "are", "but", "not", "you", "all", "can", "had",
        "her", "was", "one", "our", "out", "day", "get", "has", "him", "his",
        "how", "its", "may", "new", "now", "old", "see", "two", "way", "who",
        "boy", "did", "she", "use", "her", "than", "them", "then", "they",
        "this", "that", "with", "have", "from", "they", "been", "were", "said",
        "each", "which", "their", "will", "would", "there", "could", "other"
    )

    data class WordFreq(val word: String, val count: Int)

    /**
     * 统计词频
     * @param text 原文
     * @param minCount 最低出现次数（过滤长尾）
     * @param includeStopWords 是否包含停用词
     * @return 按出现次数从多到少排序，次数相同按长度优先、再按字典序
     */
    fun count(text: String, minCount: Int = 2, includeStopWords: Boolean = false): List<WordFreq> {
        val freq = HashMap<String, Int>()

        // ── 英文单词 ──
        Regex("[A-Za-z]{2,}").findAll(text).forEach { m ->
            val w = m.value.lowercase()
            if (includeStopWords || w !in EN_STOP) {
                freq[w] = (freq[w] ?: 0) + 1
            }
        }

        // ── 中文 n-gram ──
        // 先把非中文字符替换成空格，得到纯中文段
        val cnSegments = text.replace(Regex("[^\\u4e00-\\u9fa5]+"), " ")
            .split(" ")
            .filter { it.isNotBlank() }

        for (seg in cnSegments) {
            // 2-gram
            for (i in 0..seg.length - 2) {
                val w = seg.substring(i, i + 2)
                if (includeStopWords || w !in CN_STOP) {
                    freq[w] = (freq[w] ?: 0) + 1
                }
            }
            // 3-gram（只在足够长时）
            if (seg.length >= 3) {
                for (i in 0..seg.length - 3) {
                    val w = seg.substring(i, i + 3)
                    if (includeStopWords || w !in CN_STOP) {
                        freq[w] = (freq[w] ?: 0) + 1
                    }
                }
            }
        }

        return freq.entries
            .filter { it.value >= minCount }
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenByDescending { it.key.length }
                    .thenBy { it.key }
            )
            .map { WordFreq(it.key, it.value) }
    }

    /**
     * 统计基础信息：字数、行数、去重词数
     */
    data class TextStats(
        val charCount: Int,      // 字符数（含标点）
        val cjkCount: Int,       // 纯中文字数
        val wordCount: Int,      // 英文单词数
        val lineCount: Int,      // 行数
        val uniqueWords: Int     // 去重后的词数
    )

    fun stats(text: String): TextStats {
        val cjk = Regex("[\\u4e00-\\u9fa5]").findAll(text).count()
        val words = Regex("[A-Za-z]{2,}").findAll(text).count()
        val lines = if (text.isEmpty()) 0 else text.count { it == '\n' } + 1
        val uniq = count(text, minCount = 1, includeStopWords = false).size
        return TextStats(
            charCount = text.length,
            cjkCount = cjk,
            wordCount = words,
            lineCount = lines,
            uniqueWords = uniq
        )
    }
}

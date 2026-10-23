package com.ocassist.app.util

/**
 * 词频统计引擎 v2
 *
 * 核心改进：**长词优先覆盖**
 * 遇到「基础宇宙」，整个词被吃掉，不再重复统计「基础」「宇宙」「础宇」「础宇宙」。
 *
 * 算法：
 *  1. 中文段先按「已知长词」做贪心最长匹配（用户自定义词库 + 内置宇宙学科词）
 *  2. 未被覆盖的字符区间，再用 2-gram 补充
 *  3. 英文按单词统计
 *  4. 停用词表过滤虚词（因为、所以、这个……）
 *  5. 支持「只要名词」模式：只保留命中名词特征或用户词库的词
 */
object WordCounter {

    // ─────────────── 停用词 ───────────────

    /** 中文虚词 / 代词 / 连词 —— 这些几乎不可能是名词 */
    private val CN_STOP = setOf(
        // 代词
        "我们", "你们", "他们", "她们", "它们", "自己", "大家", "人家", "这个", "那个",
        "这些", "那些", "这样", "那样", "这里", "那里", "什么", "怎么", "为何", "这里",
        // 连词 / 副词
        "因为", "所以", "但是", "而且", "然而", "虽然", "如果", "除非", "否则", "于是",
        "然后", "接着", "况且", "并且", "或者", "还是", "只是", "不过", "只有", "只要",
        "即使", "尽管", "无论", "不管", "既然", "由于", "因此", "从而", "以致",
        // 助词 / 时态
        "的了", "是的", "的话", "了的", "是一", "上了", "里了", "过了", "着的", "地了",
        // 常见虚化动词
        "就是", "没有", "可以", "应该", "能够", "不会", "不能", "不是", "会有", "会有",
        "就是", "还是", "也是", "都是", "会有", "有着", "存在",
        // 时间 / 数量虚词
        "时候", "现在", "以后", "之前", "之后", "当时", "然后", "一会", "一点", "一些",
        "有些", "很多", "许多", "非常", "十分", "特别", "比较", "更加", "最为", "已经",
        "正在", "将要", "马上", "立刻", "刚刚", "曾经",
        // 无意义双字
        "起来", "出来", "下来", "过来", "上去", "进去", "回去", "上来", "一则", "一个",
        "一种", "一样", "一直", "一起", "一边", "一面"
    )

    /** 英文停用词 */
    private val EN_STOP = setOf(
        "the", "and", "for", "are", "but", "not", "you", "all", "can", "had",
        "her", "was", "one", "our", "out", "day", "get", "has", "him", "his",
        "how", "its", "may", "new", "now", "old", "see", "two", "way", "who",
        "boy", "did", "she", "use", "than", "them", "then", "they", "this",
        "that", "with", "have", "from", "been", "were", "said", "each", "which",
        "their", "will", "would", "there", "could", "other", "into", "more",
        "some", "such", "only", "own", "same", "very", "just", "because", "when",
        "what", "where", "while", "about", "after", "before", "being", "both"
    )

    // ─────────────── 内置领域词库（可扩展） ───────────────

    /**
     * 内置复合词 —— 命中就整体计入，不再拆分。
     * 这些是 X·D9 体系 / 宇宙学科里的高频长词。
     */
    private val BUILTIN_TERMS = listOf(
        // 宇宙层级
        "基础宇宙", "平行宇宙", "无限宇宙", "多元宇宙", "无限多元宇宙", "第一宇宙", "X宇宙",
        "叙事层", "叙事主体", "叙事权限", "叙事编辑者", "叙事尘埃", "叙事基底",
        "权柄阶序", "执笔者", "维度监察者", "律则畸变体", "规则偶合体", "规则适应者",
        "存在密度", "驱力", "自驱力", "外驱力", "原驱力", "逻辑宇宙", "非逻辑宇宙",
        "硬规则", "软规则", "康托尔", "数学瘟疫", "软规则制定者",
        // X·D9 设定
        "墨影仆从", "高级墨影仆从", "低级墨影仆从", "图书馆", "黑色卡片", "银色入场券",
        "全视之眼", "物质再编译", "概念覆写", "事物操控", "无限维度操控", "无限因果操控",
        "无限精神污染", "篇章重构", "二创禁域", "存在标点", "存在解离", "叙事瘟疫",
        "馆藏召唤", "最终解释权", "悲剧性音阶", "绝对闭环", "修辞癌变", "叙事时区",
        "风格死刑", "荣格", "圣裁", "意义锚定", "能指剥离", "误读深渊", "行距暴政",
        "元定义覆写", "未完之物", "浊墨一滴", "第四面墙", "全书之眼",
        "大基数", "集合论", "阿列夫数", "不可达基数", "马洛基数", "伍丁基数",
        // 其他
        "优莉", "小循", "旅者", "冥昭", "幽墟", "烬痕", "绯刹", "玄枢", "青冥", "苍颉",
        "无咎", "虚渊", "艾登", "幻公司", "巨构", "循声守卫", "幽匿", "主世界", "下界", "末地"
    ).sortedByDescending { it.length }   // 长的排前面，保证最长匹配

    // ─────────────── 数据结构 ───────────────

    data class WordFreq(val word: String, val count: Int, val isTerm: Boolean = false)

    data class TextStats(
        val charCount: Int,
        val cjkCount: Int,
        val wordCount: Int,
        val lineCount: Int,
        val uniqueWords: Int
    )

    // ─────────────── 主算法 ───────────────

    /**
     * @param text 原文
     * @param minCount 最低出现次数
     * @param includeStopWords 是否保留停用词
     * @param userTerms 用户自定义词库（优先级最高，会与内置词库合并）
     * @param nounsOnly 只保留名词（命中词库的词视为名词）
     */
    fun count(
        text: String,
        minCount: Int = 2,
        includeStopWords: Boolean = false,
        userTerms: List<String> = emptyList(),
        nounsOnly: Boolean = false
    ): List<WordFreq> {
        // 合并词库：用户词库优先，长的在前
        val terms = (userTerms.filter { it.isNotBlank() } + BUILTIN_TERMS)
            .map { it.trim() }
            .distinct()
            .sortedByDescending { it.length }

        val freq = HashMap<String, Int>()
        val isTermMap = HashMap<String, Boolean>()

        fun add(word: String, isTerm: Boolean) {
            if (word.isBlank()) return
            if (!includeStopWords && word in CN_STOP) return
            freq[word] = (freq[word] ?: 0) + 1
            if (isTerm) isTermMap[word] = true
        }

        // ── 1. 中文：长词优先贪心匹配 ──
        val cnSegments = text.split(Regex("[^\\u4e00-\\u9fa5]+"))
            .filter { it.isNotBlank() }

        for (seg in cnSegments) {
            var i = 0
            while (i < seg.length) {
                // 尝试从当前位置匹配最长词
                var matched: String? = null
                for (t in terms) {
                    if (t.length <= seg.length - i && seg.startsWith(t, i)) {
                        matched = t
                        break
                    }
                }
                if (matched != null) {
                    add(matched, true)
                    i += matched.length        // 整个词被吃掉，不重复统计
                } else {
                    // 匹配不到长词，当前位置开一个 2-gram
                    if (i + 2 <= seg.length) {
                        val bi = seg.substring(i, i + 2)
                        add(bi, false)
                        i += 1                      // 滑一格继续（2-gram 重叠是正常的）
                    } else {
                        i += 1                      // 剩下单字，跳过
                    }
                }
            }
        }

        // ── 2. 英文单词 ──
        Regex("[A-Za-z]{2,}").findAll(text).forEach { m ->
            val w = m.value.lowercase()
            if (includeStopWords || w !in EN_STOP) {
                freq[w] = (freq[w] ?: 0) + 1
            }
        }

        // ── 3. 过滤 + 排序 ──
        return freq.entries
            .asSequence()
            .filter { it.value >= minCount }
            .filter { e ->
                if (!nounsOnly) true
                else isTermMap[e.key] == true || e.key.matches(Regex("[A-Za-z]+"))
                // 「只要名词」模式下：词库命中的 + 英文单词（英文不做词性判断，全保留）
            }
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenByDescending { isTermMap[it.key] == true }   // 同频时专有词优先
                    .thenByDescending { it.key.length }
                    .thenBy { it.key }
            )
            .map { WordFreq(it.key, it.value, isTermMap[it.key] == true) }
            .toList()
    }

    /** 统计基础信息 */
    fun stats(text: String, userTerms: List<String> = emptyList()): TextStats {
        val cjk = Regex("[\\u4e00-\\u9fa5]").findAll(text).count()
        val words = Regex("[A-Za-z]{2,}").findAll(text).count()
        val lines = if (text.isEmpty()) 0 else text.count { it == '\n' } + 1
        val uniq = count(text, minCount = 1, userTerms = userTerms).size
        return TextStats(text.length, cjk, words, lines, uniq)
    }
}

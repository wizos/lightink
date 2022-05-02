package cn.lightink.reader.model

import cn.lightink.reader.ktx.encode
import cn.lightink.reader.ktx.md5
import cn.lightink.reader.module.EMPTY

/**
 * TXT章节
 * @property index      索引
 * @property title      标题
 */
data class StateChapter(val index: Int, val title: String, var isChecked: Boolean = true, var href: String = EMPTY, var isCached: Boolean = false) {
    val encodeHref: String
        get() = href.encode().let { if (it.length < 0xFF) it else it.md5() }
}

data class CacheChapter(val index: Int, val book: String)
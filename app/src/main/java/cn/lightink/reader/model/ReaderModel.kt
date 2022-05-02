package cn.lightink.reader.model

import android.util.Size
import androidx.viewpager2.widget.ViewPager2
import cn.lightink.reader.ktx.encode
import cn.lightink.reader.ktx.md5
import cn.lightink.reader.model.CellType.*
import cn.lightink.reader.model.PageType.*
import cn.lightink.reader.module.EMPTY

/**
 * 云端主题
 */
//data class CloudTheme(val id: Long, val name: String, val dark: Boolean, var background: Int, var foreground: Int, var control: Int, var content: Int, var secondary: Int, var auxiliary: Int, var author: String, var mipmap: String?, val authorId: Long, @SerializedName("dlCount") val count: Int = 0) {
//
//    var drawable: Drawable? = null
//
//    fun getDrawable(resources: Resources): Drawable? {
//        if (drawable != null) return drawable
//        drawable = mipmap?.let {
//            val bytes = Base64.decode(it, Base64.NO_WRAP)
//            BitmapDrawable(resources, BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
//        }
//        return drawable
//    }
//
//    fun setTheme(theme: Theme) {
//        background = theme.background
//        foreground = theme.foreground
//        content = theme.content
//        secondary = theme.secondary
//        control = theme.control
//        mipmap = theme.mipmap
//    }
//
//    fun toTheme() = Theme(name, dark, background, foreground, control, content, secondary, author, mipmap, authorId == Session.currentUser.value?.uid)
//
//    override fun hashCode() = background + foreground + control + content + secondary + auxiliary
//
//    override fun equals(other: Any?): Boolean {
//        if (other is CloudTheme) return id == other.id
//        return super.equals(other)
//    }
//}

/********************************************************************************************************************************
 * 数据类
 *******************************************************************************************************************************/

/**
 * 章节
 * @property index  章节索引
 * @property title  标题
 * @property href   超链接
 * @property level  缩进等级
 */
data class Chapter(val index: Int, var title: String, var href: String, var level: Int = 0) {

    constructor(index: Int, markdown: String) : this(index,
            title = markdown.substringAfter("[", markdown).substringBeforeLast("](", markdown),
            href = markdown.substringAfterLast("](", EMPTY).substringBeforeLast(")", EMPTY),
            level = markdown.substringBefore("*").count { it == '\t' }
    )

    val encodeHref: String
        get() = href.encode().let { if (it.length < 85) it else it.md5() }

    override fun equals(other: Any?): Boolean {
        if (other is Chapter) return other.index == index
        return super.equals(other)
    }

    override fun hashCode() = index
}

/**
 * @property BOOKLET            分卷
 * @property ILLUSTRATION       插画
 * @property ARTICLE            正文
 * @property END                结尾
 * @property LOADING            加载
 * @property ERROR              错误
 * @property AUTH               登录
 * @property AUTH_BUY           购买
 */
enum class PageType { BOOKLET, ILLUSTRATION, ARTICLE, END, LOADING, ERROR, AUTH, AUTH_BUY }

/**
 * 页
 */
data class Page(val index: Int, val chapter: Chapter, var height: Int = 0, val start: Int = 0, var type: PageType = ARTICLE) {

    val cells = mutableListOf<Cell>()
    var end = start

    fun add(cell: Cell, textHeight: Int = height) {
        cells.add(cell)
        height -= textHeight
        if (cell.type == TEXT) {
            end += cell.value.length
        } else if (cell.type == IMAGE) {
            end += cell.start
        }
    }

    fun getSegment(): String? {
        val segment = StringBuilder()
        cells.filter { it.type == TEXT }.forEach { cell ->
            segment.append(cell.value.replace(Regex("""\s+"""), EMPTY))
        }
        if (segment.length >= 128) {
            return segment.substring(0, 128)
        }
        return null
    }

    fun getTextTotal(): Int {
        var total = 0
        cells.filter { it.type == TEXT }.map { it.value.length }.forEach { total += it }
        return total
    }
}

/**
 * @property TITLE  标题
 * @property TEXT   文本
 * @property IMAGE  图片
 */
enum class CellType { TITLE, TEXT, IMAGE }

data class Cell(val type: CellType, val value: String, var image: String = EMPTY, val start: Int = 0, var size: Size = Size(0, 0)) {

    companion object {
        fun title(text: String) = Cell(TITLE, text)
        fun text(text: String, start: Int) = Cell(TEXT, text, EMPTY, start)
        fun image(text: String, image: String, start: Int, size: Size) = Cell(IMAGE, text, image, start, size = size)
    }
}

data class SpeechCell(var value: String, val chapter: Chapter, val progress: Int) {

    val utteranceId: String
        get() = "${chapter.index}-${chapter.title}-$progress"

}

/**
 * 阅读器显示
 */
class Display {
    //翻页方式
    var orientation = ViewPager2.ORIENTATION_HORIZONTAL
    //容器宽
    var width = 0
    //容器高
    var height = 0
    //横向边距
    var horizontal = 0
    //上边距
    var top = 0
    //下边距
    var bottom = 0
    //固定标题高度
    var fixed = 0
    //行高
    var lineHeight = 0
    //行间距
    var lineSpacing = 1F
    //行间距高度
    var lineSpacingHeight = 0
    //最后一行包含行高
    var hasEndLineSpacing = false
    //内容距离标题
    var titleSpacingHeight = 0
    //段落间隔启用
    var paragraphSpacing = 0
    //说明文字高度
    var figcaption = 0
}

/**
 * 屏蔽元素
 * @property key        屏蔽词
 * @property replace    替换内容
 * @property isRegex    正则
 */
data class Purify(val key: String, val replace: String, val isRegex: Boolean)
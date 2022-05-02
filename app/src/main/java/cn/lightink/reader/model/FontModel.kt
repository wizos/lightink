package cn.lightink.reader.model

import android.graphics.Typeface
import cn.lightink.reader.module.EMPTY
import java.io.File

/**
 * 字体
 * @property display    名字
 * @property path       路径
 */
data class Font(val display: String, val path: File) {

    val typeface: Typeface
        get() = if (path.exists()) Typeface.createFromFile(path) else Typeface.DEFAULT

    override fun equals(other: Any?): Boolean {
        if (other is Font) return other.path.absolutePath == path.absolutePath
        return super.equals(other)
    }
}

/**
 * 系统字体
 */
@Suppress("unused")
enum class SystemFont(val display: String, val link: String = EMPTY, val demo: String = EMPTY, val md5: String = EMPTY) {
    System("系统字体"),
    SourceHanSans("思源黑体", "/book/font/SourceHanSans.ttf", "fonts/SourceHanSans.ttf", "57d38b974487914900393f74e3bd4660"),
    SourceHanSerif("思源宋体", "/book/font/SourceHanSerif.ttf", "fonts/SourceHanSerif.ttf", "d492922f2af759711709fd169b02bd10"),
    YangRenDongZhuShi("杨任东竹石体", "/book/font/YangRenDongZhuSui.ttf", "fonts/YangRenDongZhuShi.ttf", "a0d2cd9e0cdfb511e2b9978bfd47ca35")
}

/**
 * 获取TTF名字用到的类
 */
data class TableDirectory(var name: String? = null, var checkSum: Int = 0, var offset: Int = 0, var length: Int = 0)

data class NameTableHeader(val fSelector: Int, val nRCount: Int, val storageOffset: Int)

data class NameRecord(var platformID: Int = 0, var encodingID: Int = 0, var languageID: Int = 0, var nameID: Int = 0, var stringLength: Int = 0, var stringOffset: Int = 0)
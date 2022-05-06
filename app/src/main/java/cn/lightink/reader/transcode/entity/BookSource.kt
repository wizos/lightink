package cn.lightink.reader.transcode.entity

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import kotlinx.serialization.Serializable

/**
 * 书源
 */
@Serializable
data class BookSource(
    val name: String,
    val url: String,
    val version: Int,
    val search: Search,
    val detail: Detail,
    val catalog: Catalog,
    val chapter: Chapter
) {

    /**
     * 搜索
     * @property url 搜索地址
     * @property charset 字符集 默认UTF8
     * @property list 搜索结果列表
     * @property name 书名
     * @property author 作者
     * @property cover 封面
     * @property detail 详情地址
     */
    @Serializable
    data class Search(
        val url: String,
        val charset: String = "UTF-8",
        val list: String,
        val name: String,
        val detail: String
    )

    /**
     * 详情
     * @property name 书名
     * @property author 作者
     * @property cover 封面
     * @property category 分类
     * @property words 字数
     * @property summary 简介
     * @property status 连载状态 0 完结 1 连载
     * @property update 更新时间
     * @property lastChapter 最新章节
     * @property catalog 目录地址
     */
    @Serializable
    data class Detail(val name: String = "", val catalog: String = "")

    /**
     * 目录
     * @property list 列表
     * @property orderBy 排序
     * @property booklet 分卷
     * @property name 章节名
     * @property chapter 章节地址
     * @property page 分页
     */
    @Serializable
    data class Catalog(
        val list: String,
        val orderBy: Int = 0,
        val name: String,
        val chapter: String = "",
        val booklet: Booklet? = null,
        val page: String = ""
    )

    /**
     * 分卷
     * @property name 卷名
     * @property list 章节列表
     */
    @Serializable
    data class Booklet(val name: String, val list: String)

    /**
     * 章节
     * @property content 内容
     * @property filter 过滤选择器
     * @property purify 清除内容
     * @property page 分页
     */
    @Serializable
    data class Chapter(
        val content: String = "",
        val filter: List<String> = listOf(),
        val purify: List<String> = listOf(),
        val page: String = ""
    )

}

/**
 * 图书资源
 */
@Parcelize
data class BookResource(
    val sourceName: String,
    val sourceUrl: String,
    val sourceContent: String,
    val type: String,
    val urls: List<String>,
    val chapters: List<Chapter>
) : Parcelable {

    val lastChapter get() = chapters.lastOrNull()?.name.orEmpty()

    companion object {
        const val JSON = "json"
        const val JAVA_SCRIPT = "java_script"
    }

}
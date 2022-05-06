package cn.lightink.reader.transcode.entity

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import kotlinx.serialization.Serializable

/**
 * 书源信息
 * @property name 书名
 * @property url 域名
 * @property version 版本
 * @property authorization 授权页面
 * @property cookies Cookie
 * @property rank 排行榜内容
 */
@Serializable
data class BookSourceInfo(val name: String, val url: String, val version: Int, val authorization: String = "", val cookies: List<String> = listOf(), val ranks: List<Rank> = listOf())

/**
 * 搜索结果
 * @property name 书名
 * @property author 作者
 * @property cover 封面
 * @property detail 详情地址
 */
@Serializable
data class SearchResult(val name: String, val author: String = "", val cover: String = "", val detail: String)

/**
 * 图书详情
 * @property category 分类
 * @property status 连载状态
 * @property words 字数
 * @property summary 简介
 * @property update 更新时间
 * @property lastChapter 最新章节
 * @property catalog 目录地址
 */
@Serializable
data class BookDetail(val category: String = "", val status: String = "", val words: String = "", val summary: String = "", val update: String = "", val lastChapter: String = "", val catalog: String = "")

/**
 * 章节
 * @property name 章节名
 * @property url 章节地址
 * @property vip 付费章节
 */
@Parcelize
@Serializable
data class Chapter(val name: String, val url: String = "", val vip: Boolean = false) : Parcelable

/**
 * 个人中心
 * @property value 基础信息
 * @property extra 拓展信息
 */
@Serializable
data class Profile(val basic: List<Basic>, val extra: List<Extra> = listOf()) {

    val nickname get() = basic.firstOrNull { it.name == "账号" }?.value.orEmpty()

    val permissions get() = extra.filter { it.type == Extra.TYPE_PERMISSION && it.method.isNotBlank() }

    val features get() = extra.filter { it.type == Extra.TYPE_BOOKS && it.method.isNotBlank() }

}

/**
 * 值
 * @property name 名称
 * @property value 值
 * @property url 链接
 */
@Serializable
data class Basic(val name: String, val value: String, val url: String = "")

/**
 * 拓展
 * @property name 名称
 * @property type 类型
 * @property method 方法
 * @property times 频率
 */
@Parcelize
@Serializable
data class Extra(val name: String, val type: String, val method: String, val times: String = "") : Parcelable {

    companion object {
        const val TYPE_PERMISSION = "permission"
        const val TYPE_BOOKS = "books"
    }

}

/**
 * 图书分页
 */
@Serializable
data class PagingBooks(val books: List<SearchResult> = listOf(), val end: Boolean = true)

/**
 * 排行榜
 * @property title 标题
 * @property categories 分类
 */
@Serializable
data class Rank(val title: KeyValue, val categories: List<KeyValue> = listOf())

@Serializable
data class KeyValue(val key: String, val value: String)
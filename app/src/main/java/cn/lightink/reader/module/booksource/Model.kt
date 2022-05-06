package cn.lightink.reader.module.booksource

import cn.lightink.reader.ktx.md5
import cn.lightink.reader.model.BookSource
import cn.lightink.reader.model.MPMetadata
import cn.lightink.reader.module.BOOK_STATE_END
import cn.lightink.reader.module.BOOK_STATE_IDLE
import cn.lightink.reader.module.EMPTY

data class BookSourceJson(val name: String, val url: String, val version: Int, val search: Search, val detail: Detail = Detail(), val catalog: Catalog = Catalog(), val chapter: Chapter = Chapter(), val auth: Auth? = null, val rank: List<Rank> = emptyList()) {

    data class Search(val url: String = EMPTY, val charset: String = "UTF-8", val list: String = EMPTY, val name: String = EMPTY, val author: String = EMPTY, val cover: String = EMPTY, val summary: String = EMPTY, val detail: String = EMPTY)

    data class Detail(val name: String = EMPTY, val author: String = EMPTY, val cover: String = EMPTY, val summary: String = EMPTY, val status: String = EMPTY, val update: String = EMPTY, val lastChapter: String = EMPTY, val catalog: String = EMPTY)

    data class Catalog(val list: String = EMPTY, val orderBy: Int = 0, val name: String = EMPTY, val chapter: String = EMPTY, val booklet: Booklet? = null, val page: String = EMPTY)

    data class Booklet(val name: String = EMPTY, val list: String = EMPTY)

    data class Chapter(val content: String = EMPTY, val filter: List<String> = listOf(), val purify: List<String> = listOf(), val page: String = EMPTY)

    data class Auth(val login: String = EMPTY, val cookie: String = EMPTY, val header: String = EMPTY, val params: String = EMPTY, val verify: String = EMPTY, val logged: String = EMPTY, val vip: String = EMPTY, val buy: String = EMPTY)

    data class Rank(val title: String = EMPTY, val url: String = EMPTY, val page: Int = -1, val unit: Int = 1, val size: Int = 20, val categories: List<Category> = emptyList(), val list: String = EMPTY, val name: String = EMPTY, val author: String = EMPTY, val cover: String = EMPTY, val summary: String = EMPTY, val detail: String = EMPTY)

    data class Category(val key: String, val value: String)
}

data class BookSourceResponse(val url: String, val body: Any)

/**
 * 换源搜索结果
 */
data class BookSourceSearchResponse(val book: DetailMetadata, val source: BookSource, val chapters: List<Chapter>)

data class SearchMetadata(var name: String, var author: String, var cover: String, var summary: String, var detail: String) {
    val objectId: String
        get() = "$name:$author".md5()
}

data class DetailMetadata(var name: String, var author: String, var cover: String, var summary: String, var status: String, var update: String, var lastChapter: String, var url: String, var catalog: Any) {
    val objectId: String
        get() = "$name:$author".md5()

    fun toSearchMetadata() = SearchMetadata(name, author, cover, summary, url)

    fun toMetadata() = MPMetadata(name, author, url, state = if (status.contains("完")) BOOK_STATE_END else BOOK_STATE_IDLE)
}

data class Chapter(var name: String, var url: String, var useLevel: Boolean)

data class Query(val query: String, var match: String? = null, var euqal: String? = null, var replace: String? = null, var decrypt: String? = null) {

    companion object {
        const val OPERATOR = "->"
        const val ATTR = "@attr->"
        private const val MATCH = "@match->"
        private const val EQUAL = "@equal->"
        private const val REPLACE = "@replace->"
        private const val DECRYPT = "@decrypt->"

        fun build(expression: String): Query {
            val operators = Regex("@(match|equal|replace|decrypt)->").findAll(expression).toList()
            return if (operators.isNotEmpty()) {
                val query = Query(expression.substring(0, operators.first().range.first))
                operators.forEachIndexed { index, matchResult ->
                    val endIndex = if (index < operators.lastIndex) operators[index + 1].range.first else expression.length
                    val value = expression.substring(matchResult.range.last + 1, endIndex)
                    when (matchResult.value) {
                        MATCH -> query.match = value
                        EQUAL -> query.euqal = value
                        REPLACE -> query.replace = value
                        DECRYPT -> query.decrypt = value
                    }
                }
                query
            } else {
                Query(expression)
            }
        }
    }

}
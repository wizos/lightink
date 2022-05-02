package cn.lightink.reader.module

import cn.lightink.reader.ktx.wrap
import cn.lightink.reader.model.Cover
import cn.lightink.reader.model.YouSuuResult
import cn.lightink.reader.net.Http
import org.jsoup.Jsoup

/**
 * 图书封面模块
 * 联网查询 豆瓣 or 优书 封面地址
 */
object BookCover {

    /**
     * 查询封面
     * @param name 书名
     * @param source 指定查询源
     */
    suspend fun query(name: String, source: CoverSource): List<Cover> {
        return when (source) {
            CoverSource.DOUBAN -> queryByDouban(name)
            CoverSource.YOUSUU -> queryByYousuu(name)
        }
    }

    /**
     * 查询豆瓣网封面
     */
    private fun queryByDouban(name: String): List<Cover> {
        return try {
            val document = Jsoup.connect("https://m.douban.com/search/?query=$name&type=book").get()
            val elements = document.body().select("ul.search_results_subjects > li > a").filter { it.selectFirst("span.subject-title").text().wrap().contains(name.wrap()) }
            elements.map { Cover(it.select("img").attr("src")) }.filter { it.url.contains("book-default-lpic").not() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 查询优书网封面
     */
    private suspend fun queryByYousuu(name: String): List<Cover> {
        return try {
            val response = Http.get<YouSuuResult>("http://www.yousuu.com/api/search?type=title&value=$name").data
            return response?.data?.books?.filter { it.title == name }?.map { Cover(it.cover, it.author) } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

}

enum class CoverSource {
    DOUBAN, YOUSUU
}
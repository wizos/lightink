package cn.lightink.reader.transcode

import android.accounts.NetworkErrorException
import android.webkit.URLUtil
import cn.lightink.reader.transcode.ContentParser.chinese
import cn.lightink.reader.transcode.ContentParser.singleLine
import cn.lightink.reader.transcode.NetworkBridge.charset
import cn.lightink.reader.transcode.entity.*
import cn.lightink.reader.transcode.entity.BookResource.Companion.JSON
import cn.lightink.reader.transcode.ktx.decodeJson
import cn.lightink.reader.transcode.Html
import com.github.promeg.pinyinhelper.Pinyin
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.helper.HttpConnection
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Selector
import java.io.File
import java.net.SocketException
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.regex.PatternSyntaxException

/**
 * 转码器
 * @property bookSource 书源
 */
class JsonTranscoder(private val bookSourceJson: String) {

    val bookSource = try {
        bookSourceJson.decodeJson<BookSource>()
    } catch (e: Exception) {
        null
    }

    /**
     * 搜索
     */
    @Throws(NetworkErrorException::class)
    suspend fun search(bookName: String): BookResource? = bookSource?.run {
        val url = search.url.replace("\${key}", URLEncoder.encode(bookName, search.charset))
        val response = execute(url)
        val warpBookName = bookName.chinese()
        val searchResults = response.findList(search.list)
        loop@ for (item in searchResults) {
            if (item.findValue(search.name).chinese() != warpBookName) continue@loop
            return@run parserDetail(item.findValue(search.detail, true))
        }
        if (searchResults.isEmpty() && response.findValue(detail.name).chinese() == warpBookName) {
            if (detail.catalog.isNotBlank()) {
                parserDetail(response.findValue(detail.catalog, true))
            } else {
                parserCatalog(url, response)
            }
        } else null
    }

    /**
     * 目录
     */
    suspend fun catalog(url: String): BookResource? = bookSource?.run {
        return@run parserCatalog(url, execute(url))
    }

    /**
     * 内容
     */
    suspend fun content(
        chapter: Chapter,
        catalog: List<Chapter>,
        output: File?,
        buffer: StringBuilder = StringBuilder()
    ): String? = bookSource?.run {
        if (chapter.url.isBlank()) return "## ${chapter.name}"
        val response = execute(chapter.url)
        val content = when (response.body) {
            is Document, is Element -> findHtmlContent(
                response,
                if (this.chapter.content.isNotBlank()) this.chapter.content else Html.BODY,
                output
            )
            else -> try {
                JSONObject(response.body.toString())
                findJsonContent(response, output)
            } catch (e: JSONException) {
                response.body.toString()
            }
        }
        buffer.append(content)
        //检查下一页
        if (this.chapter.page.isNotBlank() && content?.isNotBlank() == true) {
            val page = response.findValue(this.chapter.page, true)
            if (URLUtil.isNetworkUrl(page) && page != response.url && catalog.none { page == it.url }) {
                val length = buffer.length
                loop@ for (index in 1 until length) {
                    if (buffer[length - index] == '\n') continue@loop
                    if (Pinyin.isChinese(buffer[length - index])) {
                        buffer.delete(length - index + 1, length)
                    }
                    break@loop
                }
                return content(Chapter(chapter.name, page), catalog, output, buffer)
            }
        }
        //检查净化列表
        var markdown = ""
        if (buffer.isNotBlank()) {
            markdown = "## ${chapter.name}\n$buffer"
            try {
                bookSource.chapter.purify.forEach { regex ->
                    markdown = markdown.replace(Regex(regex), "")
                }
            } catch (e: PatternSyntaxException) {
                //忽略可能存在错误的正则表达式
            }
            //格式化内容
            markdown = markdown.replace("""\n+(\s|\u3000)+""".toRegex(), "\n")
            markdown = markdown.replace("""\n+""".toRegex(), "\n")
        }
        return markdown.trim()
    }

    /**
     * 读取HTML格式内容
     */
    private fun findHtmlContent(
        response: TranscodeResponse,
        query: String,
        output: File?
    ): String? = bookSource?.run {
        //提取过滤元素或文本
        val filterElements = mutableSetOf<String>()
        val filterTexts = mutableSetOf<String>()
        chapter.filter.forEach { filter ->
            when {
                filter.startsWith("@") -> filterElements.add(filter.removePrefix("@"))
                filter.startsWith("%") -> filterTexts.add(filter.removePrefix("%"))
                else -> filterElements.add(filter)
            }
        }
        //过滤元素并提取内容
        val buffer = StringBuilder()
        try {
            val elements = response.findList(query)
            elements.forEachIndexed { index, child ->
                val element = child.body as Element
                element.setBaseUri(response.url)
                filterElements.forEach { if (it.isNotBlank()) element.select(it).remove() }
                buffer.append(ContentParser.parseHtml(element, output))
                //最后一个元素不追加段落标记，某些网站正文分页可能会从段落中间开始
                if (index < elements.size - 1) buffer.append("\n")
            }
        } catch (e: Selector.SelectorParseException) {
            //可能存在意外之外Query引起异常
        }
        //过滤文本
        var content = buffer.toString()
        filterTexts.forEach { text ->
            if (text.isNotBlank()) content = buffer.replace(Regex(text), "")
        }
        return content
    }

    /**
     * 读取Json格式内容
     */
    private fun findJsonContent(response: TranscodeResponse, output: File?): String? =
        bookSource?.run {
            var content = if (chapter.content.isNotBlank()) {
                response.findValue(chapter.content)
            } else response.body as String
            if (content.contains("""<[^>]+>""".toRegex())) {
                content = content.replace("\n", "<br>")
                content = findHtmlContent(
                    TranscodeResponse(
                        response.url,
                        Jsoup.parseBodyFragment(content)
                    ), Html.BODY, output
                ).orEmpty()
            }
            return content
        }

    /**
     * 解析详情页
     */
    private suspend fun parserDetail(url: String): BookResource? = bookSource?.run {
        if (!URLUtil.isNetworkUrl(url)) return@run null
        val response = execute(url)
        if (detail.catalog.isEmpty()) return@run parserCatalog(url, response)
        val catalog = response.findValue(detail.catalog, true)
        if (!URLUtil.isNetworkUrl(catalog)) return@run null
        return@run parserCatalog(catalog, execute(catalog))
    }

    /**
     * 检查图书更新
     * @param url 目录尾页地址
     * @param latest 最新章节名
     */
    suspend fun checkUpdate(
        url: String, latest: String,
        urls: MutableSet<String> = mutableSetOf()
    ): Pair<String, Boolean>? = bookSource?.run {
        val response = execute(url).apply {
            urls.add(url)
        }
        //检查下一页
        if (catalog.page.isNotBlank()) {
            val nextUrl = response.findValue(catalog.page, true)
            if (URLUtil.isNetworkUrl(nextUrl) && !urls.contains(nextUrl)) {
                val current = url.filter { it.isDigit() }.toLongOrNull()
                val next = nextUrl.filter { it.isDigit() }.toLongOrNull()
                if (current == null || next == null || next > current) {
                    return@run checkUpdate(nextUrl, latest, urls)
                }
            }
        }
        val items = response.findList(catalog.list)
        val chapters = findBooklet(items) ?: return@run url to false
        if (chapters.last().name == latest) return@run url to false
        return@run url to true
    }

    /**
     * 解析目录页
     */
    private suspend fun parserCatalog(
        catalogUrl: String,
        response: TranscodeResponse,
        items: MutableList<TranscodeResponse> = mutableListOf(),
        urls: LinkedHashSet<String> = LinkedHashSet()
    ): BookResource? = bookSource?.run {
        urls.add(response.url)
        items.addAll(response.findList(catalog.list))
        //检查下一页
        if (catalog.page.isNotBlank()) {
            val page = response.findValue(catalog.page, true)
            if (URLUtil.isNetworkUrl(page) && !urls.contains(page)) {
                return parserCatalog(catalogUrl, execute(page), items, urls)
            }
        }
        val chapters = findBooklet(items) ?: return@run null
        if (chapters.isEmpty()) return@run null
        return@run BookResource(name, url, bookSourceJson, JSON, urls.toList(), chapters)
    }

    /**
     * 查找分卷
     * @param list  章节列表源数据
     */
    private fun findBooklet(list: List<TranscodeResponse>): List<Chapter>? = bookSource?.run {
        val chapters = mutableListOf<Chapter>()
        list.let { if (catalog.orderBy % 2 == 0) it.asReversed() else it }.forEach { item ->
            if (catalog.booklet != null) {
                //存在分卷
                val name = item.findValue(catalog.booklet.name).singleLine()
                val booklet = if (name.isNotBlank()) Chapter(name, "", false) else null
                item.findList(catalog.booklet.list)
                    .let { if (bookSource.catalog.orderBy !in 1..2) it.asReversed() else it }
                    .forEach { child ->
                        findChapter(child, chapters)
                    }
                booklet?.run { chapters.add(0, this) }
            } else {
                //不存在分卷
                findChapter(item, chapters)
            }
        }
        return chapters
    }

    /**
     * 查找章节
     * @param response  源数据
     * @param chapters  章节列表
     */
    private fun findChapter(response: TranscodeResponse, chapters: MutableList<Chapter>) =
        bookSource?.run {
            var name = response.findValue(catalog.name)
            val wrap = name.replace(Regex(CHAPTER_NAME), "").trim()
            if (wrap.isNotBlank()) name = wrap
            //章节名必不为空
            if (name.isNotBlank()) {
                val chapter = Chapter(name, response.findValue(catalog.chapter, true))
                //章节链接必不为空且不能存在重复章节
                if (chapter.url.isNotBlank() && !chapters.contains(chapter)) {
                    chapters.add(0, chapter)
                }
            }
        }

    /**
     * 请求URL
     */
    private suspend fun execute(url: String, retry: Boolean = true): TranscodeResponse {
        try {
            val response = NetworkBridge.execute(buildRequest(url))
            if (response.isSuccessful) {
                val contentType = response.header("Content-Type") ?: "text/plain"
                val body = runCatching {
                    response.body?.bytes()
                }.getOrNull() ?: ByteArray(0)
                return onResponse(url, body, contentType)
            } else throw TranscodeException(response.code, response.message)
        } catch (e: Throwable) {
            //网络异常时重试1次
            if (retry && (e is SocketException || e is TranscodeException)) {
                return execute(url, false)
            } else throw e
        }
    }

    /**
     * 解析响应内容体
     * @param url           请求地址
     * @param body          响应内容
     * @param contentType   内容类型
     */
    private fun onResponse(url: String, body: ByteArray, contentType: String): TranscodeResponse {
        var document = String(body, body.charset() ?: charset("UTF-8"))
        return when {
            contentType.contains("tar") -> TranscodeResponse(
                url,
                document.replace(
                    Regex("""(info\.txt|\u0000).+\u0000"""),
                    ""
                ).trim()
            )
            contentType.contains(Regex("html|octet-stream|xml")) -> {
                try {
                    val head = Jsoup.parse(document).head()
                    var attr = head.selectFirst("meta[charset]")?.attr("charset")
                    if (attr.isNullOrBlank()) {
                        attr = Regex("(?<=charset=).+").find(
                            head.selectFirst("meta[content*=charset]")
                                ?.attr("content").orEmpty()
                        )?.value
                    }
                    if (attr?.isNotBlank() == true) {
                        document = String(body, Charset.forName(attr))
                    }
                } catch (e: Exception) {
                    //忽略异常，不会有任何影响
                }
                TranscodeResponse(url, Jsoup.parse(document.replace("\r\n", "<br>")))
            }
            else -> TranscodeResponse(url, document)
        }
    }

    /**
     * 构造请求
     */
    private fun buildRequest(url: String) = Request.Builder().apply {
        val operators = Regex("@.+?->").findAll(url).toList()
        url(if (operators.isNotEmpty()) url.substring(0, operators.first().range.first) else url)
        val headers = Headers.Builder()
        val requestBody = StringBuilder()
        if (operators.isNotEmpty()) operators.forEachIndexed { index, operator ->
            val endIndex =
                if (index < operators.lastIndex) operators[index + 1].range.first else url.length
            val params = url.substring(operator.range.last + 1, endIndex)
            when (operator.value) {
                POST -> requestBody.append(params)
                HEADER -> headers.add(params)
            }
        }
        if (headers.get("User-Agent").isNullOrBlank()) {
            headers.add("User-Agent", HttpConnection.DEFAULT_UA)
        }
        headers(headers.build())
        if (operators.any { it.value == POST }) {
            val mediaType = try {
                JSONObject(requestBody.toString()).let { "application/json;charset=utf-8" }
            } catch (e: Exception) {
                "application/x-www-form-urlencoded;charset=utf-8"
            }
            post(requestBody.toString().toRequestBody(mediaType.toMediaType()))
        } else {
            get()
        }
    }.build()

    //HEADER标签声明
    private val HEADER = "@header->"

    //POST标签声明
    private val POST = "@post->"

    //章节名
    private val CHAPTER_NAME = """([（(].*(月票|推荐|订阅|收藏|感谢|更).*[）)])$"""

}
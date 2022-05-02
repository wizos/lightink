package cn.lightink.reader.module.booksource

import android.net.Uri
import android.util.Base64
import android.webkit.URLUtil
import androidx.core.text.HtmlCompat
import cn.lightink.reader.ktx.*
import cn.lightink.reader.model.Book
import cn.lightink.reader.module.*
import cn.lightink.reader.net.Http
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.TypeRef
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.select.Elements
import org.jsoup.select.Selector
import java.io.File
import java.io.FileNotFoundException
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.PatternSyntaxException

class BookSourceParser(val bookSource: BookSourceJson) {

    private val dateFormat by lazy { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINESE) }

    /**
     * 搜索
     */
    fun search(key: String): List<SearchMetadata> {
        val url = bookSource.search.url.replace("\${key}", key.encode(bookSource.search.charset))
        val response = BookSourceInterpreter.execute(url, bookSource.auth) ?: return emptyList()
        val results = findList(response, bookSource.search.list).map { findSearchMetadata(it) }
        if (results.isNullOrEmpty()) {
            val metadata = findDetailMetadata(response.url, response)
            return if (metadata.name.contains(key, true) || metadata.author.contains(key, true)) listOf(metadata.toSearchMetadata()) else emptyList()
        }
        return results.filter { it.name.contains(key, true) || it.author.contains(key, true) }
    }

    /**
     * 详情
     */
    fun findDetail(metadata: SearchMetadata): DetailMetadata? {
        val response = BookSourceInterpreter.execute(metadata.detail, bookSource.auth) ?: return null
        val detail = findDetailMetadata(metadata.detail, response)
        if (detail.name.isBlank()) detail.name = metadata.name
        if (detail.author.isBlank()) detail.author = metadata.author
        if (detail.cover.isBlank()) detail.cover = metadata.cover
        if (detail.summary.isBlank()) detail.summary = metadata.summary
        return detail
    }

    /**
     * 目录
     */
    fun findCatalog(metadata: DetailMetadata, chapters: MutableList<BookSourceResponse> = mutableListOf(), urls: MutableList<String> = mutableListOf()): List<Chapter> {
        val response = if (metadata.catalog is BookSourceResponse) metadata.catalog as BookSourceResponse else {
            urls.add(metadata.catalog as String)
            BookSourceInterpreter.execute(metadata.catalog as String, bookSource.auth) ?: return emptyList()
        }
        chapters.addAll(findList(response, bookSource.catalog.list))
        //检查下一页
        if (bookSource.catalog.page.isNotBlank()) {
            val page = findValue(response, bookSource.catalog.page, true)
            if (URLUtil.isNetworkUrl(page) && !urls.contains(page)) {
                return findCatalog(metadata.copy().apply { catalog = page }, chapters, urls)
            }
        }
        return findBooklet(chapters)
    }

    /**
     * 查找分卷
     * @param list  章节列表源数据
     *
     */
    private fun findBooklet(list: MutableList<BookSourceResponse>): List<Chapter> {
        val chapters = mutableListOf<Chapter>()
        list.let { if (bookSource.catalog.orderBy % 2 == 0) it.asReversed() else it }.forEach { item ->
            if (bookSource.catalog.booklet != null) {
                //存在分卷
                val name = findValue(item, bookSource.catalog.booklet.name).singleLine()
                val booklet = Chapter(if (name.isNotBlank()) name else "正文", EMPTY, false)
                findList(item, bookSource.catalog.booklet.list).let { if (bookSource.catalog.orderBy !in 1..2) it.asReversed() else it }.forEach { child ->
                    findChapter(child, true, chapters)
                }
                chapters.add(0, booklet)
            } else {
                //不存在分卷
                findChapter(item, false, chapters)
            }
        }
        return chapters
    }

    /**
     * 查找章节
     * @param response  源数据
     * @param useLevel  子章节
     * @param chapters  章节列表
     */
    private fun findChapter(response: BookSourceResponse, useLevel: Boolean, chapters: MutableList<Chapter>) {
        val name = findValue(response, bookSource.catalog.name)
        //章节名必不为空
        if (name.isNotBlank()) {
            val chapter = Chapter(name, findValue(response, bookSource.catalog.chapter, true), useLevel)
            //章节链接必不为空且不能存在重复章节
            if (chapter.url.isNotBlank() && !chapters.contains(chapter)) {
                chapters.add(0, chapter)
            }
        }
    }

    /**
     * 搜索最新章节
     */
    fun findTheLastChapter(book: Book): BookSourceSearchResponse? {
        val list = search(book.name)
        var searchMetadata = list.firstOrNull { it.name == book.name && it.author == book.author }
        if (searchMetadata == null) searchMetadata = list.firstOrNull { it.name == book.name } ?: return null
        //读取详情
        val response = BookSourceInterpreter.execute(searchMetadata.detail, bookSource.auth) ?: return null
        val metadata = findDetailMetadata(searchMetadata.detail, response)
        //读取目录
        val catalog = findCatalog(metadata)
        metadata.lastChapter = catalog.lastOrNull()?.name.orEmpty()
        if (metadata.lastChapter.isBlank()) return null
        return BookSourceSearchResponse(metadata, bookSource, catalog)
    }

    /**
     * 正文
     */
    fun findContent(url: String, output: String = EMPTY, buffer: StringBuilder = StringBuilder()): String {
        val response = BookSourceInterpreter.execute(url, bookSource.auth) ?: return GET_FAILED_NET_THROWABLE
        //vip章节
        if (bookSource.auth?.vip?.isNotBlank() == true && findValue(response, bookSource.auth.vip) == "true") {
            //未购买
            if (bookSource.auth.buy.isBlank() || findValue(response, bookSource.auth.buy) != "true") {
                return if (verify()) GET_FAILED_INVALID_AUTH_BUY else GET_FAILED_INVALID_AUTH
            }
        }
        val content = when (response.body) {
            is Document, is Element -> findHtmlContent(response, if (bookSource.chapter.content.isNotBlank()) bookSource.chapter.content else BODY, output)
            is String -> if (response.body.isJson()) {
                findJsonContent(response, output)
            } else {
                response.body.toString()
            }
            else -> response.body.toString()
        }
        buffer.append(content)
        //检查下一页
        if (bookSource.chapter.page.isNotBlank() && content.isNotBlank()) {
            val page = findValue(response, bookSource.chapter.page, true)
            if (URLUtil.isNetworkUrl(page) && page != response.url && page != url) {
                return findContent(page, output, buffer)
            }
        }
        //检查净化列表
        var markdown = buffer.toString()
        try {
            bookSource.chapter.purify.forEach { regex ->
                markdown = markdown.replace(Regex(regex), EMPTY)
            }
        } catch (e: PatternSyntaxException) {
            //忽略可能存在错误的正则表达式
        }
        //格式化内容
        markdown = markdown.replace("""\n+(\s|\u3000)+""".toRegex(), "\n")
        markdown = markdown.replace("""\n+""".toRegex(), "\n")
        return markdown.trim()
    }

    /**
     * 更新
     */
    fun checkUpdate(book: Book): String? {
        //读取详情
        val response = BookSourceInterpreter.execute(book.link, bookSource.auth) ?: return null
        val metadata = findDetailMetadata(book.link, response)
        //完结
        if (book.state != BOOK_STATE_END && metadata.status.contains("完")) {
            book.state = BOOK_STATE_END
            Room.book().update(book)
        }
        //读取目录
        val chapters = findCatalog(metadata)
        val theLastChapter = chapters.lastOrNull()?.name.orEmpty()
        //查询完成处理
        if (theLastChapter.isNotBlank() && theLastChapter != book.lastChapter) {
            //有更新
            val catalog = StringBuilder()
            chapters.apply { book.catalog = size }.forEach { chapter ->
                if (chapter.useLevel) catalog.append(MP_CATALOG_INDENTATION)
                catalog.append("* [${chapter.name}](${chapter.url})$MP_ENTER")
            }
            //写进目录
            try {
                File(book.path, MP_FILENAME_CATALOG).writeText(catalog.toString())
                book.state = BOOK_STATE_UPDATE
                book.lastChapter = theLastChapter
                Room.book().update(book)
            } catch (e: FileNotFoundException) {
                //图书已删除
            }
            return theLastChapter
        }
        return null
    }

    /**
     * 排行榜
     */
    fun queryRank(url: String, rank: BookSourceJson.Rank): List<SearchMetadata> {
        val response = BookSourceInterpreter.execute(url, bookSource.auth) ?: return emptyList()
        return findList(response, if (rank.list.isNotBlank()) rank.list else bookSource.search.list).map { findRankMetadata(it, rank) }
    }

    /**
     * 搜索封面
     */
    fun searchCover(bookName: String): String {
        val url = bookSource.search.url.replace("\${key}", bookName.encode(bookSource.search.charset))
        val response = BookSourceInterpreter.execute(url, bookSource.auth) ?: return EMPTY
        val result = findList(response, bookSource.search.list).map { findSearchMetadata(it) }
        //针对搜索结果仅一个自动跳转详情的情况
        if (result.isNullOrEmpty()) {
            val metadata = findDetailMetadata(response.url, response)
            return if (metadata.name == bookName) metadata.cover else EMPTY
        }
        return result.firstOrNull { it.name == bookName }?.cover ?: EMPTY
    }

    /**
     * 验证登录
     */
    fun verify(): Boolean {
        if (bookSource.auth?.verify == null) return false
        val response = BookSourceInterpreter.execute(bookSource.auth.verify, bookSource.auth) ?: return false
        return findValue(response, bookSource.auth.logged) == "true"
    }
    /*******************************************************************************************************************************
     * 查值
     ******************************************************************************************************************************/
    /**
     * 查找搜索结果
     */
    private fun findSearchMetadata(response: BookSourceResponse) = SearchMetadata(
            name = HtmlCompat.fromHtml(findValue(response, bookSource.search.name), HtmlCompat.FROM_HTML_MODE_LEGACY).toString(),
            author = findValue(response, bookSource.search.author),
            cover = findValue(response, bookSource.search.cover, true),
            summary = findValue(response, bookSource.search.summary),
            detail = findValue(response, bookSource.search.detail, true)
    )

    private fun findDetailMetadata(url: String, response: BookSourceResponse) = DetailMetadata(
            name = findValue(response, bookSource.detail.name),
            author = findValue(response, bookSource.detail.author),
            cover = findValue(response, bookSource.detail.cover, true),
            summary = findValue(response, bookSource.detail.summary),
            status = findValue(response, bookSource.detail.status),
            update = findValue(response, bookSource.detail.update).let {
                if (it.toLongOrNull() != null) dateFormat.format(if (it.length == 10) it.toLong() * 1000 else it.toLong()) else it
            },
            lastChapter = findValue(response, bookSource.detail.lastChapter),
            url = url,
            catalog = if (bookSource.detail.catalog.isNotBlank()) findValue(response, bookSource.detail.catalog, true) else response
    )

    private fun findRankMetadata(response: BookSourceResponse, rank: BookSourceJson.Rank) = SearchMetadata(
            name = HtmlCompat.fromHtml(findValue(response, if (rank.name.isNotBlank()) rank.name else bookSource.search.name), HtmlCompat.FROM_HTML_MODE_LEGACY).toString(),
            author = findValue(response, if (rank.author.isNotBlank()) rank.author else bookSource.search.author),
            cover = findValue(response, if (rank.cover.isNotBlank()) rank.cover else bookSource.search.cover, true),
            summary = findValue(response, if (rank.summary.isNotBlank()) rank.summary else bookSource.search.summary),
            detail = findValue(response, if (rank.detail.isNotBlank()) rank.detail else bookSource.search.detail, true)
    )

    private fun findValue(response: BookSourceResponse, query: String, isHref: Boolean = false): String {
        if (query.isBlank()) return EMPTY
        return when (response.body) {
            is Document, is Element -> findValueByHtml(response, Query.build(query), isHref)
            is String -> findValueByJson(response, Query.build(query), isHref)
            else -> response.body.toString()
        }
    }

    /**
     * 查找Json值
     */
    @Suppress("RegExpRedundantEscape")
    private fun findValueByJson(response: BookSourceResponse, query: Query, isHref: Boolean): String {
        val variables = Regex("""(?<=\$\{).+?(?=\})""").findAll(query.query).map { it.value }.toSet()
        return if (variables.isEmpty()) {
            //不存在查询模板
            val value = findJsonValueByVariable(query.query, response.body as String, Uri.parse(response.url))
            findValueByQuery(value, response.url, query, isHref)
        } else {
            //存在至少1个查询模板
            var value = query.query
            variables.forEach { variable ->
                value = value.replace("\${$variable}", findJsonValueByVariable(variable, response.body as String, Uri.parse(response.url)))
            }
            findValueByQuery(value, response.url, query, isHref)
        }
    }

    /**
     * 查找Json中指定变量值
     */
    private fun findJsonValueByVariable(variable: String, json: String, uri: Uri) = try {
        when {
            //URL参数
            variable.startsWith("\$params.") -> uri.getQueryParameter(variable.removePrefix("\$params.")).orEmpty()
            //JSON本身
            variable == "$" -> json
            //JSON取值
            else -> when (val value = JsonPath.parse(json).read<Any>(variable)) {
                is JsonArray -> StringBuilder().apply { value.forEach { append("${it.asString}\n") } }.toString().trim()
                is JsonPrimitive -> value.asString
                else -> EMPTY
            }
        }
    } catch (e: Exception) {
        EMPTY
    }

    /**
     * 查找Html值
     */
    @Suppress("RegExpRedundantEscape")
    private fun findValueByHtml(response: BookSourceResponse, query: Query, isHref: Boolean): String {
        val variables = Regex("""(?<=\$\{).+?(?=\})""").findAll(query.query).map { it.value }.toSet()
        return if (variables.isEmpty()) {
            //不存在查询模板
            val value = findHtmlValueByVariable(query.query, response.body, isHref, Uri.parse(response.url))
            findValueByQuery(value, response.url, query, isHref)
        } else {
            //存在至少1个查询模板
            var value = query.query
            variables.forEach { variable ->
                value = value.replace("\${$variable}", findHtmlValueByVariable(variable, response.body, isHref, Uri.parse(response.url)))
            }
            findValueByQuery(value, response.url, query, isHref)
        }
    }

    /**
     * 查找Html中指定变量值
     */
    private fun findHtmlValueByVariable(variable: String, body: Any, isHref: Boolean, uri: Uri) = if (variable.startsWith("\$params.")) {
        //URL参数
        uri.getQueryParameter(variable.removePrefix("\$params.")).orEmpty()
    } else {
        //Html取值
        val query = variable.split(Query.ATTR)
        val node: Element? = when (body) {
            is Document -> queryFirst(body, query.first())
            is Element -> queryFirst(body, query.first())
            else -> null
        }
        when {
            //未查找到节点
            node == null -> EMPTY
            //指定属性
            query.size > 1 -> node.attr(query.last())
            //智能提取元素的超链接
            isHref -> node.href()
            //JavaScript or CSS
            node.tagName() == STYLE || node.tagName() == SCRIPT -> node.data()
            //文本
            else -> node.text()
        }
    }

    /**
     * 执行Query操作符
     */
    private fun findValueByQuery(string: String, url: String, query: Query, isHref: Boolean): String {
        var value = string
        if (!query.decrypt.isNullOrBlank()) {
            when (query.decrypt?.lowercase(Locale.ENGLISH)) {
                "base64" -> value = String(Base64.decode(value, Base64.DEFAULT))
            }
        }
        if (!query.match.isNullOrBlank()) {
            try {
                value = Regex(query.match!!).find(value)?.value ?: EMPTY
            } catch (e: PatternSyntaxException) {
                //忽略可能存在错误的正则表达式
            }
        }
        if (isHref) value = value.autoUrl(url)
        if (!query.euqal.isNullOrBlank()) {
            value = (value == query.euqal).toString()
        }
        if (!query.replace.isNullOrBlank()) {
            val chars = query.replace!!.split(Query.OPERATOR)
            value = value.replace(chars.first(), if (chars.size > 1) chars.last() else EMPTY)
        }
        return value.trim()
    }

    /**
     * 查询列表
     */
    private fun findList(response: BookSourceResponse, query: String) = try {
        when (response.body) {
            is Document -> queryList(response.body, query).map { BookSourceResponse(response.url, it) }
            is Element -> queryList(response.body, query).map { BookSourceResponse(response.url, it) }
            is String -> JsonPath.parse(response.body).read(query, object : TypeRef<List<JsonObject>>() {}).map { BookSourceResponse(response.url, it.toJson()) }
            else -> emptyList()
        }
    } catch (e: Exception) {
        //异常查询返回空列表
        emptyList<BookSourceResponse>()
    }

    /**
     * 读取Html内容
     * @param response  源数据
     * @param output    输出路径 用于保存图片
     */
    private fun findJsonContent(response: BookSourceResponse, output: String): String {
        var content = if (bookSource.chapter.content.isNotBlank()) {
            findValueByJson(response, Query.build(bookSource.chapter.content), false)
        } else response.body as String
        if (content.isHtml()) {
            content = findHtmlContent(BookSourceResponse(response.url, Jsoup.parseBodyFragment(content)), BODY, output)
        }
        return content
    }

    /**
     * 查找第一个节点
     */
    private fun queryFirst(element: Element, cssQuery: String?) = try {
        if (cssQuery.isNullOrBlank()) null else element.selectFirst(cssQuery)
    } catch (e: Selector.SelectorParseException) {
        //查询出错
        null
    }

    /**
     * 查找节点列表
     */
    private fun queryList(element: Element, cssQuery: String?) = try {
        if (cssQuery.isNullOrBlank()) Elements() else element.select(cssQuery)
    } catch (e: Selector.SelectorParseException) {
        //查询出错
        Elements()
    }

    /**
     * 读取Html内容
     * @param response  源数据
     * @param output    输出路径 用于保存图片
     */
    private fun findHtmlContent(response: BookSourceResponse, query: String, output: String): String {
        val filters = bookSource.chapter.filter.map { if (it.startsWith("@")) it.removePrefix("@") else findValue(response, it) }
        val buffer = StringBuilder()
        try {
            queryList(response.body as Element, query).forEach { element ->
                filters.forEach { if (it.isNotBlank()) element.select(it).remove() }
                buffer.append(fromHtml(element, response.url, output))
                buffer.append("\n")
            }
        } catch (e: Selector.SelectorParseException) {
            //可能存在意外之外Query引起异常
        }
        return buffer.toString()
    }

    /**
     * 读取Html
     * @param element   节点
     * @param baseUrl   host
     * @param output    输出路径 用于保存图片
     */
    private fun fromHtml(element: Node, baseUrl: String, output: String): String {
        val buffer = StringBuilder()
        element.childNodes().forEach { node ->
            when (node.nodeName()) {
                SCRIPT, STYLE -> Unit
                TEXT -> buffer.append(HtmlCompat.fromHtml(node.outerHtml().trim(), HtmlCompat.FROM_HTML_MODE_LEGACY))
                IMG -> buffer.append(img(node, baseUrl, output) + "\n")
                FIGURE -> figure(node).run { buffer.append(fromHtml(node, baseUrl, output)) }
                H1, H2, H3, H4, H5, H6, P, BR, DIV -> {
                    buffer.append("\n")
                    buffer.append(fromHtml(node, baseUrl, output))
                }
                else -> buffer.append(fromHtml(node, baseUrl, output))
            }
        }
        return buffer.toString()
    }


    /**
     * 将figure转化
     */
    private fun figure(node: Node) {
        var img: Node? = null
        var figcaption: Node? = null
        node.childNodes().forEach { child ->
            when (child.nodeName().lowercase(Locale.ENGLISH).trim()) {
                IMG -> img = child
                FIGCAPTION -> figcaption = child
            }
        }
        if (img != null && figcaption != null) {
            img!!.attr("alt", HtmlCompat.fromHtml(figcaption!!.outerHtml(), HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim())
            figcaption!!.remove()
        }
    }

    /**
     * 下载img标签内的图片
     * @param node      图片元素
     * @param baseUrl   当前网址
     * @param output    输出目录
     */
    private fun img(node: Node, baseUrl: String, output: String): String {
        if (File(output).exists().not()) return EMPTY
        val href = node.src(baseUrl)
        if (URLUtil.isNetworkUrl(href).not()) return EMPTY
        val file = File(output, href.encode().let { if (it.length < 0xFF) it else it.md5() })
        if (file.exists()) return "![${node.attr("alt")}](${file.name})"
        var response = Http.getImmediateness<ByteArray>(href)
        //Https可能存在证书问题，强制使用http再次尝试
        if (response.isSuccessful.not() && URLUtil.isHttpsUrl(href)) {
            response = Http.getImmediateness(href.replace("https://", "http://"))
        }
        if (response.isSuccessful.not() || response.data?.isNotEmpty() != true) return EMPTY
        if (file.parentFile?.exists() == true) {
            file.writeBytes(response.data!!)
            return "![${node.attr("alt")}](${file.name})"
        }
        return EMPTY
    }
}
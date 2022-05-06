package cn.lightink.reader.transcode.entity

import android.net.Uri
import android.util.Base64
import cn.lightink.reader.transcode.Html
import cn.lightink.reader.transcode.Html.href
import com.jayway.jsonpath.JsonPath
import net.minidev.json.JSONArray
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import org.jsoup.select.Selector
import java.util.*
import java.util.regex.PatternSyntaxException

data class TranscodeResponse(val url: String, val body: Any) {

    /**
     * 查询列表 出错返回空列表
     */
    fun findList(query: String): List<TranscodeResponse> = try {
        when (body) {
            is Document -> queryList(body, query).map { TranscodeResponse(url, it) }
            is Element -> queryList(body, query).map { TranscodeResponse(url, it) }
            is String -> JsonPath.parse(body).read<JSONArray>(query).map { TranscodeResponse(url, it.toString()) }
            else -> emptyList()
        }
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * 查找节点列表 出错返回空列表
     */
    private fun queryList(element: Element, cssQuery: String?) = try {
        if (cssQuery.isNullOrBlank()) Elements() else element.select(cssQuery)
    } catch (e: Selector.SelectorParseException) {
        Elements()
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
     * 查值
     */
    fun findValue(query: String, isHref: Boolean = false): String {
        if (query.isBlank()) return ""
        return when (body) {
            is Document, is Element -> findValueByHtml(Query.build(query), isHref)
            is String -> findValueByJson(Query.build(query), isHref)
            else -> body.toString()
        }
    }

    /**
     * 查找Html值
     */
    @Suppress("RegExpRedundantEscape")
    private fun findValueByHtml(query: Query, isHref: Boolean): String {
        val variables = Regex("""(?<=\$\{).+?(?=\})""").findAll(query.query).map { it.value }.toSet()
        return if (variables.isEmpty()) {
            //不存在查询模板
            val value = findHtmlValueByVariable(query.query, body, isHref, Uri.parse(url))
            findValueByQuery(value, url, query, isHref)
        } else {
            //存在至少1个查询模板
            var value = query.query
            variables.forEach { variable ->
                value = value.replace("\${$variable}", findHtmlValueByVariable(variable, body, isHref, Uri.parse(url)))
            }
            findValueByQuery(value, url, query, isHref)
        }
    }

    /**
     * 查找Json值
     */
    @Suppress("RegExpRedundantEscape")
    private fun findValueByJson(query: Query, isHref: Boolean): String {
        val variables = Regex("""(?<=\$\{).+?(?=\})""").findAll(query.query).map { it.value }.toSet()
        return if (variables.isEmpty()) {
            //不存在查询模板
            val values = findJsonValueByVariable(query.query, body as String, Uri.parse(url))
            StringBuilder().apply {
                values.forEachIndexed { index, value ->
                    append(findValueByQuery(value, url, query, isHref))
                    if (index + 1 < values.size) append("\n")
                }
            }.toString()
        } else {
            //存在至少1个查询模板
            var value = query.query
            variables.forEach { variable ->
                value = value.replace("\${$variable}", findJsonValueByVariable(variable, body as String, Uri.parse(url)).first())
            }
            findValueByQuery(value, url, query, isHref)
        }
    }

    /**
     * 查找Json中指定变量值
     */
    private fun findJsonValueByVariable(variable: String, json: String, uri: Uri) = try {
        when {
            //URL参数
            variable.startsWith("\$params.") -> listOf(uri.getQueryParameter(variable.removePrefix("\$params.")).orEmpty())
            //JSON本身
            variable == "$" -> listOf(json)
            //JSON取值
            else -> when (val value = JsonPath.parse(json).read<Any>(variable)) {
                is JSONArray -> value.map { it.toString() }
                is String -> listOf(value)
                else -> listOf(value.toString())
            }
        }
    } catch (e: Throwable) {
        listOf("")
    }

    /**
     * 执行Query操作符
     */
    private fun findValueByQuery(string: String, url: String, query: Query, isHref: Boolean): String {
        var value = string
        if (!query.decrypt.isNullOrBlank()) {
            when (query.decrypt?.toLowerCase(Locale.ENGLISH)) {
                "base64" -> value = String(Base64.decode(value, Base64.DEFAULT))
            }
        }
        if (!query.match.isNullOrBlank()) {
            try {
                value = Regex(query.match!!).find(value)?.value ?: ""
            } catch (e: PatternSyntaxException) {
                //忽略可能存在错误的正则表达式
            }
        }
        if (isHref) value = Html.url(value, url)
        if (query.equal != null) {
            value = if (query.equal!!.second) {
                value == query.equal!!.first
            } else {
                value != query.equal!!.first
            }.toString()
        }
        if (query.replace.isNotEmpty()) {
            query.replace.forEach { replace ->
                val chars = replace.split(Query.OPERATOR)
                value = if (chars.isEmpty()) {
                    "${if (chars.size > 1) chars.last() else ""}$value"
                } else {
                    value.replace(chars.first(), if (chars.size > 1) chars.last() else "")
                }
            }
        }
        return value.trim()
    }

    /**
     * 查找Html中指定变量值
     */
    private fun findHtmlValueByVariable(variable: String, body: Any, isHref: Boolean, uri: Uri): String {
        return if (variable.startsWith("\$params.")) {
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
            val baseUrl = Uri.parse(url).let {
                "${it.scheme}://${it.authority}${it.path}"
            }
            node?.setBaseUri(baseUrl)
            when {
                //未查找到节点
                node == null -> ""
                //指定属性
                query.size > 1 -> node.attr(query.last())
                //智能提取元素的超链接
                isHref -> node.href()
                //JavaScript or CSS
                node.tagName() == Html.STYLE || node.tagName() == Html.SCRIPT -> node.data()
                //文本
                else -> node.text()
            }
        }
    }

    data class Query(val query: String, var match: String? = null, var equal: Pair<String, Boolean>? = null, var replace: MutableList<String> = mutableListOf(), var decrypt: String? = null) {

        companion object {

            const val OPERATOR = "->"
            const val ATTR = "@attr->"
            private const val MATCH = "@match->"
            private const val EQUAL = "@equal->"
            private const val EQUAL_NOT = "@equalNot->"
            private const val REPLACE = "@replace->"
            private const val DECRYPT = "@decrypt->"

            fun build(expression: String): Query {
                val operators = Regex("@(js|match|equal|equalNot|replace|decrypt)->").findAll(expression).toList()
                if (operators.isEmpty()) return Query(expression)
                val query = Query(expression.substring(0, operators.first().range.first))
                operators.forEachIndexed { index, matchResult ->
                    val endIndex = if (index < operators.lastIndex) operators[index + 1].range.first else expression.length
                    val value = expression.substring(matchResult.range.last + 1, endIndex)
                    when (matchResult.value) {
                        MATCH -> query.match = value
                        EQUAL -> query.equal = value to true
                        EQUAL_NOT -> query.equal = value to false
                        REPLACE -> query.replace.add(value)
                        DECRYPT -> query.decrypt = value
                    }
                }
                return query
            }
        }

    }

}
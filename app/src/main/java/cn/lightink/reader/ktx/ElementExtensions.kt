package cn.lightink.reader.ktx

import android.net.Uri
import cn.lightink.reader.module.EMPTY
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.select.Elements

/**
 * 安全调用select
 */
fun Element.query(cssQuery: String?): Elements {
    return if (cssQuery.isNullOrBlank()) Elements() else select(cssQuery)
}

/**
 * 智能补全超链接
 */
fun Element?.href(url: String) = url(this?.attr("href").orEmpty(), url)

/**
 * 智能补全超链接
 */
fun Node?.src(url: String) = url(this?.attr("src").orEmpty(), url)

fun Node?.href(): String = when {
    this?.hasAttr("href") == true -> attr("href")
    this?.hasAttr("src") == true -> attr("src")
    else -> ""
}

/**
 * 智能补全超链接
 */
private fun url(href: String, url: String): String {
    val uri = Uri.parse(url) ?: return href
    return when {
        href.isBlank() -> EMPTY
        href.startsWith("https:") || href.startsWith("http:") -> href
        href.startsWith("//") -> "${uri.scheme.orEmpty()}:$href"
        href.startsWith("/") -> "${uri.scheme.orEmpty()}://${uri.authority.orEmpty()}$href"
        href.startsWith("#") || href.startsWith("?") -> "$url$href"
        else -> "${uri.scheme.orEmpty()}://${uri.authority.orEmpty()}${uri.path.orEmpty().let {
            val index = it.lastIndexOf("/")
            if (index >= 0) it.substring(0, index + 1) else it
        }}$href"
    }
}
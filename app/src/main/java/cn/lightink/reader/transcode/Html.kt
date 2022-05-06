package cn.lightink.reader.transcode

import android.net.Uri
import org.jsoup.nodes.Node

object Html {
    const val H1 = "h1"
    const val H2 = "h2"
    const val H3 = "h3"
    const val H4 = "h4"
    const val H5 = "h5"
    const val H6 = "h6"
    const val BR = "br"
    const val P = "p"
    const val A = "a"
    const val IMG = "img"
    const val IMAGE = "image"
    const val DIV = "div"
    const val SCRIPT = "script"
    const val STYLE = "style"
    const val BLOCK_QUOTE = "blockquote"
    const val TEXT = "#text"
    const val BODY = "body"
    const val FIGCAPTION = "figcaption"

    fun Node?.href(): String = when {
        this?.hasAttr("href") == true -> attr("href")
        this?.hasAttr("src") == true -> attr("src")
        this?.hasAttr("data-src") == true -> attr("data-src")
        this?.hasAttr("xlink:href") == true -> attr("xlink:href")
        else -> ""
    }?.let {
        url(it, this?.baseUri().orEmpty())
    }.orEmpty()

    /**
     * 智能补全超链接
     */
    fun url(href: String, url: String): String {
        val uri = Uri.parse(url) ?: return href
        return when {
            href.isBlank() -> ""
            href.startsWith("https:") || href.startsWith("http:") -> href
            href.startsWith("//") -> "${uri.scheme.orEmpty()}:$href"
            href.startsWith("/") -> "${uri.scheme.orEmpty()}://${uri.authority.orEmpty()}$href"
            href.startsWith("#") || href.startsWith("?") -> "$url$href"
            else -> "${uri.scheme.orEmpty()}://${uri.authority.orEmpty()}${
                uri.path.orEmpty().let {
                    val index = it.lastIndexOf("/")
                    if (index >= 0) it.substring(0, index + 1) else it
                }
            }$href"
        }
    }

}
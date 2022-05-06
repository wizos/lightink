package cn.lightink.reader.transcode

import android.graphics.BitmapFactory
import android.webkit.URLUtil
import androidx.core.text.HtmlCompat
import cn.lightink.reader.transcode.Html
import cn.lightink.reader.transcode.Html.href
import org.jsoup.Jsoup
import org.jsoup.nodes.Node
import java.io.File
import java.math.BigInteger
import java.security.MessageDigest
import java.util.*

/**
 * 内容解析器
 */
object ContentParser {

    /**
     * 读取内容
     */
    fun read(url: String, content: String, output: File?): String {
        var markdown = if (content.contains("""<[^>]+>""".toRegex())) {
            val html = content.replace("\n", "<br>")
            val node = Jsoup.parseBodyFragment(html, url)
            parseHtml(node, output)
        } else content
        markdown = markdown.replace("""\n+(\s|\u3000)+""".toRegex(), "\n")
        markdown = markdown.replace("""\n+""".toRegex(), "\n")
        return markdown
    }

    /**
     * 读取HTML标签
     * @param node HTML标签
     */
    fun parseHtml(node: Node, output: File?, buffer: StringBuilder = StringBuilder()): String {
        node.childNodes().forEach { child ->
            when (child.nodeName()) {
                Html.SCRIPT, Html.STYLE -> Unit
                Html.TEXT -> buffer.append(HtmlCompat.fromHtml(child.outerHtml().trim(), HtmlCompat.FROM_HTML_MODE_LEGACY))
                Html.IMG -> if (output == null) {
                    //调试模式
                    buffer.append("![](${child.attr("src").trim()})")
                } else {
                    //存储模式
                    parseImage(child, output)?.run {
                        buffer.append(this)
                    }
                }
                Html.H1, Html.H2, Html.H3, Html.H4, Html.H5, Html.H6, Html.P, Html.BR, Html.DIV -> buffer.append("\n${parseHtml(child, output)}")
                else -> buffer.append(parseHtml(child, output))
            }
        }
        return buffer.toString()
    }

    /**
     * 解析图片
     */
    private fun parseImage(node: Node, output: File?): String? {
        val url = node.href()
        if (!URLUtil.isNetworkUrl(url)) return null
        //图片名称使用href的MD5值，避免不同路径下的相同文件名的图片被覆盖，比如：/A/1.jpg与/B/1.jpg
        val file = File(output, md5(url))
        if (!file.exists()) {
            //请求图片数据，当请求失败且是HTTPS网址时尝试使用HTTP
            var byteArray = NetworkBridge.get(url)
            if (byteArray == null && URLUtil.isHttpsUrl(url)) {
                byteArray = NetworkBridge.get(url.replace("https://", "http://"))
            }
            if (byteArray == null) return null
            //写入图片数据
            file.writeBytes(byteArray)
        }
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }.apply {
            BitmapFactory.decodeFile(file.absolutePath, this)
        }
        return "![${options.outWidth}x${options.outHeight}](${file.name})"
    }

    private fun md5(string: String): String {
        return BigInteger(1, MessageDigest.getInstance("MD5").digest(string.toByteArray())).toString(16).padStart(32, '0').toUpperCase(Locale.getDefault())
    }

    fun String.chinese() = replace(Regex("""[^\u4e00-\u9fa5]"""), "")

    /**
     * 单行
     */
    fun String.singleLine(): String {
        return HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().replace("""\n+|\r+""".toRegex(), "\t").replace("""\t+""".toRegex(), "\t\t")
    }
}
package cn.lightink.reader.ktx

import android.net.Uri
import androidx.core.text.HtmlCompat
import cn.lightink.reader.module.EMPTY
import com.google.gson.JsonParser
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.UnsupportedCharsetException
import java.security.MessageDigest
import java.util.*

/**
 * 判断字符串是否是JSON
 */
fun String.isJson(): Boolean {
    try {
        JsonParser.parseString(this)
    } catch (e: Exception) {
        return false
    }
    return true
}

/**
 * 判断字符串是HTML
 */
fun String.isHtml() = this.contains("""<[^>]+>""".toRegex())

/**
 * 清除字符串中全部空白符
 */
fun String.wrap() = if (isEnglish()) replace("\n", "\t") else replace(Regex("""\s+"""), EMPTY)

/**
 * 是否是纯英文
 */
fun String.isEnglish() = !contains("""[\u4e00-\u9fa5]""".toRegex())


/**
 * 提取Regex值
 */
fun String.regex(regex: String): String {
    return Regex(regex).find(this)?.value.orEmpty()
}

/**
 * 提取Regex全部值
 */
fun String.regexAll(regex: String): Sequence<MatchResult> {
    return Regex(regex).findAll(this)
}

/**
 * 获取encode字符串
 */
fun String.encode(): String {
    return URLEncoder.encode(this, "UTF-8")
}

/**
 * 获取decode字符串
 */
fun String.decode(): String {
    return URLDecoder.decode(this, "UTF-8")
}

/**
 * 单行
 */
fun String.singleLine(): String {
    return HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().replace("""\n+|\r+""".toRegex(), "\t").replace("""\t+""".toRegex(), "\t\t")
}

/**
 * 获取HOST
 */
fun String.host(): String {
    return Uri.parse(this).host.orEmpty()
}

/**
 * URLEncode
 */
fun String.encode(charset: String): String = try {
    URLEncoder.encode(this, charset)
} catch (e: UnsupportedCharsetException) {
    this
}

/**
 * 自动补全Url
 */
fun String.autoUrl(baseUrl: String): String {
    try {
        val uri = URI.create(baseUrl) ?: return this
        return when {
            isBlank() -> EMPTY
            startsWith("https:") || startsWith("http:") -> this
            startsWith("//") -> "${uri.scheme.orEmpty()}:$this"
            startsWith("/") -> "${uri.scheme.orEmpty()}://${uri.authority.orEmpty()}$this"
            startsWith("#") -> "$baseUrl$this"
            startsWith("?") -> "${uri.scheme.orEmpty()}://${uri.authority.orEmpty()}${uri.path.orEmpty()}$this"
            startsWith("./") -> "${uri.scheme.orEmpty()}://${uri.authority.orEmpty()}${uri.path.orEmpty().let {
                val index = it.lastIndexOf("/")
                if (index >= 0) it.substring(0, index + 1) else it
            }}${this.removePrefix("./")}"
            else -> "${uri.scheme.orEmpty()}://${uri.authority.orEmpty()}${uri.path.orEmpty().let {
                val index = it.lastIndexOf("/")
                if (index >= 0) it.substring(0, index + 1) else it
            }}$this"
        }
    } catch (e: Exception) {
        return this
    }
}

/**
 * MD5值
 */
fun String.md5(): String {
    val builder = StringBuilder()
    MessageDigest.getInstance("MD5").apply { update(toByteArray()) }.digest().forEach { byte ->
        val hex = (0xFF and byte.toInt()).toString(16)
        if (hex.length == 1) builder.append('0')
        builder.append(hex)
    }
    return builder.toString().uppercase(Locale.ENGLISH)
}

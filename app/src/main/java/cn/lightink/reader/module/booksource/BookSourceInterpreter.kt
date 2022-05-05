package cn.lightink.reader.module.booksource

import android.net.Uri
import android.webkit.CookieManager
import cn.lightink.reader.ktx.charset
import cn.lightink.reader.ktx.isJson
import cn.lightink.reader.ktx.url
import cn.lightink.reader.module.EMPTY
import cn.lightink.reader.net.Http
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import java.nio.charset.Charset

object BookSourceInterpreter {

    //Header
    private const val HEADER = "@header->"
    //Post
    private const val POST = "@post->"

    /**
     * 请求
     */
    fun execute(url: String, auth: BookSourceJson.Auth?): BookSourceResponse? {
        try {
            val request = buildRequest(url, auth) ?: return null
            val response = Http.client.newCall(request).execute()
            return if (response.isSuccessful && response.body != null) {
                onResponse(response.url, response.body!!.bytes(), response.header("Content-Type") ?: "text/plain")
            } else null
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * 响应
     */
    private fun onResponse(url: String, body: ByteArray, contentType: String): BookSourceResponse {
        var document = String(body, body.charset() ?: charset("UTF-8"))
        return when {
            contentType.contains("tar") -> BookSourceResponse(url, document.replace(Regex("""(info\.txt|\u0000).+\u0000"""), EMPTY).trim())
            contentType.contains(Regex("html|octet-stream|xml")) && !document.isJson() -> {
                try {
                    val head = Jsoup.parse(document).head()
                    var attr = head.selectFirst("meta[charset]")?.attr("charset")
                    if (attr.isNullOrBlank()) {
                        attr = Regex("(?<=charset=).+").find(head.selectFirst("meta[content*=charset]")!!.attr("content"))?.value
                    }
                    if (attr?.isNotBlank() == true) {
                        document = String(body, Charset.forName(attr))
                    }
                } catch (e: Exception) {
                    //忽略异常，不会有任何影响
                }
                BookSourceResponse(url, Jsoup.parse(document))
            }
            else -> BookSourceResponse(url, document)
        }
    }

    /**
     * 构建请求
     */
    @Suppress("RegExpRedundantEscape")
    private fun buildRequest(url: String, auth: BookSourceJson.Auth?): Request? = Request.Builder().apply {
        //cookies
        val headers = Headers.Builder()
        val cookies = hashMapOf<String, String>()
        if (auth?.cookie != null) {
            CookieManager.getInstance().getCookie(auth.cookie)?.apply { headers.add("Cookie:$this") }?.split(";")?.filter { it.contains("=") }?.forEach { cookie ->
                val index = cookie.indexOf("=")
                cookies[cookie.substring(0, index).trim()] = cookie.substring(index + 1).trim()
            }
        }
        //auth headers
        if (!auth?.header.isNullOrBlank()) {
            //TODO  Auth Headers
            var headersParams = auth?.header.orEmpty()
            var hasHeaders: Boolean = true
            Regex("""(?<=\$\{).+?(?=\})""").findAll(headersParams).map { it.value }.toSet().forEach { variable ->
                hasHeaders = hasHeaders and cookies.contains(variable)
                headersParams = headersParams.replace("\${$variable}", cookies.getOrElse(variable) { EMPTY })
            }
            //no headers cancel this request
            if (!hasHeaders) return null
            headersParams.split(HEADER).filter { it.isNotBlank() }.forEach { header -> headers.add(header) }
        }
        //operators
        val operators = Regex("@.+?->").findAll(url).toList()
        val host = if (operators.isNotEmpty()) url.substring(0, operators.first().range.first) else url
        val requestBody = StringBuilder()
        if (operators.isNotEmpty()) {
            operators.forEachIndexed { index, operator ->
                val endIndex = if (index < operators.lastIndex) operators[index + 1].range.first else url.length
                val params = url.substring(operator.range.last + 1, endIndex)
                when (operator.value) {
                    POST -> requestBody.append(params)
                    HEADER -> headers.add(params)
                }
            }
        }
        headers(headers.build())
        //auth params
        var authParams = auth?.params.orEmpty()
        if (authParams.isNotBlank()) {
            Regex("""(?<=\$\{).+?(?=\})""").findAll(authParams).map { it.value }.toSet().forEach { variable ->
                authParams = authParams.replace("\${$variable}", cookies.getOrElse(variable) { EMPTY })
            }
        }
        //body
        url(host)
        if (requestBody.isNotBlank()) {
            val mediaType = if (requestBody.toString().isJson()) "application/json;charset=utf-8" else "application/x-www-form-urlencoded;charset=utf-8"
            if (mediaType != "application/json;charset=utf-8") {
                requestBody.append(if (requestBody.isBlank()) authParams else "&$authParams")
            }
            post(requestBody.toString().toRequestBody(mediaType.toMediaType()))
        } else {
            if (authParams.isNotBlank()) {
                url("$host${if (Uri.parse(host).query.isNullOrBlank()) "?" else "&"}$authParams")
            }
            get()
        }
    }.build()

}
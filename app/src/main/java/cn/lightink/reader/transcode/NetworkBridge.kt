package cn.lightink.reader.transcode

import android.annotation.SuppressLint
import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import cn.lightink.reader.transcode.DependenciesManager
import com.hippo.quickjs.android.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONException
import org.json.JSONObject
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.mozilla.universalchardet.UniversalDetector
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.Charset
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import javax.net.ssl.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

object NetworkBridge {

    private val client by lazy { buildHttpClient() }

    /**
     * 执行异步请求同步返回结果
     * @param request 请求
     * @return response
     */
    suspend fun execute(request: Request) = suspendCoroutine<Response> {
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                it.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                it.resume(response)
            }
        })
    }

    /**
     * 读取字节数组
     */
    fun get(url: String) = try {
        val headers = Headers.Builder()
        CookieManager.getInstance().getCookie(url)?.let { cookie -> headers.add("Cookie", cookie) }
        client.newCall(Request.Builder().url(url).headers(headers.build()).build())
            .execute().body?.bytes()
    } catch (e: Exception) {
        null
    }

    /**
     * 构建请求
     */
    fun request(
        method: Connection.Method,
        url: String,
        data: String?,
        headers: List<String>,
        unzipFilename: String?
    ): String {
        //构建Headers
        val headersBuilder = Headers.Builder()
        CookieManager.getInstance().getCookie(url)?.let { cookie ->
            headersBuilder.add("Cookie", cookie)
        }
        headers.forEach { header -> headersBuilder.add(header) }
        //构建请求参数
        var requestBody: RequestBody? = null
        if (data?.isNotBlank() == true) {
            requestBody = try {
                JSONObject(data)
                data.toRequestBody("application/json;charset=utf-8".toMediaType())
            } catch (e: JSONException) {
                data.toRequestBody("application/x-www-form-urlencoded;charset=utf-8".toMediaType())
            }
        }
        //构建请求
        val request = Request.Builder().url(url)
            .headers(headersBuilder.build())
            .method(method.name, requestBody)
            .build()
        val response = client.newCall(request).execute()
        printHeaders(response)
        return string(url, response, unzipFilename)
    }

    /**
     * jsoup请求
     */
    private fun requestJsoup(
        method: Connection.Method,
        url: String,
        data: String?,
        headers: List<String>
    ): String {
        val connection = Jsoup.connect(url).method(method)
        if (data?.isNotBlank() == true) {
            connection.data(*data.split("&").toTypedArray())
        }
        CookieManager.getInstance().getCookie(url)
            ?.let { cookie -> connection.header("Cookie", cookie) }
        if (headers.isNotEmpty()) {
            headers.forEach { header ->
                val indexOf = header.indexOf(":")
                if (indexOf > -1) connection.header(
                    header.substring(0, indexOf).trim(),
                    header.substring(indexOf + 1).trim()
                )
            }
        }
        val response = connection.execute()
        val markdown = StringBuilder()
        markdown.append("```BASIC\n\nGeneral\n\n")
        markdown.append("Request URL: ${url}\n")
        markdown.append("Request Method: ${method.name}\n")
        markdown.append("Status Code: ${response.statusCode()}\n```\n---\n")
        markdown.append("```BASIC\n\nRequest Headers\n\n")
        markdown.append("Cookie: ${CookieManager.getInstance().getCookie(url).orEmpty()}\n")
        markdown.append("```\n")
        Console.println(markdown.toString(), Console.Type.Headers)
        return response.body()
    }

    /**
     * 响应结果转字符串
     */
    private fun string(url: String, response: Response, unzipFilename: String? = null): String {
        val body = response.body?.bytes() ?: byteArrayOf()
        var charset = body.charset() ?: charset("UTF-8")
        if (!unzipFilename.isNullOrBlank()) {
            val filename = url.removeSuffix("/").substringAfterLast("/")
            val file = DependenciesManager.buildZipFile(filename)
            file.writeBytes(body)
            val zipFile = ZipFile(file)
            val string = try {
                ZipInputStream(body.inputStream(), charset).use { zip ->
                    var entry: ZipEntry
                    while (zip.nextEntry.also { entry = it } != null) {
                        if (entry.name == unzipFilename) {
                            val byteArray = zipFile.getInputStream(entry).use {
                                it.readBytes()
                            }
                            charset = byteArray.charset() ?: charset("UTF-8")
                            return@use String(byteArray, charset)
                        }
                    }
                    return@use ""
                }
            } catch (e: Throwable) {
                ""
            } finally {
                file.delete()
            }
            return string
        }
        val string = String(body, charset)
        return string
    }

    /**
     * 打印请求
     */
    private fun printHeaders(response: Response) {
        val markdown = StringBuilder()
        markdown.append("```BASIC\n\nGeneral\n\n")
        markdown.append("Request URL: ${response.request.url.toString()}\n")
        markdown.append("Request Method: ${response.request.method}\n")
        markdown.append("Status Code: ${response.code}\n```\n---\n")
        markdown.append("```BASIC\n\nRequest Headers\n\n")
        response.request.headers.toList().forEach { keyValue ->
            markdown.append("${keyValue.first}: ${keyValue.second.orEmpty()}\n")
        }
        markdown.append("```\n---\n")
        markdown.append("```BASIC\n\nResponse Headers\n\n")
        response.headers.toList().forEach { keyValue ->
            markdown.append("${keyValue.first}: ${keyValue.second.orEmpty()}\n")
            if (keyValue.first.uppercase(Locale.getDefault()) == "SET-COOKIE") {
                CookieManager.getInstance().run {
                    setCookie(response.request.url.toString(), keyValue.second)
                    flush()
                }
            }
        }
        markdown.append("```\n")
        Console.println(markdown.toString(), Console.Type.Headers)
    }

    /**
     * 字节数组字符集
     */
    fun ByteArray.charset() = UniversalDetector(null).apply {
        handleData(this@charset, 0, this@charset.size)
        dataEnd()
    }.detectedCharset?.let { Charset.forName(it) }

    /**
     * 构建OkHttp客户端
     */
    private fun buildHttpClient(): OkHttpClient {
        return buildTrustManager().let { trustManagers ->
            OkHttpClient.Builder()
                .addNetworkInterceptor(HttpLoggingInterceptor().apply {
                    level =
                        if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
                })
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .hostnameVerifier { _, _ -> true }
                .sslSocketFactory(
                    buildSSLSocketFactory(trustManagers),
                    trustManagers[0] as X509TrustManager
                )
                .build()
        }
    }

    /**
     * 构建SSL工厂
     */
    private fun buildSSLSocketFactory(trustManagers: Array<TrustManager>): SSLSocketFactory {
        val context = SSLContext.getInstance("SSL")
        context.init(null, trustManagers, SecureRandom())
        return context.socketFactory
    }

    /**
     * 构建X509信任管理者
     */
    @Suppress("CAST_NEVER_SUCCEEDS")
    private fun buildTrustManager(): Array<TrustManager> {
        val x509TrustManager = @SuppressLint("CustomX509TrustManager")
        object : X509TrustManager {
            @SuppressLint("TrustAllX509TrustManager")
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            }

            @SuppressLint("TrustAllX509TrustManager")
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return arrayOf()
            }
        }
        return arrayOf(x509TrustManager)
    }

    /**
     * 构建JavaScript环境
     */
    fun inject(runtime: JSContext, host: String, bookSource: String) {
        //文件名
        val filename = "$host.js"
        //构建依赖库
        runtime.globalObject.setProperty("require", runtime.createJSFunction() { context, args ->
            val dependency = args[0].cast(JSString::class.java).string
            val code = DependenciesManager.load(dependency)
            if (code.isNotBlank()) runtime.evaluate(code, "${dependency}.js")
            return@createJSFunction context.createJSUndefined()
        })
        //构建COOKIE方法
        runtime.globalObject.setProperty("COOKIE", runtime.createJSFunction() { context, args ->
            val key = args[0].cast(JSString::class.java).string
            var cookies = CookieManager.getInstance().getCookie("https://$host").orEmpty()
            if (!cookies.contains(key)) {
                cookies = CookieManager.getInstance().getCookie("http://$host").orEmpty()
            }
            val value = Regex("""(?<=$key=)(.+?)(?=;)""").find(cookies)?.value?.trim().orEmpty()
            return@createJSFunction context.createJSString(value)
        })
        //构建Cookie存储功能
        runtime.globalObject.setProperty("SET_COOKIE", runtime.createJSFunction() { context, args ->
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setCookie(".${host}", args[0].cast(JSString::class.java).string)
            cookieManager.flush()
            return@createJSFunction context.createJSUndefined()
        })
        //构建本地存错
        runtime.globalObject.setProperty(
            "LOCAL_STORAGE",
            runtime.createJSFunction() { context, args ->
                when (args[0].cast(JSNumber::class.java).int) {
                    //getItem
                    0 -> return@createJSFunction context.createJSString(
                        LocalStorage.callback?.getItem(
                            host,
                            args[1].cast(JSString::class.java).string
                        )
                    )
                    //setItem
                    1 -> {
                        LocalStorage.callback?.setItem(
                            host,
                            args[1].cast(
                                JSString::class.java
                            ).string,
                            args[2].cast(
                                JSString::class.java
                            ).string
                        )
                        return@createJSFunction context.createJSUndefined()
                    }
                    //removeItem
                    2 -> LocalStorage.callback?.removeItem(
                        host,
                        args[1].cast(
                            JSString::class.java
                        ).string
                    )
                    //clear
                    3 -> LocalStorage.callback?.clear(host)
                    //keys
                    4 -> {
                        val keys = LocalStorage.callback?.key(host).orEmpty()
                        val array = context.createJSArray()
                        keys.forEachIndexed { index, key ->
                            array.setProperty(
                                index,
                                context.createJSString(key)
                            )
                        }
                        return@createJSFunction array
                    }
                    //length
                    5 -> return@createJSFunction context.createJSNumber(
                        LocalStorage.callback?.length(host) ?: 0
                    )
                }
                return@createJSFunction context.createJSUndefined()
            })
        runtime.evaluate(
            "var localStorage = {\n" +
                    "  getItem: function(key) { return LOCAL_STORAGE(0, key) },\n" +
                    "  setItem: function(key, value) { LOCAL_STORAGE(1, key, value) },\n" +
                    "  removeItem: function(key) { return LOCAL_STORAGE(2, key) },\n" +
                    "  clear: function() { return LOCAL_STORAGE(3) },\n" +
                    "  key: LOCAL_STORAGE(4),\n" +
                    "  length: LOCAL_STORAGE(5),\n" +
                    "}", filename
        )
        //构建GET/PUT/POST/DELETE/PATCH/HEAD/OPTIONS/TRACE方法
        Connection.Method.values().forEach { method ->
            runtime.globalObject.setProperty(
                method.name,
                runtime.createJSFunction() { context, args ->
                    return@createJSFunction context.createJSString(
                        evaluateRequest(0x01, method, args)
                    )
                })
        }
        Connection.Method.values().forEach { method ->
            runtime.globalObject.setProperty(
                "JSOUP_${method.name}",
                runtime.createJSFunction() { context, args ->
                    return@createJSFunction context.createJSString(
                        evaluateRequest(0x02, method, args)
                    )
                })
        }
        //构建编码解码
        runtime.globalObject.setProperty("ENCODE", runtime.createJSFunction() { context, args ->
            if (args.size == 0) return@createJSFunction context.createJSString("")
            val charset = if (args.size == 1) "utf8" else try {
                args[1].cast(JSString::class.java).string
            } catch (e: Exception) {
                "utf8"
            }
            val content = args[0].cast(JSString::class.java).string
            if (charset.lowercase(Locale.CHINESE) == "base64") {
                return@createJSFunction context.createJSString(
                    String(
                        Base64.encode(
                            content.toByteArray(),
                            Base64.DEFAULT
                        )
                    )
                )
            }
            return@createJSFunction context.createJSString(URLEncoder.encode(content, charset))
        })
        runtime.globalObject.setProperty("DECODE", runtime.createJSFunction() { context, args ->
            if (args.size == 0) return@createJSFunction context.createJSString("")
            val charset = if (args.size == 1) "utf8" else try {
                args[1].cast(JSString::class.java).string
            } catch (e: Exception) {
                "utf8"
            }
            val content = args[0].cast(JSString::class.java).string
            if (charset.lowercase(Locale.CHINESE) == "base64") {
                return@createJSFunction context.createJSString(
                    String(
                        Base64.decode(
                            content,
                            Base64.DEFAULT
                        )
                    )
                )
            }
            return@createJSFunction context.createJSString(URLDecoder.decode(content, charset))
        })
        //构建SELECT方法并拓展String.select(query)
        runtime.globalObject.setProperty("SELECT", runtime.createJSFunction() { context, args ->
            val document = Jsoup.parse(args[0].cast(JSString::class.java).string)
            val cssQuery = args[1].cast(JSString::class.java).string
            val elements = document.select(cssQuery)
            val array = context.createJSArray()
            elements.forEachIndexed { index, element ->
                array.setProperty(index, context.createJSString(element.toString()))
            }
            return@createJSFunction array
        })
        runtime.evaluate(
            "let HTML = { parse: function(html){ return function(query) { return SELECT(html, query);}}}",
            filename
        )
        //构建REMOVE方法并拓展String.remove(query)
        runtime.globalObject.setProperty("REMOVE", runtime.createJSFunction() { context, args ->
            val html = args[0].castString()
            val document = Jsoup.parseBodyFragment(html).body().child(0)
            try {
                val array = args[1].cast(JSArray::class.java)
                (0 until array.length).forEach { index ->
                    document.select(array.getProperty(index).cast(JSString::class.java).string)
                        .remove()
                }
            } catch (e: JSDataException) {
                document.select(args[1].cast(JSString::class.java).string).remove()
            }
            return@createJSFunction context.createJSString(document.outerHtml())
        })
        runtime.evaluate(
            "String.prototype.remove=function(key){ return REMOVE(this.valueOf(), key); }",
            filename
        )
        runtime.evaluate(
            "Array.prototype.remove=function(key){ return REMOVE(this.valueOf(), key); }",
            filename
        )
        //构建TEXT方法并拓展String.attr(key)
        runtime.globalObject.setProperty("ATTR", runtime.createJSFunction() { context, args ->
            val html = args[0].castString()
            val document = Jsoup.parseBodyFragment(html).body()
            if (document.childrenSize() == 0) {
                return@createJSFunction context.createJSString("")
            }
            val attributeKey = args[1].cast(JSString::class.java).string
            val value = document.child(0).attr(attributeKey).trim()
            return@createJSFunction context.createJSString(value)
        })
        runtime.evaluate(
            "String.prototype.attr=function(key){ return ATTR(this.valueOf(), key); }",
            filename
        )
        runtime.evaluate(
            "Array.prototype.attr=function(key){ return ATTR(this.valueOf(), key); }",
            filename
        )
        //构建TEXT方法并拓展String.text()
        runtime.globalObject.setProperty("TEXT", runtime.createJSFunction() { context, args ->
            val html = args[0].castString()
            if (html.isBlank()) return@createJSFunction context.createJSString("")
            val document = Jsoup.parseBodyFragment(html).body().child(0)
            return@createJSFunction context.createJSString(document.text().trim())
        })
        runtime.evaluate(
            "String.prototype.text=function(){ return TEXT(this.valueOf()); }",
            filename
        )
        runtime.evaluate("Array.prototype.text=function(){ return TEXT(this); }", filename)
        //构建console.log打印
        runtime.globalObject.setProperty("LOG", runtime.createJSFunction() { context, args ->
            Console.println(args[0].cast(JSString::class.java).string)
            return@createJSFunction context.createJSUndefined()
        })
        runtime.evaluate(
            "const console = {log: function(arg) { LOG(typeof arg !== 'object' ? String(arg) : JSON.stringify(arg)) }}",
            filename
        )
        //参数提取
        runtime.evaluate(
            "String.prototype.query=function(variable) { let index = this.valueOf().indexOf(\"?\"); if(index > -1) { var vars = this.valueOf().substring(index + 1).split(\"&\"); for (var i=0;i<vars.length;i++) { var pair = vars[i].split(\"=\"); if(pair[0] == variable){return pair[1];} } return '';} else return '';}",
            filename
        )
        //注入书源
        try {
            runtime.evaluate(bookSource, filename)
            Console.println("", Console.Type.Build)
        } catch (e: Exception) {
            Log.e("NetworkBridge", "runtime.evaluate error, filename: $filename", e)
            Console.println(e.message.orEmpty(), Console.Type.Build)
        }
    }

    /**
     * 构建通用请求
     */
    private fun evaluateRequest(
        client: Int,
        method: Connection.Method,
        args: Array<out JSValue>
    ): String {
        try {
            if (args.isEmpty()) return ""
            val url = args[0].cast(JSString::class.java).string
            val config = args.getOrNull(1)?.cast(JSObject::class.java)
            var data: String? = null
            val headers = mutableListOf<String>()
            try {
                data = config?.getProperty("data")?.cast(JSString::class.java)?.string
            } catch (e: JSDataException) {
                //忽略数值错误
            }
            try {
                val array = config?.getProperty("headers")?.cast(JSArray::class.java)
                (0 until (array?.length ?: 0)).forEach { index ->
                    headers.add(
                        array?.getProperty(index)?.cast(JSString::class.java)?.string.orEmpty()
                    )
                }
            } catch (e: JSDataException) {
                Log.w("NetWorkBridge", "getProperty headers error", e)
                //忽略数值错误
            }
            //zip文件提取
            val unzipFilename = try {
                config?.getProperty("zip")?.cast(JSString::class.java)?.string
            } catch (e: JSDataException) {
                null
            }
            val result = if (client == 0x01) {
                request(method, url, data, headers, unzipFilename)
            } else {
                requestJsoup(method, url, data, headers)
            }.trim().trim { it == '\ufeff' }
            Console.println(result, Console.Type.Response)
            return result
        } catch (e: Exception) {
            Console.println("```${e::class.java.simpleName}\n\n${e.message.orEmpty()}\n```")
            return ""
        }
    }

    /**
     * 提取字符串
     */
    fun JSValue.castString(): String {
        return try {
            val array = cast(JSArray::class.java)
            if (array.length == 0) return ""
            array.getProperty(0).cast(JSString::class.java).string
        } catch (e: Exception) {
            try {
                cast(JSString::class.java).string
            } catch (e: Exception) {
                ""
            }
        }
    }
}
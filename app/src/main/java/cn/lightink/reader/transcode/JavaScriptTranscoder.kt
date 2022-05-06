package cn.lightink.reader.transcode

import android.util.Log
import cn.lightink.reader.transcode.ContentParser
import cn.lightink.reader.transcode.NetworkBridge
import cn.lightink.reader.transcode.NetworkBridge.castString
import cn.lightink.reader.transcode.entity.*
import cn.lightink.reader.transcode.ktx.decodeJson
import com.hippo.quickjs.android.*
import java.io.File


/**
 * 转码器
 * @property bookSource 书源
 */
class JavaScriptTranscoder(private val host: String, private val bookSource: String) {

    private val filename = "run.js"
    private val quickJS = QuickJS.Builder().build()

    /**
     * 书源信息
     */
    fun bookSource() = javaScript<BookSourceInfo?> { context ->
        try {
            val json = context.globalObject.getProperty("bookSource").cast(JSString::class.java).string
            return@javaScript json.decodeJson<BookSourceInfo>()
        } catch (e: Exception) {
            Log.e("JavaScriptTranscoder", "", e)
            null
        }
    }

    /**
     * 搜索
     */
    fun search(key: String) = javaScript { context ->
        val response = context.evaluate("search('$key');", filename, String::class.java)
        val results = response.decodeJson<List<SearchResult>>()
        return@javaScript results.filter {
            it.author.isNotBlank() && (it.name.contains(key) || it.author.contains(key))
        }
    }

    /**
     * 详情
     */
    fun detail(url: String) = javaScript<BookDetail?> { context ->
        val response = context.evaluate("detail('$url');", filename, String::class.java)
        return@javaScript response.decodeJson<BookDetail>()
    }

    /**
     * 目录
     */
    fun catalog(url: String) = javaScript<List<Chapter>?> { context ->
        val response = context.evaluate("catalog('$url');", filename, String::class.java)
        return@javaScript response.decodeJson<List<Chapter>>()
    }

    /**
     * 章节
     */
    fun chapter(chapter: Chapter, output: File? = null) = javaScript<String> { context ->
        try {
//            if (chapter.url.isBlank()) return@javaScript "## ${chapter.name}"
            if (chapter.url.isBlank()) return@javaScript ""
            val response =
                context.evaluate("chapter('${chapter.url}');", filename, JSValue::class.java)
                    .castString()
            val markdown = ContentParser.read(chapter.url, response, output)
//            if (markdown.isNotBlank()) "## ${chapter.name}\n${markdown}" else ""
            if (markdown.isNotBlank()) markdown else ""
        } catch (e: JSEvaluationException) {
            if (e.message?.contains("code") == true) {
                throw e.message.orEmpty().removePrefix("Throw:").decodeJson<TranscodeException>()
            }
            throw e
        }
    }

    /**
     * 登录
     */
    fun login(args: Array<String>) = javaScript<String> { context ->
        val array = StringBuilder()
        args.forEachIndexed { index, arg ->
            if (index > 0) array.append(",")
            array.append("'$arg'")
        }
        return@javaScript context.evaluate("login([$array]);", filename, String::class.java) ?: ""
    }

    /**
     * 个人资料
     */
    fun profile() = javaScript<Profile?> { context ->
        val json = context.evaluate("profile();", filename, String::class.java)
        val profile = json.decodeJson<Profile>()
        if (profile.basic.any { it.value.isBlank() }) {
            return@javaScript null
        }
        return@javaScript profile
    }

    /**
     * 排行榜
     */
    suspend fun rank(title: String, category: String, page: Int) = javaScript { context ->
        val response =
            context.evaluate("rank('$title', '$category', $page);", filename, String::class.java)
        return@javaScript response.decodeJson<PagingBooks>()
    }


    /**
     * 拓展方法 - 权限
     */
    fun permission(script: String) = javaScript { context ->
        return@javaScript context.evaluate("${script}();", filename, Boolean::class.java)
    }

    /**
     * 拓展方法 - 图书列表
     */
    fun books(script: String, page: Int = 0) = javaScript { context ->
        val response = context.evaluate("${script}($page);", filename, String::class.java)
        return@javaScript response.decodeJson<PagingBooks>()
    }

    /**
     * 构建JavaScript解释器
     */
    private fun <T> javaScript(callback: (JSContext) -> T): T {
        quickJS.createJSRuntime().use { runtime ->
            runtime.createJSContext().use { context ->
                NetworkBridge.inject(context, host, bookSource)
                return callback.invoke(context)
            }
        }
    }

}
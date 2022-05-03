package cn.lightink.reader.net

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import androidx.lifecycle.MutableLiveData
import cn.lightink.reader.ktx.encode
import cn.lightink.reader.ktx.fromJson
import cn.lightink.reader.ktx.md5
import cn.lightink.reader.ktx.toJson
import cn.lightink.reader.model.BookRank
import cn.lightink.reader.model.BookSource
import cn.lightink.reader.model.Chapter
import cn.lightink.reader.module.*
import cn.lightink.reader.module.booksource.BookSourceJson
import cn.lightink.reader.module.booksource.BookSourceParser
import cn.lightink.reader.module.booksource.BookSourceResponse
import cn.lightink.reader.module.booksource.SearchMetadata
import com.yanzhenjie.andserver.AndServer
import com.yanzhenjie.andserver.Server
import com.yanzhenjie.andserver.annotation.*
import com.yanzhenjie.andserver.framework.HandlerInterceptor
import com.yanzhenjie.andserver.framework.body.FileBody
import com.yanzhenjie.andserver.framework.config.WebConfig
import com.yanzhenjie.andserver.framework.handler.RequestHandler
import com.yanzhenjie.andserver.framework.website.AssetsWebsite
import com.yanzhenjie.andserver.http.HttpRequest
import com.yanzhenjie.andserver.http.HttpResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.io.File
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern


@RestController
class AirPlayService : Service(), Server.ServerListener {

    private val hostLiveData = MutableLiveData<String>()
    private val binder = AirPlayBinder()
    private var bookSourceParser: BookSourceParser? = null
    private lateinit var server: Server

    override fun onBind(intent: Intent?) = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onCreate() {
        super.onCreate()
        server = AndServer.serverBuilder(applicationContext).inetAddress(IPAddress.getLocalIPAddress()).port(8888).timeout(10, TimeUnit.SECONDS).listener(this).build()
        startForeground(NotificationHelper.AIR_PLAY, NotificationHelper.airPlay(applicationContext))
        start()
    }

    override fun onDestroy() {
        super.onDestroy()
        stop()
    }

    @GetMapping("/api/bookshelves")
    fun getBookshelves() = Room.bookshelf().getAllImmediately().map { bookshelf ->
        mapOf("id" to bookshelf.id, "name" to bookshelf.name, "current" to (bookshelf.id == Preferences.get(Preferences.Key.BOOKSHELF, 1L)))
    }.toJson()

    @GetMapping("/api/books")
    fun getBooks(@QueryParam("bookshelf") bookshelf: String) = Room.book().getAll(bookshelf.toLongOrNull() ?: 0).map { book ->
        mapOf("id" to book.objectId, "name" to book.name, "author" to book.author, "cover" to "/cover/${book.objectId}", "updatedAt" to book.updatedAt)
    }.toJson()

    @GetMapping("/api/book")
    fun getBook(@QueryParam("id") objectId: String) = Room.book().get(objectId).let { book ->
        mapOf("id" to book.objectId, "name" to book.name, "author" to book.author, "cover" to "/cover/${book.objectId}", "bookshelf" to book.bookshelf, "chapter" to book.chapter, "chapterName" to book.chapterName, "chapterTotal" to book.catalog, "speed" to book.speed, "state" to book.state, "time" to book.time, "updatedAt" to book.updatedAt)
    }.toJson()

    @GetMapping("/api/book/catalog")
    fun getBookCatalog(@QueryParam("id") objectId: String) = Room.book().get(objectId).let { book ->
        File(book.path, MP_FILENAME_CATALOG).readLines().mapIndexed { i, line -> Chapter(i, line) }
    }.toJson()

    @ExperimentalCoroutinesApi
    @GetMapping("/api/book/chapter")
    fun getBookChapter(@QueryParam("id") objectId: String, @QueryParam("href") href: String) = Room.book().get(objectId).let { book ->
        val markdown = File(book.path, "$MP_FOLDER_TEXTS/${href.encode().let { if (it.length < 0xFF) it else it.md5() }}.md")
        var content = when {
            markdown.exists() -> markdown.readText()
            book.hasBookSource() -> when (val bookSource = book.getBookSource()) {
                is BookSourceParser -> bookSource.findContent(href, "${book.path}/$MP_FOLDER_IMAGES")
                else -> EMPTY
            }
            else -> EMPTY
        }
        //缓存网络数据
        if (markdown.parentFile?.exists() == true && markdown.exists().not() && content.isNotBlank() && content != GET_FAILED_NET_THROWABLE) markdown.writeText(content)
        content = content.replace("""\n+\s+\n+""".toRegex(), "\n\n")
        content = content.replace("""\n+""".toRegex(), "\n")
        mapOf("content" to content)
    }.toJson()

    @GetMapping("/cover/{id}")
    fun getCover(@PathVariable("id") objectId: String) = FileBody(File(Room.book().get(objectId).cover))

    @PostMapping("/dev/debug")
    fun debug(@RequestBody json: String) = try {
        bookSourceParser = BookSourceParser(json.fromJson())
        mapOf("message" to "调试书源已更新").toJson(true)
    } catch (e: Exception) {
        mapOf("message" to "书源格式错误").toJson(true)
    }

    @PostMapping("/dev/install")
    fun install(@RequestBody json: String) = try {
        val bookSource = json.fromJson<BookSourceJson>()
        bookSourceParser = BookSourceParser(bookSource)
        when {
            bookSource.name.isBlank() -> mapOf("message" to "书源未命名")
            Room.bookSource().isInstalled(bookSource.url) -> mapOf("message" to "书源已安装，无法覆盖")
            else -> Room.bookSource().install(BookSource(0, bookSource.name, bookSource.url, bookSource.version, !bookSource.rank.isNullOrEmpty(), bookSource.auth != null, EMPTY,  0F, json)).let {
                if (!bookSource.rank.isNullOrEmpty()) {
                    Room.bookRank().insert(BookRank(bookSource.url, bookSource.name))
                }
                mapOf("message" to "书源已安装")
            }
        }.toJson(true)
    } catch (e: Exception) {
        mapOf("message" to "格式错误：${e.message}").toJson(true)
    }

    @GetMapping("/dev/debug/auto")
    fun debugAuto(@QueryParam("key") key: String) = try {
        if (bookSourceParser != null) {
            val search = bookSourceParser?.search(key)
            if (search.isNullOrEmpty()) {
                mapOf("search" to "无结果").toJson(true)
            } else {
                val detail = bookSourceParser!!.findDetail(search.first())?.apply { if (catalog is BookSourceResponse) catalog = search.first().detail }
                if (detail == null) {
                    mapOf("search" to search.first(), "detail" to "无结果").toJson(true)
                } else {
                    val catalog = bookSourceParser!!.findCatalog(detail)
                    if (catalog.isEmpty()) {
                        mapOf("detail" to detail, "catalog" to "无结果").toJson(true)
                    } else {
                        val content = bookSourceParser!!.findContent(catalog[minOf(5, catalog.size)].url)
                        mapOf("detail" to detail, "chapter" to catalog[minOf(5, catalog.size)], "content" to content).toJson(true)
                    }
                }
            }
        } else {
            mapOf("message" to "请先调用/dev/debug设置调试书源").toJson(true)
        }
    } catch (e: Exception) {
        mapOf("message" to e.toString()).toJson(true)
    }

    @GetMapping("/dev/debug/search")
    fun debugSearch(@QueryParam("key") key: String) = try {
        if (bookSourceParser != null) {
            bookSourceParser?.search(key)?.toJson(true)
        } else {
            mapOf("message" to "请先调用/dev/debug设置调试书源").toJson(true)
        }
    } catch (e: Exception) {
        mapOf("message" to e.toString()).toJson(true)
    }

    @PostMapping("/dev/debug/detail")
    fun debugDetail(@RequestBody json: String) = try {
        if (bookSourceParser != null) {
            val book = json.fromJson<SearchMetadata>()
            bookSourceParser?.findDetail(book)?.apply {
                if (catalog is BookSourceResponse) catalog = book.detail
            }?.toJson(true)
        } else {
            mapOf("message" to "请先调用/dev/debug设置调试书源").toJson(true)
        }
    } catch (e: Exception) {
        mapOf("message" to e.toString()).toJson(true)
    }

    @PostMapping("/dev/debug/catalog")
    fun debugCatalog(@RequestBody json: String) = try {
        if (bookSourceParser != null) {
            bookSourceParser?.findCatalog(json.fromJson())?.toJson(true)
        } else {
            mapOf("message" to "请先调用/dev/debug设置调试书源").toJson(true)
        }
    } catch (e: Exception) {
        mapOf("message" to e.toString()).toJson(true)
    }

    @PostMapping("/dev/debug/chapter")
    fun debugChapter(@RequestBody json: String) = try {
        if (bookSourceParser != null) {
            mapOf("content" to bookSourceParser?.findContent((json.fromJson() as cn.lightink.reader.module.booksource.Chapter).url)).toJson(true)
        } else {
            mapOf("message" to "请先调用/dev/debug设置调试书源").toJson(true)
        }
    } catch (e: Exception) {
        mapOf("message" to e.toString()).toJson(true)
    }

    @GetMapping("/dev/debug/rank")
    fun debugRank() = try {
        if (bookSourceParser != null) {
            if (bookSourceParser?.bookSource?.rank?.isNotEmpty() == true) {
                val results = mutableMapOf<String, List<SearchMetadata>>()
                bookSourceParser!!.bookSource.rank.forEach { rank ->
                    var url = rank.url
                    if (rank.page > -1) url = url.replace("\${page}", rank.page.toString())
                    if (rank.categories.isNotEmpty()) url = url.replace("\${key}", rank.categories.first().key)
                    results[rank.title] = bookSourceParser!!.queryRank(url, rank)
                }
                results.toJson(true)
            } else {
                mapOf("message" to "正在调试的书源没有排行榜").toJson(true)
            }
        } else {
            mapOf("message" to "请先调用/dev/debug设置调试书源").toJson(true)
        }
    } catch (e: Exception) {
        mapOf("message" to e.toString()).toJson(true)
    }


    override fun onStarted() {
        hostLiveData.postValue("http://${server.inetAddress.hostAddress}:8888")
    }

    override fun onStopped() {

    }

    override fun onException(e: Exception?) {

    }

    //开启服务器
    fun start() {
        if (!server.isRunning) {
            server.startup()
        }
    }

    //关闭服务器
    private fun stop() {
        if (server.isRunning) {
            server.shutdown()
        }
    }

    /***********************************************************************************************
     * BINDER
     ***********************************************************************************************
     * 通信
     */
    inner class AirPlayBinder : Binder() {

        fun hostLiveData() = hostLiveData

        fun stop() {
            stopSelf()
        }
    }

}

@Interceptor
class LoggerInterceptor : HandlerInterceptor {
    override fun onIntercept(request: HttpRequest, response: HttpResponse, handler: RequestHandler): Boolean {
        response.setHeader("Access-Control-Allow-Origin", "*")
        response.setHeader("Server", "lightink")
        return false
    }
}

@Controller
class WebController {
    @GetMapping(path = ["/", "/shelf"])
    fun index() = "/index"
}

@Config
open class WebsiteConfig : WebConfig {
    override fun onConfig(context: Context?, delegate: WebConfig.Delegate?) {
        delegate?.addWebsite(AssetsWebsite(context!!, "/web"))
    }
}

object IPAddress {

    private val IPV4_PATTERN = Pattern.compile("^(" + "([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}" + "([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$")

    fun getLocalIPAddress(): InetAddress? {
        val enumeration = NetworkInterface.getNetworkInterfaces() ?: return null
        while (enumeration.hasMoreElements()) {
            val nif = enumeration.nextElement()
            val addresses = nif.inetAddresses ?: continue
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (!address.isLoopbackAddress && IPV4_PATTERN.matcher(address.hostAddress).matches()) {
                    return address
                }
            }
        }
        return null
    }
}
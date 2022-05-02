package cn.lightink.reader.controller

import android.content.Context
import android.graphics.Color
import android.webkit.URLUtil
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import cn.lightink.reader.R
import cn.lightink.reader.ktx.src
import cn.lightink.reader.model.*
import cn.lightink.reader.module.EMPTY
import cn.lightink.reader.module.Preferences
import cn.lightink.reader.module.Room
import cn.lightink.reader.module.TimeFormat
import cn.lightink.reader.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import org.w3c.dom.Document
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.max

class FeedController : ViewModel() {

    private val documentFactory by lazy { DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true } }
    val groupLiveData = Room.feedGroup().getAll()
    val verifyResultLiveData = MediatorLiveData<FeedVerifyResult>()
    var startedFlowLink = EMPTY
    var isStarted = false
    lateinit var theme: Theme

    fun queryFeedsByGroupId(groupId: Long) = Room.feed().getByGroupId(groupId)

    fun queryFlows(feeds: List<Feed>) = Room.flow().getAllByFeed(feeds.map { it.id })

    fun queryLoves() = Room.flow().getLoved()

    fun queryFlowLinks(feeds: List<Feed>) = Room.flow().getLinksByFeed(feeds.map { it.id })

    /**
     * 删除指定频道分组
     */
    fun deleteFeedGroup(group: FeedGroup) = liveData<String>(Dispatchers.IO) {
        if (Room.feed().isNotEmpty(group.id)) return@liveData emit("请先取消订阅分组内的全部频道")
        Room.feedGroup().remove(group)
        emit(EMPTY)
    }

    /**
     * 清理已读
     */
    fun clean(groupId: Long) {
        if (groupId == -1L) {
            //清理全部
            Room.flow().clearAll()
        } else {
            //清理指定分组
            Room.flow().clear(Room.feed().getByGroupIdImmediately(groupId))
        }
    }

    /**
     * 删除指定文章
     */
    fun deleteFlow(flow: Flow) = viewModelScope.launch(Dispatchers.IO) {
        Room.flow().delete(flow)
    }

    /**
     * 是否存在订阅频道
     */
    fun hasFeed(feed: Feed): Boolean {
        return Room.feed().has(feed.link)
    }

    /**
     * 查询Flow
     */
    fun queryFlow(context: Context, flowLink: String?) = liveData(Dispatchers.IO) {
        val flow = Room.flow().get(flowLink.orEmpty()) ?: return@liveData emit(null)
        val feedName = if (flow.author.isEmpty()) flow.feedName else "${flow.feedName}\u2000${flow.author}"
        val fontSize = Preferences.get(Preferences.Key.FONT_SIZE, 17F)
        var css = context.resources.openRawResource(R.raw.feed).bufferedReader().use { it.readText() }
//        if (Preferences.get(Preferences.Key.FLOW_FONT, false)) {
//        css = "@font-face {\nfont-family: 'custom';\nsrc: url('file://${FontModule.mCurrentFont.path.absolutePath}') format('truetype');\nfont-weight: normal;\nfont-style: normal;\n}\n" + css
//        css = css.replace("%font%", "custom")
//        } else {
        css = css.replace("%font%", "sans-serif")
//        }
        css = css.replace("%font-size-big%", (fontSize * 1.4F).toString())
        css = css.replace("%font-size-normal%", fontSize.toString())
        css = css.replace("%font-size-small%", (fontSize * 0.8F).toString())
        css = css.replace("%foreground%", "rgb(${Color.red(theme.foreground)},${Color.green(theme.foreground)},${Color.blue(theme.foreground)})")
        css = css.replace("%content%", "rgb(${Color.red(theme.content)},${Color.green(theme.content)},${Color.blue(theme.content)})")
        css = css.replace("%control%", "rgb(${Color.red(theme.control)},${Color.green(theme.control)},${Color.blue(theme.control)})")
        css = css.replace("%secondary%", "rgb(${Color.red(theme.secondary)},${Color.green(theme.secondary)},${Color.blue(theme.secondary)})")
        flow.summary = "<html><head><style type=\"text/css\">$css</style></head><body><h1 class=\"title\">${flow.title}</h1><p class=\"author\">${feedName}&nbsp;&nbsp;<span>${TimeFormat.format(flow.date)}</span></p>${flow.summary}</body></html>"
        emit(flow)
    }

    /**
     * 设置已读
     */
    fun isAlreadyRead(flowLink: String?) = viewModelScope.launch(Dispatchers.IO) {
        val flow = Room.flow().get(flowLink.orEmpty()) ?: return@launch
        if (flow.read.not()) {
            Room.flow().update(flow.apply { read = true })
        }
    }

    /**
     * 收藏
     */
    fun collect(flowLink: String?) = liveData(Dispatchers.IO) {
        val flow = Room.flow().get(flowLink.orEmpty()) ?: return@liveData emit(null)
        if (flow.love && flow.feed == 0L) {
            //删除无频道依赖的文章
            Room.flow().delete(flow.apply { love = !love })
        } else {
            Room.flow().update(flow.apply { love = !love })
        }
        if (!Room.feedGroup().has("收藏")) {
            Room.feedGroup().insert(FeedGroup("收藏", 0))
        }
        emit(flow.love)
    }


    /**
     * 订阅频道
     */
    fun subscribeFeed(groupId: Long, feed: Feed, flows: List<Flow>) {
        feed.group = groupId
        feed.id = Room.feed().insert(feed.apply { date = max(date, flows.first().date) })
        flows.forEach { it.feed = feed.id }
        Room.flow().insert(*flows.toTypedArray())
    }

    /**
     * 取消订阅
     */
    fun unsubscribeFeed(link: String) {
        val feed = Room.feed().getByLink(link) ?: return
        Room.flow().removeByFeed(feed.id)
        Room.feed().remove(feed)
    }

    /**
     * 移动频道至新分组
     */
    fun moveFeedToGroup(feed: Feed, groupId: Long) {
        if (feed.group == groupId) return
        feed.group = groupId
        Room.feed().update(feed)
    }

    /**
     * 已添加至书架
     */
    fun hasPushpin(group: FeedGroup) = (Room.feedGroup().get(group.id)?.bookshelf ?: 0L) != 0L

    /**
     * 添加分组到书架
     */
    fun pushpin(group: FeedGroup, bookshelf: Bookshelf) = liveData(Dispatchers.IO) {
        Room.feedGroup().unPushpin(bookshelf.id)
        Room.feedGroup().update(group.apply { this.bookshelf = bookshelf.id })
        emit(true)
    }

    /**
     * 添加分组到书架
     */
    fun unPushpin(group: FeedGroup) = liveData(Dispatchers.IO) {
        Room.feedGroup().update(group.apply { this.bookshelf = 0 })
        emit(true)
    }

    /**
     * 验证订阅频道
     * @param link   地址
     */
    fun verify(link: String, upload: Boolean): MediatorLiveData<FeedVerifyResult> {
        viewModelScope.launch(Dispatchers.IO) {
            val response = Http.get<ByteArray>(link)
            if (response.isSuccessful && response.data?.isNotEmpty() == true) {
                try {
                    val document = documentFactory.newDocumentBuilder().parse(ByteArrayInputStream(response.data))
                    val feed = Feed(link, EMPTY)
                    val flows = parseFlowList(feed, document)
                    verifyResultLiveData.postValue(FeedVerifyResult(EMPTY, feed, flows))
                } catch (e: Exception) {
                    //非法格式，由于用户错误的请求了非RSS地址导致
                    verifyResultLiveData.postValue(FeedVerifyResult("网址有误：${e.message}"))
                }
            } else {
                //访问失败，由于网址错误或客户端网络问题
                verifyResultLiveData.postValue(FeedVerifyResult("访问失败：${response.message}"))
            }
        }
        return verifyResultLiveData
    }

    /**
     * 检查更新
     */
    fun checkUpdate() = viewModelScope.launch(Dispatchers.IO) {
        Room.feed().getAll().forEach { feed ->
            launch {
                val response = Http.get<ByteArray>(feed.link)
                if (response.isSuccessful && response.data?.isNotEmpty() == true) {
                    try {
                        val document = documentFactory.newDocumentBuilder().parse(ByteArrayInputStream(response.data))
                        val flows = parseFlowList(feed, document)
                        if (flows.isNotEmpty() && hasFeed(feed)) {
                            Room.feed().update(feed.apply { date = max(date, flows.first().date) })
                            Room.flow().insert(*flows.toTypedArray())
                        }
                    } catch (e: Exception) {
                        //非法格式，由于用户错误的请求了非RSS地址导致
                    }
                }
            }
        }
    }

    private fun parseFlowList(feed: Feed, document: Document): List<Flow> {
        //根元素 RSS/Channel Atom/Feed
        val nodes = when {
            document.getElementsByTagName("feed").length == 1 -> document.getElementsByTagName("feed").item(0).childNodes
            document.getElementsByTagName("channel").length == 1 -> document.getElementsByTagName("channel").item(0).childNodes
            else -> return emptyList()
        }
        var flowList = mutableListOf<Flow>()
        (0 until nodes.length).map { nodes.item(it) }.forEach { node ->
            when (node.nodeName) {
                "title" -> feed.name = node.textContent
                "subtitle", "description" -> feed.summary = node.textContent
                "entry", "item" -> parseFlow(node.childNodes, feed)?.run { flowList.add(this) }
            }
        }
        //排序
        flowList = flowList.sortedByDescending { it.date }.filter { it.date > feed.date }.toMutableList()
        return flowList
    }

    /**
     * 解析信息流
     */
    private fun parseFlow(list: NodeList, feed: Feed): Flow? {
        val flow = Flow(EMPTY, EMPTY, EMPTY, EMPTY, null, System.currentTimeMillis(), feed.id, feed.name)
        var enclosure = ""
        //遍历节点读取信息
        (0 until list.length).map { list.item(it) }.forEach { node ->
            when (node.nodeName) {
                //链接
                "link" -> flow.link = node.attributes.getNamedItem("href")?.textContent ?: node.textContent
                //标题
                "title" -> flow.title = node.textContent
                //作者
                "author" -> flow.author = node.textContent
                //日期
                "updated", "pubDate" -> flow.date = TimeFormat.parse(node.textContent)
                //摘要
                "summary", "description", "content:encoded" -> flow.summary = node.textContent.replace("""<iframe.+?(/iframe>|/>)""".toRegex(), EMPTY).let {
                    if (URLUtil.isNetworkUrl(enclosure)) "<audio src=\"$enclosure\"  controls=\"controls\"></audio> $it" else it
                }
                //封面
                "image" -> flow.cover = node.textContent
                "itunes:image" -> if (!URLUtil.isNetworkUrl(flow.cover)) flow.cover = node.attributes.getNamedItem("href")?.textContent.orEmpty()
                //多媒体
                "enclosure" -> enclosure = node.attributes.getNamedItem("url")?.textContent.orEmpty()
            }
        }
        //非网络链接需要补全host地址
        if (!URLUtil.isNetworkUrl(flow.link)) {
            val element = Jsoup.parseBodyFragment(flow.link)
            var node = element.selectFirst("*[src]")
            if (node != null && node.attr("src").isNotBlank()) {
                flow.link = node.attr("src")
            } else {
                node = element.selectFirst("*[href]")
                if (node != null && node.attr("href").isNotBlank()) {
                    flow.link = node.attr("href")
                }
            }
        }
        //尝试从摘要中提取图片
        if (flow.cover.isNullOrBlank()) flow.cover = Jsoup.parse(flow.summary).selectFirst("img")?.src(feed.link)
        //如果上一步得出的图片非网络链接则取消使用
        if (!URLUtil.isNetworkUrl(flow.cover)) flow.cover = EMPTY
        //无标题或链接视为无效
        if (flow.title.isBlank() || flow.link.isBlank()) return null
        return flow
    }

}
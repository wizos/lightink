package cn.lightink.reader.controller

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.net.Uri
import android.text.StaticLayout
import android.text.TextPaint
import android.util.DisplayMetrics
import android.util.Size
import android.util.SparseIntArray
import android.util.TypedValue
import android.view.ViewConfiguration
import androidx.core.util.getOrDefault
import androidx.lifecycle.*
import androidx.viewpager2.widget.ViewPager2
import cn.lightink.reader.ktx.*
import cn.lightink.reader.model.*
import cn.lightink.reader.module.*
import cn.lightink.reader.module.booksource.BookSourceJson
import cn.lightink.reader.module.booksource.BookSourceParser
import cn.lightink.reader.module.booksource.BookSourceSearchResponse
import cn.lightink.reader.module.booksource.DetailMetadata
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.gyf.immersionbar.ktx.statusBarHeight
import kotlinx.coroutines.*
import java.io.File
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import cn.lightink.reader.R

class ReaderController : ViewModel(), LifecycleObserver {

    //书源
    private var bookSource: BookSourceParser? = null

    //图书
    lateinit var book: Book

    //目录
    var catalog: List<Chapter> = emptyList()

    //书签
    var bookmarks: LiveData<List<Bookmark>> = MutableLiveData()

    //已读
    private var hasReadList = mutableListOf<Int>()

    //预览模式
    var preview = false

    //liveData
    private val list = CopyOnWriteArrayList<Page>()
    private val pageLive = MutableLiveData<List<Page>>()

    //初始化
    val initializedLive = MutableLiveData<List<Page>?>()

    //阅读器显示
    val display = Display()

    //画笔
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    //图片文字测量画笔
    private val figurePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    //页码
    val currentPageLiveData = MutableLiveData<Page>()
    var pageNumber = SparseIntArray()
    var currentPage: Page? = null

    //任务状态
    private val isNextTask = AtomicBoolean(false)
    private val isPreviousTask = AtomicBoolean(false)
    private var nextJob: Job? = null
    private var previousJob: Job? = null

    /**
     * 初始化
     */
    fun attach(activity: Activity): Boolean {
        //检查参数传递
        if (activity.intent.hasExtra(INTENT_BOOK).not()) return false
        //读取图书对象
        book = activity.intent.getParcelableExtra(INTENT_BOOK) ?: return false
        preview = book.bookshelf < 0
        if (!preview) book = Room.book().get(book.objectId)
        //检查是否存在目录文件
        if (File(book.path, MP_FILENAME_CATALOG).exists().not()) return false
        //读取书源
        if (File(book.path, MP_FILENAME_BOOK_SOURCE).exists()) {
            bookSource = book.getBookSource()
        }
        //读取目录
        catalog = File(book.path, MP_FILENAME_CATALOG).readLines().mapIndexed { i, line -> Chapter(i, line) }
        viewModelScope.launch(Dispatchers.IO) {
            hasReadList.clear()
            hasReadList.addAll(Room.bookRecord().query(book.objectId))
            bookmarks = Room.bookmark().getAll(book.objectId)
            purifyList.apply { clear() }.addAll(getBookPurifyList())
        }
        //主题
        theme = UIModule.getConfiguredTheme(activity)
        return true
    }

    /***********************************************************************************************
     * 生命周期、数据统计、数据同步
     **********************************************************************************************/
    private var timestamp = System.currentTimeMillis()

    /**
     * 章节是否已读
     */
    fun isHaveRead(chapter: Chapter) = hasReadList.contains(chapter.index)

    /**
     * 图书读完
     */
    fun isHaveRead() = hasReadList.size / catalog.size.toFloat() > 0.8F

    /**
     * 统计
     */
    private fun statistics(page: Page? = null) = viewModelScope.launch(Dispatchers.IO) {
        val current = System.currentTimeMillis()
        val time = min(((current - timestamp) / MILLISECOND).toInt(), READ_MAX_PAGE_OF_SECOND)
        if (book.state == BOOK_STATE_UPDATE) book.state = BOOK_STATE_IDLE
        if (book.word == 0 && book.time > 0) book.word = (book.time * book.speed / 60).toInt()
        book.updatedAt = current
        book.time += time
        //当页低于2S时不计入平均速度
        if (time > 2) {
            book.word += page?.getTextTotal() ?: 0
            book.speed = book.word / (book.time / 60F)
        }
        //确保在读的章节名一定存在
        if (book.chapterName.isBlank()) book.chapterName = catalog.getOrNull(book.chapter)?.title.orEmpty()
        timestamp = System.currentTimeMillis()
        if (preview) return@launch
        //更新图书阅读时长
        Room.book().update(book)
        //更新主题使用时长
        Room.theme().get(theme.id)?.run {
            this.time += time
            Room.theme().update(this)
        }
        //记录已读章节
        if (page?.chapter != null && !isHaveRead(page.chapter) && Room.book().has(book.objectId)) {
            Room.bookRecord().insert(BookRecord(book.objectId, page.chapter.index))
            hasReadList.add(page.chapter.index)
        }
        //校验上传条件
    }

    /**
     * 自动同步进度，PID与云端进度
     */
    fun autoSynchronize(): LiveData<BookSyncProgress> {
        return MutableLiveData()
    }

    /**
     * 同步来自服务器的进度
     */
    fun syncProgress(progress: BookSyncProgress) {
        book.chapter = progress.chapter
        book.chapterProgress = progress.progress
        book.chapterName = progress.title
        book.time = (progress.total * 60).toInt()
        book.speed = progress.speed.toFloat()
        book.finishedAt = progress.endAt
        Room.book().update(book)
        jump()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun onPause() {
        if (preview) return
        statistics()
    }

    override fun onCleared() {
        super.onCleared()
        if (preview) book.path.deleteRecursively()
    }

    /**
     * 章节变化
     */
    fun onPageChanged(page: Page, offset: Int) {
        currentPageLiveData.postValue(page)
        this.currentPage = page
        book.chapter = page.chapter.index
        book.chapterName = page.chapter.title
        book.chapterProgress = page.start + offset
        val theLoadedLastChapter = list.toList().lastOrNull()?.chapter?.index
        if (page.chapter.index == theLoadedLastChapter) {
            nextJob = loadNextChapter(page.chapter.index + 1)
        } else if (page.chapter.index == theLoadedLastChapter) {
            previousJob = loadPreviousChapter(page.chapter.index - 1)
        }
        statistics(page)
    }

    /**
     * 查询是否存在书签
     */
    fun hasBookmark(page: Page): MutableLiveData<Boolean> {
        val result = MutableLiveData<Boolean>()
        viewModelScope.launch(Dispatchers.IO) {
            result.postValue(Room.bookmark().has(book.objectId, page.chapter.index, page.start, page.end))
        }
        return result
    }

    /**
     * 添加或移除书签
     */
    fun addOrRemoveBookmark(): MutableLiveData<Boolean> {
        val result = MutableLiveData<Boolean>()
        viewModelScope.launch(Dispatchers.IO) {
            when {
                //错误情况
                currentPage == null || !Room.book().has(book.objectId) -> result.postValue(false)
                //添加书签
                !Room.bookmark().has(book.objectId, currentPage!!.chapter.index, currentPage!!.start, currentPage!!.end) -> {
                    Room.bookmark().insert(Bookmark(currentPage!!.chapter.index, currentPage!!.start, currentPage!!.chapter.title, currentPage!!.getSegment().orEmpty(), book.objectId))
                    result.postValue(true)
                }
                //删除书签
                else -> {
                    Room.bookmark().remove(book.objectId, currentPage!!.chapter.index, currentPage!!.start, currentPage!!.end)
                    result.postValue(false)
                }
            }
        }
        return result
    }

    /***********************************************************************************************
     * 广播接收器
     **********************************************************************************************/
    val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val calendar = Calendar.getInstance()
            // 在23:59分时，提交本次阅读时长
            if (calendar.get(Calendar.HOUR_OF_DAY) == 23 && calendar.get(Calendar.MINUTE) == 59) {
                onPause()
            }
        }
    }
    /***********************************************************************************************
     * 章节读取
     **********************************************************************************************/
    /**
     * 初始化
     */
    fun initialized() = initializedLive.value.isNullOrEmpty().not()

    /**
     * 跳章
     */
    fun jump(chapter: Int? = null, progress: Int = 0) {
        pageLive.postValue(null)
        chapter?.run {
            //有书源或原始文件的图书允许刷新
            if (chapter == book.chapter && (book.hasBookSource() || File(book.path, MP_FOLDER_FILES).exists())) {
                //刷新 如果章节缓存存在需删除
                val markdown = File(book.path, "$MP_FOLDER_TEXTS/${catalog[this].encodeHref}.md")
                if (markdown.exists()) markdown.delete()
            }
            book.chapter = chapter
            book.chapterProgress = progress
            if (!preview) Room.book().update(book)
        }
        loadChapter(true)
    }

    /**
     * 刷新当前章节
     */
    fun refresh() {
        val chapter = catalog.getOrNull(book.chapter) ?: return
        File(book.path, "$MP_FOLDER_TEXTS/${chapter.encodeHref}.md").delete()
        jump()
    }

    /**
     * 加载章节
     */
    fun loadChapter(force: Boolean): LiveData<List<Page>> {
        if (force) {
            list.clear()
            initializedLive.value = null
            pageNumber.clear()
            isNextTask.set(false)
            if (nextJob?.isActive == true) nextJob?.cancel()
            isPreviousTask.set(false)
            if (previousJob?.isActive == true) previousJob?.cancel()
            nextJob = loadNextChapter(max(min(book.chapter, catalog.lastIndex), 0))
        }
        return pageLive
    }

    /**
     * 读取下一章
     */
    private fun loadNextChapter(index: Int) = viewModelScope.launch {
        if (isNextTask.compareAndSet(false, true)) {
            withContext(Dispatchers.IO) { analyze(index, next = true, preload = index != book.chapter) }
            if (index == book.chapter) {
                if (index > 0) previousJob = loadPreviousChapter(index - 1)
                withContext(Dispatchers.IO) { analyze(index + 1, next = true, preload = true) }
            }
            isNextTask.compareAndSet(true, false)
        }
    }

    /**
     * 读取上一章
     */
    private fun loadPreviousChapter(index: Int) = viewModelScope.launch {
        if (isPreviousTask.compareAndSet(false, true)) {
            withContext(Dispatchers.IO) { analyze(index, next = false, preload = false) }
            isPreviousTask.compareAndSet(true, false)
        }
    }

    /**
     * 读取章节
     */
    private suspend fun analyze(index: Int, next: Boolean, preload: Boolean) {
        val chapter = catalog.getOrNull(index) ?: return
        var needPreload: Boolean
        //分卷章节 没有内容直接返回
        if (chapter.href.isBlank()) {
            append(Page(0, chapter, type = PageType.BOOKLET), next).run { needPreload = true }
        } else {
            append(Page(0, chapter, type = PageType.LOADING), next)
            val markdown = convertMarkdown(chapter)
            needPreload = markdown.isBlank() || markdown == GET_FAILED_NET_THROWABLE || markdown == GET_FAILED_INVALID_AUTH || markdown == GET_FAILED_INVALID_AUTH_BUY
            when {
                markdown.isBlank() -> append(Page(0, chapter, type = PageType.BOOKLET), next)
                markdown == GET_FAILED_NET_THROWABLE -> append(Page(0, chapter, type = PageType.ERROR), next)
                markdown == GET_FAILED_INVALID_AUTH -> append(Page(0, chapter, type = PageType.AUTH), next)
                markdown == GET_FAILED_INVALID_AUTH_BUY -> append(Page(0, chapter, type = PageType.AUTH_BUY), next)
                else -> convert(chapter, markdown, 0, next)
            }
        }
        if (needPreload && preload) analyze(index + 1, next, preload)
    }

    /**
     * 转换元素再转页码
     */
    private suspend fun convert(chapter: Chapter, markdown: String, position: Int, next: Boolean) {
        val cells = convertCells(chapter, markdown, position)
        //插画章节
        if (cells.filter { it.type != CellType.TITLE }.all { it.type == CellType.IMAGE }) {
            append(Page(0, chapter, type = PageType.BOOKLET))
            return cells.filter { it.type != CellType.TITLE }.forEachIndexed { index, page ->
                append(Page(index, chapter, type = PageType.ILLUSTRATION).apply { add(page) }, next)
            }
        }
        convertPages(chapter, cells, next)
    }

    /**
     * 元素转页码 支持中断
     */
    private suspend fun convertPages(chapter: Chapter, cells: List<Cell>, next: Boolean) = suspendCancellableCoroutine<Unit> { continuation ->
        continuation.invokeOnCancellation {
            continuation.resumeWithException(CancellationException())
        }
        val list = mutableListOf<Page>()
        var page = Page(list.size, chapter, display.height, cells.firstOrNull()?.start ?: 0)
        cells.forEach { cell ->
            if (continuation.isCancelled) return@suspendCancellableCoroutine
            when (cell.type) {
                CellType.TITLE -> page.add(cell, display.fixed)
                CellType.TEXT -> {
                    var child = measureCellHeight(page, cell)
                    while (child != null) {
                        if (next) append(page, next)
                        list.add(page)
                        page = Page(list.size, chapter, display.height, page.end)
                        child = measureCellHeight(page, child)
                    }
                }
                CellType.IMAGE -> when {
                    page.height >= cell.size.height -> page.add(cell, cell.size.height)
                    else -> {
                        if (next) append(page, next)
                        list.add(page)
                        page = Page(list.size, chapter, display.height, page.end)
                        page.add(cell, cell.size.height)
                    }
                }
            }
        }
        if (page.cells.isNotEmpty()) {
            if (next) append(page, next)
            list.add(page)
        }
        if (!next && list.isNotEmpty()) append(list)
        pageNumber.put(chapter.index, list.size + pageNumber.getOrDefault(chapter.index, 0))
        if (!preview && chapter.index == catalog.last().index) append(Page(list.size, chapter, page.start, page.end, type = PageType.END), true)
        continuation.resume(Unit)
    }

    /**
     * 读取章节Markdown内容
     * @param chapter   章节
     */
    private fun convertMarkdown(chapter: Chapter): String {
        //构建缓存文件名
        val file = File(book.path, "$MP_FOLDER_TEXTS/${chapter.encodeHref}.md")
        var content = when {
            //优先读取本地缓存
            file.exists() -> file.readText()
            //否则尝试从网络读取数据
            else -> bookSource?.findContent(chapter.title, chapter.href, "${book.path}/$MP_FOLDER_IMAGES") ?: GET_FAILED_NET_THROWABLE
        }
        if (content == GET_FAILED_NET_THROWABLE) return GET_FAILED_NET_THROWABLE
        if (content == GET_FAILED_INVALID_AUTH) return GET_FAILED_INVALID_AUTH
        if (content == GET_FAILED_INVALID_AUTH_BUY) return GET_FAILED_INVALID_AUTH_BUY
        if (content == GET_FAILED_IS_BOOKLET) file.writeText(EMPTY).run { return EMPTY }
        //缓存网络数据
        if (file.parentFile?.exists() == true && file.exists().not() && content.isNotBlank() && content != GET_FAILED_NET_THROWABLE) file.writeText(content)
        //首行缩进
        content = if (Preferences.get(Preferences.Key.FIRST_LINE_INDENT, true)) {
            firstLineIndent + content.replace(Regex("\n+"), "\n$firstLineIndent").trim()
        } else content.trim()
        //净化
        purifyList.forEach { purify ->
            content = if (purify.isRegex) {
                content.replace(Regex(purify.key), purify.replace)
            } else {
                content.replace(purify.key, purify.replace)
            }
        }
        content = content.replace("""\n+\s*\n+""".toRegex(), "\n\n")
        content = content.replace("""\n+""".toRegex(), "\n")
        return content
    }

    /**
     * 将内容转化为元素
     */
    private fun convertCells(chapter: Chapter, content: String, position: Int): List<Cell> {
        val cells = mutableListOf<Cell>()
        if (position == 0) cells.add(Cell.title(chapter.title))
        var start = 0
        var begin = position
        content.regexAll(REGEX_IMAGE_MATCH).forEach {
            var offset = 0
            val text = content.substring(start, it.range.first)
            if (text.isNotBlank()) {
                cells.add(Cell.text(text, begin))
            } else {
                //当空白字符被忽略时需要计算偏移量
                offset += text.length
            }
            val url = it.value.regex(REGEX_IMAGE_VALUE)
            if (url.isNotBlank()) {
                val image = File(book.path, "$MP_FOLDER_IMAGES/$url")
                if (image.exists()) {
                    cells.add(convertImageCell(it.value.regex(REGEX_IMAGE_ALT), image.absolutePath, it.value.length))
                }
            }
            start = it.range.last + 1
            begin += it.range.last + 1
        }
        val text = content.substring(start)
        if (text.isNotBlank()) {
            cells.add(Cell.text(text, begin))
        }
        return cells
    }

    /**
     * 转化图片元素
     */
    private fun convertImageCell(alt: String, image: String, length: Int): Cell {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(image, options)
        val width = display.width
        var height = options.outHeight * display.width / options.outWidth
        if (alt.isNotBlank()) height += display.figcaption + measureFigureHeight(alt)
        height += display.lineSpacingHeight
        val lineHeight = display.lineHeight + display.lineSpacingHeight
        if (height % lineHeight != 0) {
            height += (lineHeight - height % lineHeight)
        }
        height += display.lineSpacingHeight
        return Cell.image(alt, image, length, Size(width, height))
    }

    /**
     * 追加页面
     * @param page      页面
     * @param next      顺序
     */
    private fun append(page: Page, next: Boolean = true) = synchronized(ReaderController::class) {
        if (page.type == PageType.ARTICLE && page.cells.all { it.type == CellType.TITLE }) {
            page.type = PageType.BOOKLET
        }
        try {
            val indexOf = list.indexOfLast { it.chapter == page.chapter }
            when {
                indexOf >= 0 && list[indexOf].type == PageType.LOADING -> list[indexOf] = page
                indexOf >= 0 -> list.add(indexOf + 1, page)
                else -> if (next) list.add(page) else list.add(0, page)
            }
        } catch (e: Exception) {
            //捕获正在追加的时候列表被清空引起的崩溃
        }
        when {
            //未初始化过、正在阅读的章节、索引正确或非正文非加载
            !initialized() && page.chapter.index == book.chapter && (book.chapterProgress in page.start until page.end || (page.type != PageType.ARTICLE && page.type != PageType.LOADING)) -> initializedLive.postValue(list.toList())
            initialized() && page.chapter.index == book.chapter -> pageLive.postValue(list.toList())
            initialized() -> pageLive.postValue(list.toList())
        }
    }

    /**
     * 追加多页面
     */
    private fun append(pages: List<Page>) = synchronized(ReaderController::class) {
        if (pages.size == 1 && pages[0].type == PageType.ARTICLE && pages[0].cells.all { it.type == CellType.TITLE }) {
            pages[0].type = PageType.BOOKLET
        }
        try {
            val indexOf = list.indexOfFirst { it.chapter == pages[0].chapter }
            when {
                indexOf >= 0 && list[indexOf].type == PageType.LOADING -> {
                    list.removeAt(indexOf)
                    list.addAll(indexOf, pages)
                }
                indexOf >= 0 -> list.addAll(indexOf, pages)
                else -> list.addAll(0, pages)
            }
        } catch (e: Exception) {
            //捕获正在追加的时候列表被清空引起的崩溃
        }
        pageLive.postValue(list.toList())
    }

    /**
     * 查找页面范围
     */
    fun findRangeByPage(): IntRange {
        if (currentPage == null) return IntRange(1, 1)
        val start = list.indexOfFirst { it.chapter.index == currentPage!!.chapter.index }
        if (start < 0) return IntRange(1, 1)
        val end = list.indexOfLast { it.chapter.index == currentPage!!.chapter.index }
        val current = list.indexOfFirst { it.chapter.index == currentPage!!.chapter.index && it.start == currentPage!!.start }
        return IntRange(max(current - start + 1, 1), max(end - start, 1))
    }

    fun findChapterStartIndex() = list.indexOfFirst { it.chapter.index == currentPage!!.chapter.index }

    /***********************************************************************************************
     * UI交互
     **********************************************************************************************/
    var defaultHalfExpandedRatio = 0.2F
    var defaultExpandedOffset = 0F
    var defaultCatalogHeight = 0
    var defaultMoreActionSize = 0
    var defaultMoreTopMargin = 0
    var defaultMenuActionHeight = 0F
    var defaultFastScrollerPopupHeight = 0
    var defaultMenuPaddingBottom = 0
    var scaledTouchSlop = 0
    var pageSeekCallback: ((Int) -> Unit)? = null
    val bottomSheetOffsetCallbacks = mutableListOf<(Float, Float) -> Unit>()
    val bottomSheetStateLiveData = MutableLiveData(STATE_HIDDEN)

    //当页面重新设置显示参数时触发
    val displayStateLiveData = MutableLiveData(Unit)

    //当点击目录等需关闭菜单时触发
    val menuHiddenStateLiveData = MutableLiveData<Int>()

    //下拉书签
    val pullBookmarkEnableLiveData = MutableLiveData(Preferences.get(Preferences.Key.TEXT_PULL_BOOKMARK, true))

    fun addBottomSheetOffsetCallbacks(callback: (Float, Float) -> Unit) {
        bottomSheetOffsetCallbacks.add(callback)
    }

    /***********************************************************************************************
     * 显示与测量
     **********************************************************************************************/
    private var theLatestHeight = Preferences.get(Preferences.Key.READER_HEIGHT, 0)
    var firstLineIndent = "\u3000\u3000"

    /**
     * 设置显示
     */
    fun setupDisplay(activity: Activity, height: Int = theLatestHeight): ReaderController {
        val sp = activity.resources.getDimension(R.dimen.sp)
        val dp = activity.resources.getDimension(R.dimen.dp)
        val metrics = DisplayMetrics().apply { activity.windowManager.defaultDisplay.getRealMetrics(this) }
        val hasNotch = Preferences.get(Preferences.Key.HAS_NOTCH, false)
        paint.typeface = FontModule.mCurrentFont.typeface
        //计算底部菜单默认Top边距
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14.5F, activity.resources.displayMetrics)
        //修正首行缩进 部分字体\u3000与\u2000宽度一致需要补充
        firstLineIndent = if (paint.measureText("\u3000") <= paint.measureText("\u58a8") / 2F) "\u3000\u3000\u3000\u3000" else "\u3000\u3000"
        var textHeight = StaticLayout.Builder.obtain(activity.getString(R.string.app_name), 0, 1, paint, 200).setIncludePad(false).build().height
        defaultExpandedOffset = height * 0.2F
        defaultHalfExpandedRatio = defaultExpandedOffset / height
        //ItemPadding 24 + ItemTextHeight + UI need 4dp
        defaultCatalogHeight = activity.px(24 * 3 + 12) + textHeight * 3
        defaultFastScrollerPopupHeight = textHeight + activity.px(24)
        defaultMenuActionHeight = activity.resources.getDimension(R.dimen.topbarDefaultSize)
        //更多设置字号
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12F, activity.resources.displayMetrics)
        textHeight = StaticLayout.Builder.obtain(activity.getString(R.string.app_name), 0, 1, paint, 200).setIncludePad(false).build().height
        defaultMoreActionSize = (defaultCatalogHeight * 0.355555).roundToInt()
        defaultMoreTopMargin = ((defaultCatalogHeight + defaultMenuActionHeight - activity.px(40) - (textHeight + defaultMoreActionSize) * 2) / 2).toInt()
        scaledTouchSlop = ViewConfiguration.get(activity).scaledTouchSlop
        display.orientation = if (Preferences.get(Preferences.Key.TURN_VERTICAL, false)) ViewPager2.ORIENTATION_VERTICAL else ViewPager2.ORIENTATION_HORIZONTAL
        //横向边距
        display.horizontal = (theme.horizontal * dp).toInt()
        //容器宽度
        display.width = metrics.widthPixels - display.horizontal * 2
        //段落间距
        display.paragraphSpacing = (Preferences.get(Preferences.Key.PARAGRAPH_DISTANCE, 0) * dp).toInt()
        //下边距
        display.bottom = (theme.bottom * dp).toInt()
        //画笔
        paint.isFakeBoldText = Preferences.get(Preferences.Key.FONT_BOLD, false)
        //容器高度
        if (display.orientation == ViewPager2.ORIENTATION_VERTICAL) {
            //上边距
            display.top = if (Preferences.get(Preferences.Key.STATUS_BAR, false)) activity.statusBarHeight else 0
            if (Preferences.get(Preferences.Key.NOTCH_BAR, false)) {
                display.top = max(display.top, activity.statusBarHeight)
            }
            //上下翻页
            display.height = height
            if (hasNotch || display.top > 0) display.height -= display.top
        } else {
            //上边距
            display.top = (theme.top * dp).toInt()
            if (Preferences.get(Preferences.Key.NOTCH_BAR, false)) {
                display.top = max(display.top, activity.statusBarHeight)
            }
            //左右翻页
            display.height = height - display.top - display.bottom
            //非专注模式
            paint.textSize = 12 * sp
            display.lineSpacing = 1F
            display.figcaption = measureTextHeight(TEST_STRING1, false)
            if (Preferences.get(Preferences.Key.SHOW_TITLE, true)) {
                display.height -= display.figcaption * 2
            } else if (Preferences.get(Preferences.Key.CUSTOM_STATUS_BAR, true)) {
                display.height -= display.figcaption
            }
        }
        //图片画笔
        figurePaint.textSize = 14 * sp
        display.figcaption = (8 * dp).toInt()
        //字号
        paint.textSize = Preferences.get(Preferences.Key.FONT_SIZE, 17F) * sp
        //字间距
        paint.letterSpacing = Preferences.get(Preferences.Key.LETTER_SPACING, 0F)
        //行高
        display.lineHeight = measureTextHeight(TEST_STRING1, false)
        //行间距倍数
        display.lineSpacing = Preferences.get(Preferences.Key.LINE_SPACING, 1.3F)
        //是否存在末行行间距
        display.hasEndLineSpacing = measureTextHeight(TEST_STRING1, false) > display.lineHeight
        //行间距
        display.lineSpacingHeight = if (display.hasEndLineSpacing) {
            measureTextHeight(TEST_STRING1, false) - display.lineHeight
        } else {
            measureTextHeight(TEST_STRING2, false) - display.lineHeight * 2
        }
        //内容距离标题
        display.titleSpacingHeight = 0
        if (Preferences.get(Preferences.Key.CUSTOM_STATUS_BAR, true)) {
            display.titleSpacingHeight = display.lineSpacingHeight
        } else if (Preferences.get(Preferences.Key.SHOW_TITLE, true)) {
            display.titleSpacingHeight += display.lineSpacingHeight / 2
        }
        if (display.titleSpacingHeight != 0) {
            display.titleSpacingHeight += (display.height - display.titleSpacingHeight) % (display.lineHeight + display.lineSpacingHeight)
            if (display.orientation != ViewPager2.ORIENTATION_VERTICAL) {
                display.height -= display.titleSpacingHeight
            }
            display.titleSpacingHeight /= 2
        } else {
            display.titleSpacingHeight = (display.height - display.titleSpacingHeight) % (display.lineHeight + display.lineSpacingHeight) / 2
        }
        //标题固定高度
        display.fixed = (display.lineHeight + display.lineSpacingHeight) * 7
        //主题
        theme = UIModule.getConfiguredTheme(activity)
        displayStateLiveData.postValue(Unit)
        return this
    }

    /**
     * 测量文本高度
     * 需要当前画笔、容器宽度、行间距
     */
    private fun measureTextHeight(text: String, containsParagraphSpacing: Boolean = true): Int {
        val height = StaticLayout.Builder.obtain(text, 0, text.length, paint, display.width).setLineSpacing(0F, display.lineSpacing).setIncludePad(false).build().height
        return height + if (containsParagraphSpacing) measureParagraphHeight(text) else 0
    }

    /**
     * 测量注解文本高度
     */
    private fun measureFigureHeight(text: String): Int {
        return StaticLayout.Builder.obtain(text, 0, text.length, figurePaint, display.width).setLineSpacing(0F, 1.2F).setIncludePad(false).build().height
    }

    /**
     * 修正段落间隔
     */
    private fun measureParagraphHeight(text: String): Int {
        if (display.paragraphSpacing == 0 || text.length < 2 || !text.contains("\n")) return 0
        return text.substring(1, text.lastIndex).count { it == '\n' } * display.paragraphSpacing
    }

    private fun measureCellHeight(page: Page, cell: Cell): Cell? {
        val height = measureTextHeight(cell.value)
        return if (height <= page.height) {
            page.add(cell.apply { size = Size(0, height) }, height)
            null
        } else {
            val index = findLastLineIndex(page, cell)
            page.add(Cell(CellType.TEXT, cell.value.substring(0, index).trim { it == '\n' }, start = cell.start))
            Cell(CellType.TEXT, cell.value.substring(index), start = cell.start + index)
        }
    }

    private fun findLastLineIndex(page: Page, cell: Cell): Int {
        val layout = StaticLayout.Builder.obtain(cell.value, 0, cell.value.length, paint, display.width).setLineSpacing(0F, display.lineSpacing).setIncludePad(false).build()
        for (line in 0 until layout.lineCount) {
            val bottom = layout.getLineBottom(line) + measureParagraphHeight(cell.value.substring(0, layout.getLineEnd(line)))
            if (bottom > page.height) {
                val lineEnd = layout.getLineEnd(line - 1)
                return if (display.orientation == ViewPager2.ORIENTATION_VERTICAL) {
                    val paragraphEnd = cell.value.indexOf("\n", lineEnd)
                    if (paragraphEnd < 0) layout.getLineEnd(layout.lineCount - 1) else paragraphEnd + 1
                } else lineEnd
            }
        }
        return layout.getLineEnd(layout.lineCount - 1)
    }
    /***********************************************************************************************
     * 预览模式
     **********************************************************************************************/
    /**
     * 加入书架
     */
    fun insertBookshelf(bookshelf: Bookshelf) {
        book.bookshelf = bookshelf.id
        preview = false
        Room.book().insert(book)
    }

    /***********************************************************************************************
     * 屏蔽
     **********************************************************************************************/
    //屏蔽列表
    val purifyList = mutableListOf<Purify>()

    /**
     * 当前正在使用的书源屏蔽列表
     */
    fun getBookSourcePurifyList() = bookSource?.bookSource?.json?.chapter?.purify ?: emptyList()


    /**
     * 当前图书屏蔽列表 用户自设
     */
    private fun getBookPurifyList(): List<Purify> = if (File(book.path, MP_FILENAME_PURIFY).exists()) {
        Gson().fromJson(File(book.path, MP_FILENAME_PURIFY).readText(), object : TypeToken<List<Purify>>() {}.type)
    } else {
        listOf()
    }

    /**
     * 屏蔽
     */
    fun purify(content: String, replace: String, isRegex: Boolean) {
        if (purifyList.any { it.key == content }) return
        purifyList.add(Purify(content, replace, isRegex))
        File(book.path, MP_FILENAME_PURIFY).writeText(purifyList.toJson(true))
        jump()
    }

    /**
     * 屏蔽
     */
    fun purifyRemove(purify: Purify) {
        purifyList.remove(purify)
        File(book.path, MP_FILENAME_PURIFY).writeText(purifyList.toJson(true))
        jump()
    }

    /***********************************************************************************************
     * 换源
     **********************************************************************************************/
    /**
     * 当前正在使用的书源名
     */
    fun getBookSourceName() = bookSource?.bookSource?.name ?: EMPTY

    /**
     * 换源
     */
    fun changeBookSource(result: BookSourceSearchResponse): LiveData<Book> {
        val response = MutableLiveData<Book>()
        viewModelScope.launch(Dispatchers.IO) {
            val chapters = result.chapters
            val catalog = StringBuilder()
            chapters.forEach { chapter ->
                if (chapter.useLevel) catalog.append(MP_CATALOG_INDENTATION)
                catalog.append("* [${chapter.name}](${chapter.url})$MP_ENTER")
            }
            //写进目录
            File(book.path, MP_FILENAME_CATALOG).writeText(catalog.toString())
            File(book.path, MP_FILENAME_BOOK_SOURCE).writeText(result.source.toJson())
            File(book.path, MP_FOLDER_TEXTS).listFiles { file -> file.delete() }
            val indexOf = if (book.chapter > chapters.size / 2) {
                chapters.indexOfLast { chapter -> chapter.name.wrap() == book.chapterName.wrap() }
            } else {
                chapters.indexOfFirst { chapter -> chapter.name.wrap() == book.chapterName.wrap() }
            }
            if (indexOf >= 0 && indexOf != book.chapter) {
                book.chapter = indexOf
            }
            if (book.chapter >= chapters.size) {
                book.chapter = chapters.lastIndex
            }
            book.chapterName = chapters[min(book.chapter, chapters.lastIndex)].name
            book.catalog = chapters.size
            book.link = result.book.url
            book.lastChapter = result.book.lastChapter
            Room.book().update(book)
            Room.bookSource().get(if (result.source.url.startsWith("http")) Uri.parse(result.source.url)?.host.orEmpty() else result.source.url)?.run { Room.bookSource().update(this.apply { frequency += 1 }) }
            response.postValue(book)
        }
        return response
    }

    /**
     * 搜索全部书源
     */
    fun searchAll(): LiveData<BookSourceSearchResponse?> {
        val result = MutableLiveData<BookSourceSearchResponse?>()
        viewModelScope.launch {
            async(Dispatchers.IO) {
                result.postValue(BookSourceSearchResponse(DetailMetadata(book.name, book.author, EMPTY, EMPTY, EMPTY, EMPTY, catalog.lastOrNull()?.title.orEmpty(), EMPTY, EMPTY), bookSource!!.bookSource, listOf()))
                Room.bookSource().getAllImmediately().filter { it.url != bookSource?.bookSource?.url }.map { BookSourceParser(it) }.forEach { source ->
                    launch {
                        source.findTheLastChapter(book)?.run { result.postValue(this) }
                    }
                }
                return@async null
            }.run { result.postValue(await()) }
        }
        return result
    }


    /***********************************************************************************************
     * 主题
     ***********************************************************************************************
     * 变量声明
     **/
    var theme = THEME_LIGHT

    /**
     * 查询主题列表
     */
    fun queryThemes(isLight: Boolean) = Room.theme().getAll(if (isLight) 0 else 1)

    /**
     * 删除主题
     */
    fun removeTheme(theme: Theme) = viewModelScope.launch(Dispatchers.IO) {
        if (theme.mipmap.isNotBlank() && File(theme.mipmap).exists()) {
            File(theme.mipmap).delete()
        }
        Room.theme().remove(theme)
    }

}
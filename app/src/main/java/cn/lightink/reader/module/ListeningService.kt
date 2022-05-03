package cn.lightink.reader.module

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Binder
import android.os.Handler
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.MutableLiveData
import cn.lightink.reader.model.*
import cn.lightink.reader.module.booksource.BookSourceParser
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.min

class ListeningService : Service(), TextToSpeech.OnInitListener {

    //基础变量声明
    private val binder = ListeningBinder()
    private var book: Book? = null
    private var bookSource: BookSourceParser? = null
    private val purifyList = mutableListOf<Purify>()
    private var catalog = emptyList<Chapter>()
    private var current = 0

    //TTS变量声明
    private var status = STATUS_IDLE
    private val cells = CopyOnWriteArrayList<SpeechCell>()

    //LiveData
    private val statusLiveData = MutableLiveData<Int>()
    private val progressLiveData = MutableLiveData<SpeechCell>()

    //本地TTS变量声明
    private var tts: TextToSpeech? = null
    private var ttsEngine = EMPTY
    private var utteranceIdTimestamp = mutableMapOf<String, Long>()

    override fun onBind(intent: Intent?) = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onCreate() {
        super.onCreate()
        Notify.register(this)
        tts = TextToSpeech(this, this)
        tts?.setOnUtteranceProgressListener(buildUtteranceProgressListener())
        startForeground(NotificationHelper.MEDIA, NotificationHelper.play(this@ListeningService, "", "", "", null, true))
    }


    /**
     * 设置状态
     */
    private fun setStatus(newStatus: Int) {
        status = newStatus
        statusLiveData.postValue(status)
    }

    /**
     * 设置播放进度
     */
    private fun setProgress(cell: SpeechCell) {
        if (progressLiveData.value == null || book?.chapter != cell.chapter.index) {
            notifyPlay(chapterName = cell.chapter.title)
        }
        progressLiveData.postValue(cell)
    }

    /**
     * 播放
     */
    private fun play() {
        if (tts == null || tts?.isSpeaking == true) return
        loadChapter(false)
    }

    /**
     * 暂停/播放
     */
    fun switch() {
        if (tts?.isSpeaking == true) {
            tts?.stop()
            notifyPlay(false)
        } else if (cells.isNotEmpty()) {
            tts?.speak(cells.first().value, TextToSpeech.QUEUE_FLUSH, null, cells.first().utteranceId)
            cells.subList(1, cells.size).forEach { tts?.speak(it.value, TextToSpeech.QUEUE_ADD, null, it.utteranceId) }
        }
    }

    //TTS初始化
    override fun onInit(status: Int) {
        //成功
        if (status == TextToSpeech.SUCCESS) {
            if (tts?.isLanguageAvailable(Locale.CHINESE) == TextToSpeech.LANG_AVAILABLE) {
                ttsEngine = tts?.engines?.firstOrNull { it.name == tts?.defaultEngine }?.label.orEmpty()
                if (ttsEngine != EMPTY) setStatus(STATUS_PREPARE)
            } else {
                //不支持中文
                setStatus(STATUS_ERROR)
            }
        } else {
            //未安装TTS引擎
            setStatus(STATUS_ERROR)
        }
    }

    /**
     * TTS进度监听
     */
    private fun buildUtteranceProgressListener() = object : UtteranceProgressListener() {
        //播完一节
        override fun onDone(utteranceId: String?) {
            cells.firstOrNull { it.utteranceId == utteranceId }?.run {
                statistics(this, utteranceIdTimestamp[utteranceId] ?: 0)
                cells.remove(this)
                if (cells.size < 7) loadChapter()
            }
        }

        //错误
        override fun onError(utteranceId: String?) {

        }

        //播放一节
        override fun onStart(utteranceId: String?) {
            if (status != STATUS_PLAY) {
                setStatus(STATUS_PLAY)
                notifyPlay(true)
            }
            cells.firstOrNull { it.utteranceId == utteranceId }?.run {
                setProgress(this)
                utteranceIdTimestamp[this.utteranceId] = System.currentTimeMillis()
                book?.chapter = chapter.index
                book?.chapterProgress = progress
                book?.run { Room.book().update(this) }
            }
        }

        //停止
        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            setStatus(STATUS_STOP)
        }
    }

    /**
     * 统计阅读时长
     */
    private fun statistics(cell: SpeechCell, timestamp: Long) {
//        if (timestamp > 0 && book != null && user != null) {
//            val time = ((System.currentTimeMillis() - timestamp) / MILLISECOND).toInt()
//            book!!.time += time
//            Room.book().update(book!!)
//            if (user != null) {
//                val progress = BookSyncProgress(book!!.chapter, book!!.chapterProgress, book!!.chapterName, time / 60F, book!!.time / 60F, book!!.speed.toInt(), book!!.finishedAt > 0, book!!.createdAt, book!!.finishedAt)
//                progress.valid = Swiftlet.signature(progress.signature())
//                Room.statistics().insert(Statistics(progress.id, user!!.id, book!!.objectId, progress.toJson()))
//                if (Room.statistics().count(user!!.id, book!!.objectId) >= LIMIT_STATISTICS) synchronize(book!!, user!!)
//            }
//        }
        if (book != null && !Room.bookRecord().has(book!!.objectId, cell.chapter.index)) {
            Room.bookRecord().insert(BookRecord(book!!.objectId, cell.chapter.index))
        }
    }

//    /**
//     * 同步阅读记录和时长
//     */
//    private fun synchronize(book: Book, user: User) {
//        val list = Room.statistics().getAll(user.id, book.objectId)
//        if (list.isNullOrEmpty()) return
//        val response = RESTful.put<ReadExp>(API.BOOK_SYNC.format(book.publishId), mapOf("progress" to list.map { Gson().fromJson(it.progress, BookSyncProgress::class.java) }.toJson()))
//        if (response.isSuccessful) {
//            //上传成功，删除已提交的离线数据
//            Room.statistics().delete(*list.toTypedArray())
//            response.data?.run { Session.updateReadTime(this) }
//            if (list.size == LIMIT_STATISTICS) synchronize(book, user)
//        }
//    }

    /***********************************************************************************************
     * 定时相关
     ***********************************************************************************************
     * 变量声明
     */
    private val timerLiveData = MutableLiveData<Int>()
    private val timer = Handler()
    private val callback = Runnable { onTimerCallback() }
    private var minute = 0

    private fun startTimer(minute: Int) {
        this.minute = minute
        timer.removeCallbacks(callback)
        timer.postDelayed(callback, 1000 * 60)
        timerLiveData.postValue(minute)
        notifyPlay()
    }

    private fun onTimerCallback() {
        if (--minute > 0) {
            timerLiveData.postValue(minute)
            timer.postDelayed(callback, 1000 * 60)
            notifyPlay()
        } else {
            Notify.post(Notify.MediaActionEvent(2))
        }
    }

    /***********************************************************************************************
     * 通知相关
     ***********************************************************************************************
     * 播放
     */
    private fun notifyPlay(isPlaying: Boolean = tts?.isSpeaking == true, chapterName: String = book?.chapterName.orEmpty()) = book?.run {
        val content = when {
            !isPlaying -> "暂停朗读：${chapterName}"
            minute > 0 -> "朗读${minute}分钟：${chapterName}"
            else -> "正在朗读：${chapterName}"
        }
        NotificationHelper.play(this@ListeningService, objectId, name, content, if (File(cover).exists()) BitmapFactory.decodeFile(cover) else null, isPlaying)
    }

    /***********************************************************************************************
     * 图书相关
     ***********************************************************************************************
     * 读取章节
     */
    private fun loadChapter(isNext: Boolean = true) {
        if (book == null || current + 1 > catalog.lastIndex || book!!.chapter < 0) return
        current = if (isNext) min(current + 1, catalog.lastIndex) else min(book!!.chapter, catalog.lastIndex)
        if (current !in 0..catalog.lastIndex) return
        val chapter = catalog[current]
        if (chapter.href.isBlank()) return loadChapter(true)
        val markdown = convertMarkdown(chapter)
        val list = if (markdown == GET_FAILED_NET_THROWABLE || markdown == GET_FAILED_INVALID_AUTH) {
            listOf(SpeechCell("读取失败", chapter, 0))
        } else {
            convertCells(chapter, markdown, if (!isNext && book!!.chapterProgress < markdown.lastIndex) book!!.chapterProgress else 0)
        }
        if (list.isEmpty()) return loadChapter(isNext)
        list.forEach { tts?.speak(it.value, TextToSpeech.QUEUE_ADD, null, it.utteranceId) }
        cells.addAll(list)
    }

    /**
     * 读取章节Markdown内容
     */
    private fun convertMarkdown(chapter: Chapter): String {
        val file = File(book!!.path, "$MP_FOLDER_TEXTS/${chapter.encodeHref}.md")
        var content = when {
            //优先读取本地缓存
            file.exists() -> file.readText()
            //否则尝试从网络读取数据
            else -> bookSource?.findContent(chapter.href, "${book!!.path}/$MP_FOLDER_IMAGES") ?: GET_FAILED_NET_THROWABLE
        }
        if (content == GET_FAILED_NET_THROWABLE) return GET_FAILED_NET_THROWABLE
        if (content == GET_FAILED_INVALID_AUTH) return GET_FAILED_INVALID_AUTH
        if (file.parentFile?.exists() == true && file.exists().not() && content.isNotBlank() && content != GET_FAILED_NET_THROWABLE) file.writeText(content)
        content = if (Preferences.get(Preferences.Key.FIRST_LINE_INDENT, true)) {
            "\u3000\u3000" + content.replace(Regex("\n+"), "\n\u3000\u3000").trim()
        } else content.trim()
        purifyList.forEach { purify ->
            content = if (purify.isRegex) {
                content.replace(Regex(purify.key), purify.replace)
            } else {
                content.replace(purify.key, purify.replace)
            }
        }
        content = content.replace("""\n+\s+\n+""".toRegex(), "\n\n")
        content = content.replace("""\n+""".toRegex(), "\n")
        return content
    }

    /**
     * 将内容转化为元素
     */
    private fun convertCells(chapter: Chapter, content: String, position: Int): List<SpeechCell> {
        val cells = mutableListOf<SpeechCell>()
        var progress = position
        val cell: SpeechCell? = if (position == 0) SpeechCell(chapter.title, chapter, 0) else null
        content.substring(position).split("\n").forEach { paragraph ->
            if (paragraph.startsWith("\u3000\u3000")) progress += 2
            cells.add(SpeechCell(paragraph.removePrefix("\u3000\u3000"), chapter, progress))
            progress += paragraph.length + 1
        }
        if (cell != null) {
            cells.add(cell)
        }
        return cells
    }

    override fun onDestroy() {
        super.onDestroy()
        Notify.unregister(this)
        NotificationHelper.stop(this)
        binder.isActive = false
        tts?.stop()
        tts?.shutdown()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: Notify.MediaActionEvent) {
        when (event.action) {
            0x01 -> startTimer(Preferences.get(Preferences.Key.TTS_TIMER, 30))
            0x02 -> stopSelf()
            0x03 -> switch()
        }
    }

    /***********************************************************************************************
     * BINDER
     ***********************************************************************************************
     * 通信
     */
    inner class ListeningBinder : Binder() {

        var isActive = true

        fun book() = book

        fun statusLiveData() = statusLiveData

        fun progressLiveData() = progressLiveData

        fun timerLiveData() = timerLiveData

        /**
         * 设置图书
         */
        fun setupWithBookId(objectId: String): Book? {
            //读取图书（目录、书源、净化列表）
            book = Room.book().get(objectId)
            if (book?.path?.exists() == true) {
                this@ListeningService.catalog = File(book!!.path, MP_FILENAME_CATALOG).readLines().mapIndexed { i, line -> Chapter(i, line) }
                bookSource = book!!.getBookSource()
                purifyList.apply { clear() }.addAll(if (File(book!!.path, MP_FILENAME_PURIFY).exists()) {
                    Gson().fromJson(File(book!!.path, MP_FILENAME_PURIFY).readText(), object : TypeToken<List<Purify>>() {}.type)
                } else {
                    listOf()
                })
                return book
            }
            return null
        }

        /**
         * 获取正在使用的引擎名
         */
        fun getEngine() = if (ttsEngine.isNotBlank()) ttsEngine else "未设置文字转语音引擎"


        /**
         * 开始播放
         */
        fun play() {
            this@ListeningService.play()
        }

        /**
         * 播放/暂停
         */
        fun switch() {
            this@ListeningService.switch()
        }

        /**
         * 停止播放
         */
        fun release() {
            stopSelf()
        }

        /**
         * 设置定时
         */
        fun setTimer(minute: Int) {
            startTimer(minute)
        }

    }

}

class ListeningBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Notify.post(Notify.MediaActionEvent(intent?.getIntExtra(INTENT_ACTION, 0)))
    }
}

const val STATUS_IDLE = 0
const val STATUS_PREPARE = 1
const val STATUS_PLAY = 2
const val STATUS_STOP = 3
const val STATUS_ERROR = 9
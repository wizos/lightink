package cn.lightink.reader.module

import android.content.Context
import android.util.SparseIntArray
import androidx.core.util.contains
import androidx.core.util.containsKey
import androidx.lifecycle.MutableLiveData
import androidx.work.Worker
import androidx.work.WorkerParameters
import cn.lightink.reader.model.Book
import cn.lightink.reader.model.CacheChapter
import cn.lightink.reader.model.StateChapter
import cn.lightink.reader.module.booksource.BookSourceParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.util.*

object BookCacheModule {

    private val statusLiveData = MutableLiveData<Unit>()
    private val liveData = MutableLiveData<CacheChapter>()
    private val caching = SparseIntArray()

    fun attachCacheLive() = liveData
    fun attachCacheStatusLive() = statusLiveData

    fun isCaching(book: Book?) = book != null && caching.contains(book.hashCode())

    /**
     * 缓存
     */
    fun cache(book: Book, chapters: List<StateChapter>) {
        if (caching.containsKey(book.hashCode()) || book.hasBookSource().not() || chapters.isEmpty()) return
        caching.put(book.hashCode(), 0)
        statusLiveData.postValue(Unit)
        GlobalScope.launch(Dispatchers.IO) {
            notify(book, "正在准备", 0, chapters.size)
            book.getBookSource()?.run {
                download(book, this, chapters.toMutableList(), chapters.size)
            }
        }
    }

    /**
     * 暂停缓存
     */
    fun pause(context: Context, book: Book?) {
        book?.run {
            caching.delete(book.hashCode())
            NotificationHelper.cancel(context, book.hashCode())
        }
        statusLiveData.postValue(Unit)
    }

    /**
     * 下载
     */
    private suspend fun download(book: Book, parser: BookSourceParser, chapters: MutableList<StateChapter>, total: Int) {
        var success = 0
        withContext(Dispatchers.IO) {
            chapters.forEachIndexed { index, chapter ->
                if (!isCaching(book)) return@forEachIndexed
                val file = File(book.path, "$MP_FOLDER_TEXTS/${chapter.encodeHref}.md")
                if (file.exists().not()) {
                    val content = parser.findContent(chapter.href, "${book.path}/$MP_FOLDER_IMAGES")
                    if (content.isNotBlank() && content != GET_FAILED_NET_THROWABLE) try {
                        file.writeText(content)
                    } catch (e: FileNotFoundException) {
                        //缓存时删除了图书引起
                        caching.delete(book.hashCode())
                        notify(book, "停止缓存 | 图书已删除", total, total)
                        statusLiveData.postValue(Unit)
                        return@withContext
                    }
                }
                val isSuccess = file.exists()
                if (isSuccess) {
                    success++
                    liveData.postValue(CacheChapter(chapter.index, book.objectId))
                }
                notify(book, chapter.title, index, total)
            }
        }
        caching.delete(book.hashCode())
        GL.CONTEXT?.get()?.run { NotificationHelper.cancel(this, book.hashCode()) }
        statusLiveData.postValue(Unit)
    }

    private fun notify(book: Book, chapterName: String, progress: Int, max: Int) {
        GL.CONTEXT?.get()?.run {
            NotificationHelper.progress(this, book.hashCode(), book, "下载 《${book.name}》", chapterName, progress, max)
        }
    }

}

/**
 * 追更任务
 */
class ChaseUpdateWorker(val context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    override fun doWork(): Result {
        if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) in 0..5) return Result.success()
        Room.book().getAllNeedCheckUpdate().filter { it.hasBookSource() }.forEach { book ->
            val bookSource = book.getBookSource()
            if (bookSource is BookSourceParser) {
                val chapterName = bookSource.checkUpdate(book)
                if (chapterName.isNullOrBlank().not()) {
                    NotificationHelper.normal(context, book.hashCode(), book.name, chapterName.orEmpty())
                }
            }
        }
        return Result.success()
    }

}
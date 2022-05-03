package cn.lightink.reader.controller

import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.lightink.reader.model.Book
import cn.lightink.reader.model.BookSyncProgress
import cn.lightink.reader.model.Chapter
import cn.lightink.reader.model.StateChapter
import cn.lightink.reader.module.INTENT_BOOK
import cn.lightink.reader.module.MP_FILENAME_CATALOG
import cn.lightink.reader.module.MP_FOLDER_TEXTS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class BookSummaryController : ViewModel() {

    var book: Book? = null

    fun attach(intent: Intent) {
        book = intent.getParcelableExtra(INTENT_BOOK)
    }

    /**
     * 读取章节列表
     */
    fun loadChapters(): LiveData<List<StateChapter>> {
        val liveData = MutableLiveData<List<StateChapter>>()
        viewModelScope.launch(Dispatchers.IO) {
            if (book == null || !File(book!!.path, MP_FILENAME_CATALOG).exists()) return@launch
            val isLocalBook = book?.hasBookSource() != true
            val chapters = File(book!!.path, MP_FILENAME_CATALOG).readLines().mapIndexed { index, markdown ->
                val chapter = Chapter(index, markdown)
                val cached = isLocalBook || chapter.href.isBlank() || File(book!!.path, "${MP_FOLDER_TEXTS}/${chapter.encodeHref}.md").exists()
                StateChapter(index, chapter.title, true, chapter.href, cached)
            }
            liveData.postValue(chapters)
        }
        return liveData
    }

    /**
     * 读取章节列表
     */
    fun cleanChapters() = viewModelScope.launch(Dispatchers.IO) {
        if (book == null) return@launch
        File(book!!.path, MP_FOLDER_TEXTS).listFiles()?.forEach { if (it.exists()) it.delete() }
    }

    /**
     * 图书当前同步进度
     */
    fun synchronize(): LiveData<BookSyncProgress> {
        val result = MutableLiveData<BookSyncProgress>()
        viewModelScope.launch(Dispatchers.IO) {
            if (book == null) return@launch
            //未登录 使用本地数据生成
            val localProgress = BookSyncProgress(0, 0, book!!.chapterName, 0F, book!!.time / 60F, book!!.speed.toInt(), false, 0L, 0L)
            return@launch result.postValue(localProgress)
        }
        return result
    }

}
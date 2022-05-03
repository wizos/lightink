package cn.lightink.reader.controller

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.lightink.reader.BOOK_PATH
import cn.lightink.reader.model.Book
import cn.lightink.reader.model.Bookshelf
import cn.lightink.reader.module.Preferences
import cn.lightink.reader.module.Room
import com.scwang.smartrefresh.layout.constant.RefreshState
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.File

class MainController : ViewModel() {

    val bookshelfCheckUpdateLiveData = MutableLiveData<RefreshState>()
    val bookshelfLive = MutableLiveData(getTheLastBookshelf())

    private var checkUpdateJob: Deferred<Int>? = null

    /**
     * 切换书架
     */
    fun changedBookshelf(bookshelf: Bookshelf? = getTheLastBookshelf()) {
        bookshelfLive.postValue(bookshelf)
    }

    /**
     * 查询书架列表
     */
    fun queryBookshelves() = Room.bookshelf().getAll()

    /**
     * 查询指定书架中的图书
     * @param bookshelf 指定书架
     */
    fun queryBooksByBookshelf(bookshelf: Bookshelf) = if (bookshelf.sort == 1) {
        Room.book().getAllSortByDrag(bookshelf.id)
    } else {
        Room.book().getAllSortByTime(bookshelf.id)
    }

    /**
     * 移动图书
     */
    fun moveBooks(books: List<Book>, bookshelf: Bookshelf) {
        if (books.isEmpty()) return
        books.forEach { book -> book.bookshelf = bookshelf.id }
        Room.book().update(*books.toTypedArray())
    }

    /**
     * 删除图书
     */
    fun deleteBooks(books: List<Book>, withResource: Boolean) {
        if (books.isEmpty()) return
        Room.book().delete(*books.toTypedArray())
        books.map { File(BOOK_PATH, it.objectId) }.forEach { it.deleteRecursively() }
        books.forEach { Room.bookRecord().remove(it.objectId) }
    }

    /**
     * 删除书架
     */
    fun deleteBookshelf(bookshelf: Bookshelf) {
        Room.bookshelf().remove(bookshelf)
        if (Preferences.get(Preferences.Key.BOOKSHELF, 1L) == bookshelf.id) {
            val defaultBookshelf = Room.bookshelf().getFirst()
            Preferences.put(Preferences.Key.BOOKSHELF, defaultBookshelf.id)
            changedBookshelf(defaultBookshelf)
        }
        deleteBooks(Room.book().getAll(bookshelf.id), withResource = true)
    }

    /**
     * 获取图书更新方式
     */
    fun getBookCheckUpdateType() = Preferences.get(Preferences.Key.BOOK_CHECK_UPDATE_TYPE, 60)

    /**
     * 检查更新
     */
    fun checkBooksUpdate() = viewModelScope.launch {
        //当前有检查任务则不再重复执行
        if (checkUpdateJob?.isActive == true) return@launch
        if (Room.book().hasNeedCheckUpdate()) {
            checkUpdateJob = async(Dispatchers.IO) {
                val books = Room.book().getAllNeedCheckUpdate().filter { it.hasBookSource() }
                books.forEach { book ->
                    launch { book.getBookSource()?.run { checkUpdate(book) } }
                }
                return@async books.size
            }
            bookshelfCheckUpdateLiveData.postValue(if (checkUpdateJob!!.await() == 0) RefreshState.PullDownCanceled else RefreshState.PullDownCanceled)
        } else {
            bookshelfCheckUpdateLiveData.postValue(RefreshState.PullDownCanceled)
        }
    }

    /**
     * 读取上次浏览的书架
     */
    private fun getTheLastBookshelf() = Room.bookshelf().get(Preferences.get(Preferences.Key.BOOKSHELF, Room.bookshelf().getFirst().id))

    /**
     * 清理不存在记录但文件残留的图书
     */
    fun autoClearInvalidBook(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val books = Room.book().getObjectIdAll()
            BOOK_PATH.listFiles()?.filter { it.isFile || !books.contains(it.name) }?.forEach { it.deleteRecursively() }
            if (File(context.cacheDir, "WebDAV").exists()) File(context.cacheDir, "WebDAV").deleteRecursively()
        }
    }

}
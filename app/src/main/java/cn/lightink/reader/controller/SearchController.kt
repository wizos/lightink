package cn.lightink.reader.controller

import android.webkit.URLUtil
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.lightink.reader.model.*
import cn.lightink.reader.module.EMPTY
import cn.lightink.reader.module.Room
import cn.lightink.reader.module.SearchObserver
import cn.lightink.reader.module.booksource.BookSourceParser
import cn.lightink.reader.module.booksource.SearchMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 搜索控制器
 *
 * @see cn.lightink.reader.ui.main.SearchFragment
 * @since 1.0.0
 */
class SearchController : ViewModel() {

    private val bookSources = mutableListOf<BookSource>()

    private var searchJob: Job? = null
    private var statistic: Boolean = false
    private var results = CopyOnWriteArrayList<SearchBook>()
    val localLiveData = MutableLiveData<List<Book>>()
    var searchKey = EMPTY

    /**
     * 搜索历史
     */
    fun searchHistory() = Room.search().getAll()

    /**
     * 搜索
     */
    fun search(key: String): LiveData<Boolean> {
        val state = MutableLiveData<Boolean>()
        searchKey = key
        statistic = false
        Room.search().insert(SearchHistory(key))
        viewModelScope.launch {
            state.postValue(true)
            localLiveData.postValue(Room.book().search("%$key%"))
            //如果书源列表为空读取列表
            if (bookSources.isEmpty()) bookSources.addAll(Room.bookSource().getAllImmediately())
            //取消正在执行的任务
            cancelSearch()
            //挂起搜索
            searchJob = launch(Dispatchers.IO) {
                bookSources.forEach { source ->
                    //并发
                    launch {
                        val begin = System.currentTimeMillis()
                        val list = BookSourceParser(source).search(key)
                        //任务如果已取消就不再推送
                        if (isActive && list.isNotEmpty()) {
                            val speed = System.currentTimeMillis() - begin
                            list.forEach { metadata -> handleSearchBook(metadata, source, speed) }
                            if (searchJob?.isActive == true) {
                                SearchObserver.postValue(results)
                            }
                        }
                    }
                }
            }
            searchJob?.join()
            state.postValue(false)
        }
        return state
    }

    /**
     * 停止搜索
     */
    fun stopSearch() = viewModelScope.launch { cancelSearch() }

    private suspend fun cancelSearch() {
        if (searchJob?.isActive == true) {
            searchJob?.cancel()
        }
        results.clear()
        SearchObserver.postValue(results)
    }

    /**
     * 处理搜索结果
     */
    private fun handleSearchBook(metadata: SearchMetadata, source: BookSource, speed: Long) {
        val book = results.firstOrNull { it.name == metadata.name && (it.author == metadata.author || it.author.isBlank() || metadata.author.isBlank()) }
        if (book == null) {
            //不存在同名图书
            val books = CopyOnWriteArrayList<SearchResult>()
            books.add(SearchResult(metadata, source, speed))
            //准确搜索的书名排在首位
            if (searchKey == metadata.name) {
                results.add(0, SearchBook(metadata.name, metadata.author, metadata.summary, metadata.cover, books))
            } else {
                results.add(SearchBook(metadata.name, metadata.author, metadata.summary, metadata.cover, books))
            }
        } else {
            //存在同名且同作者图书
            if (!URLUtil.isNetworkUrl(book.cover)) book.cover = metadata.cover
            book.list.add(SearchResult(metadata, source, speed))
            if (book.author.isBlank()) book.author = metadata.author
        }
    }

}
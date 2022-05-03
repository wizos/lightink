package cn.lightink.reader.controller

import android.webkit.URLUtil
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.lightink.reader.model.BookRank
import cn.lightink.reader.model.BookSource
import cn.lightink.reader.model.SearchBook
import cn.lightink.reader.model.SearchResult
import cn.lightink.reader.module.Room
import cn.lightink.reader.module.SearchObserver
import cn.lightink.reader.module.booksource.BookSourceJson
import cn.lightink.reader.module.booksource.BookSourceParser
import cn.lightink.reader.module.booksource.SearchMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

class BookRankController : ViewModel() {

    private val bookRankDataList = CopyOnWriteArrayList<SearchMetadata>()
    private val bookSources = mutableListOf<BookSource>()
    private var searchBook: SearchBook? = null
    private var searchJob: Job? = null

    /**
     * 查询全部排行榜
     */
    fun getBookRanks() = Room.bookRank().getAllImmediately()

    /**
     * 查询可见排行榜
     */
    fun getVisibleBookRanks() = Room.bookRank().getVisibleImmediately()

    /**
     * 更新排行榜
     */
    fun updateBookRanks(ranks: List<BookRank>) = viewModelScope.launch(Dispatchers.IO) {
        Room.bookRank().update(*ranks.toTypedArray())
    }

    /**
     * 查询排行榜更多数据
     */
    fun loadMore(bookSourceParser: BookSourceParser, rank: BookSourceJson.Rank, page: Int, category: String? = null): LiveData<List<SearchMetadata>> {
        val liveData = MutableLiveData<List<SearchMetadata>>()
        viewModelScope.launch(Dispatchers.IO) {
            val list = bookSourceParser.queryRank(buildUrl(rank, page, category), rank)
            if (list.isNotEmpty() && bookRankDataList.none { it.detail == list.firstOrNull()?.detail }) {
                bookRankDataList.addAll(list)
                liveData.postValue(bookRankDataList.toList())
            } else {
                liveData.postValue(emptyList())
            }
        }
        return liveData
    }

    fun refresh() {
        bookRankDataList.clear()
    }

    fun search(metadata: SearchMetadata, bookSource: BookSource): LiveData<SearchBook?> {
        val liveData = MutableLiveData<SearchBook?>()
        viewModelScope.launch {
            //如果书源列表为空读取列表
            if (bookSources.isEmpty()) bookSources.addAll(Room.bookSource().getAllImmediately().filter { it.url != bookSource.url })
            //取消正在执行的任务
            cancelSearch()
            handleSearchBook(metadata, bookSource, 0)
            searchBook?.run { SearchObserver.postValue(listOf(this)) }
            liveData.postValue(searchBook)
            //挂起搜索
            searchJob = launch(Dispatchers.IO) {
                bookSources.forEach { source ->
                    //并发
                    launch {
                        val begin = System.currentTimeMillis()
                        val list = BookSourceParser(source.json).search(metadata.name)
                        //任务如果已取消就不再推送
                        if (isActive && list.isNotEmpty()) {
                            val speed = System.currentTimeMillis() - begin
                            list.forEach { metadata -> handleSearchBook(metadata, source, speed) }
                            if (searchJob?.isActive == true && searchBook != null) {
                                SearchObserver.postValue(listOf(searchBook!!))
                            }
                        }
                    }
                }
            }
            searchJob?.join()
        }
        return liveData
    }

    /**
     * 处理搜索结果
     */
    private fun handleSearchBook(metadata: SearchMetadata, source: BookSource, speed: Long) {
        if (searchBook == null || (speed == 0L && searchBook?.name != metadata.name)) {
            val books = CopyOnWriteArrayList<SearchResult>()
            books.add(SearchResult(metadata, source, speed))
            searchBook = SearchBook(metadata.name, metadata.author, metadata.summary, metadata.cover, books)
        } else if (searchBook?.name == metadata.name) {
            if (!URLUtil.isNetworkUrl(searchBook?.cover)) searchBook?.cover = metadata.cover
            searchBook?.list?.add(SearchResult(metadata, source, speed))
        }
    }

    /**
     * 取消搜索
     */
    private fun cancelSearch() {
        if (searchJob?.isActive == true) {
            searchJob?.cancel()
        }
        searchBook?.list?.clear()
        searchBook == null
        SearchObserver.postValue(emptyList())
    }

    /**
     * 构建URL
     */
    private fun buildUrl(rank: BookSourceJson.Rank, page: Int = rank.page, category: String? = null): String {
        var url = rank.url
        if (page > -1) url = url.replace("\${page}", page.toString())
        if (category != null) url = url.replace("\${key}", category)
        return url
    }

}
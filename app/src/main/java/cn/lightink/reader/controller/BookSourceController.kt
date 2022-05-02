package cn.lightink.reader.controller

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.toLiveData
import cn.lightink.reader.ktx.toJson
import cn.lightink.reader.model.BookRank
import cn.lightink.reader.model.BookSource
import cn.lightink.reader.module.EMPTY
import cn.lightink.reader.module.LIMIT
import cn.lightink.reader.module.Room
import cn.lightink.reader.module.booksource.BookSourceJson
import cn.lightink.reader.module.booksource.BookSourceParser
import cn.lightink.reader.net.BookSourceStore
import cn.lightink.reader.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookSourceController : ViewModel() {

    /**************************************************************************************************************************************
     * 书源列表
     *************************************************************************************************************************************/
    //读取已安装书源列表
    val bookSources = Room.bookSource().getAll().toLiveData(LIMIT)

    /**
     * 评分
     */
    fun scoreBookSource(bookSource: BookSource, score: Float): LiveData<Unit> {
        val liveData = MutableLiveData<Unit>()
        viewModelScope.launch(Dispatchers.IO) {
            if (BookSourceStore.score(bookSource.id, score).isSuccessful) {
                upgradeBookSource(bookSource)
                liveData.postValue(Unit)
            }
        }
        return liveData
    }

    /**
     * 更新升级低版本书源
     */
    fun upgradeBookSources() = viewModelScope.launch(Dispatchers.IO) {
        Room.bookSource().getAllOnline().forEach { bookSource ->
            launch {
                upgradeBookSource(bookSource)
            }
        }
    }

    /**
     * 更新升级低版本书源
     */
    @Suppress("SENSELESS_COMPARISON")
    private fun upgradeBookSource(bookSource: BookSource) {
        val result = BookSourceStore.download(bookSource.id, bookSource.version)
        when {
            //中央仓库已废弃此书源
            result.code >= 400 == null -> Room.bookSource().remove(bookSource)
            //中央仓库有新版本
            result.isSuccessful && result.data?.content?.isNotBlank() == true -> {
                bookSource.sameTo(result.data)
                Room.bookSource().update(bookSource)
            }
            //中央仓库无新版本但有其他信息更新
            result.isSuccessful && result.data != null && !bookSource.sameTo(result.data) -> Room.bookSource().update(bookSource)
        }
    }

    /**
     * 验证书源登录
     */
    fun verify(bookSource: BookSource): LiveData<Boolean> {
        val result = MutableLiveData<Boolean>()
        viewModelScope.launch(Dispatchers.IO) {
            result.postValue(BookSourceParser(bookSource.json).verify())
        }
        return result
    }

    /**************************************************************************************************************************************
     * 验证网络书源
     *************************************************************************************************************************************/
    fun verifyRepository(url: String): LiveData<String> {
        val liveData = MutableLiveData<String>()
        viewModelScope.launch(Dispatchers.IO) {
            val response = Http.get<List<String>>(url)
            when {
                response.isSuccessful && response.data.isNullOrEmpty() -> liveData.postValue("该网址不存在书源索引")
                response.isSuccessful -> {
                    withContext(Dispatchers.IO) {
                        response.data?.forEach { name ->
                            launch { verifyBookSource(name, url) }
                        }
                    }
                    liveData.postValue(EMPTY)
                }
                else -> liveData.postValue(response.message)
            }
        }
        return liveData
    }

    private suspend fun verifyBookSource(name: String, baseUrl: String) {
        val url = "${baseUrl.substringBeforeLast("/")}/sources/$name.json"
        val response = Http.get<BookSourceJson>(url)
        if (response.isSuccessful && response.data != null && !Room.bookSource().isInstalled(response.data.url)) {
            val bookSource = Room.bookSource().getLocalInstalled(response.data.url)
            if (bookSource != null) {
                //已安装需要更新版本
                if (bookSource.version < response.data.version) {
                    if (!bookSource.rank && !response.data.rank.isNullOrEmpty() && !Room.bookRank().isExist(response.data.url)) {
                        Room.bookRank().insert(BookRank(response.data.url, response.data.name))
                    }
                    bookSource.name = response.data.name
                    bookSource.version = response.data.version
                    bookSource.rank = response.data.rank.isNullOrEmpty()
                    bookSource.account = response.data.auth != null
                    bookSource.content = response.data.toJson()
                    Room.bookSource().update(bookSource)
                }
            } else {
                //未安装
                Room.bookSource().install(BookSource(0, response.data.name, response.data.url, response.data.version, !response.data.rank.isNullOrEmpty(), response.data.auth != null, baseUrl, -1, 0F, response.data.toJson()))
                if (!response.data.rank.isNullOrEmpty() && !Room.bookRank().isExist(response.data.url)) {
                    Room.bookRank().insert(BookRank(response.data.url, response.data.name))
                }
            }
        }
    }

    /**
     * 卸载书源
     */
    fun uninstall(bookSource: BookSource) {
        viewModelScope.launch(Dispatchers.IO) {
            Room.bookSource().uninstall(bookSource)
        }
    }

}
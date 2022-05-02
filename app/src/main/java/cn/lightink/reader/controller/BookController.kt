package cn.lightink.reader.controller

import android.content.Context
import android.net.Uri
import android.webkit.URLUtil
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.lightink.reader.BOOK_PATH
import cn.lightink.reader.ktx.only
import cn.lightink.reader.ktx.toJson
import cn.lightink.reader.model.Book
import cn.lightink.reader.model.Bookshelf
import cn.lightink.reader.model.SearchBook
import cn.lightink.reader.model.SearchResult
import cn.lightink.reader.module.*
import cn.lightink.reader.module.booksource.*
import cn.lightink.reader.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 图书控制器
 *
 * @see cn.lightink.reader.ui.book.BookDetailActivity
 * @since 1.0.0
 */
class BookController : ViewModel() {

    private val bookDetailLive = MutableLiveData<DetailMetadata>()
    private var bookSource: BookSourceJson? = null

    val catalogLive = MutableLiveData<List<Chapter>>()

    /**
     * 查询书架列表
     */
    fun queryBookshelves() = Room.bookshelf().getAll()

    /**
     * 查询图书详情
     * 先返回上个页面传递来的元数据
     * 再通过书源规则联网读取元数据
     */
    fun queryBookDetail(result: SearchResult): LiveData<DetailMetadata> {
        viewModelScope.launch(Dispatchers.IO) {
            bookSource = result.source.json
            queryBookDetail(result.metadata)
        }
        return bookDetailLive
    }

    /**
     * 读取图书详情
     * @param metadata      元数据
     */
    private fun queryBookDetail(metadata: SearchMetadata) {
        val parser = BookSourceParser(bookSource!!)
        val detail = parser.findDetail(metadata) ?: return
        bookDetailLive.postValue(detail)
        //读取目录
        catalogLive.postValue(parser.findCatalog(detail))
    }

    /**
     * 将网络图书转为本地图书
     * @param bookshelf     指定书架 无数据就是预览模式
     */
    fun publish(baseInfo: SearchBook?, bookshelf: Bookshelf? = null): LiveData<Book> {
        val liveData = MutableLiveData<Book>()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                //章节列表
                val chapters = if (catalogLive.value?.isNotEmpty() == true) catalogLive.value!! else return@launch liveData.postValue(null)
                //图书元数据
                val metadata = bookDetailLive.value ?: return@launch liveData.postValue(null)
                metadata.name = baseInfo?.name ?: metadata.name
                metadata.author = if (baseInfo?.author.isNullOrBlank()) metadata.author else baseInfo!!.author
                //生成输出目录
                val output = File(BOOK_PATH, metadata.objectId).only()
                //生成图片文件夹
                File(output, MP_FOLDER_IMAGES).mkdirs()
                //生成章节文件夹
                File(output, MP_FOLDER_TEXTS).mkdirs()
                //生成目录
                val catalog = File(output, MP_FILENAME_CATALOG).apply { createNewFile() }
                chapters.forEach { chapter ->
                    if (chapter.useLevel) catalog.appendText(MP_CATALOG_INDENTATION)
                    catalog.appendText("* [${chapter.name}](${chapter.url})$MP_ENTER")
                }
                //存储元数据
                File(output, MP_FILENAME_METADATA).writeText(metadata.toMetadata().toJson())
                //存储书源
                bookSource?.run { File(output, MP_FILENAME_BOOK_SOURCE).writeText(toJson()) }
                //构造图书对象
                val book = Book(metadata.toMetadata(), bookshelf?.id ?: -1L)
                book.catalog = chapters.size
                book.lastChapter = chapters.last().name
                if (URLUtil.isNetworkUrl(baseInfo?.cover ?: metadata.cover)) withContext(Dispatchers.IO) {
                    if (File(book.cover).parentFile?.exists() == false) File(book.cover).parentFile?.mkdirs()
                    try {
                        Http.download(baseInfo?.cover ?: metadata.cover).data?.run { File(book.cover).writeBytes(this) }
                    } catch (e: Exception) {
                        //防止超时或其他网络错误
                    }
                }
                if (bookshelf != null) {
                    //非预览模式检查同步、下载封面、加入书架
                    Room.book().insert(book)
                    Room.bookSource().get(if (bookSource?.url?.startsWith("http") == true) Uri.parse(bookSource?.url)?.host.orEmpty() else bookSource?.url.orEmpty())?.run { Room.bookSource().update(this.apply { frequency += 1 }) }
                    liveData.postValue(book)
                } else {
                    //预览模式直接返回
                    liveData.postValue(book)
                }
            } catch (e: Exception) {
                //防止写入文件时因缓存删除报错
            }
        }
        return liveData
    }

    /**
     * 查询图书封面
     * @param bookName  书名
     * @sample cn.lightink.reader.ui.book.BookCoverActivity
     * @since 1.0.0
     */
    fun queryCover(bookName: String): LiveData<List<String>> {
        val result = MutableLiveData<List<String>>()
        viewModelScope.launch(Dispatchers.IO) {
            CoverSource.values().forEach { source ->
                launch {
                    result.postValue(BookCover.query(bookName, source).map { it.url })
                }
            }
            Room.bookSource().getAllImmediately().forEach { source ->
                launch {
                    val cover = BookSourceParser(source.json).searchCover(bookName)
                    if (URLUtil.isNetworkUrl(cover)) result.postValue(listOf(cover))
                }
            }
        }
        return result
    }

    /**
     * 下载封面
     */
    fun downloadCover(book: Book, url: String): LiveData<Boolean> {
        val result = MutableLiveData<Boolean>()
        viewModelScope.launch(Dispatchers.IO) {
            val response = Http.get<ByteArray>(url)
            if (response.isSuccessful && response.data?.isNotEmpty() == true) {
                if (File(book.cover).parentFile?.exists() == true) {
                    File(book.cover).writeBytes(response.data)
                    Notify.post(Notify.BookCoverChangedEvent(book))
                    result.postValue(true)
                } else {
                    result.postValue(false)
                }
            } else {
                result.postValue(false)
            }
        }
        return result
    }

    /**
     * 拷贝相册封面
     */
    fun copyCover(context: Context, uri: Uri, book: Book): LiveData<Boolean> {
        val result = MutableLiveData<Boolean>()
        viewModelScope.launch(Dispatchers.IO) {
            val document = DocumentFile.fromSingleUri(context, uri) ?: return@launch result.postValue(false)
            val byteArray = context.contentResolver.openInputStream(document.uri).use { it?.readBytes() } ?: return@launch result.postValue(false)
            if (File(book.cover).parentFile?.exists() == true) {
                File(book.cover).writeBytes(byteArray)
                Notify.post(Notify.BookCoverChangedEvent(book))
                result.postValue(true)
            } else {
                result.postValue(false)
            }
        }
        return result
    }

}
package cn.lightink.reader.model

import android.os.Parcelable
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.room.*
import cn.lightink.reader.BOOK_PATH
import cn.lightink.reader.ktx.md5
import cn.lightink.reader.ktx.toJson
import cn.lightink.reader.module.*
import cn.lightink.reader.module.Room
import cn.lightink.reader.module.booksource.BookSourceJson
import cn.lightink.reader.module.booksource.BookSourceParser
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parcelize
import java.io.File

/********************************************************************************************************************************
 * 存储类  书
 ********************************************************************************************************************************
 *  @property objectId          唯一ID
 *  @property name              名字
 *  @property author            作者
 *  @property link              链接
 *  @property publishId         出版ID
 *  @property bookshelf         书架
 *  @property lastChapter       最新章节
 *  @property state             连载状态
 *  @property catalog           目录章节数
 *  @property chapter           上次阅读章节索引
 *  @property chapterProgress   上次阅读章节内索引
 *  @property chapterName       上次阅读章节名
 *  @property time              累计阅读时长
 *  @property word              累计阅读字数
 *  @property speed             平均阅读速度
 *  @property updatedAt         上次阅读时间
 *  @property finishedAt        结束阅读时间
 *  @property createdAt         加入书架时间
 *  @property configuration     配置
 *  @property version           同步版本号
 */
@Parcelize
@Entity(primaryKeys = ["objectId"], indices = [Index("objectId", unique = true)])
data class Book(val objectId: String, var name: String, var author: String, var link: String, var publishId: String, var bookshelf: Long, var lastChapter: String = EMPTY, var state: Int = BOOK_STATE_IDLE, var catalog: Int = 0, var chapter: Int = 0, var chapterProgress: Int = 0, var chapterName: String = EMPTY, var time: Int = 0, var word: Int = 0, var speed: Float = 0F, var updatedAt: Long = System.currentTimeMillis(), var finishedAt: Long = 0L, var configuration: Long = 0, var createdAt: Long = System.currentTimeMillis(), var version: Int = -1) : Parcelable {

    @Ignore
    @IgnoredOnParcel
    val path = File(BOOK_PATH, objectId)

    @Ignore
    @IgnoredOnParcel
    val cover = File(BOOK_PATH, "$objectId/$MP_FOLDER_IMAGES/cover").absolutePath.orEmpty()

    //是否有书源
    fun hasBookSource() = File(path, MP_FILENAME_BOOK_SOURCE).exists()

    //读取书源
    fun getBookSource(): BookSourceParser? {
        if (!File(path, MP_FILENAME_BOOK_SOURCE).exists()) return null
        val json = File(path, MP_FILENAME_BOOK_SOURCE).readText()
        return try {
            val currentJson = Gson().fromJson(json, BookSource::class.java)
            val bookSource = Room.bookSource().get(currentJson.url)
//            val roomJson = bookSource?.json
            if (bookSource?.version != null && bookSource.version > currentJson.version) {
                File(path, MP_FILENAME_BOOK_SOURCE).writeText(bookSource.toJson())
                BookSourceParser(bookSource)
            } else {
                BookSourceParser(bookSource!!)
            }
        } catch (e: Exception) {
            Log.e("Book", "getBookSource error", e)
            null
        }
    }

    val stateResId: Int
        get() = when {
            state == BOOK_STATE_UPDATE -> cn.lightink.reader.R.drawable.ic_book_state_update
            else -> 0
        }

    val status: String
        get() = when {
            state == BOOK_STATE_UPDATE -> "更新"
            else -> EMPTY
        }

    val statusResId: Int
        get() = when {
            state == BOOK_STATE_UPDATE -> cn.lightink.reader.R.drawable.bg_book_state_update
            else -> 0
        }

    fun same(other: Book) = updatedAt == other.updatedAt && objectId == other.objectId && state == other.state && cover == other.cover && name == other.name && author == other.author && lastChapter == other.lastChapter && other.chapter == chapter

    constructor(metadata: MPMetadata, bookshelf: Long) : this(metadata.objectId, metadata.name, metadata.author, metadata.link, metadata.publishId, bookshelf, state = metadata.state)

    override fun equals(other: Any?): Boolean {
        if (other is Book) return other.objectId == objectId
        return super.equals(other)
    }

    override fun hashCode(): Int {
        var result = objectId.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }

}

@Dao
interface BookDao {

    @Query("SELECT * FROM book")
    fun getAll(): List<Book>

    @Query("SELECT * FROM book WHERE bookshelf = :bookshelf")
    fun getAll(bookshelf: Long): List<Book>

    @Query("SELECT objectId FROM book")
    fun getObjectIdAll(): List<String>

    @Query("SELECT * FROM book WHERE name LIKE :key OR author LIKE :key")
    fun search(key: String): List<Book>

    //最后阅读
    @Query("SELECT * FROM book ORDER BY updatedAt DESC LIMIT 1")
    fun getTheLastRead(): Book?

    //需要更新
    @Query("SELECT * FROM book WHERE state = 0 OR state = 1")
    fun getAllNeedCheckUpdate(): List<Book>

    //需要更新
    @Query("SELECT COUNT(objectId) FROM book WHERE state = 0 OR state = 1 LIMIT 1")
    fun hasNeedCheckUpdate(): Boolean

    @Query("SELECT * FROM book WHERE bookshelf = :bookshelf ORDER BY updatedAt DESC")
    fun getAllSortByTime(bookshelf: Long): LiveData<List<Book>>

    @Query("SELECT * FROM book WHERE bookshelf = :bookshelf ORDER BY createdAt")
    fun getAllSortByDrag(bookshelf: Long): LiveData<List<Book>>

    @Query("SELECT * FROM book WHERE objectId = :objectId LIMIT 1")
    fun get(objectId: String): Book

    //读取云端状态
    @Query("SELECT * FROM book WHERE publishId = :publishId AND state = 3 LIMIT 1")
    fun getByCloudState(publishId: String): Book?

    @Query("SELECT * FROM book WHERE name LIKE '%' || :key || '%' OR author LIKE '%' || :key || '%'")
    fun queryLike(key: String): List<Book>

    @Query("SELECT COUNT(objectId) FROM book WHERE objectId=:objectId")
    fun has(objectId: String): Boolean

    @Query("SELECT COUNT(objectId) FROM book WHERE publishId=:publishId")
    fun has(publishId: Long): Boolean

    @Query("SELECT COUNT(objectId) FROM book WHERE bookshelf=:bookshelf LIMIT 1")
    fun isNotEmpty(bookshelf: Long): Boolean

    @Query("SELECT COUNT(objectId) FROM book WHERE bookshelf=:bookshelf")
    fun count(bookshelf: Long): Int

    @Query("SELECT COUNT(objectId) FROM book WHERE bookshelf=:bookshelf AND state=$BOOK_STATE_UPDATE")
    fun countUpdate(bookshelf: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg books: Book)

    @Update
    fun update(vararg book: Book): Int

    @Delete
    fun delete(vararg book: Book)
}

/********************************************************************************************************************************
 * 存储类  书架
 ********************************************************************************************************************************
 * @property id             唯一ID
 * @property name           名字
 * @property layout         布局（0 网格 1 列表）
 * @property info           信息（0 简要 1 详细）
 * @property sort           排序（0 时间 1 书名）
 * @property preferred      首选书架
 */
@Entity(primaryKeys = ["id"])
@Parcelize
data class Bookshelf(@SerializedName("a") var id: Long = System.currentTimeMillis(), @SerializedName("b") var name: String, @SerializedName("c") var layout: Int = 0, @SerializedName("d") var info: Int = 1, @SerializedName("e") var sort: Int = 0, @SerializedName("f") var preferred: Boolean = false, @SerializedName("g") var synchronize: Boolean = false) : Parcelable

@Dao
interface BookshelfDao {

    @Query("SELECT * FROM bookshelf ORDER BY id")
    fun getAll(): LiveData<List<Bookshelf>>

    @Query("SELECT * FROM bookshelf ORDER BY id")
    fun getAllImmediately(): List<Bookshelf>

    @Query("SELECT * FROM bookshelf LIMIT 1")
    fun getFirst(): Bookshelf

    @Query("SELECT * FROM bookshelf WHERE id = :id LIMIT 1")
    fun get(id: Long): Bookshelf

    @Query("SELECT * FROM bookshelf WHERE name = :name LIMIT 1")
    fun get(name: String): Bookshelf?

    @Query("SELECT * FROM bookshelf WHERE preferred = 1 LIMIT 1")
    fun getPreferred(): Bookshelf

    @Query("SELECT COUNT(*) FROM bookshelf WHERE preferred = 1")
    fun hasPreferred(): Int

    @Query("SELECT COUNT(*) FROM bookshelf WHERE name = :name")
    fun has(name: String): Boolean

    @Query("SELECT COUNT(*) FROM bookshelf")
    fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg bookshelf: Bookshelf)

    @Update
    fun update(bookshelf: Bookshelf)

    @Delete
    fun remove(bookshelf: Bookshelf)
}

/***************************************************************************************************
 * 存储类  书签
 **************************************************************************************************/
@Entity(indices = [Index("bookId")], foreignKeys = [ForeignKey(entity = Book::class, parentColumns = ["objectId"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)])
data class Bookmark(val chapter: Int, val progress: Int, val title: String, val summary: String, val bookId: String, @PrimaryKey(autoGenerate = true) val id: Long = 0)

@Dao
interface BookmarkDao {

    //查询
    @Query("SELECT * FROM bookmark WHERE bookId=:bookId ORDER BY chapter, progress")
    fun getAll(bookId: String): LiveData<List<Bookmark>>

    //查询
    @Query("SELECT COUNT(chapter) FROM bookmark WHERE bookId=:bookId AND chapter=:chapter AND progress>=:start AND progress<:end LIMIT 1")
    fun has(bookId: String, chapter: Int, start: Int, end: Int): Boolean

    //删除
    @Query("DELETE FROM bookmark WHERE bookId=:bookId AND chapter=:chapter AND progress>=:start AND progress<:end")
    fun remove(bookId: String, chapter: Int, start: Int, end: Int)

    //插入
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(bookmark: Bookmark)

    //删除
    @Delete
    fun delete(vararg bookmark: Bookmark)

}

/********************************************************************************************************************************
 * 存储类  离线进度
 *******************************************************************************************************************************/
@Entity(indices = [Index("pid")], foreignKeys = [ForeignKey(entity = Book::class, parentColumns = ["objectId"], childColumns = ["pid"], onDelete = ForeignKey.CASCADE)])
data class Statistics(@PrimaryKey val id: Long, val uid: String, val pid: String, val progress: String)

@Dao
interface StatisticsDao {

    @Query("SELECT * FROM statistics WHERE uid = :uid ORDER BY id LIMIT 100")
    fun getAll(uid: String): List<Statistics>

    @Query("SELECT * FROM statistics WHERE uid = :uid AND pid = :pid ORDER BY id LIMIT 100")
    fun getAll(uid: String, pid: String): List<Statistics>

    @Query("SELECT COUNT(*) FROM statistics WHERE uid = :uid AND pid = :pid")
    fun count(uid: String, pid: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(statistics: Statistics)

    @Delete
    fun delete(vararg statistics: Statistics)
}

/********************************************************************************************************************************
 * 存储类  阅读记录
 *******************************************************************************************************************************/
@Entity
data class BookRecord(val bookId: String, val chapter: Int, val date: Long = System.currentTimeMillis(), @PrimaryKey(autoGenerate = true) val id: Long = 0L)

@Dao
interface BookRecordDao {

    @Query("SELECT chapter FROM bookrecord WHERE bookId=:bookId")
    fun query(bookId: String): List<Int>

    @Query("SELECT COUNT(*) FROM bookrecord WHERE bookId=:bookId")
    fun count(bookId: String): Int

    @Query("SELECT COUNT(*) FROM bookrecord WHERE bookId=:bookId AND chapter=:chapter LIMIT 1")
    fun has(bookId: String, chapter: Int): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(record: BookRecord)

    //删除某本书的全部阅读记录
    @Query("DELETE FROM bookrecord WHERE bookId=:bookId")
    fun remove(bookId: String)

}

/********************************************************************************************************************************
 * 存储类  同步记录
 *******************************************************************************************************************************/
@Entity(tableName = "synchronize", indices = [Index("book")], foreignKeys = [ForeignKey(entity = Book::class, parentColumns = ["objectId"], childColumns = ["book"], onDelete = ForeignKey.CASCADE)])
data class BookSynchronize(@PrimaryKey val id: String, var book: String)

@Dao
interface BookSynchronizeDao {

    @Query("SELECT COUNT(*) FROM synchronize WHERE id=:id LIMIT 1")
    fun has(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(synchronize: BookSynchronize)
}

/********************************************************************************************************************************
 * 存储类  本地文件关联
 *******************************************************************************************************************************/
@Entity(tableName = "file", indices = [Index("book")], foreignKeys = [ForeignKey(entity = Book::class, parentColumns = ["objectId"], childColumns = ["book"], onDelete = ForeignKey.CASCADE)])
data class BookFileLink(@PrimaryKey val path: String, var book: String)

@Dao
interface BookFileLinkDao {

    @Query("SELECT COUNT(*) FROM file WHERE path=:path LIMIT 1")
    fun has(path: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(fileLink: BookFileLink)
}
/********************************************************************************************************************************
 * 数据类
 *******************************************************************************************************************************/
/**
 * 图书选择状态
 * @property book       图书
 * @property checked    选中状态
 */
data class StateBook(val book: Book, var checked: Boolean = false, var force: Boolean = false) {

    fun same(other: StateBook) = checked == other.checked && force == other.force && book.updatedAt == other.book.updatedAt && book.objectId == other.book.objectId && book.state == other.book.state && book.cover == other.book.cover && book.name == other.book.name && book.author == other.book.author && book.lastChapter == other.book.lastChapter && other.book.chapter == book.chapter

    override fun equals(other: Any?) = if (other !is StateBook) false else book.objectId == other.book.objectId

    override fun hashCode() = book.objectId.hashCode()
}

/**
 * MarkdownPublish元数据
 * @property name       书名
 * @property author     作者
 * @property link       链接
 * @property publishId  出版ID
 * @property state      状态
 */
data class MPMetadata(val name: String = EMPTY, val author: String = EMPTY, val link: String = EMPTY, var publishId: String = EMPTY, val state: Int = BOOK_STATE_IDLE) {
    var objectId = "$name:$author".md5()
}


/**
 * 阅读同步进度
 * @property id         ID
 * @property chapter    章节索引
 * @property progress   内容索引
 * @property title      章节标题
 * @property count      本地阅读时长
 * @property total      累计时长
 * @property speed      平均速度
 * @property finished   读完标记
 * @property startAt    起始时间
 * @property endAt      完结时间
 * @property valid      数据校验
 */
@Parcelize
data class BookSyncProgress(val chapter: Int, val progress: Int, val title: String, val count: Float, val total: Float, val speed: Int, val finished: Boolean, var startAt: Long, val endAt: Long, val id: Long = System.currentTimeMillis(), var valid: String = EMPTY) : Parcelable {

}

/**
 * 优书网请求结果
 */
class YouSuuResult(val data: Data) {

    class Data(val books: List<Book>)

    class Book(val title: String, val cover: String, val author: String)
}
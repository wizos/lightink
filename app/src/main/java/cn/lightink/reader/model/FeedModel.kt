package cn.lightink.reader.model

import android.os.Parcelable
import androidx.lifecycle.LiveData
import androidx.room.*
import cn.lightink.reader.module.Room
import com.google.gson.annotations.SerializedName
import kotlinx.android.parcel.Parcelize

/**
 * 订阅分组
 * @property id     ID 关联Feed
 * @property name   标题 用于显示
 * @property date   日期 用于排序
 * @property bookshelf 书架快捷入口
 */
@Entity
data class FeedGroup(var name: String, var date: Long = System.currentTimeMillis(), var bookshelf: Long = 0, @PrimaryKey(autoGenerate = true) val id: Long = 0L)

@Dao
interface FeedGroupDao {

    @Query("SELECT * FROM feedgroup WHERE id=:id LIMIT 1")
    fun get(id: Long): FeedGroup?

    @Query("SELECT * FROM feedgroup ORDER BY date")
    fun getAll(): LiveData<List<FeedGroup>>

    @Query("SELECT * FROM feedgroup ORDER BY date")
    fun getAllImmediately(): List<FeedGroup>

    @Query("SELECT * FROM feedgroup WHERE bookshelf=:bookshelfId ORDER BY date LIMIT 1")
    fun getByBookshelf(bookshelfId: Long): LiveData<FeedGroup?>

    @Query("SELECT * FROM feedgroup WHERE name=:name LIMIT 1")
    fun getByName(name: String): FeedGroup?

    @Query("SELECT COUNT(name) FROM feedgroup WHERE name=:name LIMIT 1")
    fun has(name: String): Boolean

    @Query("UPDATE feedgroup SET bookshelf=0 WHERE bookshelf=:bookshelf")
    fun unPushpin(bookshelf: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(group: FeedGroup): Long

    @Query("SELECT COUNT(*) FROM feedgroup LIMIT 1")
    fun isNotEmpty(): Boolean

    @Update
    fun update(group: FeedGroup)

    @Delete
    fun remove(group: FeedGroup)

}

/**
 * 频道
 * @property link           订阅地址
 * @property name           名字
 * @property summary        摘要
 * @property date           更新日期
 */
@Entity
data class Feed(var link: String, var name: String, var group: Long = 0, var summary: String? = null, var date: Long = 0, @PrimaryKey(autoGenerate = true) var id: Long = 0)

@Dao
interface FeedDao {

    @Query("SELECT * FROM feed")
    fun getAll(): List<Feed>

//    @Query("SELECT * FROM feed WHERE feed=:feed LIMIT 1")
//    fun getImmediately(feed: String): Feed?

    @Query("SELECT * FROM feed WHERE id=:id LIMIT 1")
    fun getById(id: Long): Feed?

    @Query("SELECT * FROM feed WHERE `group`=:groupId ")
    fun getByGroupId(groupId: Long): LiveData<List<Feed>>

    @Query("SELECT id FROM feed WHERE `group`=:groupId ")
    fun getByGroupIdImmediately(groupId: Long): List<Long>

    @Query("SELECT * FROM feed WHERE `group`=:groupId ")
    fun getByGroupImmediately(groupId: Long): List<Feed>

    @Query("SELECT * FROM feed WHERE link=:link LIMIT 1")
    fun getByLink(link: String): Feed?

    @Query("SELECT COUNT(link) FROM feed WHERE link=:link LIMIT 1")
    fun has(link: String): Boolean

    @Query("SELECT COUNT(link) FROM feed WHERE `group`=:groupId LIMIT 1")
    fun isNotEmpty(groupId: Long): Boolean

    //
//    @Query("SELECT COUNT(feed) FROM feed LIMIT 1")
//    fun isNotEmpty(): Boolean
//
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(feed: Feed): Long

    @Update
    fun update(feed: Feed)

    @Delete
    fun remove(feed: Feed)
}

/**
 * 信息流
 * @property link       链接
 * @property title      标题
 * @property author     作者
 * @property cover      封面
 * @property feed       订阅
 * @property date       时间
 * @property read       已读
 */
@Parcelize
@Entity
data class Flow(@PrimaryKey var link: String, var title: String, var author: String, var summary: String, var cover: String?, var date: Long, var feed: Long, @ColumnInfo(name = "feed_name") var feedName: String, var read: Boolean = false, var love: Boolean = false, var id: Long = 0) : Parcelable {

    val owner: Feed?
        get() = Room.feed().getById(feed)

    fun same(flow: Flow) = link == flow.link && read == flow.read && love == flow.love

}

@Dao
interface FlowDao {

    //    @Query("SELECT * FROM flow ORDER BY date DESC")
//    fun getAll(): LiveData<L ist<Flow>>
//
    @Query("SELECT * FROM flow WHERE feed in (:feeds) ORDER BY date DESC")
    fun getAllByFeed(feeds: List<Long>): LiveData<List<Flow>>

    @Query("SELECT link FROM flow WHERE feed in (:feeds) ORDER BY date DESC")
    fun getLinksByFeed(feeds: List<Long>): LiveData<List<String>>

    @Query("SELECT * FROM flow WHERE feed in (:feeds) ORDER BY date DESC LIMIT 5")
    fun getForBookshelf(feeds: List<Long>): LiveData<List<Flow>>

    //
    @Query("SELECT * FROM flow WHERE love = 1 ORDER BY date DESC")
    fun getLoved(): LiveData<List<Flow>>

    @Query("SELECT * FROM flow WHERE link = :link LIMIT 1")
    fun get(link: String): Flow?

    @Query("SELECT * FROM flow WHERE id = :id LIMIT 1")
    fun getById(id: Long): Flow?

    //    @Query("SELECT COUNT(link) FROM flow WHERE read = 1")
//    fun hasRead(): LiveData<Int>
//
//    @Query("SELECT COUNT(link) FROM flow WHERE feed=:feed AND (title=:title OR link=:link)")
//    fun has(feed: String, title: String, link: String): Boolean
//
//    @Query("SELECT COUNT(link) FROM flow")
//    fun countAll(): Int
//
//    @Query("SELECT COUNT(link) FROM flow WHERE feed = :feed")
//    fun count(feed: String): Int
//
//    @Query("SELECT COUNT(link) FROM flow WHERE feed = :feed AND read = 0")
//    fun unread(feed: String): Int

    @Query("DELETE FROM flow WHERE feed in (:feeds) AND read = 1 AND love = 0")
    fun clear(feeds: List<Long>)

    @Query("DELETE FROM flow WHERE read = 1 AND love = 0")
    fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg flows: Flow)

    @Query("DELETE FROM flow WHERE feed = :feed AND love = 0")
    fun removeByFeed(feed: Long)

    @Update
    fun update(flow: Flow)

    @Delete
    fun delete(flow: Flow)
}


/**
 * 频道验证结果
 */
data class FeedVerifyResult(val message: String, val feed: Feed? = null, val flows: List<Flow>? = null)

/**
 * 商店频道
 */
@Parcelize
data class StoreFeed(val name: String, val logo: String, @SerializedName("introduction") val summary: String, val tag: String, val rss: String) : Parcelable


package cn.lightink.reader.model

import androidx.lifecycle.LiveData
import androidx.room.*
import cn.lightink.reader.ktx.md5
import cn.lightink.reader.module.booksource.SearchMetadata
import java.util.concurrent.CopyOnWriteArrayList

/********************************************************************************************************************************
 * 存储类  阅读记录
 *******************************************************************************************************************************/
@Entity(tableName = "search", primaryKeys = ["key"])
data class SearchHistory(val key: String, val date: Long = System.currentTimeMillis())

@Dao
interface SearchHistoryDao {

    //查询全部
    @Query("SELECT * FROM search ORDER BY date DESC")
    fun getAll(): LiveData<List<SearchHistory>>

    //是否为空
    @Query("SELECT COUNT(`key`) FROM search LIMIT 1")
    fun isNotEmpty(): Boolean

    //添加
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(history: SearchHistory)

    //删除
    @Delete
    fun delete(history: SearchHistory)

    //清空
    @Query("DELETE FROM search")
    fun clear()
}
/********************************************************************************************************************************
 * 数据类
 *******************************************************************************************************************************/

data class SearchBook(val name: String, var author: String, var summary: String, var cover: String, var list: CopyOnWriteArrayList<SearchResult>) {

    fun objectId() = "$name:$author".md5()

    fun same(metadata: SearchMetadata) = name == metadata.name

    fun same(book: SearchBook) = name == book.name

    fun sameContent(book: SearchBook) = name == book.name && author == book.author && cover == book.cover

}

data class SearchResult(val metadata: SearchMetadata, val source: BookSource, val speed: Long)


package cn.lightink.reader.model

import android.os.Parcelable
import androidx.room.*
import cn.lightink.reader.module.EMPTY
import kotlinx.android.parcel.Parcelize

/********************************************************************************************************************************
 * 存储类 排行榜
 ********************************************************************************************************************************
 */
@Entity(foreignKeys = [ForeignKey(entity = BookSource::class, parentColumns = ["url"], childColumns = ["url"], onDelete = ForeignKey.CASCADE)])
@Parcelize
data class BookRank(@PrimaryKey val url: String, val name: String, var preferred: Int = 0, var category: String = EMPTY, var visible: Boolean = true, var timestamp: Long = System.currentTimeMillis()) : Parcelable

@Dao
interface BookRankDao {

    @Query("SELECT * FROM bookrank ORDER BY timestamp")
    fun getAllImmediately(): List<BookRank>

    @Query("SELECT * FROM bookrank WHERE visible = 1 ORDER BY timestamp")
    fun getVisibleImmediately(): List<BookRank>

    @Query("SELECT COUNT(url) FROM bookrank WHERE url=:url LIMIT 1")
    fun isExist(url: String): Boolean

    @Insert
    fun insert(rank: BookRank)

    @Update
    fun update(vararg ranks: BookRank)

}


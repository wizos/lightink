package cn.lightink.reader.model

import android.os.Parcelable
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.DiffUtil
import androidx.room.*
import cn.lightink.reader.ktx.fromJson
import kotlinx.android.parcel.Parcelize

@Entity(
    tableName = "third",
    primaryKeys = ["account", "category"],
)
@Parcelize
data class ThirdAccount(
    var account: String, var password: String, val server: String, val category: Int
) : Parcelable {

    val profile
        get() = try {
            account.fromJson<Profile>()
        } catch (e: Exception) {
            Profile()
        }

    companion object {
        const val ACCOUNT_VERSION = "AppVer"
        const val CATEGORY_WEBDAV = 0
        const val CATEGORY_BOOK_SOURCE_REPOSITORY = 1
        const val CATEGORY_BAIDU = 2
        const val CATEGORY_SESSION = 9
        const val CATEGORY_VERSION = 7

        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ThirdAccount>() {
            override fun areItemsTheSame(
                oldItem: ThirdAccount, newItem: ThirdAccount
            ) = oldItem.server == newItem.server

            override fun areContentsTheSame(
                oldItem: ThirdAccount, newItem: ThirdAccount
            ) = oldItem == newItem
        }

    }

    class Profile(var nickname: String = "", var balance: String = "")

}

@Dao
interface ThirdAccountDao {

    @Query("SELECT * FROM third WHERE category=:category")
    fun loadByCategory(category: Int): ThirdAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(account: ThirdAccount)

    @Query("SELECT * FROM third WHERE category=:category AND server=:url LIMIT 1")
    fun getByUrl(category: Int, url: String): ThirdAccount?

    @Query("SELECT * FROM third WHERE category=:category")
    fun getByCategory(category: Int): LiveData<ThirdAccount>

    @Query("SELECT * FROM third WHERE category=:category")
    fun getAllByCategory(category: Int): LiveData<List<ThirdAccount>>

    @Query("SELECT COUNT(account) FROM third WHERE account=:account LIMIT 1")
    fun contains(account: String): Boolean

    @Query("DELETE FROM third WHERE category=:category")
    fun deleteByCategory(category: Int)

    @Update
    fun update(account: ThirdAccount)

    @Delete
    fun delete(account: ThirdAccount)

}
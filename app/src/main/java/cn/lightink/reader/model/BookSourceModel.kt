package cn.lightink.reader.model

import androidx.paging.DataSource
import androidx.room.*
import cn.lightink.reader.module.booksource.BookSourceJson
import com.google.gson.Gson

/********************************************************************************************************************************
 * 存储类  书源
 *******************************************************************************************************************************/
/**
 * 书源
 * @param id            ID
 * @param name          名称
 * @param url           地址
 * @param version       版本号
 * @param rank          排行榜
 * @param account       需要账号
 * @param owner         作者
 * @param installs      安装量
 * @param score         评分
 * @param content       内容
 */
@Entity
data class BookSource(val id: Int, var name: String, @PrimaryKey val url: String, var version: Int, var rank: Boolean, var account: Boolean, var owner: String, var installs: Int, var score: Float, var content: String, var frequency: Int = 0) {

    val json: BookSourceJson
        get() = Gson().fromJson(content, BookSourceJson::class.java)

    val author: String
        get() = "${if (installs > -1) owner else "privacy"}\u2000-\u2000$url"

    fun sameTo(bookSource: BookSource): Boolean {
        if (name == bookSource.name && version == bookSource.version && rank == bookSource.rank && account == bookSource.account && owner == bookSource.owner && score == bookSource.score) {
            return true
        }
        name = bookSource.name
        version = bookSource.version
        rank = bookSource.rank
        account = bookSource.account
        owner = bookSource.owner
        installs = bookSource.installs
        score = bookSource.score
        if (bookSource.content.isNotBlank()) {
            content = bookSource.content
        }
        return false
    }

}

@Dao
interface BookSourceDao {

    //查询全部（立即返回）
    @Query("SELECT * FROM booksource")
    fun getAllImmediately(): List<BookSource>

    //查询全部在线书源（立即返回）
    @Query("SELECT * FROM booksource WHERE id > 0")
    fun getAllOnline(): List<BookSource>

    //查询（立即返回）
    @Query("SELECT * FROM booksource WHERE url =:url")
    fun get(url: String): BookSource?

    //查询全部
    @Query("SELECT * FROM booksource ORDER BY frequency DESC")
    fun getAll(): DataSource.Factory<Int, BookSource>

    //查询全部在线书源（立即返回）
    @Query("SELECT DISTINCT owner FROM booksource WHERE id = 0 and owner != ''")
    fun getPrivacyRepositories(): List<String>

    //查询全部在线书源（立即返回）
    @Query("SELECT COUNT(id) FROM booksource WHERE id > 0")
    fun countOnline(): Int

    //是否为空
    @Query("SELECT COUNT(id) FROM booksource LIMIT 1")
    fun isNotEmpty(): Boolean

    //已安装
    @Query("SELECT COUNT(id) FROM booksource WHERE url=:url and id > 0 LIMIT 1")
    fun isInstalled(url: String): Boolean

    //已安装
    @Query("SELECT * FROM booksource WHERE url=:url and id = 0 LIMIT 1")
    fun getLocalInstalled(url: String): BookSource?

    //安装
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun install(bookSource: BookSource)

    //卸载
    @Delete
    fun uninstall(vararg bookSources: BookSource)

    //更新
    @Update
    fun update(vararg bookSources: BookSource)

    @Delete
    fun remove(vararg bookSources: BookSource)

    //清空
    @Query("DELETE FROM booksource")
    fun clear()

    //限制数量
    @Query("SELECT * FROM booksource WHERE id > 0 LIMIT 20,100")
    fun limit(): List<BookSource>

    //清空
    @Query("SELECT COUNT(id) FROM booksource WHERE id > 0")
    fun isLimit(): Int

}
/********************************************************************************************************************************
 * 数据类
 *******************************************************************************************************************************/

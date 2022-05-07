package cn.lightink.reader.module

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import cn.lightink.reader.model.*

object Room {

    private lateinit var database: AppDatabase

    fun attach(context: Context) {
        database = androidx.room.Room.databaseBuilder(context, AppDatabase::class.java, "canary")
//                .addMigrations()
                .allowMainThreadQueries()
                .build()
        initDatabase()
    }

    /**
     * 初始化数据库
     */
    private fun initDatabase() {
        //保证存在一个书架
        if (bookshelf().count() == 0) bookshelf().insert(Bookshelf(1L, "正在阅读", preferred = true))
        //默认主题
        if (!theme().isNotEmpty()) theme().insert(THEME_LIGHT, THEME_NIGHT, THEME_SHEEP, THEME_STORY)
        //限制在线书源数量
        bookSource().remove(*bookSource().limit().toTypedArray())
    }

    /**
     * 获取首选书架
     * 1. 存在首选书架
     * 2. 仅有一个书架
     */
    fun getPreferredBookshelf() = when {
        bookshelf().hasPreferred() == 1 -> bookshelf().getPreferred()
        bookshelf().count() == 1 -> bookshelf().getFirst()
        else -> null
    }

    /***********************************************************************************************
     * 中间层 DAO
     ***********************************************************************************************/
    fun book() = database.book()

    fun bookshelf() = database.bookshelf()

    fun bookRecord() = database.record()

    fun bookSource() = database.bookSource()

    fun theme() = database.theme()

    fun feedGroup() = database.feedGroup()

    fun feed() = database.feed()

    fun flow() = database.flow()

    fun search() = database.search()

    fun file() = database.file()

    fun bookmark() = database.bookmark()

    fun bookRank() = database.bookRank()

    fun folder() = database.folder()

    fun thirdAccount() = database.thirdAccount();
}

@Database(version = 1, entities = [Book::class, Bookshelf::class, Bookmark::class, Statistics::class, BookRecord::class, BookSource::class, Theme::class, FeedGroup::class, Feed::class, Flow::class, SearchHistory::class, BookFileLink::class, BookSynchronize::class, BookRank::class, Folder::class, ThirdAccount::class], exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun book(): BookDao
    abstract fun bookshelf(): BookshelfDao
    abstract fun statistics(): StatisticsDao
    abstract fun record(): BookRecordDao
    abstract fun bookSource(): BookSourceDao
    abstract fun theme(): ThemeDao
    abstract fun feedGroup(): FeedGroupDao
    abstract fun feed(): FeedDao
    abstract fun flow(): FlowDao
    abstract fun search(): SearchHistoryDao
    abstract fun file(): BookFileLinkDao
    abstract fun synchronize(): BookSynchronizeDao
    abstract fun bookmark(): BookmarkDao
    abstract fun bookRank(): BookRankDao
    abstract fun folder(): FolderDao
    abstract fun thirdAccount(): ThirdAccountDao

}

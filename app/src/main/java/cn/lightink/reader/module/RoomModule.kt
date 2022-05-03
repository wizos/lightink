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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
                        MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19)
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
     * 升级补丁
     ***********************************************************************************************/
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("DROP TABLE IF EXISTS 'record'")
            database.execSQL("CREATE TABLE IF NOT EXISTS 'record' ('id' INTEGER PRIMARY KEY NOT NULL, 'bookId' TEXT NOT NULL, 'chapter' INTEGER NOT NULL, 'date' INTEGER NOT NULL, FOREIGN KEY (bookId) REFERENCES Book(objectId) ON DELETE CASCADE)")
        }
    }
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("DROP TABLE IF EXISTS 'record'")
            database.execSQL("CREATE TABLE IF NOT EXISTS 'record' ('id' INTEGER PRIMARY KEY NOT NULL, 'bookId' TEXT NOT NULL, 'chapter' INTEGER NOT NULL, 'date' INTEGER NOT NULL, FOREIGN KEY (bookId) REFERENCES Book(objectId) ON DELETE CASCADE)")
        }
    }
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("DROP TABLE IF EXISTS 'offline'")
            database.execSQL("CREATE TABLE IF NOT EXISTS 'statistics' ('id' INTEGER PRIMARY KEY NOT NULL, 'uid' TEXT NOT NULL, 'pid' TEXT NOT NULL, 'progress' TEXT NOT NULL, FOREIGN KEY (pid) REFERENCES Book(objectId) ON DELETE CASCADE)")
        }
    }
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE UNIQUE INDEX index_Book_objectId ON Book(objectId)")
            database.execSQL("CREATE INDEX index_File_book ON File(book)")
            database.execSQL("CREATE INDEX index_Record_bookId ON Record(bookId)")
            database.execSQL("CREATE INDEX index_Synchronize_book ON Synchronize(book)")
            database.execSQL("CREATE INDEX index_Statistics_pid ON Statistics(pid)")
            database.execSQL("CREATE INDEX index_Flow_feed ON Flow(feed)")
        }
    }
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS bookmark (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, chapter INTEGER NOT NULL, progress INTEGER NOT NULL, title TEXT NOT NULL, summary TEXT NOT NULL, bookId TEXT NOT NULL, FOREIGN KEY (bookId) REFERENCES Book(objectId) ON DELETE CASCADE)")
            database.execSQL("CREATE INDEX index_Bookmark_bookId ON Bookmark(bookId)")
        }
    }
    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE book ADD COLUMN word INTEGER NOT NULL DEFAULT 0")
        }
    }
    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE feed ADD COLUMN description TEXT")
            database.execSQL("ALTER TABLE feed ADD COLUMN logo TEXT")
            database.execSQL("ALTER TABLE feed ADD COLUMN pubDate INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE flow ADD COLUMN description TEXT")
            database.execSQL("ALTER TABLE flow ADD COLUMN logo TEXT")
            database.execSQL("ALTER TABLE flow ADD COLUMN pubDate INTEGER NOT NULL DEFAULT 0")
        }
    }
    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS purify (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, source TEXT NOT NULL, content TEXT NOT NULL)")
        }
    }

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("DELETE FROM synchronize")
            database.execSQL("ALTER TABLE book ADD COLUMN version INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS rank (title TEXT PRIMARY KEY NOT NULL, url TEXT NOT NULL, page INTEGER NOT NULL, type INTEGER NOT NULL, lock TEXT, categories TEXT, 'key' TEXT, value TEXT)")
        }
    }

    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS temp_book_source (name TEXT NOT NULL, url TEXT PRIMARY KEY NOT NULL, category INTEGER NOT NULL, auth INTEGER NOT NULL, version INTEGER NOT NULL, repository TEXT NOT NULL, json TEXT NOT NULL, enable INTEGER NOT NULL)")
            database.execSQL("INSERT INTO temp_book_source (name, url, category, auth, version, repository, json, enable) SELECT name, url, category, auth, version, repository, json, enable FROM BookSource")
            database.execSQL("DROP TABLE BookSource")
            database.execSQL("ALTER TABLE temp_book_source RENAME TO BookSource")
        }
    }

    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS folder (uri TEXT PRIMARY KEY NOT NULL)")
        }
    }

    private val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("DROP TABLE Purify")
            database.execSQL("DROP TABLE BookSource")
            database.execSQL("DROP TABLE Repository")
            database.execSQL("DROP TABLE rank")
            database.execSQL("CREATE TABLE IF NOT EXISTS BookSource (id INTEGER NOT NULL, name TEXT NOT NULL, url TEXT PRIMARY KEY NOT NULL, version INTEGER NOT NULL, rank INTEGER NOT NULL, account INTEGER NOT NULL, owner TEXT NOT NULL, installs INTEGER NOT NULL, score REAL NOT NULL, content TEXT NOT NULL)")
            database.execSQL("CREATE TABLE IF NOT EXISTS BookRank (url TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, preferred INTEGER NOT NULL, category TEXT NOT NULL, visible INTEGER NOT NULL, timestamp INTEGER NOT NULL, FOREIGN KEY (url) REFERENCES BookSource(url) ON DELETE CASCADE)")
        }
    }

    private val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS BookRecord (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, bookId TEXT NOT NULL, chapter INTEGER NOT NULL, date INTEGER NOT NULL)")
            database.execSQL("INSERT INTO BookRecord (id, bookId, chapter, date) SELECT id, bookId, chapter, date FROM record")
            database.execSQL("DROP TABLE record")
            database.execSQL("ALTER TABLE BookSource ADD COLUMN frequency INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("DROP TABLE Flow")
            database.execSQL("CREATE TABLE IF NOT EXISTS Flow (link TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, author TEXT NOT NULL, summary TEXT NOT NULL, cover TEXT, date INTEGER NOT NULL, feed TEXT NOT NULL, read INTEGER NOT NULL, FOREIGN KEY (feed) REFERENCES Feed(feed) ON DELETE CASCADE)")
            database.execSQL("UPDATE Feed SET pubDate = 0")
        }
    }

    private val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("DROP TABLE theme")
            database.execSQL("CREATE TABLE IF NOT EXISTS Theme (id INTEGER PRIMARY KEY NOT NULL, name TEXT NOT NULL, dark INTEGER NOT NULL, background INTEGER NOT NULL, foreground INTEGER NOT NULL, control INTEGER NOT NULL, content INTEGER NOT NULL, secondary INTEGER NOT NULL, horizontal INTEGER NOT NULL, top INTEGER NOT NULL, bottom INTEGER NOT NULL, author TEXT NOT NULL, mipmap TEXT NOT NULL, owner INTEGER NOT NULL, time INTEGER NOT NULL)")
        }
    }

    private val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("DROP TABLE flow")
            database.execSQL("DROP TABLE feed")
            database.execSQL("CREATE TABLE IF NOT EXISTS FeedGroup (id INTEGER PRIMARY KEY NOT NULL, name TEXT NOT NULL, date INTEGER NOT NULL, bookshelf INTEGER NOT NULL)")
            database.execSQL("CREATE TABLE IF NOT EXISTS Feed (id INTEGER PRIMARY KEY NOT NULL, link TEXT NOT NULL, name TEXT NOT NULL, summary TEXT, 'group' INTEGER NOT NULL, date INTEGER NOT NULL)")
            database.execSQL("CREATE TABLE IF NOT EXISTS Flow (link TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, author TEXT NOT NULL, summary TEXT NOT NULL, cover TEXT, date INTEGER NOT NULL, feed INTEGER NOT NULL, feed_name TEXT NOT NULL, read INTEGER NOT NULL, love INTEGER NOT NULL)")
        }
    }

    private val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE Flow ADD COLUMN id INTEGER NOT NULL DEFAULT 0")
        }
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
}

@Database(version = 19, entities = [Book::class, Bookshelf::class, Bookmark::class, Statistics::class, BookRecord::class, BookSource::class, Theme::class, FeedGroup::class, Feed::class, Flow::class, SearchHistory::class, BookFileLink::class, BookSynchronize::class, BookRank::class, Folder::class], exportSchema = false)
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
}

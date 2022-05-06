package cn.lightink.reader.ui.book

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.webkit.URLUtil
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import cn.lightink.reader.R
import cn.lightink.reader.controller.BookController
import cn.lightink.reader.ktx.toast
import cn.lightink.reader.model.Book
import cn.lightink.reader.model.Bookshelf
import cn.lightink.reader.model.SearchBook
import cn.lightink.reader.module.*
import cn.lightink.reader.module.booksource.Chapter
import cn.lightink.reader.module.booksource.DetailMetadata
import cn.lightink.reader.ui.base.LifecycleActivity
import cn.lightink.reader.ui.base.WarningMessageDialog
import cn.lightink.reader.ui.bookshelf.SelectPreferredBookshelfDialog
import cn.lightink.reader.ui.main.SearchActivity
import cn.lightink.reader.ui.reader.ReaderActivity
import com.google.android.material.appbar.AppBarLayout
import kotlinx.android.synthetic.main.activity_book_detail.*
import kotlin.math.max
import kotlin.math.min

class BookDetailActivity : LifecycleActivity() {

    private val controller by lazy { ViewModelProvider(this)[BookController::class.java] }
    private val adapter by lazy { buildAdapter() }
    private var book: SearchBook? = null

    private var index = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_detail)
        setupWithSearch()
        setupView()
        controller.catalogLive.observe(this, Observer { setupBookResult(it) })
    }

    /**
     * 搜索 详情
     */
    private fun setupWithSearch() {
        book = SearchObserver.findValue(intent.getStringExtra(INTENT_BOOK)) ?: return
        SearchObserver.observer(this) { }
        mBookDetailName.text = book!!.name
        mBookDetailAuthor.text = book!!.author
        mBookDetailCover.hint(book!!.name).load(book!!.cover)
        queryBookDetail().observe(this, Observer { it?.run { setupBookMetadata(this) } })
    }

    /**
     * 设置控件
     */
    private fun setupView() {
        //标题显示
        mBookDetailAppBar.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
            mTopbar.text = if (appBarLayout.getChildAt(0).height < -verticalOffset) mBookDetailName.text.toString() else EMPTY
        })
        //显示简介
        mBookDetailSummary.setOnClickListener {
            if (mBookDetailSummary.text.isBlank()) return@setOnClickListener
            WarningMessageDialog(this, mBookDetailSummary.text.toString()).show()
        }
        //设置目录排序菜单
        mBookDetailCatalogLayout.inflateMenu(R.menu.sort)
        mBookDetailCatalogLayout.setOnMenuItemClickListener { item ->
            Preferences.put(Preferences.Key.BOOK_DETAIL_SORT, item.itemId == R.id.menu_sort_ascending)
            setupMenu()
        }
        //设置RV
        mBookDetailRecycler.layoutManager = RVLinearLayoutManager(this)
        mBookDetailRecycler.adapter = adapter
        //书源列表
        mBookDetailSource.setOnClickListener { showBookSourceDialog() }
        //设置目录排序
        setupMenu()
    }

    private fun setupMenu(): Boolean {
        val isDescending = Preferences.get(Preferences.Key.BOOK_DETAIL_SORT, false)
        mBookDetailCatalogLayout.menu.findItem(R.id.menu_sort_ascending).isVisible = !isDescending
        mBookDetailCatalogLayout.menu.findItem(R.id.menu_sort_descending).isVisible = isDescending
        val layoutManager = mBookDetailRecycler.layoutManager as LinearLayoutManager
        layoutManager.stackFromEnd = !isDescending
        layoutManager.reverseLayout = !isDescending
        return true
    }

    /**
     * 查询图书详情
     */
    private fun queryBookDetail(position: Int = 0): LiveData<DetailMetadata> {
        index = position
        adapter.submitList(emptyList())
        mBookDetailCatalogLayout.visibility = View.INVISIBLE
        mBookDetailRead.visibility = View.INVISIBLE
        mBookDetailLoading.isVisible = true
        mBookDetailSource.isEnabled = book?.list?.get(index)?.source?.name.isNullOrBlank().not()
        mBookDetailSource.text = book?.list?.get(index)?.source?.name
        return controller.queryBookDetail(book!!.list[index])
    }

    /**
     * 设置图书元数据
     */
    private fun setupBookMetadata(metadata: DetailMetadata) {
        mTopbar.setMenuVisible(URLUtil.isNetworkUrl(metadata.url))
        mTopbar.setOnMenuClickListener { openOriginWeb(metadata.url) }
        //封面地址
        if (!URLUtil.isNetworkUrl(book?.cover) && URLUtil.isNetworkUrl(metadata.cover)) {
            book?.cover = metadata.cover
            mBookDetailCover.hint(book?.name.orEmpty()).load(book?.cover)
        }
        //作者
        if (book?.author.isNullOrBlank() && metadata.author.isNotBlank()) {
            book?.author = metadata.author
            mBookDetailAuthor.text = book?.author
        }
        //摘要
        if (book?.summary.isNullOrBlank() && metadata.summary.isNotBlank()) {
            book?.summary = metadata.summary
        }
        //作者快速搜索
        mBookDetailAuthor.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java).putExtra(INTENT_SEARCH, book!!.author))
        }
        mBookDetailSummary.text = book?.summary
        mBookDetailCatalogTitle.text = when {
            metadata.status.contains("完") -> "目录\u3000>>\u3000完结\u2000"
            metadata.status.contains("连") || metadata.status.contains("载") -> String.format("目录\u3000>>\u3000连载\u2000%s", metadata.update)
            else -> "目录"
        }
        //书架已存在同名图书时，显示继续阅读按钮
        if (book != null && Room.book().has(book!!.objectId())) {
            intent.putExtra(INTENT_BOOK, book!!.objectId())
            mBookDetailLoading.isVisible = false
            mBookDetailRead.isEnabled = true
            mBookDetailRead.isVisible = true
            mBookDetailRead.setOnClickListener { openBook(Room.book().get(book!!.objectId())) }
        }
    }

    /**
     * 访问原地址
     */
    private fun openOriginWeb(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            toast("未安装浏览器！")
        }
    }

    /**
     * 设置图书请求结果
     */
    private fun setupBookResult(chapters: List<Chapter>?) {
        adapter.submitList(chapters.orEmpty().asReversed())
        mBookDetailCatalogLayout.isVisible = true
        mBookDetailCatalogLayout.menu.findItem(R.id.menu_sort_descending).isEnabled = true
        mBookDetailLoading.isVisible = false
        //书架不存在同名图书
        if (!mBookDetailRead.isVisible) {
            mBookDetailRead.isVisible = true
            mBookDetailRead.isEnabled = chapters?.isNotEmpty() == true
            mBookDetailRead.text = getString(if (mBookDetailRead.isEnabled) R.string.book_detail_insert_bookshelf else R.string.book_detail_loading_failed)
            mBookDetailRead.setOnClickListener { getPreferredBookshelf() }
        }
    }

    /**
     * 获取首选书架，无首选书架则弹出书架选择列表
     */
    private fun getPreferredBookshelf() {
        val preferred = Room.getPreferredBookshelf()
        if (preferred == null) {
            SelectPreferredBookshelfDialog().callback { bookshelf -> publish(bookshelf) }.show(supportFragmentManager)
        } else {
            publish(preferred)
        }
    }

    /**
     * 出版图书
     */
    private fun publish(bookshelf: Bookshelf) {
        mBookDetailLoading.isVisible = true
        mBookDetailRead.visibility = View.INVISIBLE
        controller.publish(book, bookshelf).observe(this, Observer { book ->
            mBookDetailLoading.isVisible = false
            mBookDetailRead.isVisible = true
            if (book != null) {
                intent.putExtra(INTENT_BOOK, book.objectId)
                mBookDetailRead.text = getString(R.string.book_detail_immediately_read)
                mBookDetailRead.setOnClickListener { openBook(book) }
            } else {
                mBookDetailRead.text = getString(R.string.book_detail_insert_bookshelf)
            }
        })
    }

    /**
     * 打开图书
     */
    private fun openBook(book: Book) {
        val intent = Intent(this, ReaderActivity::class.java)
        intent.putExtra(INTENT_BOOK, book)
        startActivity(intent)
    }

    /**
     * 预览图书
     */
    private fun preview(index: Int, chapter: Chapter) {
        if (mBookDetailLoading.isVisible) return toast("正在读取目录，稍候再试")
        //已加入的书架不能预览
        val objectId = intent.getStringExtra(INTENT_BOOK)
        if (!objectId.isNullOrBlank() && Room.book().has(objectId)) {
            val book = Room.book().get(objectId)
            book.chapter = min(index, book.catalog)
            book.chapterProgress = 0
            Room.book().update(book)
            return openBook(book)
        }
        //预览
        mBookDetailLoading.isVisible = true
        mBookDetailRead.visibility = View.INVISIBLE
        controller.publish(book).observe(this, Observer { book ->
            if (book != null) {
                mBookDetailLoading.isVisible = false
                mBookDetailRead.isVisible = true
                controller.catalogLive.value?.run { book.chapter = max(indexOf(chapter), 0) }
                openBook(book)
            }
        })
    }

    /**
     * 书源列表
     */
    private fun showBookSourceDialog() {
        book?.list?.run {
            BookDetailSourceDialog(this@BookDetailActivity, this) { position -> queryBookDetail(position) }.show()
        }
    }

    /**
     * 构建数据适配器
     */
    private fun buildAdapter() = ListAdapter<Chapter>(R.layout.item_book_detail_chapter) { item, chapter ->
        (item.view as TextView).text = chapter.name
        item.view.setTextColor(getColor(if (chapter.url.isNotEmpty()) R.color.colorTitle else R.color.colorContent))
        item.view.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (chapter.url.isNotEmpty()) 15F else 12F)
        item.view.setOnClickListener { preview(mBookDetailRecycler.adapter!!.itemCount - 1 - item.adapterPosition, chapter) }
    }
}
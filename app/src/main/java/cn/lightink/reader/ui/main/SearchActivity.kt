package cn.lightink.reader.ui.main

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import cn.lightink.reader.R
import cn.lightink.reader.controller.SearchController
import cn.lightink.reader.ktx.change
import cn.lightink.reader.ktx.parentView
import cn.lightink.reader.ktx.startActivity
import cn.lightink.reader.model.Book
import cn.lightink.reader.model.SearchBook
import cn.lightink.reader.model.SearchHistory
import cn.lightink.reader.module.*
import cn.lightink.reader.ui.base.LifecycleActivity
import cn.lightink.reader.ui.book.BookDetailActivity
import cn.lightink.reader.ui.booksource.BookSourceActivity
import cn.lightink.reader.ui.reader.ReaderActivity
import kotlinx.android.synthetic.main.activity_search.*
import kotlinx.android.synthetic.main.item_local_book.view.*
import kotlinx.android.synthetic.main.item_search_history.view.*
import kotlinx.android.synthetic.main.item_simple_book.view.*

class SearchActivity : LifecycleActivity() {

    //软键盘管理者
    private val inputMethodManager by lazy { getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager }
    private val controller by lazy { ViewModelProvider(this).get(SearchController::class.java) }

    //历史搜索数据适配器
    private val historyAdapter by lazy { buildHistoryAdapter() }

    //本地搜索数据适配器
    private val localAdapter by lazy { buildLocalAdapter() }

    //便捷搜索词
    private var convenientSearchKey = EMPTY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        mSearchBack.setOnClickListener { onBackPressed() }
        mSearchBookSource.setOnClickListener { startActivity(BookSourceActivity::class) }
        mSearchHistoryLayout.isVisible = Room.search().isNotEmpty()
        mSearchHistoryRecycler.layoutManager = RVLinearLayoutManager(this)
        mSearchHistoryRecycler.adapter = historyAdapter
        mSearchHistoryClear.setOnClickListener { Room.search().clear().run { mSearchHistoryLayout.isVisible = false } }
        //搜索
        mSearchRecycler.layoutManager = RVLinearLayoutManager(this)
        mSearchRecycler.adapter = adapter
        //搜索框
        mSearchBox.setOnEditorActionListener { editor, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
            editor.clearFocus()
            inputMethodManager?.hideSoftInputFromWindow(mSearchBox.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
            mSearchWordLayout.isVisible = false
            mSearchRecyclerLayout.isVisible = true
            val searchKey = editor.text.toString().trim()
            if (searchKey.isNotBlank()) {
                controller.search(searchKey).observe(this, Observer { isNotEnd ->
                    mSearchBox.compoundDrawableTintList = ColorStateList.valueOf(editor.context.getColor(if (isNotEnd) R.color.colorForeground else R.color.colorTitle))
                    mSearchProgressBar.isVisible = isNotEnd
                    if (!isNotEnd) {
                        mSearchResultNone.isVisible = adapter.itemCount == 0 && mSearchRecyclerLayout.isVisible
                        mSearchResultNone.setText(if (Room.bookSource().isNotEmpty()) R.string.booksource_search_none else R.string.booksource_none)
                    }
                })
            }
            return@setOnEditorActionListener true
        }
        mSearchBox.change { value -> mSearchClear.isVisible = value.isNotBlank() }
        mSearchBox.post { inputMethodManager?.showSoftInput(mSearchBox, InputMethodManager.SHOW_IMPLICIT) }
        //清除搜索结果
        mSearchClear.setOnClickListener {
            mSearchBox.requestFocusFromTouch()
            mSearchBox.text?.clear()
            inputMethodManager?.showSoftInput(mSearchBox, InputMethodManager.SHOW_IMPLICIT)
            mSearchBox.compoundDrawableTintList = ColorStateList.valueOf(it.context.getColor(R.color.colorTitle))
            mSearchProgressBar.isVisible = false
            mSearchWordLayout.isVisible = true
            mSearchRecyclerLayout.isVisible = false
            mSearchResultNone.isVisible = false
            controller.stopSearch()
        }
        //本地搜索
        mLocalRecycler.layoutManager = RVLinearLayoutManager(this, LinearLayoutManager.HORIZONTAL)
        mLocalRecycler.adapter = localAdapter
        //数据请求
        SearchObserver.observer(this) {
            mSearchRecycler.adapter = adapter
            adapter.submitList(it)
        }
        controller.localLiveData.observe(this, Observer {
            mLocalRecycler.parentView.isVisible = !it.isNullOrEmpty()
            localAdapter.submitList(it)
        })
        controller.searchHistory().observe(this, Observer { historyAdapter.submitList(it) { mSearchHistoryRecycler.scrollToPosition(0) } })
        intent?.getStringExtra(INTENT_SEARCH)?.run { convenientSearch(this) }
    }

    /**
     * 便捷搜索
     */
    private fun convenientSearch(key: String) {
        convenientSearchKey = key
        mSearchBox.setText(key)
        mSearchBox.setSelection(key.length)
        mSearchBox.onEditorAction(EditorInfo.IME_ACTION_SEARCH)
    }

    /**
     * 打开书籍详情
     */
    private fun openBookDetail(view: View?, book: SearchBook) {
        val intent = Intent(this, BookDetailActivity::class.java)
        intent.putExtra(INTENT_BOOK, book.objectId())
        if (view != null) {
            val options = ActivityOptions.makeSceneTransitionAnimation(this, view, getString(R.string.transition))
            startActivity(intent, options.toBundle())
        } else {
            startActivity(intent)
        }
    }

    /**
     * 打开图书
     */
    private fun openBook(view: View, book: Book) {
        startActivity(Intent(this, ReaderActivity::class.java).putExtra(INTENT_BOOK, book))
    }

    /**
     * 构建搜索历史数据适配器
     */
    private fun buildHistoryAdapter() = ListAdapter<SearchHistory>(R.layout.item_search_history, equalContent = { old, new -> old.key == new.key }, equalItem = { old, new -> old.key == new.key }) { item, history ->
        item.view.mSearchHistoryKey.text = history.key
        item.view.setOnClickListener { convenientSearch(history.key) }
    }

    /***
     * 构建数据适配器
     */
    private fun buildLocalAdapter() = ListAdapter<Book>(R.layout.item_local_book) { item, book ->
        item.view.mLocalBookCover.hint(book.name).load(book.cover)
        item.view.setOnClickListener { openBook(item.view.mLocalBookCover, book) }
    }

    /***
     * 构建数据适配器
     */
    private val adapter = ListAdapter<SearchBook>(R.layout.item_simple_book, { old, new -> old.sameContent(new) }, { old, new -> old.same(new) }) { item, book ->
        item.view.mSimpleBookCover.hint(book.name).load(book.cover)
        item.view.mSimpleBookNo.text = (item.adapterPosition + 1).toString()
        item.view.mSimpleBookName.text = book.name
        item.view.mSimpleBookAuthor.text = book.author
        item.view.setOnClickListener { openBookDetail(item.view.mSimpleBookCover, book) }
    }

}
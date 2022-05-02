package cn.lightink.reader.ui.reader

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import cn.lightink.reader.R
import cn.lightink.reader.controller.ReaderController
import cn.lightink.reader.ktx.dialog
import cn.lightink.reader.ktx.toast
import cn.lightink.reader.module.*
import cn.lightink.reader.module.booksource.BookSourceSearchResponse
import cn.lightink.reader.ui.base.LifecycleActivity
import kotlinx.android.synthetic.main.activity_reader_change_book_source.*
import kotlinx.android.synthetic.main.item_reader_change_book_source.view.*

class ReaderChangeBookSourceActivity : LifecycleActivity() {

    private val controller by lazy { ViewModelProvider(this)[ReaderController::class.java] }
    private val adapter by lazy { buildAdapter() }
    private var current = EMPTY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader_change_book_source)
        if (controller.attach(this).not()) {
            toast("读取异常，请重新打开图书")
            return onBackPressed()
        }
        mReaderChangeBookSourceRecycler.layoutManager = RVLinearLayoutManager(this)
        mReaderChangeBookSourceRecycler.adapter = adapter
        controller.searchAll().observe(this, Observer { result ->
            if (result != null) {
                adapter.submitList(adapter.currentList.plus(result))
            } else {
                mTopbar.setProgressVisible(false)
            }
        })
    }

    /**
     * 换源
     */
    private fun changeBookSource(item: VH, result: BookSourceSearchResponse) {
        if (current.isNotBlank()) return
        current = result.source.url
        adapter.notifyItemChanged(item.adapterPosition)
        controller.changeBookSource(result).observe(this, Observer {
            toast("已使用「${result.source.name}」", TOAST_TYPE_SUCCESS)
            setResult(READER_RESULT_RECREATE, Intent().putExtra(INTENT_BOOK, it))
            finish()
        })
    }

    /**
     * 首次换源有提示对话框
     */
    private fun checkFirstChangeNotice(item: VH, result: BookSourceSearchResponse) {
        if (Preferences.get(Preferences.Key.FIRST_CHANGE_BOOK_SOURCE, true)) {
            dialog("换源会清空已缓存的章节内容") {
                Preferences.put(Preferences.Key.FIRST_CHANGE_BOOK_SOURCE, false)
                changeBookSource(item, result)
            }
        } else {
            changeBookSource(item, result)
        }
    }

    /**
     * 构建数据适配器
     */
    private fun buildAdapter() = ListAdapter<BookSourceSearchResponse>(R.layout.item_reader_change_book_source) { item, result ->
        item.view.mBookSourceName.text = result.source.name
        item.view.mBookSourceChapter.text = result.book.lastChapter
        item.view.mBookSourceLoading.isVisible = result.source.url == current
        item.view.mBookSourceUsed.isEnabled = result.source.name != controller.getBookSourceName()
        item.view.mBookSourceUsed.setText(if (!item.view.mBookSourceUsed.isEnabled) R.string.used else R.string.change_source)
        item.view.mBookSourceUsed.visibility = if (result.source.url == current) View.INVISIBLE else View.VISIBLE
        item.view.mBookSourceUsed.setOnClickListener { checkFirstChangeNotice(item, result) }
    }
}
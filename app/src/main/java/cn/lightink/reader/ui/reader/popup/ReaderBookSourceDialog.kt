package cn.lightink.reader.ui.reader.popup

import android.content.res.ColorStateList
import android.text.TextPaint
import android.view.Gravity
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import cn.lightink.reader.R
import cn.lightink.reader.controller.ReaderController
import cn.lightink.reader.ktx.parentView
import cn.lightink.reader.model.Theme
import cn.lightink.reader.module.EMPTY
import cn.lightink.reader.module.ListAdapter
import cn.lightink.reader.module.VH
import cn.lightink.reader.module.booksource.BookSourceSearchResponse
import cn.lightink.reader.ui.reader.ReaderActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.android.synthetic.main.dialog_reader_book_source.*
import kotlinx.android.synthetic.main.item_reader_change_book_source.view.*

class ReaderBookSourceDialog(val context: FragmentActivity) : BottomSheetDialog(context, R.style.AppTheme_BottomSheet) {

    private val controller by lazy { ViewModelProvider(context)[ReaderController::class.java] }
    private var current = EMPTY

    private val adapter = ListAdapter<BookSourceSearchResponse>(R.layout.item_reader_change_book_source) { item, result -> onBindView(item, result) }

    init {
        setContentView(R.layout.dialog_reader_book_source)
        setupViewTheme(controller.theme, controller.paint)
        mTopbar.setNavigationOnClickListener { dismiss() }
        mBookSourceRecycler.adapter = adapter
        mBookSourceRecycler.post { mBookSourceRecycler.minimumHeight = context.resources.displayMetrics.heightPixels / 2 - mBookSourceRecycler.top }
        controller.searchAll().observe(context, Observer { result -> onSearchResult(result) })
    }

    private fun onSearchResult(response: BookSourceSearchResponse?) {
        if (response != null) {
            adapter.submitList(adapter.currentList.plus(response))
        } else {
            mBookSourceLoading.isVisible = false
        }
    }

    private fun setupViewTheme(theme: Theme, paint: TextPaint) {
        mTopbar.parentView.backgroundTintList = ColorStateList.valueOf(theme.foreground)
        mTopIndicator.backgroundTintList = ColorStateList.valueOf(theme.secondary)
        mTopbar.setTint(theme.content)
        mTopbar.setTypeface(paint.typeface)
        mBookSourceLoading.indeterminateTintList = ColorStateList.valueOf(theme.control)
    }

    /**
     * 换源
     */
    private fun changeBookSource(item: VH, result: BookSourceSearchResponse) {
        if (current.isNotBlank()) return
        current = result.source.url
        adapter.notifyItemChanged(item.adapterPosition)
        controller.changeBookSource(result).observe(context, Observer { (context as ReaderActivity).recreate().run { dismiss() } })
    }

    private fun onBindView(item: VH, result: BookSourceSearchResponse) {
        item.view.mBookSourceName.typeface = controller.paint.typeface
        item.view.mBookSourceName.text = result.source.name
        item.view.mBookSourceName.setTextColor(controller.theme.content)
        item.view.mBookSourceChapter.typeface = controller.paint.typeface
        item.view.mBookSourceChapter.text = result.book.lastChapter
        item.view.mBookSourceChapter.setTextColor(controller.theme.secondary)
        item.view.mBookSourceLoading.indeterminateTintList = ColorStateList.valueOf(controller.theme.control)
        item.view.mBookSourceLoading.isVisible = result.source.url == current
        item.view.mBookSourceUsed.typeface = controller.paint.typeface
        item.view.mBookSourceUsed.isEnabled = result.source.name != controller.getBookSourceName()
        item.view.mBookSourceUsed.setText(if (!item.view.mBookSourceUsed.isEnabled) R.string.used else R.string.use)
        item.view.mBookSourceUsed.setTextColor(if (!item.view.mBookSourceUsed.isEnabled) controller.theme.secondary else controller.theme.control)
        item.view.mBookSourceUsed.backgroundTintList = ColorStateList.valueOf(controller.theme.background)
        item.view.mBookSourceUsed.visibility = if (result.source.url == current) View.INVISIBLE else View.VISIBLE
        item.view.mBookSourceUsed.setOnClickListener { changeBookSource(item, result) }
    }

    override fun onStart() {
        super.onStart()
        window?.navigationBarColor = controller.theme.foreground
        window?.setLayout(-1, (context.resources.displayMetrics.heightPixels * 0.8F).toInt())
        window?.setDimAmount(0.4F)
        window?.setGravity(Gravity.BOTTOM)
    }
}
package cn.lightink.reader.ui.reader

import android.os.Bundle
import android.text.TextPaint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import cn.lightink.reader.R
import cn.lightink.reader.controller.ReaderController
import cn.lightink.reader.model.Bookmark
import cn.lightink.reader.model.Theme
import cn.lightink.reader.module.ListAdapter
import cn.lightink.reader.module.RVLinearLayoutManager
import cn.lightink.reader.module.Room
import cn.lightink.reader.ui.base.LifecycleFragment
import cn.lightink.reader.ui.base.PopupMenu
import kotlinx.android.synthetic.main.fragment_reader_summary.view.*
import kotlinx.android.synthetic.main.item_bookmark.view.*
import kotlin.math.min

class ReaderSummaryFragment : LifecycleFragment() {

    private val controller by lazy { ViewModelProvider(activity!!)[ReaderController::class.java] }
    private val adapter by lazy { buildBookmarkAdapter() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_reader_summary, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.mReaderBookmarkRecycler.updateLayoutParams<LinearLayout.LayoutParams> { setMargins(0, 0, 0, controller.defaultMenuPaddingBottom) }
        view.mReaderBookmarkRecycler.layoutManager = RVLinearLayoutManager(activity)
        view.mReaderBookmarkRecycler.adapter = adapter
        controller.displayStateLiveData.observe(viewLifecycleOwner, Observer { setupViewTheme(view, controller.theme, controller.paint) })
        controller.bookmarks.observe(viewLifecycleOwner, Observer { adapter.submitList(it) })
        onUpdateSummary()
    }

    /**
     * 设置主题
     */
    private fun setupViewTheme(view: View, theme: Theme, paint: TextPaint) {
        view.mReaderProgressTitle.setTextColor(theme.secondary)
        view.mReaderProgressTitle.typeface = paint.typeface
        view.mReaderProgress.setTextColor(theme.content)
        view.mReaderProgress.typeface = paint.typeface
        view.mReaderStatisticsTitle.setTextColor(theme.secondary)
        view.mReaderStatisticsTitle.typeface = paint.typeface
        view.mReaderStatistics.setTextColor(theme.content)
        view.mReaderStatistics.typeface = paint.typeface
        view.mReaderBookmarkTitle.setTextColor(theme.secondary)
        view.mReaderBookmarkTitle.typeface = paint.typeface
    }

    /**
     * 构建书签数据适配器
     */
    private fun buildBookmarkAdapter() = ListAdapter<Bookmark>(R.layout.item_bookmark) { item, bookmark ->
        item.view.mBookmarkTitle.setTextColor(controller.theme.content)
        item.view.mBookmarkTitle.typeface = controller.paint.typeface
        item.view.mBookmarkTitle.text = bookmark.title
        item.view.mBookmarkSummary.setTextColor(controller.theme.content)
        item.view.mBookmarkSummary.typeface = controller.paint.typeface
        item.view.mBookmarkSummary.text = bookmark.summary
        item.view.setOnClickListener {
            controller.book.chapter = bookmark.chapter
            controller.book.chapterProgress = bookmark.progress
            (activity as? ReaderActivity)?.recreate()
        }
        item.view.setOnLongClickListener {
            PopupMenu(requireActivity()).items(R.string.delete).callback { Room.bookmark().delete(bookmark) }.show(item.view.mBookmarkTitle)
            return@setOnLongClickListener true
        }
    }

    override fun onResume() {
        super.onResume()
        onUpdateSummary()
    }

    private fun onUpdateSummary() {
        view?.mReaderProgress?.text = getString(R.string.reader_summary_progress, min(Room.bookRecord().count(controller.book.objectId), controller.catalog.size), controller.catalog.size)
        view?.mReaderStatistics?.text = getString(R.string.reader_summary_statistics, controller.book.time / 60, controller.book.speed.toInt())
    }

}
package cn.lightink.reader.ui.reader

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PointF
import android.os.Bundle
import android.text.TextPaint
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import cn.lightink.reader.App
import cn.lightink.reader.R
import cn.lightink.reader.controller.ReaderController
import cn.lightink.reader.ktx.px
import cn.lightink.reader.ktx.setDrawableEnd
import cn.lightink.reader.ktx.tint
import cn.lightink.reader.model.CacheChapter
import cn.lightink.reader.model.Chapter
import cn.lightink.reader.model.Page
import cn.lightink.reader.model.Theme
import cn.lightink.reader.module.*
import cn.lightink.reader.ui.base.LifecycleFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import kotlinx.android.synthetic.main.fragment_reader_catalog.view.*
import kotlinx.android.synthetic.main.item_catalog_chapter.view.*
import java.io.File
import kotlin.math.abs
import kotlin.math.max

class ReaderCatalogFragment : LifecycleFragment(), View.OnTouchListener, RecyclerViewFastScroller.HandleStateListener {

    private val controller by lazy { ViewModelProvider(activity!!)[ReaderController::class.java] }
    private val layoutManager by lazy { RVLinearLayoutManager(activity) }
    private val adapter by lazy { buildCatalogAdapter() }
    private val chapterList = mutableListOf<Chapter>()
    private var reverse = false
    private val point = PointF()
    private var hasBooklet = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_reader_catalog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupView(view)
        hasBooklet = controller.catalog.any { it.level > 0 }
        chapterList.addAll(controller.catalog)
        adapter.submitList(chapterList)
        onBottomSheetOffsetChanged()
        controller.addBottomSheetOffsetCallbacks { offset, offsetF -> onBottomSheetOffsetChanged(offset, offsetF) }
        controller.bottomSheetStateLiveData.observe(viewLifecycleOwner, Observer { onBottomSheetStateChanged(it) })
        controller.currentPageLiveData.observe(viewLifecycleOwner, Observer { onCurrentPageChanged(it) })
        controller.displayStateLiveData.observe(viewLifecycleOwner, Observer { setupViewTheme(view, controller.theme, controller.paint) })
        BookCacheModule.attachCacheLive().observe(viewLifecycleOwner, Observer { onChapterCacheChanged(it) })
    }

    private fun setupView(view: View) {
        view.mReaderMenuTitle.text = controller.book.name
        view.mReaderCatalog.layoutManager = layoutManager
        view.mReaderCatalog.adapter = adapter
        view.mReaderCatalog.setOnTouchListener(this)
        view.mReaderFastScroller.setHandleStateListener(this)
        view.mReaderMenuArrow.setOnClickListener { activity?.onBackPressed() }
        view.mReaderMenuListen.setOnClickListener { startActivity(Intent(requireActivity(), ListeningActivity::class.java).putExtra(INTENT_BOOK, controller.book.objectId)).run { activity?.finish() } }
        view.mReaderMenuNight.setImageResource(if (UIModule.isNightMode(requireActivity())) R.drawable.ic_reader_day else R.drawable.ic_reader_night)
        view.mReaderMenuNight.setOnClickListener {
            Preferences.put(Preferences.Key.LIGHT, Preferences.get(Preferences.Key.LIGHT, true).not())
            (activity?.application as? App)?.setupTheme()
        }
        view.mReaderMenuSort.setOnClickListener { (it as ImageButton).setImageResource(onCatalogChangeClicked()) }
    }

    /**
     * 设置主题
     */
    private fun setupViewTheme(view: View, theme: Theme, paint: TextPaint) {
        view.mReaderMenuTitle.setTextColor(controller.theme.content)
        view.mReaderMenuTitle.typeface = paint.typeface
        view.mReaderMenuArrow.imageTintList = ColorStateList.valueOf(controller.theme.content)
        view.mReaderMenuListen.imageTintList = ColorStateList.valueOf(controller.theme.content)
        view.mReaderMenuNight.imageTintList = ColorStateList.valueOf(controller.theme.content)
        view.mReaderMenuSort.imageTintList = ColorStateList.valueOf(controller.theme.content)
        view.mReaderFastScroller.handleDrawable = view.mReaderFastScroller.handleDrawable?.tint(theme.control)
        view.mReaderFastScroller.popupDrawable = resources.getDrawable(R.drawable.bg_fast_scroll, context?.theme).tint(theme.control)
        view.mReaderFastScroller.popupTextView.includeFontPadding = false
        view.mReaderFastScroller.popupTextView.typeface = paint.typeface
        view.mReaderFastScroller.popupTextView.setTextColor(theme.foreground)
        view.mReaderFastScroller.popupTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14F)
        view.mReaderFastScroller.popupTextView.setPadding(view.mReaderFastScroller.px(14), 0, view.mReaderFastScroller.px(14), 0)
        view.mReaderFastScroller.popupTextView.updateLayoutParams { height = controller.defaultFastScrollerPopupHeight }
        adapter.notifyDataSetChanged()
    }

    override fun onDragged(offset: Float, postion: Int) {
    }

    override fun onEngaged() {
        view?.mReaderFastScroller?.handleDrawable?.tint(controller.theme.control)
        if (controller.bottomSheetStateLiveData.value == STATE_EXPANDED) {
            view?.mReaderFastScroller?.popupDrawable = resources.getDrawable(R.drawable.bg_fast_scroll, context?.theme).tint(controller.theme.control)
        } else {
            view?.mReaderFastScroller?.popupDrawable = null
        }
    }

    override fun onReleased() {
        if (controller.bottomSheetStateLiveData.value != STATE_EXPANDED) {
            view?.mReaderFastScroller?.handleDrawable?.tint(Color.TRANSPARENT)
        }
    }

    /**
     * 目录 正序 或 倒序
     */
    private fun onCatalogChangeClicked(): Int {
        adapter.submitList(chapterList.apply { reverse() })
        adapter.notifyDataSetChanged()
        reverse = !reverse
        return if (reverse) R.drawable.ic_sort_descending else R.drawable.ic_sort_ascending
    }

    /**
     * 在读章节变化
     */
    private fun onCurrentPageChanged(page: Page) {
        layoutManager.scrollToPositionWithOffset(max(chapterList.indexOfFirst { it.index == page.chapter.index } - 1, 0), 0)
        adapter.notifyItemRangeChanged(0, adapter.itemCount, adapter.currentList)
    }

    /**
     * 菜单偏移量改变
     */
    private fun onBottomSheetOffsetChanged(offset: Float = 0F, offsetF: Float = 0F) {
        view?.mReaderCatalog?.updateLayoutParams { height = max(controller.defaultCatalogHeight, controller.defaultCatalogHeight + offset.toInt()) }
        if (offsetF > 0.5F && controller.currentPageLiveData.value != null) {
            //优化目录抬起定位
            val position = chapterList.indexOfFirst { it.index == controller.currentPageLiveData.value!!.chapter.index }
            val index = position - (layoutManager.findLastVisibleItemPosition() - layoutManager.findFirstVisibleItemPosition()) / 2
            layoutManager.scrollToPositionWithOffset(max(index, 0), 0)
        }
        view?.mReaderMenuListen?.alpha = 1 - offsetF
        view?.mReaderMenuListen?.isEnabled = offsetF < 0.5F
        view?.mReaderMenuNight?.alpha = 1 - offsetF
        view?.mReaderMenuNight?.isEnabled = offsetF < 0.5F
        view?.mReaderMenuSort?.alpha = offsetF
        view?.mReaderMenuSort?.isEnabled = offsetF > 0.5F
        view?.mReaderMenuSort?.isVisible = offsetF > 0F
    }

    /**
     * 菜单状态改变
     */
    private fun onBottomSheetStateChanged(newState: Int?) {
        if (view == null) return
        when (newState) {
            STATE_EXPANDED -> onEngaged()
            STATE_COLLAPSED -> {
                onReleased()
                if (controller.currentPage != null) onCurrentPageChanged(controller.currentPage!!)
            }
        }
    }

    /**
     * 章节缓存
     */
    private fun onChapterCacheChanged(chapter: CacheChapter) {
        if (view == null || chapter.book != controller.book.objectId) return
        val indexOf = adapter.currentList.indexOfFirst { it.index == chapter.index }
        if (indexOf > -1) {
            adapter.notifyItemChanged(indexOf, adapter.currentList[indexOf])
        }
    }

    /**
     * 构建目录数据适配器
     */
    private fun buildCatalogAdapter() = CatalogAdapter { item, chapter ->
        item.view.mBookChapter.text = chapter.title
        item.view.mBookChapter.typeface = controller.paint.typeface
        item.view.mBookChapter.setTextColor(when {
            chapter.index == controller.book.chapter -> controller.theme.control
            (hasBooklet && chapter.level == 0) || controller.isHaveRead(chapter) -> controller.theme.secondary
            else -> controller.theme.content
        })
        item.view.mBookChapter.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (hasBooklet && chapter.level == 0) 12F else 14.5F)
        item.view.mBookChapter.compoundDrawableTintList = ColorStateList.valueOf(controller.theme.secondary)
        item.view.mBookChapter.setDrawableEnd(if (controller.getBookSourceName().isBlank() || chapter.href.isBlank() || File(controller.book.path, "$MP_FOLDER_TEXTS/${chapter.encodeHref}.md").exists()) 0 else R.drawable.ic_chapter_on_cloud)
        item.view.mBookChapter.setOnClickListener {
            controller.menuHiddenStateLiveData.postValue(View.INVISIBLE)
            controller.jump(chapter.index)
        }
    }

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        if (controller.bottomSheetStateLiveData.value != STATE_EXPANDED) {
            when (event?.action) {
                MotionEvent.ACTION_DOWN -> point.set(event.rawX, event.rawY)
                MotionEvent.ACTION_MOVE -> return true
                MotionEvent.ACTION_UP -> if (abs(event.rawX - point.x) < controller.scaledTouchSlop && abs(event.rawY - point.y) < controller.scaledTouchSlop) {
                    v?.performClick()
                } else {
                    return true
                }
            }
        }
        return false
    }

    inner class CatalogAdapter(private val onBindImpl: (item: VH, Chapter) -> Unit) : androidx.recyclerview.widget.ListAdapter<Chapter, VH>(ChapterDiffUtil()), RecyclerViewFastScroller.OnPopupTextUpdate {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(LayoutInflater.from(parent.context).inflate(R.layout.item_catalog_chapter, parent, false))
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            onBindImpl.invoke(holder, getItem(position))
        }

        override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
            onBindImpl.invoke(holder, getItem(position))
        }

        override fun onChange(position: Int) = if (controller.bottomSheetStateLiveData.value == STATE_EXPANDED) getItem(position).title else EMPTY

    }

    class ChapterDiffUtil : DiffUtil.ItemCallback<Chapter>() {
        override fun areItemsTheSame(oldItem: Chapter, newItem: Chapter) = oldItem.href == newItem.href
        override fun areContentsTheSame(oldItem: Chapter, newItem: Chapter) = oldItem.href == newItem.href
    }
}
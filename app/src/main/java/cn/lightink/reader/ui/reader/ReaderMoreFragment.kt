package cn.lightink.reader.ui.reader

import android.graphics.PointF
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import cn.lightink.reader.R
import cn.lightink.reader.controller.ReaderController
import cn.lightink.reader.ktx.alpha
import cn.lightink.reader.ktx.px
import cn.lightink.reader.ktx.startActivityForResult
import cn.lightink.reader.ktx.toColorStateList
import cn.lightink.reader.model.StateChapter
import cn.lightink.reader.module.BookCacheModule
import cn.lightink.reader.module.ListAdapter
import cn.lightink.reader.module.Preferences
import cn.lightink.reader.module.RVGridLayoutManager
import cn.lightink.reader.ui.base.LifecycleFragment
import cn.lightink.reader.ui.reader.popup.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.android.synthetic.main.fragment_reader_more.view.*
import kotlinx.android.synthetic.main.item_action.view.*
import kotlin.math.abs
import kotlin.math.max

class ReaderMoreFragment : LifecycleFragment(), View.OnTouchListener {

    private val controller by lazy { ViewModelProvider(requireActivity())[ReaderController::class.java] }
    private val adapter by lazy { buildAdapter() }
    private val point = PointF()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_reader_more, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.mReaderMoreRecycler.setOnTouchListener(this)
        view.mReaderMoreRecycler.updateLayoutParams<LinearLayout.LayoutParams> { setMargins(0, max(controller.defaultMoreTopMargin, 0) + view.px(8), 0, controller.defaultMenuPaddingBottom) }
        view.mReaderMoreRecycler.layoutManager = RVGridLayoutManager(context, 4)
        view.mReaderMoreRecycler.adapter = adapter.apply { submitList(Action.values().toList()) }
        BookCacheModule.attachCacheStatusLive().observe(viewLifecycleOwner, Observer { adapter.notifyItemChanged(6) })
    }

    private fun buildAdapter() = ListAdapter<Action>(R.layout.item_action) { item, action ->
        item.view.mActionTitle.setTextColor(controller.theme.secondary)
        item.view.mActionTitle.text = getString(action.title)
        item.view.mActionTitle.typeface = controller.paint.typeface
        item.view.mActionIcon.updateLayoutParams<LinearLayout.LayoutParams> {
            height = controller.defaultMoreActionSize
            width = controller.defaultMoreActionSize
        }
        item.view.mActionIcon.isEnabled = true
        item.view.mActionIcon.setImageResource(action.icon)
        item.view.mActionIcon.setOnClickListener { onActionClicked(item.adapterPosition, action) }
        checkActionState(item.view.mActionIcon, action)
    }

    private fun onActionClicked(position: Int, action: Action) {
        when (action) {
            Action.THEME -> ReaderThemeDialog(requireActivity()).show()
            Action.CACHE -> cache()
            Action.BOOK_SOURCE -> ReaderBookSourceDialog(requireActivity()).show()
            Action.BRIGHTNESS -> view?.run { ReaderBrightnessPopup(requireActivity()).showAtLocation(this, Gravity.BOTTOM, 0, 0) }
            Action.FAMILY -> activity?.startActivityForResult(ReaderFontActivity::class)
            Action.PURIFY -> ReaderPurifyDialog(requireActivity()).show()
            Action.FONT -> view?.run { ReaderFontPopup(requireActivity()).showAtLocation(this, Gravity.BOTTOM, 0, 0) }
            Action.LINE -> view?.run { ReaderLinePopup(requireActivity()).showAtLocation(this, Gravity.BOTTOM, 0, 0) }
            Action.LOCATION -> view?.run { ReaderLocationPopup(requireActivity()).showAtLocation(this, Gravity.BOTTOM, 0, 0) }
            Action.VERTICAL -> Preferences.put(Preferences.Key.TURN_VERTICAL, Preferences.get(Preferences.Key.TURN_VERTICAL, false).not())
            Action.VOLUME -> Preferences.put(Preferences.Key.VOLUME_KEY, Preferences.get(Preferences.Key.VOLUME_KEY, false).not())
            Action.PULL -> Preferences.put(Preferences.Key.TEXT_PULL_BOOKMARK, Preferences.get(Preferences.Key.TEXT_PULL_BOOKMARK, true).not())
            Action.LONG_CLICKABLE -> Preferences.put(Preferences.Key.TEXT_LONG_CLICKABLE, Preferences.get(Preferences.Key.TEXT_LONG_CLICKABLE, true).not())
            Action.INDENT -> Preferences.put(Preferences.Key.FIRST_LINE_INDENT, Preferences.get(Preferences.Key.FIRST_LINE_INDENT, true).not())
            Action.BOLD -> Preferences.put(Preferences.Key.FONT_BOLD, Preferences.get(Preferences.Key.FONT_BOLD, false).not())
            Action.MIPMAP -> Preferences.put(Preferences.Key.MIPMAP_FOLLOW, Preferences.get(Preferences.Key.MIPMAP_FOLLOW, false).not())
            Action.CUSTOM_STATUS_BAR -> Preferences.put(Preferences.Key.CUSTOM_STATUS_BAR, Preferences.get(Preferences.Key.CUSTOM_STATUS_BAR, true).not())
            Action.TITLE -> Preferences.put(Preferences.Key.SHOW_TITLE, Preferences.get(Preferences.Key.SHOW_TITLE, true).not())
            Action.DORMANT -> Preferences.put(Preferences.Key.SCREEN_BRIGHT, Preferences.get(Preferences.Key.SCREEN_BRIGHT, false).not())
            Action.CLICK_ANIMATE -> Preferences.put(Preferences.Key.TURN_ANIMATE, Preferences.get(Preferences.Key.TURN_ANIMATE, true).not())
            Action.STATUS_BAR -> Preferences.put(Preferences.Key.STATUS_BAR, Preferences.get(Preferences.Key.STATUS_BAR, false).not())
            Action.NAVIGATION_BAR -> Preferences.put(Preferences.Key.NAVIGATION_BAR, Preferences.get(Preferences.Key.NAVIGATION_BAR, false).not())
            Action.NEXT_ONLY -> Preferences.put(Preferences.Key.ONLY_NEXT, Preferences.get(Preferences.Key.ONLY_NEXT, false).not())
            Action.NOTCH_BAR -> Preferences.put(Preferences.Key.NOTCH_BAR, Preferences.get(Preferences.Key.NOTCH_BAR, false).not())
        }
        when (action) {
            Action.THEME, Action.FAMILY, Action.FONT, Action.LINE, Action.PURIFY, Action.BOOK_SOURCE, Action.BRIGHTNESS, Action.LOCATION -> controller.menuHiddenStateLiveData.postValue(View.INVISIBLE)
            Action.VERTICAL, Action.NAVIGATION_BAR -> (activity as? ReaderActivity)?.recreate()
            Action.BOLD, Action.STATUS_BAR, Action.NOTCH_BAR, Action.CUSTOM_STATUS_BAR, Action.TITLE -> adapter.notifyItemChanged(position).run { controller.setupDisplay(requireActivity()).jump() }
            Action.PULL -> adapter.notifyItemChanged(position).run { controller.pullBookmarkEnableLiveData.postValue(Preferences.get(Preferences.Key.TEXT_PULL_BOOKMARK, true)) }
            Action.INDENT, Action.MIPMAP -> adapter.notifyItemChanged(position).run { controller.jump() }
            Action.VOLUME, Action.LONG_CLICKABLE, Action.CACHE, Action.CLICK_ANIMATE, Action.NEXT_ONLY -> adapter.notifyItemChanged(position)
            Action.DORMANT -> adapter.notifyItemChanged(position)
        }
    }

    /**
     * 开关按钮状态
     */
    private fun checkActionState(view: ImageView, action: Action) {
        val isChecked = when {
            action == Action.VERTICAL && Preferences.get(Preferences.Key.TURN_VERTICAL, false) -> true
            action == Action.VOLUME && Preferences.get(Preferences.Key.VOLUME_KEY, false) -> true
            action == Action.PULL && Preferences.get(Preferences.Key.TEXT_PULL_BOOKMARK, true) -> true
            action == Action.INDENT && Preferences.get(Preferences.Key.FIRST_LINE_INDENT, true) -> true
            action == Action.LONG_CLICKABLE && Preferences.get(Preferences.Key.TEXT_LONG_CLICKABLE, true) -> true
            action == Action.BOLD && Preferences.get(Preferences.Key.FONT_BOLD, false) -> true
            action == Action.MIPMAP && Preferences.get(Preferences.Key.MIPMAP_FOLLOW, false) -> true
            action == Action.CUSTOM_STATUS_BAR && Preferences.get(Preferences.Key.CUSTOM_STATUS_BAR, true) -> true
            action == Action.TITLE && Preferences.get(Preferences.Key.SHOW_TITLE, true) -> true
            action == Action.DORMANT && Preferences.get(Preferences.Key.SCREEN_BRIGHT, false) -> true
            action == Action.CLICK_ANIMATE && Preferences.get(Preferences.Key.TURN_ANIMATE, true) -> true
            action == Action.NEXT_ONLY && Preferences.get(Preferences.Key.ONLY_NEXT, false) -> true
            action == Action.CACHE && BookCacheModule.isCaching(controller.book) -> true
            action == Action.STATUS_BAR && Preferences.get(Preferences.Key.STATUS_BAR, false) -> true
            action == Action.NAVIGATION_BAR && Preferences.get(Preferences.Key.NAVIGATION_BAR, false) -> true
            action == Action.NOTCH_BAR && Preferences.get(Preferences.Key.NOTCH_BAR, false) -> true
            else -> false
        }
        when {
            isChecked -> {
                view.backgroundTintList = controller.theme.control.alpha(20).toColorStateList()
                view.imageTintList = controller.theme.control.toColorStateList()
            }
            action == Action.CACHE && controller.book.chapter >= controller.catalog.lastIndex -> {
                view.backgroundTintList = controller.theme.background.toColorStateList()
                view.imageTintList = controller.theme.secondary.toColorStateList().apply { view.isEnabled = false }
            }
            action == Action.BOOK_SOURCE && (controller.preview || controller.book.hasBookSource().not()) -> {
                view.backgroundTintList = controller.theme.background.toColorStateList()
                view.imageTintList = controller.theme.secondary.toColorStateList().apply { view.isEnabled = false }
            }
            else -> {
                view.backgroundTintList = controller.theme.background.toColorStateList()
                view.imageTintList = controller.theme.content.toColorStateList()
            }
        }
    }

    /**
     * 缓存
     */
    private fun cache() {
        if (BookCacheModule.isCaching(controller.book)) {
            BookCacheModule.pause(requireContext(), controller.book)
        } else {
            BookCacheModule.cache(controller.book, controller.catalog.subList(controller.book.chapter, controller.catalog.size).map {
                StateChapter(it.index, it.title, true, it.href)
            })
        }
    }

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        if (controller.bottomSheetStateLiveData.value != BottomSheetBehavior.STATE_EXPANDED) {
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

    enum class Action(@StringRes val title: Int, @DrawableRes val icon: Int) {
        THEME(R.string.reader_setting_theme, R.drawable.ic_reader_theme),
        FAMILY(R.string.reader_setting_family, R.drawable.ic_reader_font),
        PURIFY(R.string.reader_setting_purify, R.drawable.ic_reader_purify),
        BOOK_SOURCE(R.string.reader_setting_book_source, R.drawable.ic_reader_book_source),
        FONT(R.string.reader_setting_font, R.drawable.ic_reader_font_size),
        LINE(R.string.reader_setting_line, R.drawable.ic_reader_line),
        LOCATION(R.string.reader_setting_location, R.drawable.ic_reader_location),
        BRIGHTNESS(R.string.reader_setting_brightness, R.drawable.ic_reader_brightness),
        CACHE(R.string.reader_setting_cache, R.drawable.ic_reader_cache),
        VERTICAL(R.string.reader_setting_turn_vertical, R.drawable.ic_reader_turn_vertical),
        VOLUME(R.string.reader_setting_turn_volume, R.drawable.ic_reader_turn_volume),
        DORMANT(R.string.reader_setting_dormant, R.drawable.ic_reader_dormant),
        CUSTOM_STATUS_BAR(R.string.reader_setting_custom_status_bar, R.drawable.ic_reader_setting_time),
        TITLE(R.string.reader_setting_title, R.drawable.ic_reader_setting_title),
        INDENT(R.string.reader_setting_first_line_indent, R.drawable.ic_reader_first_line_indent),
        CLICK_ANIMATE(R.string.reader_setting_click_animate, R.drawable.ic_reader_click_animate),
        LONG_CLICKABLE(R.string.reader_setting_long_clickable, R.drawable.ic_reader_long_clickable),
        PULL(R.string.reader_setting_pull_bookmark, R.drawable.ic_reader_pull_bookmark),
        NEXT_ONLY(R.string.reader_setting_turn_only_next, R.drawable.ic_reader_next_only),
        BOLD(R.string.reader_setting_font_bold, R.drawable.ic_reader_font_bold),
        MIPMAP(R.string.reader_setting_mipmap_follow, R.drawable.ic_reader_mipmap_follow),
        STATUS_BAR(R.string.reader_setting_status_bar, R.drawable.ic_reader_status_bar),
        NAVIGATION_BAR(R.string.reader_setting_navigation_bar, R.drawable.ic_reader_navigation_bar),
        NOTCH_BAR(R.string.reader_setting_notch_bar, R.drawable.ic_reader_notch_bar)
    }

}
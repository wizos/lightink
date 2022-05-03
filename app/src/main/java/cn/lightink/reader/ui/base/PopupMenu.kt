package cn.lightink.reader.ui.base

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.setMargins
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import cn.lightink.reader.R
import cn.lightink.reader.model.THEME_LIGHT
import cn.lightink.reader.model.THEME_NIGHT
import cn.lightink.reader.model.Theme
import cn.lightink.reader.module.ListAdapter
import cn.lightink.reader.module.RVLinearLayoutManager
import cn.lightink.reader.module.UIModule

/**
 * 弹出菜单
 * @sample cn.lightink.reader.ui.book.BookCoverActivity
 * @since 1.0.0
 */
@SuppressLint("InflateParams")
class PopupMenu(val context: FragmentActivity) : PopupWindow(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) {

    private var callback: ((Int) -> Unit)? = null
    private var gravity = Gravity.NO_GRAVITY
    private var minWidth = 152
    private var center = false
    private var typeface = Typeface.DEFAULT
    private var theme = if (UIModule.isNightMode(context)) THEME_NIGHT else THEME_LIGHT

    init {
        isFocusable = true
        isTouchable = true
        isOutsideTouchable = true
        contentView = RelativeLayout(context)
        setIgnoreCheekPress()
    }

    fun width(minWidth: Int): PopupMenu {
        this.minWidth = minWidth
        return this
    }

    fun center(center: Boolean): PopupMenu {
        this.center = center
        return this
    }

    fun theme(theme: Theme, typeface: Typeface): PopupMenu {
        this.theme = theme
        this.typeface = typeface
        return this
    }

    /**
     * 设置item
     */
    fun items(@StringRes vararg items: Int): PopupMenu {
        val recycler = RecyclerView(context)
        recycler.setBackgroundResource(R.drawable.bg_popup_menu)
        recycler.backgroundTintList = ColorStateList.valueOf(theme.foreground)
        recycler.elevation = 8F
        recycler.layoutManager = RVLinearLayoutManager(context)
        recycler.adapter = ListAdapter<Int>(R.layout.item_popup) { item, value ->
            (item.view as TextView).setText(value)
            item.view.setTextColor(theme.content)
            item.view.typeface = typeface
            item.view.includeFontPadding = false
            if (center) item.view.gravity = Gravity.CENTER
            if (minWidth > 0) item.view.minWidth = (recycler.resources.getDimension(R.dimen.dimen1) * minWidth).toInt()
            item.view.setOnClickListener { dismiss().run { callback?.invoke(value) } }
        }.apply { submitList(items.toList()) }
        (contentView as RelativeLayout).addView(recycler, RelativeLayout.LayoutParams(-2, -2).apply { setMargins(12) })
        return this
    }

    /**
     * 设置回调
     */
    fun callback(callback: (Int) -> Unit): PopupMenu {
        this.callback = callback
        return this
    }

    /**
     * 设置位置
     */
    fun gravity(gravity: Int): PopupMenu {
        this.gravity = gravity
        return this
    }

    /**
     * 显示菜单
     */
    fun show(anchor: View, x: Int = if (gravity == Gravity.END) 0 else -12, y: Int = 0) {
        anchor.post { showAsDropDown(anchor, x, y, gravity) }
    }
}
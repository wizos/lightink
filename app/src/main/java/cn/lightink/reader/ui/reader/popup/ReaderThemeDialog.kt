package cn.lightink.reader.ui.reader.popup

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.TextPaint
import android.view.Gravity
import android.view.View
import android.widget.RelativeLayout
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import cn.lightink.reader.R
import cn.lightink.reader.controller.ReaderController
import cn.lightink.reader.ktx.parentView
import cn.lightink.reader.ktx.px
import cn.lightink.reader.ktx.startActivity
import cn.lightink.reader.ktx.toast
import cn.lightink.reader.model.Theme
import cn.lightink.reader.module.*
import cn.lightink.reader.ui.base.PopupMenu
import cn.lightink.reader.ui.reader.ReaderActivity
import cn.lightink.reader.ui.reader.theme.ThemeEditorActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.android.synthetic.main.dialog_reader_theme.*
import kotlinx.android.synthetic.main.item_theme.view.*

class ReaderThemeDialog(val context: FragmentActivity) : BottomSheetDialog(context, R.style.AppTheme_BottomSheet) {

    private val controller by lazy { ViewModelProvider(context)[ReaderController::class.java] }
    private val size by lazy { (context.resources.displayMetrics.widthPixels - context.px(48)) / 2 }
    private val adapter = ListAdapter<Theme>(R.layout.item_theme) { item, theme -> onBindView(item, theme) }

    init {
        setContentView(R.layout.dialog_reader_theme)
        setupViewTheme(controller.theme, controller.paint)
        mTopbar.text = context.getString(if (UIModule.isNightMode(context)) R.string.theme_night else R.string.theme_light)
        mTopbar.setNavigationOnClickListener { dismiss() }
        mTopbar.setOnMenuClickListener { showPopup() }
        mThemeRecycler.layoutManager = RVGridLayoutManager(context, 2)
        mThemeRecycler.adapter = adapter
        mThemeRecycler.post { mThemeRecycler.minimumHeight = context.resources.displayMetrics.heightPixels / 2 - mThemeRecycler.top }
        controller.queryThemes(!UIModule.isNightMode(context)).observe(context, Observer { adapter.submitList(it) })
    }

    private fun showPopup() {
        PopupMenu(context).items( R.string.theme_new).gravity(Gravity.END).callback { item ->
            context.startActivity(ThemeEditorActivity::class)
        }.show(mTopbar)
    }

    private fun setupViewTheme(theme: Theme, paint: TextPaint) {
        mTopbar.parentView.backgroundTintList = ColorStateList.valueOf(theme.foreground)
        mTopIndicator.backgroundTintList = ColorStateList.valueOf(theme.secondary)
        mTopbar.setTint(theme.content)
        mTopbar.setTypeface(paint.typeface)
    }

    private fun onBindView(item: VH, theme: Theme) {
        item.view.mThemeCardView.setCardBackgroundColor(theme.background)
        item.view.mThemeBackground.layoutParams.height = (size * 1.3F).toInt()
        if (theme.mipmap.isNotBlank()) {
            item.view.mThemeBackground.background = UIModule.getMipmapByTheme(theme)
        } else {
            item.view.mThemeBackground.setBackgroundColor(theme.background)
        }
        item.view.mThemeForegroundDark.backgroundTintList = ColorStateList.valueOf(theme.foreground)
        item.view.mThemeForegroundDark.updateLayoutParams<RelativeLayout.LayoutParams> {
            width = size
            height = size
            setMargins(0, 0, -size / 2, -size / 2)
        }
        item.view.mThemeForegroundLight.backgroundTintList = ColorStateList.valueOf(theme.foreground)
        item.view.mThemeForegroundLight.updateLayoutParams<RelativeLayout.LayoutParams> {
            width = (size * 1.5F).toInt()
            height = (size * 1.5F).toInt()
            setMargins(0, 0, (-size * 1.5F / 2).toInt(), (-size * 1.5F / 2).toInt())
        }
        item.view.mThemeName.setTextColor(theme.content)
        item.view.mThemeName.text = theme.name
        item.view.mThemeAuthor.setTextColor(theme.secondary)
        item.view.mThemeAuthor.text = if (theme.owner) "我" else theme.author
        item.view.mThemeTime.setTextColor(theme.secondary)
        item.view.mThemeTime.text = String.format("累计阅读%d分钟", theme.time / 60)
        item.view.mThemeCheckStatus.imageTintList = ColorStateList.valueOf(theme.background)
        item.view.mThemeCheckStatus.backgroundTintList = ColorStateList.valueOf(theme.control)
        item.view.mThemeCheckStatus.setImageResource(if (theme.id == controller.theme.id) R.drawable.ic_check_line else 0)
        item.view.mThemeMore.imageTintList = ColorStateList.valueOf(theme.secondary)
        item.view.mThemeMore.setOnClickListener { showPopupMenu(it, theme) }
        item.view.setOnClickListener {
            if (controller.theme.id != theme.id) {
                Preferences.put(if (UIModule.isNightMode(context)) Preferences.Key.THEME_NIGHT_ID else Preferences.Key.THEME_LIGHT_ID, theme.id)
                (context as ReaderActivity).recreate()
            }
        }
    }

    /**
     * 展示菜单
     */
    private fun showPopupMenu(view: View, theme: Theme) {
        if (theme.id < 2) {
            return context.toast("不允许删除默认主题")
        }
        PopupMenu(context).items(R.string.edit, R.string.delete).theme(controller.theme, Typeface.DEFAULT).callback { item ->
            if (item == R.string.edit) context.startActivityForResult(Intent(context, ThemeEditorActivity::class.java).putExtra(INTENT_THEME, theme.id), Activity.RESULT_FIRST_USER)
            else controller.removeTheme(theme)
        }.show(view)
    }

    override fun onStart() {
        super.onStart()
        window?.navigationBarColor = controller.theme.foreground
        window?.setLayout(-1, (context.resources.displayMetrics.heightPixels * 0.88).toInt())
        window?.setDimAmount(0.4F)
        window?.setGravity(Gravity.BOTTOM)
    }
}
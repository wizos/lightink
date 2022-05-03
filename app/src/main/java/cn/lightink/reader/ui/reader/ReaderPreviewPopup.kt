package cn.lightink.reader.ui.reader

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ViewModelProvider
import cn.lightink.reader.R
import cn.lightink.reader.controller.ReaderController
import cn.lightink.reader.ktx.toast
import cn.lightink.reader.module.FontModule
import cn.lightink.reader.module.Room
import cn.lightink.reader.module.TOAST_TYPE_SUCCESS
import cn.lightink.reader.ui.bookshelf.SelectPreferredBookshelfDialog
import kotlinx.android.synthetic.main.popup_reader_preview.view.*

@SuppressLint("InflateParams")
class ReaderPreviewPopup(val activity: ReaderActivity) : PopupWindow(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), LifecycleObserver {

    private val controller by lazy { ViewModelProvider(activity)[ReaderController::class.java] }

    init {
        isTouchable = true
        contentView = buildContentView()
        activity.lifecycle.addObserver(this)
        elevation = activity.resources.getDimension(R.dimen.dimen1x)
    }

    private fun buildContentView(): View {
        val theme = controller.theme
        val view = LayoutInflater.from(activity).inflate(R.layout.popup_reader_preview, null)
        view.mPopupButton.backgroundTintList = ColorStateList.valueOf(theme.foreground)
        view.mPopupButton.setTextColor(theme.content)
        view.mPopupButton.typeface = FontModule.mCurrentFont.typeface
        view.mPopupButton.setOnClickListener { insertBookshelf() }
        return view
    }

    /**
     * 加入书架
     */
    private fun insertBookshelf() {
        val preferred = Room.getPreferredBookshelf()
        if (preferred == null) {
            SelectPreferredBookshelfDialog().callback { bookshelf ->
                controller.insertBookshelf(bookshelf).run { onSuccess() }
                onSuccess()
            }.show(activity.supportFragmentManager)
        } else {
            controller.insertBookshelf(preferred).run { onSuccess() }
        }
    }

    /**
     * 加入书架成功
     */
    private fun onSuccess() {
        activity.toast("已加入书架", TOAST_TYPE_SUCCESS)
        activity.lifecycle.removeObserver(this)
        dismiss()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun show() {
        if (!isShowing) showAtLocation(activity.window.decorView, Gravity.TOP or Gravity.END, 0, 0)
    }

}
package cn.lightink.reader.ui.reader.theme

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.PointF
import android.graphics.drawable.ColorDrawable
import android.transition.TransitionInflater
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.PopupWindow
import android.widget.SeekBar
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import cn.lightink.reader.R
import cn.lightink.reader.controller.ThemeController
import kotlinx.android.synthetic.main.popup_theme_distance.view.*
import kotlin.math.roundToInt

@SuppressLint("InflateParams")
class ThemeDistancePopup(val context: FragmentActivity, val distance: Int, val callback: (Int) -> Unit) : PopupWindow(LayoutInflater.from(context).inflate(R.layout.popup_theme_distance, null), -1, -2, true) {

    private val controller by lazy { ViewModelProvider(context)[ThemeController::class.java] }
    private val xAndProgress = PointF()

    init {
        isOutsideTouchable = true
        isTouchable = true
        enterTransition = TransitionInflater.from(context).inflateTransition(R.transition.slide_show_bottom)
        exitTransition = TransitionInflater.from(context).inflateTransition(R.transition.slide_hide_bottom)
        setBackgroundDrawable(ColorDrawable())
        setupViewData()
        setupViewTheme()
    }

    private fun setupViewData() {
        contentView.mDistanceForeground.setOnTouchListener { v, event -> passTouchEvent(v, contentView.mDistanceSeekBar, event) }
        contentView.mDistanceSeekBar.progress = distance
        onProgressChanged(contentView.mDistanceSeekBar)
    }

    private fun passTouchEvent(formView: View, toView: SeekBar, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> xAndProgress.set(event.x, toView.progress.toFloat())
            MotionEvent.ACTION_MOVE -> {
                toView.progress = (xAndProgress.y + (event.x - xAndProgress.x) / (formView.width.toDouble() / toView.max * 0.8)).roundToInt()
                onProgressChanged(toView)
            }
        }
        return false
    }

    private fun setupViewTheme() {
        contentView.mDistanceBackground.backgroundTintList = ColorStateList.valueOf(controller.theme.background)
        contentView.mDistanceForeground.backgroundTintList = ColorStateList.valueOf(controller.theme.foreground)
        contentView.mDistanceText.setTextColor(controller.theme.foreground)
    }

    private fun onProgressChanged(seekBar: SeekBar) {
        contentView.mDistanceText.text = seekBar.progress.toString()
        callback.invoke(seekBar.progress)
    }

}
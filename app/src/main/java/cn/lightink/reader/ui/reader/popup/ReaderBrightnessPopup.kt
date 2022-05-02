package cn.lightink.reader.ui.reader.popup

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
import cn.lightink.reader.controller.ReaderController
import cn.lightink.reader.ktx.windowBrightness
import cn.lightink.reader.model.Theme
import cn.lightink.reader.module.Preferences
import kotlinx.android.synthetic.main.popup_reader_brightness.view.*
import kotlin.math.roundToInt

@SuppressLint("InflateParams")
class ReaderBrightnessPopup(val context: FragmentActivity) : PopupWindow(LayoutInflater.from(context).inflate(R.layout.popup_reader_brightness, null), -1, -2, true) {

    private val controller by lazy { ViewModelProvider(context)[ReaderController::class.java] }
    private val xAndProgress = PointF()

    init {
        isOutsideTouchable = true
        isTouchable = true
        enterTransition = TransitionInflater.from(context).inflateTransition(R.transition.slide_show_bottom)
        exitTransition = TransitionInflater.from(context).inflateTransition(R.transition.slide_hide_bottom)
        setBackgroundDrawable(ColorDrawable())
        setupViewData()
        setupViewTheme(controller.theme)
    }

    private fun setupViewData() {
        contentView.mBrightnessForeground.setOnTouchListener { v, event -> passTouchEvent(v, contentView.mBrightnessSeekBar, event) }
        contentView.mBrightnessSeekBar.progress = Preferences.get(Preferences.Key.BRIGHTNESS, -1F).let { if (it in 0F..1F) it.toInt() * 10000 else 10001 }
        onProgressChanged(contentView.mBrightnessSeekBar)
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

    private fun setupViewTheme(theme: Theme) {
        contentView.mBrightnessForeground.backgroundTintList = ColorStateList.valueOf(theme.foreground)
        contentView.mBrightnessBackground.backgroundTintList = ColorStateList.valueOf(theme.background)
        contentView.mBrightnessText.backgroundTintList = ColorStateList.valueOf(theme.control)
        contentView.mBrightnessText.setTextColor(theme.foreground)
        contentView.mBrightnessSeekBar.progressTintList = ColorStateList.valueOf(theme.control)
    }

    private fun onProgressChanged(seekBar: SeekBar) {
        val progress = if (seekBar.progress == 10001) -1F else seekBar.progress * 0.0001F
        contentView.mBrightnessText.text = if (progress == -1F) context.getString(R.string.reader_setting_brightness_auto) else context.getString(R.string.reader_setting_brightness_value, (progress * 100).toInt())
        if (progress != Preferences.get(Preferences.Key.BRIGHTNESS, -1F)) {
            Preferences.put(Preferences.Key.BRIGHTNESS, progress)
            context.windowBrightness = progress
        }
    }

}
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
import cn.lightink.reader.model.Theme
import cn.lightink.reader.module.Preferences
import kotlinx.android.synthetic.main.popup_reader_font.view.*
import kotlin.math.roundToInt

@SuppressLint("InflateParams")
class ReaderFontPopup(val context: FragmentActivity) : PopupWindow(LayoutInflater.from(context).inflate(R.layout.popup_reader_font, null), -1, -2, true) {

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
        //字号
        contentView.mFontSizeBackground.setOnTouchListener { v, event -> passTouchEvent(v, contentView.mFontSizeSeekBar, event) }
        contentView.mFontSizeSeekBar.progress = (Preferences.get(Preferences.Key.FONT_SIZE, 17F) - 14F).toInt()
        onProgressChanged(contentView.mFontSizeSeekBar)
        //字距
        contentView.mFontDistanceBackground.setOnTouchListener { v, event -> passTouchEvent(v, contentView.mFontDistanceSeekBar, event) }
        contentView.mFontDistanceSeekBar.progress = (Preferences.get(Preferences.Key.LETTER_SPACING, 0F) * 10).toInt()
        onProgressChanged(contentView.mFontDistanceSeekBar)
    }

    private fun passTouchEvent(formView: View, toView: SeekBar, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> xAndProgress.set(event.x, toView.progress.toFloat())
            MotionEvent.ACTION_MOVE -> {
                toView.progress = (xAndProgress.y + (event.x - xAndProgress.x) / (formView.width / toView.max * 0.8)).roundToInt()
                onProgressChanged(toView)
            }
            MotionEvent.ACTION_UP -> if (xAndProgress.y.toInt() != toView.progress) onStopTrackingTouch(toView)
        }
        return false
    }

    private fun setupViewTheme(theme: Theme) {
        //字号
        contentView.mFontSizeForeground.backgroundTintList = ColorStateList.valueOf(theme.foreground)
        contentView.mFontSizeBackground.backgroundTintList = ColorStateList.valueOf(theme.background)
        contentView.mFontSizeText.backgroundTintList = ColorStateList.valueOf(theme.control)
        contentView.mFontSizeText.setTextColor(theme.foreground)
        contentView.mFontSizeSeekBar.progressTintList = ColorStateList.valueOf(theme.control)
        //字距
        contentView.mFontDistanceForeground.backgroundTintList = ColorStateList.valueOf(theme.foreground)
        contentView.mFontDistanceBackground.backgroundTintList = ColorStateList.valueOf(theme.background)
        contentView.mFontDistanceText.backgroundTintList = ColorStateList.valueOf(theme.control)
        contentView.mFontDistanceText.setTextColor(theme.foreground)
        contentView.mFontDistanceSeekBar.progressTintList = ColorStateList.valueOf(theme.control)
    }

    private fun onProgressChanged(seekBar: SeekBar) {
        when (seekBar) {
            //字号
            contentView.mFontSizeSeekBar -> contentView.mFontSizeText.text = context.getString(R.string.reader_setting_font_size, seekBar.progress + 14)
            //字间距
            contentView.mFontDistanceSeekBar -> contentView.mFontDistanceText.text = context.getString(R.string.reader_setting_letter_spacing, seekBar.progress * 0.1F)
        }
    }

    private fun onStopTrackingTouch(seekBar: SeekBar) {
        when (seekBar) {
            //字号
            contentView.mFontSizeSeekBar -> if (seekBar.progress + 14F != Preferences.get(Preferences.Key.FONT_SIZE, 17F)) {
                Preferences.put(Preferences.Key.FONT_SIZE, seekBar.progress + 14F).run { controller.setupDisplay(context).jump() }
            }
            //字间距
            contentView.mFontDistanceSeekBar -> if (seekBar.progress * 0.1F != Preferences.get(Preferences.Key.LETTER_SPACING, 0F)) {
                Preferences.put(Preferences.Key.LETTER_SPACING, seekBar.progress * 0.1F).run { controller.setupDisplay(context).jump() }
            }
        }
    }

}
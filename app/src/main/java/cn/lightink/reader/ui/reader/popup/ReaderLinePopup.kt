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
import kotlinx.android.synthetic.main.popup_reader_line.view.*
import kotlin.math.roundToInt

@SuppressLint("InflateParams")
class ReaderLinePopup(val context: FragmentActivity) : PopupWindow(LayoutInflater.from(context).inflate(R.layout.popup_reader_line, null), -1, -2, true) {

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
        //行间距
        contentView.mLineForeground.setOnTouchListener { v, event -> passTouchEvent(v, contentView.mLineSeekBar, event) }
        contentView.mLineSeekBar.progress = ((Preferences.get(Preferences.Key.LINE_SPACING, 1.3F) - 1) * 10).toInt()
        onProgressChanged(contentView.mLineSeekBar)
        //段间距
        contentView.mParagraphForeground.setOnTouchListener { v, event -> passTouchEvent(v, contentView.mParagraphSeekBar, event) }
        contentView.mParagraphSeekBar.progress = Preferences.get(Preferences.Key.PARAGRAPH_DISTANCE, 0)
        onProgressChanged(contentView.mParagraphSeekBar)
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
        //行间距
        contentView.mLineForeground.backgroundTintList = ColorStateList.valueOf(theme.foreground)
        contentView.mLineBackground.backgroundTintList = ColorStateList.valueOf(theme.background)
        contentView.mLineText.backgroundTintList = ColorStateList.valueOf(theme.control)
        contentView.mLineText.setTextColor(theme.foreground)
        contentView.mLineSeekBar.progressTintList = ColorStateList.valueOf(theme.control)
        //段间距
        contentView.mParagraphForeground.backgroundTintList = ColorStateList.valueOf(theme.foreground)
        contentView.mParagraphBackground.backgroundTintList = ColorStateList.valueOf(theme.background)
        contentView.mParagraphText.backgroundTintList = ColorStateList.valueOf(theme.control)
        contentView.mParagraphText.setTextColor(theme.foreground)
        contentView.mParagraphSeekBar.progressTintList = ColorStateList.valueOf(theme.control)
    }


    private fun onProgressChanged(seekBar: SeekBar) {
        when (seekBar) {
            //字号
            contentView.mLineSeekBar -> contentView.mLineText.text = context.getString(R.string.reader_setting_line_spacing, seekBar.progress * 0.1F + 1F)
            //字间距
            contentView.mParagraphSeekBar -> contentView.mParagraphText.text = context.getString(R.string.reader_setting_paragraph_spacing, seekBar.progress * 0.1F)
        }
    }

    private fun onStopTrackingTouch(seekBar: SeekBar) {
        when (seekBar) {
            //行间距
            contentView.mLineSeekBar -> if (seekBar.progress + 14F != Preferences.get(Preferences.Key.LINE_SPACING, 1.3F)) {
                Preferences.put(Preferences.Key.LINE_SPACING, seekBar.progress * 0.1F + 1F).run { controller.setupDisplay(context).jump() }
            }
            //段间距
            contentView.mParagraphSeekBar -> if (seekBar.progress != Preferences.get(Preferences.Key.PARAGRAPH_DISTANCE, 0)) {
                Preferences.put(Preferences.Key.PARAGRAPH_DISTANCE, seekBar.progress).run { controller.setupDisplay(context).jump() }
            }
        }
    }

}
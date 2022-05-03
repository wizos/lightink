package cn.lightink.reader.ui.reader.theme

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PointF
import android.graphics.drawable.ColorDrawable
import android.transition.TransitionInflater
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.PopupWindow
import android.widget.SeekBar
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import cn.lightink.reader.R
import cn.lightink.reader.controller.ThemeController
import kotlinx.android.synthetic.main.popup_theme_color_picker.view.*
import okhttp3.internal.toHexString
import kotlin.math.roundToInt

@SuppressLint("InflateParams")
class ThemeColorPickerPopup(val context: FragmentActivity, val color: Int, val callback: (Int) -> Unit) : PopupWindow(LayoutInflater.from(context).inflate(R.layout.popup_theme_color_picker, null), -1, -2, true) {

    private val controller by lazy { ViewModelProvider(context)[ThemeController::class.java] }
    private val inputMethodManager by lazy { context.applicationContext.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager }
    private val xAndProgress = PointF()

    init {
        isOutsideTouchable = true
        isTouchable = true
        enterTransition = TransitionInflater.from(context).inflateTransition(R.transition.slide_show_bottom)
        exitTransition = TransitionInflater.from(context).inflateTransition(R.transition.slide_hide_bottom)
        setBackgroundDrawable(ColorDrawable())
        setupViewData(color)
        setupViewTheme()
        contentView.mColorValueInput.setOnFocusChangeListener { _, hasFocus -> contentView.mColorValueText.isVisible =  !hasFocus }
        contentView.mColorValueInput.doOnTextChanged { hex, _, _, _ ->
            when {
                hex != null && hex.startsWith("#") && hex.length == 7 -> parseColor(hex.toString())
                hex != null && !hex.startsWith("#") && hex.length == 6 -> parseColor("#$hex")
            }
        }
        contentView.mColorValueReset.setOnClickListener { setupViewData(color) }
    }

    private fun setupViewData(color: Int) {
        //RED
        contentView.mColorRForeground.setOnTouchListener { v, event -> passTouchEvent(v, contentView.mColorRSeekBar, event) }
        contentView.mColorRSeekBar.progress = Color.red(color)
        onProgressChanged(contentView.mColorRSeekBar)
        //GREEN
        contentView.mColorGForeground.setOnTouchListener { v, event -> passTouchEvent(v, contentView.mColorGSeekBar, event) }
        contentView.mColorGSeekBar.progress = Color.green(color)
        onProgressChanged(contentView.mColorGSeekBar)
        //BLUE
        contentView.mColorBForeground.setOnTouchListener { v, event -> passTouchEvent(v, contentView.mColorBSeekBar, event) }
        contentView.mColorBSeekBar.progress = Color.blue(color)
        onProgressChanged(contentView.mColorBSeekBar)
        //Reset
        contentView.mColorValueInput.text?.clear()
        contentView.mColorValueInput.clearFocus()
        inputMethodManager.hideSoftInputFromWindow(contentView.mColorValueInput.windowToken, 0)
    }

    private fun setupViewTheme() {
        contentView.mColorValueForeground.backgroundTintList = ColorStateList.valueOf(controller.theme.foreground)
        contentView.mColorRForeground.backgroundTintList = ColorStateList.valueOf(controller.theme.foreground)
        contentView.mColorRBackground.backgroundTintList = ColorStateList.valueOf(controller.theme.background)
        contentView.mColorGForeground.backgroundTintList = ColorStateList.valueOf(controller.theme.foreground)
        contentView.mColorGBackground.backgroundTintList = ColorStateList.valueOf(controller.theme.background)
        contentView.mColorBForeground.backgroundTintList = ColorStateList.valueOf(controller.theme.foreground)
        contentView.mColorBBackground.backgroundTintList = ColorStateList.valueOf(controller.theme.background)
    }

    /**
     * 解析颜色
     */
    private fun parseColor(colorString: String) = try {
        setupViewData(Color.parseColor(colorString))
    } catch (e: Exception) {
        contentView.mColorValueInput.text?.clear()
    }

    private fun passTouchEvent(formView: View, toView: SeekBar, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> xAndProgress.set(event.x, toView.progress.toFloat())
            MotionEvent.ACTION_MOVE -> {
                toView.progress = (xAndProgress.y + (event.x - xAndProgress.x) / (formView.width / toView.max * 0.8)).roundToInt()
                onProgressChanged(toView)
            }
        }
        return false
    }

    private fun onProgressChanged(seekBar: SeekBar) {
        when (seekBar) {
            //RED
            contentView.mColorRSeekBar -> {
                contentView.mColorRText.text = context.getString(R.string.theme_color_red, seekBar.progress)
            }
            //GREEN
            contentView.mColorGSeekBar -> {
                contentView.mColorGText.text = context.getString(R.string.theme_color_green, seekBar.progress)
            }
            //BLUE
            contentView.mColorBSeekBar -> {
                contentView.mColorBText.text = context.getString(R.string.theme_color_blue, seekBar.progress)
            }
        }
        //VALUE
        val color = Color.rgb(contentView.mColorRSeekBar.progress, contentView.mColorGSeekBar.progress, contentView.mColorBSeekBar.progress)
        contentView.mColorValueText.text = color.toHexString().let { if (it.length > 6) it.substring(it.length - 6, it.length) else it }
        contentView.mColorValueBackground.backgroundTintList = ColorStateList.valueOf(color)
        callback.invoke(color)
        setupViewTheme()
    }
}
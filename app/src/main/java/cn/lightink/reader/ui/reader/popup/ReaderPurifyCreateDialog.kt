package cn.lightink.reader.ui.reader.popup

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.TextPaint
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import cn.lightink.reader.R
import cn.lightink.reader.controller.ReaderController
import cn.lightink.reader.ktx.parentView
import cn.lightink.reader.ktx.toast
import cn.lightink.reader.model.Theme
import cn.lightink.reader.module.EMPTY
import kotlinx.android.synthetic.main.dialog_purify_create.*

class ReaderPurifyCreateDialog(context: FragmentActivity, val content: String = EMPTY) : Dialog(context) {

    private val controller by lazy { ViewModelProvider(context)[ReaderController::class.java] }

    init {
        setContentView(R.layout.dialog_purify_create)
        if (content.isNotBlank()) {
            mPurifyCreateKey.text = content
        } else {
            mPurifyCreateRegexInput.isVisible = true
        }
        mPurifyCreateSubmit.setOnClickListener { purify() }
        setupViewTheme(controller.theme, controller.paint)
    }

    private fun setupViewTheme(theme: Theme, paint: TextPaint) {
        mPurifyCreateTitle.parentView.backgroundTintList = ColorStateList.valueOf(theme.foreground)
        mPurifyCreateTitle.typeface = paint.typeface
        mPurifyCreateTitle.setTextColor(theme.content)
        mPurifyCreateKey.typeface = paint.typeface
        mPurifyCreateKey.setTextColor(theme.content)
        mPurifyCreateRegexInput.typeface = paint.typeface
        mPurifyCreateRegexInput.backgroundTintList = ColorStateList.valueOf(theme.secondary)
        mPurifyCreateRegexInput.setTextColor(theme.content)
        mPurifyCreateRegexInput.setHintTextColor(theme.secondary)
        mPurifyCreateInput.typeface = paint.typeface
        mPurifyCreateInput.backgroundTintList = ColorStateList.valueOf(theme.secondary)
        mPurifyCreateInput.setTextColor(theme.content)
        mPurifyCreateInput.setHintTextColor(theme.secondary)
        mPurifyCreateSubmit.typeface = paint.typeface
        mPurifyCreateSubmit.backgroundTintList = ColorStateList.valueOf(theme.background)
        mPurifyCreateSubmit.setTextColor(theme.control)
    }

    private fun purify() {
        if (content.isNotBlank()) {
            controller.purify(content, mPurifyCreateInput.text.toString().trim(), false)
        } else {
            val regex = mPurifyCreateRegexInput.text.toString()
            try {
                Regex(regex)
                if (regex.isNotEmpty()) {
                    controller.purify(regex, mPurifyCreateInput.text.toString().trim(), true)
                }
            } catch (e: Exception) {
                return context.toast("正则表达式有误")
            }
        }
        dismiss()
    }

    override fun onStart() {
        super.onStart()
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setLayout(-1, -2)
        window?.setDimAmount(0.4F)
    }

}
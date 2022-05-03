package cn.lightink.reader.ui.reader.popup

import android.content.res.ColorStateList
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.view.Gravity
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import cn.lightink.reader.R
import cn.lightink.reader.controller.ReaderController
import cn.lightink.reader.ktx.parentView
import cn.lightink.reader.model.Purify
import cn.lightink.reader.model.Theme
import cn.lightink.reader.module.ListAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.android.synthetic.main.dialog_reader_purify.*
import kotlinx.android.synthetic.main.item_purify.view.*

class ReaderPurifyDialog(val context: FragmentActivity) : BottomSheetDialog(context, R.style.AppTheme_BottomSheet) {

    private val controller by lazy { ViewModelProvider(context)[ReaderController::class.java] }

    private val defaultAdapter = ListAdapter<String>(R.layout.item_purify) { item, purify ->
        item.view.mPurifyKey.text = SpannableStringBuilder(purify).apply {
            setSpan(StrikethroughSpan(), 0, length, SpannableStringBuilder.SPAN_INCLUSIVE_EXCLUSIVE)
        }
        item.view.mPurifyKey.typeface = controller.paint.typeface
        item.view.mPurifyKey.setTextColor(controller.theme.secondary)
        item.view.mPurifyRemove.isVisible = false
        item.view.setOnClickListener { item.view.mPurifyKey.maxLines = if (item.view.mPurifyKey.maxLines == 1) Int.MAX_VALUE else 1 }
    }

    private val customAdapter = ListAdapter<Purify>(R.layout.item_purify) { item, purify ->
        item.view.mPurifyKey.typeface = controller.paint.typeface
        item.view.mPurifyKey.setTextColor(controller.theme.secondary)
        item.view.mPurifyKey.text = if (purify.replace.isBlank()) {
            SpannableStringBuilder(purify.key).apply {
                setSpan(StrikethroughSpan(), 0, length, SpannableStringBuilder.SPAN_INCLUSIVE_EXCLUSIVE)
            }
        } else {
            SpannableStringBuilder("${purify.replace} -> ${purify.key}").apply {
                setSpan(ForegroundColorSpan(controller.theme.content), 0, purify.replace.length + 4, SpannableStringBuilder.SPAN_INCLUSIVE_EXCLUSIVE)
                setSpan(StrikethroughSpan(), purify.replace.length + 4, length, SpannableStringBuilder.SPAN_INCLUSIVE_EXCLUSIVE)
            }
        }
        item.view.mPurifyRemove.imageTintList = ColorStateList.valueOf(controller.theme.content)
        item.view.mPurifyRemove.setOnClickListener { purifyRemove(item.adapterPosition, purify) }
        item.view.setOnClickListener { item.view.mPurifyKey.maxLines = if (item.view.mPurifyKey.maxLines == 1) Int.MAX_VALUE else 1 }
    }

    init {
        setContentView(R.layout.dialog_reader_purify)
        mTopbar.setNavigationOnClickListener { dismiss() }
        mTopbar.setOnMenuClickListener { createRegexRule() }
        mPurifyScrollView.post { mPurifyScrollView.minimumHeight = context.resources.displayMetrics.heightPixels / 2 - mPurifyScrollView.top }
        //书源内置
        defaultAdapter.submitList(controller.getBookSourcePurifyList())
        mPurifyDefaultLayout.isVisible = defaultAdapter.itemCount > 0
        mPurifyDefaultRecycler.adapter = defaultAdapter
        //用户自设
        customAdapter.submitList(controller.purifyList)
        mPurifyCustomHint.isVisible = controller.purifyList.isEmpty()
        mPurifyCustomRecycler.adapter = customAdapter
        setupViewTheme(controller.theme, controller.paint)
    }

    private fun setupViewTheme(theme: Theme, paint: TextPaint) {
        mTopbar.parentView.backgroundTintList = ColorStateList.valueOf(theme.foreground)
        mTopIndicator.backgroundTintList = ColorStateList.valueOf(theme.secondary)
        mTopbar.setTint(theme.content)
        mPurifyDefaultTitle.typeface = paint.typeface
        mPurifyDefaultTitle.setTextColor(theme.content)
        mPurifyCustomTitle.typeface = paint.typeface
        mPurifyCustomTitle.setTextColor(theme.content)
        mPurifyCustomHint.typeface = paint.typeface
        mPurifyCustomHint.setTextColor(theme.secondary)
    }

    private fun createRegexRule() {
        ReaderPurifyCreateDialog(context).apply {
            setOnDismissListener { customAdapter.notifyDataSetChanged() }
        }.show()
    }

    private fun purifyRemove(position: Int, purify: Purify) {
        controller.purifyRemove(purify)
        customAdapter.notifyItemRemoved(position)
    }

    override fun onStart() {
        super.onStart()
        window?.navigationBarColor = controller.theme.foreground
        window?.setLayout(-1, (context.resources.displayMetrics.heightPixels * 0.8F).toInt())
        window?.setDimAmount(0.4F)
        window?.setGravity(Gravity.BOTTOM)
    }
}
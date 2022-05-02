package cn.lightink.reader.ui.feed

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import cn.lightink.reader.R
import cn.lightink.reader.ktx.toast
import cn.lightink.reader.model.FeedGroup
import cn.lightink.reader.module.Room
import kotlinx.android.synthetic.main.dialog_feed_group_create.*

/**
 * 弹出式对话框
 * 编辑Feed分组名并保存到数据库
 */
class FeedGroupCreateDialog(context: Context, val group: FeedGroup? = null) : Dialog(context) {

    init {
        setContentView(R.layout.dialog_feed_group_create)
        mFeedGroupCreateInput.setText(group?.name)
        mFeedGroupCreateInput.post {
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(mFeedGroupCreateInput, InputMethodManager.SHOW_IMPLICIT)
        }
        mFeedGroupCreateInput.setOnEditorActionListener { _, actionId, _ -> if (actionId == EditorInfo.IME_ACTION_SEND) mFeedGroupCreateSubmit.performClick() else false }
        mFeedGroupCreateSubmit.setOnClickListener { submit(mFeedGroupCreateInput.text.toString().trim()) }
    }

    private fun submit(name: String) {
        if (name.isBlank()) return
        if (Room.feedGroup().has(name)) return context.toast(R.string.feed_group_create_same_warning)
        when {
            group != null -> Room.feedGroup().update(group.apply { this.name = name })
            name == "收藏" -> return context.toast(R.string.feed_group_create_built_in)
            //新建
            else -> Room.feedGroup().insert(FeedGroup(name))
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
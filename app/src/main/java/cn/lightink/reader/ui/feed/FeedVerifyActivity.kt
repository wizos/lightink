package cn.lightink.reader.ui.feed

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.URLUtil
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import cn.lightink.reader.R
import cn.lightink.reader.controller.FeedController
import cn.lightink.reader.ktx.parentView
import cn.lightink.reader.ktx.toast
import cn.lightink.reader.model.Feed
import cn.lightink.reader.model.Flow
import cn.lightink.reader.model.StoreFeed
import cn.lightink.reader.module.INTENT_FEED
import cn.lightink.reader.module.ListAdapter
import cn.lightink.reader.module.TOAST_TYPE_SUCCESS
import cn.lightink.reader.module.TimeFormat
import cn.lightink.reader.ui.base.LifecycleActivity
import kotlinx.android.synthetic.main.activity_feed_verify.*
import kotlinx.android.synthetic.main.item_flow_compat.view.*

class FeedVerifyActivity : LifecycleActivity() {

    private val controller by lazy { ViewModelProvider(this)[FeedController::class.java] }
    private val adapter by lazy { buildAdapter() }

    private val storeFeed by lazy { intent.getParcelableExtra<StoreFeed?>(INTENT_FEED) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feed_verify)
        if (storeFeed != null) {
            mFeedVerifyInput.parentView.isVisible = false
            mFeedVerifySubmit.isVisible = false
            mFeedVerifyCancel.isVisible = false
            verify("http://${storeFeed!!.rss}", false)
        } else {
            mFeedVerifyInput.doOnTextChanged { text, _, _, _ ->
                mFeedVerifySubmit.isVisible = URLUtil.isNetworkUrl(text.toString())
                mFeedVerifyClear.isVisible = text.isNullOrBlank().not()
            }
            mFeedVerifyInput.postDelayed({
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(mFeedVerifyInput, InputMethodManager.SHOW_IMPLICIT)
            }, 200)
            mFeedVerifyInput.setOnEditorActionListener { _, actionId, _ -> if (actionId == EditorInfo.IME_ACTION_GO) mFeedVerifySubmit.callOnClick() else false }
            mFeedVerifyClear.setOnClickListener { cancel() }
            mFeedVerifySubmit.setOnClickListener { verify(mFeedVerifyInput.text.toString().trim(), true) }
            mFeedVerifyCancel.setOnClickListener { cancel() }
        }
        mFeedVerifyRecycler.adapter = adapter
    }

    private fun updateFeedInfoView(feed: Feed, flows: List<Flow>) {
        mFeedInfoLayout.isVisible = true
        mFeedName.text = storeFeed?.name ?: feed.name
        mFeedSummary.text = storeFeed?.summary ?: feed.summary
        adapter.submitList(flows)
        if (controller.hasFeed(feed)) {
            mFeedVerifySubscribe.setText(R.string.feed_unsubscribe)
            mFeedVerifySubscribe.backgroundTintList = ColorStateList.valueOf(getColor(R.color.colorRed))
            mFeedVerifySubscribe.setOnClickListener { unsubscribe() }
        } else {
            mFeedVerifySubscribe.setText(R.string.feed_subscribe)
            mFeedVerifySubscribe.backgroundTintList = ColorStateList.valueOf(getColor(R.color.colorAccent))
            mFeedVerifySubscribe.setOnClickListener { subscribe() }
        }
    }

    /**
     * 验证频道
     */
    private fun verify(link: String, upload: Boolean) {
        mFeedVerifyInput.isEnabled = false
        mFeedVerifyClear.isVisible = false
        mFeedVerifySubmit.isVisible = false
        mFeedVerifyProgressBar.isVisible = true
        controller.verify(link.replace("https://", "http://"), upload).observe(this, Observer { result ->
            mFeedVerifyProgressBar.isVisible = false
            mFeedVerifyInput.isEnabled = true
            mFeedVerifyClear.isVisible = true
            //验证失败
            if (result.message.isNotBlank() || result.feed == null || result.flows == null) {
                return@Observer toast(result.message)
            }
            updateFeedInfoView(result.feed, result.flows)
        })
    }

    /**
     * 取消验证
     */
    private fun cancel() {
        mFeedInfoLayout.isVisible = false
        mFeedVerifyInput.isEnabled = true
        mFeedVerifyInput.text?.clear()
    }

    /**
     * 订阅
     */
    private fun subscribe() {
        controller.verifyResultLiveData.value?.feed?.run {
            //空内容时订阅不会成功
            if (controller.verifyResultLiveData.value?.flows.isNullOrEmpty()) return toast(R.string.feed_subscribe_failure)
            FeedSelectGroupDialog(this@FeedVerifyActivity, this, storeFeed?.tag) { groupId ->
                controller.subscribeFeed(groupId, this, controller.verifyResultLiveData.value?.flows.orEmpty())
                toast(R.string.feed_subscribe_success, TOAST_TYPE_SUCCESS)
                updateFeedInfoView(this, controller.verifyResultLiveData.value?.flows.orEmpty())
            }.show()
        }
    }

    /**
     * 取消订阅
     */
    private fun unsubscribe() {
        controller.verifyResultLiveData.value?.feed?.run {
            controller.unsubscribeFeed(link)
            updateFeedInfoView(this, controller.verifyResultLiveData.value?.flows.orEmpty())
        }
    }

    /**
     * 构建数据适配器
     */
    private fun buildAdapter() = ListAdapter<Flow>(R.layout.item_flow_compat, equalContent = { old, new -> old.same(new) }, equalItem = { old, new -> old.link == new.link }) { item, flow ->
        item.view.mFlowTitle.setTextColor(resources.getColor(if (flow.read) R.color.colorContent else R.color.colorTitle, item.view.context.theme))
        item.view.mFlowTitle.text = flow.title.trim()
        item.view.mFlowSummary.text = TimeFormat.format(flow.date)
        item.view.mFlowCover.isVisible = flow.cover.isNullOrBlank().not()
        if (item.view.mFlowCover.isVisible) {
            item.view.mFlowCover.radius(1F).load(flow.cover)
        }
    }

}
package cn.lightink.reader.ui.feed

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import cn.lightink.reader.R
import cn.lightink.reader.controller.FeedController
import cn.lightink.reader.ktx.toast
import cn.lightink.reader.model.Feed
import cn.lightink.reader.model.FeedGroup
import cn.lightink.reader.module.ListAdapter
import cn.lightink.reader.module.RVLinearLayoutManager
import cn.lightink.reader.module.Room
import cn.lightink.reader.module.TOAST_TYPE_SUCCESS
import cn.lightink.reader.ui.base.BottomSelectorDialog
import cn.lightink.reader.ui.base.LifecycleActivity
import kotlinx.android.synthetic.main.activity_feed_management.*
import kotlinx.android.synthetic.main.item_feed_group_management.view.*
import kotlinx.android.synthetic.main.item_feed_management.view.*

class FeedManagementActivity : LifecycleActivity() {

    private val controller by lazy { ViewModelProvider(this)[FeedController::class.java] }
    private val adapter by lazy { buildAdapter() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feed_management)
        mTopbar.setOnMenuClickListener { createFeedGroup() }
        mFeedManageRecycler.layoutManager = RVLinearLayoutManager(this)
        mFeedManageRecycler.adapter = adapter
        controller.groupLiveData.observe(this, Observer {
            adapter.submitList(it)
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
        })
    }

    /**
     * 新建频道分组
     */
    private fun createFeedGroup() {
        FeedGroupCreateDialog(this).show()
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildAdapter() = ListAdapter<FeedGroup>(R.layout.item_feed_group_management, equalContent = { o, n -> o.name == n.name }, equalItem = { o, n -> o.id == n.id }) { item, group ->
        item.view.mFeedGroupName.text = group.name
        item.view.mFeedGroupArrow.setOnClickListener { arrowView ->
            item.view.mFeedGroupRecycler.isVisible = !item.view.mFeedGroupRecycler.isVisible
            arrowView.rotation = if (item.view.mFeedGroupRecycler.isVisible) 0F else -90F
        }
        var adapter = item.view.mFeedGroupRecycler.adapter as? ListAdapter<Feed>
        if (adapter == null) {
            adapter = buildChildAdapter()
            item.view.mFeedGroupRecycler.adapter = adapter
        }
        controller.queryFeedsByGroupId(group.id).observe(this, Observer { feeds ->
            item.view.mFeedGroupRecycler.isVisible = feeds.isNotEmpty()
            item.view.mFeedGroupArrow.isVisible = feeds.isNotEmpty()
            item.view.mFeedGroupArrow.rotation = if (item.view.mFeedGroupRecycler.isVisible) 0F else -90F
            adapter.submitList(feeds)
        })
        item.view.setOnClickListener { showFeedGroupPopup(group) }
    }

    private fun buildChildAdapter() = ListAdapter<Feed>(R.layout.item_feed_management) { item, feed ->
        item.view.mFeedName.text = feed.name
        item.view.setOnClickListener { showFeedPopup(feed) }
    }

    /**
     * 频道分组菜单
     */
    private fun showFeedGroupPopup(group: FeedGroup) {
        BottomSelectorDialog(this, group.name, listOf(if (controller.hasPushpin(group)) R.string.feed_pushpin_cancel else R.string.feed_pushpin, R.string.edit, R.string.delete)) { resId -> getString(resId) }.callback { item ->
            when (item) {
                R.string.edit -> FeedGroupCreateDialog(this, group).show()
                R.string.delete -> controller.deleteFeedGroup(group).observe(this, Observer { message -> if (message.isNotBlank()) toast(message) })
                R.string.feed_pushpin -> showSelectBookshelfPopup(group)
                R.string.feed_pushpin_cancel -> controller.unPushpin(group).observe(this, Observer {
                    toast(R.string.feed_pushpin_cancel, TOAST_TYPE_SUCCESS)
                })
            }
        }.show()
    }

    /**
     * 选择书架
     */
    private fun showSelectBookshelfPopup(group: FeedGroup) {
        BottomSelectorDialog(this, "选择书架", Room.bookshelf().getAllImmediately()) { it.name }.callback { bookshelf ->
            controller.pushpin(group, bookshelf).observe(this, Observer { result ->
                toast(if (result) R.string.feed_pushpin_success else R.string.feed_pushpin_failure)
            })
        }.show()
    }

    /**
     * 频道菜单
     */
    private fun showFeedPopup(feed: Feed) {
        BottomSelectorDialog(this, feed.name, listOf(R.string.move, R.string.unsubscribe_public_account)) { resId -> getString(resId) }.callback { item ->
            when (item) {
                R.string.move -> FeedSelectGroupDialog(this, feed) { groupId -> controller.moveFeedToGroup(feed, groupId) }.show()
                R.string.unsubscribe_public_account -> controller.unsubscribeFeed(feed.link)
            }
        }.show()
    }

}
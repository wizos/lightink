package cn.lightink.reader.ui.feed

import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import cn.lightink.reader.R
import cn.lightink.reader.model.Feed
import cn.lightink.reader.model.FeedGroup
import cn.lightink.reader.module.ListAdapter
import cn.lightink.reader.module.Room
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.android.synthetic.main.dialog_feed_select_group.*
import kotlinx.android.synthetic.main.item_feed_group.view.*

class FeedSelectGroupDialog(context: FragmentActivity, feed: Feed, tag: String? = null, val callback: (Long) -> Unit) : BottomSheetDialog(context, R.style.AppTheme_BottomSheet) {

    init {
        setContentView(R.layout.dialog_feed_select_group)
        mFeedGroupCreateByTag.isVisible = tag.isNullOrBlank().not()
        mFeedGroupCreateByTag.text = context.getString(R.string.feed_group_create_by_tag, tag)
        mFeedGroupCreateByTag.setOnClickListener { createGroupBySelf(tag!!) }
        mFeedGroupCreateBySelf.text = context.getString(R.string.feed_group_create_by_self, feed.name)
        mFeedGroupCreateBySelf.setOnClickListener { createGroupBySelf(feed.name) }
        mFeedGroupCreate.setOnClickListener { showCreateDialog() }
        mFeedGroupCancel.setOnClickListener { dismiss() }
        mFeedGroupRecycler.adapter = buildAdapter().apply {
            Room.feedGroup().getAll().observe(context, Observer { submitList(it.filterNot { group -> group.name == "收藏" }) })
        }
    }

    /**
     * 查找是否已存在同名的分组
     * 存在：返回分组ID
     * 不存在：创建后返回新分组ID
     */
    private fun createGroupBySelf(name: String) {
        callback.invoke(Room.feedGroup().getByName(name)?.id ?: Room.feedGroup().insert(FeedGroup(name)))
        dismiss()
    }

    private fun showCreateDialog() {
        FeedGroupCreateDialog(context).show()
    }

    private fun buildAdapter() = ListAdapter<FeedGroup>(R.layout.item_feed_group) { item, group ->
        item.view.mFeedGroupName.text = group.name
        item.view.setOnClickListener { callback.invoke(group.id).run { dismiss() } }
    }

    override fun onStart() {
        super.onStart()
        window?.setDimAmount(0.6F)
    }

}
package cn.lightink.reader.ui.feed

import android.graphics.Typeface
import android.os.Bundle
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager.widget.ViewPager
import cn.lightink.reader.R
import cn.lightink.reader.controller.FeedController
import cn.lightink.reader.ktx.startActivity
import cn.lightink.reader.ktx.toast
import cn.lightink.reader.model.FeedGroup
import cn.lightink.reader.module.Preferences
import cn.lightink.reader.ui.base.BottomSelectorDialog
import cn.lightink.reader.ui.base.LifecycleActivity
import cn.lightink.reader.ui.discover.help.FeedHelpFragment
import kotlinx.android.synthetic.main.activity_feed.*
import kotlin.math.max

class FeedActivity : LifecycleActivity(), ViewPager.OnPageChangeListener {

    private val controller by lazy { ViewModelProvider(this)[FeedController::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feed)
        mTopbar.setOnMenuClickListener { startActivity(FeedVerifyActivity::class) }
        mFeedClean.setOnClickListener { showPopupMenu() }
        mFeedManage.setOnClickListener { startActivity(FeedManagementActivity::class) }
        mFeedTabLayout.setOnPageChangeListener(this)
        controller.groupLiveData.observe(this, Observer { groups ->
            mFeedClean.isVisible = groups.isNotEmpty()
            mFeedManage.isVisible = groups.isNotEmpty()
            mFeedViewPager.adapter = PagerAdapter(supportFragmentManager, groups)
            mFeedViewPager.offscreenPageLimit = 3
            mFeedViewPager.setCurrentItem(max(groups.indexOfFirst { it.id == Preferences.get(Preferences.Key.LAST_FEED, 0L) }, 0), false)
            mFeedTabLayout.setViewPager(mFeedViewPager)
            onPageSelected(mFeedViewPager.currentItem)
            checkHelpView(groups.isEmpty())
        })
    }

    private fun showPopupMenu() {
        BottomSelectorDialog(this, getString(R.string.feed_clear), listOf(R.string.feed_clear_current, R.string.feed_clear_all)) { resId -> getString(resId) }.callback { item ->
            controller.clean(if (item == R.string.feed_clear_current) (mFeedViewPager.adapter as? PagerAdapter)?.getGroupId(mFeedViewPager.currentItem) ?: 0L else -1)
            toast(R.string.feed_clean_completed)
        }.show()
    }

    /**
     * 检查是否显示使用指南
     */
    private fun checkHelpView(isVisible: Boolean) {
        container.isVisible = isVisible
        val transaction = supportFragmentManager.beginTransaction()
        if (isVisible && supportFragmentManager.fragments.isEmpty()) {
            transaction.replace(R.id.container, FeedHelpFragment(), "HELP").commitAllowingStateLoss()
        } else if (!isVisible) {
            supportFragmentManager.findFragmentByTag("HELP")?.run { transaction.remove(this).commitAllowingStateLoss() }
        }
    }

    override fun onPageScrollStateChanged(state: Int) {
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
    }

    override fun onPageSelected(position: Int) {
        if (mFeedViewPager.adapter?.count ?: 0 <= 0) return
        (0 until (mFeedViewPager.adapter?.count ?: 0)).mapNotNull { mFeedTabLayout.getTabAt(it) }.forEachIndexed { index, view ->
            (view as TextView).typeface = if (position == index) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        Preferences.put(Preferences.Key.LAST_FEED, (mFeedViewPager.adapter as? PagerAdapter)?.getGroupId(position) ?: 0L)
    }

    class PagerAdapter(fm: FragmentManager, private val groups: List<FeedGroup>) : FragmentStatePagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        fun getGroupId(position: Int) = groups[position].id

        override fun getItem(position: Int) = FeedFragment.newInstance(if (groups[position].date == 0L) -1 else groups[position].id)

        override fun getCount() = groups.size

        override fun getPageTitle(position: Int) = groups[position].name
    }

}
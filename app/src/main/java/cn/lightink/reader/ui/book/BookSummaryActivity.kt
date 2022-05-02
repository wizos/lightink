package cn.lightink.reader.ui.book

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import androidx.fragment.app.FragmentPagerAdapter
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager.widget.ViewPager
import cn.lightink.reader.R
import cn.lightink.reader.controller.BookSummaryController
import cn.lightink.reader.module.INTENT_BOOK_CACHE
import cn.lightink.reader.ui.base.LifecycleActivity
import com.google.android.material.tabs.TabLayout
import kotlinx.android.synthetic.main.activity_book_summary.*
import kotlinx.android.synthetic.main.layout_tab_item.view.*

class BookSummaryActivity : LifecycleActivity(), TabLayout.OnTabSelectedListener, ViewPager.OnPageChangeListener {

    private val controller by lazy { ViewModelProvider(this)[BookSummaryController::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_summary)
        controller.attach(intent)
        mViewPager.adapter = FragmentAdapter()
        mViewPager.addOnPageChangeListener(this)
        mTabLayout.addOnTabSelectedListener(this)
        listOf(R.string.book_summary_info, R.string.book_summary_cache).forEach { typeResId ->
            val itemView = LayoutInflater.from(this).inflate(R.layout.layout_tab_item, mTabLayout, false)
            itemView.mLabel.text = getString(typeResId)
            itemView.mLabel.minWidth = (resources.getDimension(R.dimen.dimen1) * 64).toInt()
            itemView.mLabel.setTextColor(mTabLayout.tabTextColors)
            mTabLayout.addTab(mTabLayout.newTab().setCustomView(itemView))
        }
        if (intent.getBooleanExtra(INTENT_BOOK_CACHE, false)) {
            mViewPager.setCurrentItem(1, false)
        }
    }

    override fun onTabReselected(tab: TabLayout.Tab?) {
    }

    override fun onTabUnselected(tab: TabLayout.Tab?) {
        tab?.customView?.mLabel?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14F)
        tab?.customView?.mLabel?.paint?.isFakeBoldText = false
    }

    override fun onTabSelected(tab: TabLayout.Tab?) {
        tab?.customView?.mLabel?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16F)
        tab?.customView?.mLabel?.paint?.isFakeBoldText = true
        tab?.position?.run { mViewPager.currentItem = this }
    }

    override fun onPageScrollStateChanged(state: Int) {
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
    }

    override fun onPageSelected(position: Int) {
        mTabLayout.selectTab(mTabLayout.getTabAt(position))
    }

    inner class FragmentAdapter : FragmentPagerAdapter(supportFragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        override fun getItem(position: Int) = if (position == 0) BookSummaryInfoFragment() else BookSummaryCacheFragment()

        override fun getCount() = 2
    }

}
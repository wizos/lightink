package cn.lightink.reader.ui.discover.help

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.viewpager.widget.ViewPager
import cn.lightink.reader.R
import cn.lightink.reader.ui.base.LifecycleActivity
import com.google.android.material.tabs.TabLayout
import kotlinx.android.synthetic.main.activity_book_rank.*
import kotlinx.android.synthetic.main.layout_tab_item.view.*

class HelpActivity : LifecycleActivity(), TabLayout.OnTabSelectedListener, ViewPager.OnPageChangeListener {

    private val titles = listOf("关于书架", "关于时刻")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)
        mTabLayout.addOnTabSelectedListener(this)
        titles.forEach { title ->
            val itemView = LayoutInflater.from(this).inflate(R.layout.layout_tab_item, mTabLayout, false)
            itemView.mLabel.text = title
            itemView.mLabel.setTextColor(mTabLayout.tabTextColors)
            mTabLayout.addTab(mTabLayout.newTab().setCustomView(itemView))
        }
        mViewPager.adapter = FragmentAdapter(titles)
        mViewPager.addOnPageChangeListener(this)
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

    inner class FragmentAdapter(private val titles: List<String>) : FragmentStatePagerAdapter(supportFragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        override fun getItem(position: Int) = when (position) {
            1 -> FeedHelpFragment()
            else -> BookshelfHelpFragment()
        }

        override fun getCount() = titles.size
    }

}
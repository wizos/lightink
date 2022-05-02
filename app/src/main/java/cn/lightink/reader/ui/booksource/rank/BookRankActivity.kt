package cn.lightink.reader.ui.booksource.rank

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager.widget.ViewPager
import cn.lightink.reader.R
import cn.lightink.reader.controller.BookRankController
import cn.lightink.reader.ktx.startActivityForResult
import cn.lightink.reader.model.BookRank
import cn.lightink.reader.ui.base.LifecycleActivity
import com.google.android.material.tabs.TabLayout
import kotlinx.android.synthetic.main.activity_book_rank.*
import kotlinx.android.synthetic.main.layout_tab_item.view.*

class BookRankActivity : LifecycleActivity(), TabLayout.OnTabSelectedListener, ViewPager.OnPageChangeListener {

    private val controller by lazy { ViewModelProvider(this)[BookRankController::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_rank)
        mTopbar.setOnMenuClickListener { startActivityForResult(BookRankSettingsActivity::class) }
        mTabLayout.addOnTabSelectedListener(this)
        setupRanks(controller.getVisibleBookRanks())
    }

    /**
     * 设置排行榜
     */
    private fun setupRanks(ranks: List<BookRank>) {
        mBookRankNone.isVisible = ranks.isEmpty()
        mTabLayout.isVisible = ranks.isNotEmpty()
        mTabLayout.removeAllTabs()
        ranks.forEach { rank ->
            val itemView = LayoutInflater.from(this).inflate(R.layout.layout_tab_item2, mTabLayout, false)
            itemView.mLabel.text = rank.name
            itemView.mLabel.setTextColor(mTabLayout.tabTextColors)
            mTabLayout.addTab(mTabLayout.newTab().setCustomView(itemView))
        }
        mViewPager.adapter = FragmentAdapter(ranks)
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

    inner class FragmentAdapter(private val ranks: List<BookRank>) : FragmentStatePagerAdapter(supportFragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        override fun getItem(position: Int) = BookRankFragment.newInstance(ranks[position])

        override fun getCount() = ranks.size
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == Activity.RESULT_FIRST_USER && resultCode == Activity.RESULT_OK) {
            setupRanks(controller.getVisibleBookRanks())
        }
    }
}
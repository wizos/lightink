package cn.lightink.reader.ui.discover

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import cn.lightink.reader.App
import cn.lightink.reader.R
import cn.lightink.reader.ktx.parentView
import cn.lightink.reader.ktx.startActivity
import cn.lightink.reader.module.Preferences
import cn.lightink.reader.module.UIModule
import cn.lightink.reader.ui.base.LifecycleFragment
import cn.lightink.reader.ui.booksource.BookSourceActivity
import cn.lightink.reader.ui.booksource.rank.BookRankActivity
import cn.lightink.reader.ui.discover.setting.SettingActivity
import cn.lightink.reader.ui.feed.FeedActivity
import kotlinx.android.synthetic.main.fragment_discover.*

class DiscoverFragment : LifecycleFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_discover, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupView()
    }

    /**
     * 设置控件
     */
    private fun setupView() {
        mDiscoverNight.post {
            mDiscoverNight.onCheckedChangeListener = null
            mDiscoverNight.isChecked = !UIModule.isNightMode(requireContext())
            mDiscoverNight.setOnCheckedChangeListener { _, isChecked ->
                Preferences.put(Preferences.Key.LIGHT, isChecked)
                (activity?.application as? App)?.setupTheme()
            }
        }
        mDiscoverNight.parentView.setOnClickListener { mDiscoverNight.toggle() }
        mDiscoverBookSource.setOnClickListener { startActivity(BookSourceActivity::class) }
        mDiscoverRank.setOnClickListener { startActivity(BookRankActivity::class) }
        mDiscoverFeed.setOnClickListener { startActivity(FeedActivity::class) }
        mDiscoverSetting.setOnClickListener { startActivity(SettingActivity::class) }
    }

}
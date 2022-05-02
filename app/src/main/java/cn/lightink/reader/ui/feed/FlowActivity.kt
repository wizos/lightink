package cn.lightink.reader.ui.feed

import android.os.Bundle
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import cn.lightink.reader.R
import cn.lightink.reader.controller.FeedController
import cn.lightink.reader.model.Feed
import cn.lightink.reader.model.THEME_LIGHT
import cn.lightink.reader.model.THEME_NIGHT
import cn.lightink.reader.module.INTENT_FEED_FLOW
import cn.lightink.reader.module.INTENT_FEED_GROUP
import cn.lightink.reader.module.UIModule
import cn.lightink.reader.ui.base.LifecycleActivity
import com.gyf.immersionbar.ktx.immersionBar
import kotlinx.android.synthetic.main.activity_flow.*
import kotlin.math.max

class FlowActivity : LifecycleActivity() {

    private val controller by lazy { ViewModelProvider(this)[FeedController::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flow)
        controller.startedFlowLink = intent.getStringExtra(INTENT_FEED_FLOW).orEmpty()
        controller.theme = if (UIModule.isNightMode(this)) THEME_NIGHT else THEME_LIGHT
//        controller.theme = when {
//            Preferences.get(Preferences.Key.FLOW_THEME, true) -> UIModule.getConfiguredTheme(this)
//            UIModule.isNightMode(this) -> THEME_NIGHT
//            else -> THEME_LIGHT
//        }
        val groupId = intent.getLongExtra(INTENT_FEED_GROUP, 0)
        if (groupId != -1L) {
            controller.queryFeedsByGroupId(groupId).observe(this, Observer { queryFlows(it) })
        } else {
            controller.queryLoves().observe(this, Observer { submitList(it.map { it.link }) })
        }
        controller.queryFeedsByGroupId(intent.getLongExtra(INTENT_FEED_GROUP, 0)).observe(this, Observer { queryFlows(it) })
        immersionBar {
            statusBarDarkFont(!UIModule.isNightMode(this@FlowActivity))
            navigationBarColorInt(controller.theme.background)
        }
        window.decorView.setBackgroundColor(controller.theme.background)
//        if (controller.theme.mipmap.isNotBlank()) {
//            window.decorView.background = UIModule.getMipmapByTheme(controller.theme)
//        }
    }

    private fun queryFlows(feeds: List<Feed>) {
        controller.queryFlowLinks(feeds).observe(this, Observer { flowLinks -> submitList(flowLinks) })
    }

    private fun submitList(flowLinks: List<String>) {
        if (mViewPager.adapter == null) {
            mViewPager.adapter = FlowAdapter(this, flowLinks)
            mViewPager.setCurrentItem(max(0, flowLinks.indexOf(controller.startedFlowLink)), false)
        }
    }

    class FlowAdapter(activity: FlowActivity, private val flowLinks: List<String>) : FragmentStatePagerAdapter(activity.supportFragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        override fun getItem(position: Int) = FlowFragment.newInstance(flowLinks[position])

        override fun getCount() = flowLinks.size

    }
}
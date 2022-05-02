package cn.lightink.reader.ui.discover.help

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import cn.lightink.reader.R
import cn.lightink.reader.ktx.startActivity
import cn.lightink.reader.ui.base.LifecycleFragment
import cn.lightink.reader.ui.feed.FeedVerifyActivity
import kotlinx.android.synthetic.main.fragment_help_feed.*

class FeedHelpFragment : LifecycleFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_help_feed, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mHelpFeedVerify.setOnClickListener { startActivity(FeedVerifyActivity::class) }
    }
}
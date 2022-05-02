package cn.lightink.reader.ui.main

import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.ConnectivityManager
import android.net.NetworkRequest
import android.os.Bundle
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.lifecycle.ViewModelProvider
import cn.lightink.reader.R
import cn.lightink.reader.controller.MainController
import cn.lightink.reader.module.*
import cn.lightink.reader.ui.base.LifecycleActivity
import cn.lightink.reader.ui.bookshelf.BookshelfFragment
import cn.lightink.reader.ui.dashboard.DashboardFragment
import cn.lightink.reader.ui.discover.DiscoverFragment
import cn.lightink.reader.ui.reader.ReaderActivity
import com.gyf.immersionbar.ktx.hasNotchScreen
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class MainActivity : LifecycleActivity() {

    private val connectivityManager by lazy { applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    private val controller by lazy { ViewModelProvider(this)[MainController::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connectivityManager.registerNetworkCallback(NetworkRequest.Builder().build(), NetworkCallback)
        //尝试使用此行代码解决某些情况下点击图标重启应用的问题
        if (!isTaskRoot && intent.hasCategory(Intent.CATEGORY_LAUNCHER) && intent.action == Intent.ACTION_MAIN) return finish()
        setContentView(R.layout.activity_main)
        checkShortcuts()
        setupView()
        controller.autoClearInvalidBook(this)
    }

    private fun setupView() {
        window.setBackgroundDrawable(ColorDrawable(getColor(R.color.colorBackground)))
        window.decorView.post {
            Preferences.put(Preferences.Key.HAS_NAVIGATION, window.decorView.height > (window.decorView as ViewGroup).getChildAt(0).height)
            Preferences.put(Preferences.Key.HAS_NOTCH, hasNotchScreen)
            Preferences.put(Preferences.Key.READER_HEIGHT, window.decorView.height)
        }
        mViewPager.offscreenPageLimit = 2
        mViewPager.adapter = SlidePagerAdapter(supportFragmentManager)
        mViewPager.setCurrentItem(1, false)
    }

    /**
     * 检查快捷方式
     */
    private fun checkShortcuts() {
        when (intent.dataString) {
            //继续阅读
            "lightink://continue" -> Room.book().getTheLastRead()?.run {
                startActivity(Intent(this@MainActivity, ReaderActivity::class.java).putExtra(INTENT_BOOK, this))
            }
        }
    }

    override fun onBackPressed() {
        if (mViewPager.currentItem != 1) {
            return openBookshelfPage()
        }
        super.onBackPressed()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: Notify.Event) {
        when (event) {
            is Notify.BookshelfChangedEvent -> controller.changedBookshelf()
            is Notify.RestartEvent -> GlobalScope.launch {
                delay(500)
                startActivity(Intent(this@MainActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        try {
            connectivityManager.unregisterNetworkCallback(NetworkCallback)
        } catch (e: IllegalArgumentException) {
            //防止重复取消注册
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
    }

    fun openDiscoverPage() {
        mViewPager.currentItem = 2
    }

    fun openDashboardPage() {
        mViewPager.currentItem = 0
    }

    private fun openBookshelfPage() {
        mViewPager.currentItem = 1
    }

    /***********************************************************************************************
     * 子类声明
     **********************************************************************************************/
    class SlidePagerAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        override fun getItem(position: Int) = when (position) {
            0 -> DashboardFragment()
            1 -> BookshelfFragment()
            else -> DiscoverFragment()
        }

        override fun getCount() = 3

    }

}
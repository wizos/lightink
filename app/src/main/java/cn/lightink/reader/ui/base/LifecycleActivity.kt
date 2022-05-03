package cn.lightink.reader.ui.base

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import cn.lightink.reader.R
import cn.lightink.reader.module.Notify
import cn.lightink.reader.module.Preferences
import cn.lightink.reader.module.UIModule

abstract class LifecycleActivity : AppCompatActivity() {

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindowPreference()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(fm: FragmentManager, fragment: Fragment, v: View, savedInstanceState: Bundle?) {
                if (fragment is LifecycleFragment) Notify.register(fragment)
            }

            override fun onFragmentViewDestroyed(fm: FragmentManager, fragment: Fragment) {
                if (fragment is LifecycleFragment) Notify.unregister(fragment)
            }
        }, false)
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun setupWindowPreference() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            window.navigationBarColor = getColor(R.color.colorBackground)
            if (!UIModule.isNightMode(this)) {
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        }
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(LifecycleContextWrapper.wrap(newBase))
    }
}

class LifecycleContextWrapper(base: Context?) : ContextWrapper(base) {

    companion object {
        fun wrap(base: Context?): LifecycleContextWrapper {
            if (base == null) return LifecycleContextWrapper(base)
            val config = base.resources.configuration
            config.fontScale = if (config.fontScale < 1F) {
                if (Preferences.get(Preferences.Key.MIDDLE_FONT_SIZE, false)) 0.99F else config.fontScale
            } else {
                if (Preferences.get(Preferences.Key.MIDDLE_FONT_SIZE, false)) 1.1F else 1F
            }
            return LifecycleContextWrapper(base.createConfigurationContext(config))
        }
    }

}
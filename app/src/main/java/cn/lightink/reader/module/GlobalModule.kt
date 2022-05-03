package cn.lightink.reader.module

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.core.content.pm.PackageInfoCompat
import cn.lightink.reader.ui.main.MainActivity
import java.lang.ref.SoftReference

object GL : Application.ActivityLifecycleCallbacks {

    private var APP_ON_PAUSED = false

    var CONTEXT: SoftReference<Context>? = null
    var VERSION = EMPTY

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        activity.run {
            if (VERSION.isBlank()) {
                VERSION = PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(packageName, 0)).toString()
            }
            CONTEXT = SoftReference(this)
            Notify.register(this)
        }
    }

    override fun onActivityPaused(activity: Activity) {
        APP_ON_PAUSED = activity is MainActivity
    }

    override fun onActivityResumed(activity: Activity) {

    }

    override fun onActivityStarted(activity: Activity) {
    }

    override fun onActivityDestroyed(activity: Activity) {
        activity.run { Notify.unregister(this) }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityStopped(activity: Activity) {
    }


}
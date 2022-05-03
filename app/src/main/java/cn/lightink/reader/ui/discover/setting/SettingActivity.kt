package cn.lightink.reader.ui.discover.setting

import android.content.Intent
import android.os.Bundle
import androidx.core.view.get
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import cn.lightink.reader.App
import cn.lightink.reader.R
import cn.lightink.reader.ktx.parentView
import cn.lightink.reader.ktx.startActivity
import cn.lightink.reader.module.CHASE_UPDATE_TAG
import cn.lightink.reader.module.ChaseUpdateWorker
import cn.lightink.reader.module.Preferences
import cn.lightink.reader.ui.base.LifecycleActivity
import cn.lightink.reader.ui.base.SimpleDialog
import cn.lightink.reader.ui.discover.AirPlayActivity
import cn.lightink.reader.ui.discover.help.HelpActivity
import cn.lightink.reader.ui.main.MainActivity
import kotlinx.android.synthetic.main.activity_setting.*
import java.util.concurrent.TimeUnit

class SettingActivity : LifecycleActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting)
        //中等字号
        mSettingFontSize.parentView.setOnClickListener { mSettingFontSize.toggle() }
        mSettingFontSize.setCheckedImmediatelyNoEvent(Preferences.get(Preferences.Key.MIDDLE_FONT_SIZE, false))
        mSettingFontSize.setOnCheckedChangeListener { _, isChecked -> changeFontSize(isChecked) }
        //跟随系统夜间模式
        mSettingFollowSystem.parentView.parentView.setOnClickListener { mSettingFollowSystem.toggle() }
        mSettingFollowSystem.setCheckedImmediatelyNoEvent(Preferences.get(Preferences.Key.FOLLOW_SYSTEM, false))
        mSettingFollowSystem.setOnCheckedChangeListener { _, _ ->
            Preferences.put(Preferences.Key.FOLLOW_SYSTEM, !Preferences.get(Preferences.Key.FOLLOW_SYSTEM, false))
            (application as App).setupTheme(false)
        }
        //投屏
        mDiscoverAirPlay.setOnClickListener { startActivity(AirPlayActivity::class) }
        //存储空间
        mSettingMemory.setOnClickListener { startActivity(MemoryActivity::class) }
        //使用指南
        mSettingHelp.setOnClickListener { startActivity(HelpActivity::class) }
        //开源协议
        mSettingOpenSource.setOnClickListener { startActivity(OpenSourceActivity::class) }
        //检查更新
        val indexOf = when (Preferences.get(Preferences.Key.BOOK_CHECK_UPDATE_TYPE, 60)) {
            60 -> 2
            30 -> 1
            else -> 0
        }
        mBookCheckUpdateGroup.check(mBookCheckUpdateGroup[indexOf].id)
        mBookCheckUpdateGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val type = group.indexOfChild(group.findViewById(checkedId))
            Preferences.put(Preferences.Key.BOOK_CHECK_UPDATE_TYPE, when (type) {
                0 -> 0
                1 -> 30
                else -> 60
            })
        }
        //追更模式
        mSettingChaseUpdate.parentView.parentView.setOnClickListener { mSettingChaseUpdate.toggle() }
        mSettingChaseUpdate.setCheckedImmediatelyNoEvent(isChaseUpdateWorker())
        mSettingChaseUpdate.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                //启动任务
                startChaseUpdateWorker()
            } else if (!isChecked) {
                //取消任务
                stopChaseUpdateWorker()
            }
        }
    }

    /**
     * 改变字号
     */
    private fun changeFontSize(isChecked: Boolean) {
        SimpleDialog(this, getString(R.string.setting_simple_content)) { isOK ->
            if (isOK) {
                Preferences.put(Preferences.Key.MIDDLE_FONT_SIZE, isChecked)
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                android.os.Process.killProcess(android.os.Process.myPid())
            } else {
                mSettingFontSize.toggleNoEvent()
            }
        }.show()
    }

    /**
     * 是否有追更工作
     */
    private fun isChaseUpdateWorker(): Boolean {
        val workerInfo = WorkManager.getInstance(this).getWorkInfosByTag(CHASE_UPDATE_TAG).get()
        return (workerInfo.isNullOrEmpty() || workerInfo.all { it.state.isFinished }).not()
    }

    /**
     * 启动追更工作
     */
    private fun startChaseUpdateWorker() {
        val request = PeriodicWorkRequest.Builder(ChaseUpdateWorker::class.java, 1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                .addTag(CHASE_UPDATE_TAG)
                .build()
        WorkManager.getInstance(this).enqueue(request)
    }

    /**
     * 停止追更工作
     */
    private fun stopChaseUpdateWorker() {
        WorkManager.getInstance(this).cancelAllWorkByTag(CHASE_UPDATE_TAG)
    }

}
package cn.lightink.reader.ui.discover

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import cn.lightink.reader.R
import cn.lightink.reader.net.AirPlayService
import cn.lightink.reader.ui.base.LifecycleActivity
import kotlinx.android.synthetic.main.activity_air_play.*

class AirPlayActivity : LifecycleActivity() {

    private var binder: AirPlayService.AirPlayBinder? = null
    private val connection by lazy { buildServiceConnection() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_air_play)
        bindService(Intent(this, AirPlayService::class.java), connection, Context.BIND_AUTO_CREATE)
        ContextCompat.startForegroundService(this, Intent(this, AirPlayService::class.java))
        mAirPlayShutdown.setOnClickListener { onBackPressed() }
    }

    private fun buildServiceConnection() = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) = Unit
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binder = service as? AirPlayService.AirPlayBinder
            binder?.hostLiveData()?.observe(this@AirPlayActivity, Observer { mAirPlayIPAddress.text = it })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binder?.stop()
        unbindService(connection)
    }
}
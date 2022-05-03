package cn.lightink.reader.widget

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.AttributeSet
import cn.lightink.reader.R

class BatteryView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : androidx.appcompat.widget.AppCompatImageView(context, attrs, defStyleAttr) {

    private var status: Int = 0

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.run {
            status = if (getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING || getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_FULL) {
                //充电中
                R.drawable.ic_battery_charging
            } else {
                val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1) / getIntExtra(BatteryManager.EXTRA_SCALE, -1).toFloat()
                when {
                    level < .1F -> R.drawable.ic_battery_0
                    level < .3F -> R.drawable.ic_battery_20
                    level < .4F -> R.drawable.ic_battery_40
                    level < .7F -> R.drawable.ic_battery_60
                    level < .9F -> R.drawable.ic_battery_80
                    else -> R.drawable.ic_battery_100
                }
            }
        }
        setImageResource(status)
    }
}
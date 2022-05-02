package cn.lightink.reader.ktx

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.view.View
import kotlin.reflect.KClass

fun Activity.openFullscreen() {
    window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.TRANSPARENT
}

//亮度
var Activity.windowBrightness
    get() = window.attributes.screenBrightness
    set(brightness) {
        window.attributes = window.attributes.apply {
            screenBrightness = if (brightness > 1.0 || brightness < 0) -1.0F else brightness
        }
    }

/**
 * 启动页面
 */
fun Activity.startActivity(target: KClass<*>) = startActivity(Intent(this, target.java))

fun Activity.startActivityForResult(target: KClass<*>) {
    startActivityForResult(Intent(this, target.java), Activity.RESULT_FIRST_USER)
}


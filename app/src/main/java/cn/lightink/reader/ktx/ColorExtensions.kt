package cn.lightink.reader.ktx

import android.content.res.ColorStateList
import android.graphics.Color

/**
 * 颜色透明度
 */
fun Int.alpha(alpha: Int) = Color.argb(alpha, Color.red(this), Color.green(this), Color.blue(this))

fun Int.toColorStateList() = ColorStateList.valueOf(this)
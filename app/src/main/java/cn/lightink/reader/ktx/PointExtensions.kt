package cn.lightink.reader.ktx

import android.graphics.PointF
import kotlin.math.abs

/**
 * 判断2个点是否不同
 */
fun PointF.equal(x: Float, y: Float, basis: Int) : Boolean {
    return abs(this.x - x) < basis && abs(this.y - y) < basis
}
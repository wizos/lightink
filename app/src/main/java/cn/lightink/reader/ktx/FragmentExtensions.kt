package cn.lightink.reader.ktx

import androidx.fragment.app.Fragment
import kotlin.reflect.KClass

fun Fragment.startActivity(target: KClass<*>) {
    activity?.startActivity(target)
}
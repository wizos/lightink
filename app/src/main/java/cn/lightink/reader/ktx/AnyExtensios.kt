package cn.lightink.reader.ktx

import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import com.google.gson.GsonBuilder

/**
 * 利用Gson将实体类转化为Json字符串
 */
fun Any.toJson(isPretty: Boolean = false) = GsonBuilder().disableHtmlEscaping().apply {
    if (isPretty) setPrettyPrinting()
}.create().toJson(this).orEmpty()

/**
 * 利用Gson将Json字符串转化为实体类
 */
inline fun <reified T> String.fromJson() = GsonBuilder().disableHtmlEscaping().create().fromJson<T>(this, T::class.java)!!

/**
 * 取色
 */
fun Drawable.dominant(callback: (Palette.Swatch) -> Unit) {
    Palette.from(toBitmap()).generate { palette ->
        palette?.dominantSwatch?.run { callback.invoke(this) }
    }
}
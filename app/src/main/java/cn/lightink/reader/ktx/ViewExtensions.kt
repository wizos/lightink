package cn.lightink.reader.ktx

import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.view.children
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textview.MaterialTextView

val View.parentView: View
    get() = parent as View

fun View.px(dp: Int) = (dp * context.resources.displayMetrics.density + 0.5F).toInt()

fun View.px(dp: Float) = dp * context.resources.displayMetrics.density + 0.5F

fun EditText.change(callback: (text: String) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) = Unit
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            callback.invoke(s.toString())
        }
    })
}

/**
 * 获取TabLayout.Tab中的TextView
 */
fun TabLayout.Tab?.setTypeface(typeface: Typeface) {
    this?.view?.children?.firstOrNull { it is MaterialTextView }?.run {
        (this as MaterialTextView).includeFontPadding = false
        this.typeface = typeface
    }
}

/**
 * 设置左icon
 */
fun TextView.setDrawableStart(@DrawableRes drawableRes: Int) {
    val drawable = if (drawableRes != 0) context.getDrawable(drawableRes) else null
    drawable?.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
    setCompoundDrawables(drawable, compoundDrawables[1], compoundDrawables[2], compoundDrawables[3])
}

/**
 * 设置右icon
 */
fun TextView.setDrawableEnd(@DrawableRes drawableRes: Int) {
    val drawable = if (drawableRes != 0) context.getDrawable(drawableRes) else null
    drawable?.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
    setCompoundDrawables(compoundDrawables[0], compoundDrawables[1], drawable, compoundDrawables[3])
}


/**
 * 图片着色
 *
 * @param color 色值
 */
fun Drawable.tint(color: Int): Drawable {
    return mutate().apply { setTint(color) }
}
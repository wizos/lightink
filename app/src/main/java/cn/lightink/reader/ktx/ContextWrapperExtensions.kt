package cn.lightink.reader.ktx

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.util.TypedValue
import android.widget.Toast
import androidx.annotation.StringRes
import cn.lightink.reader.R
import cn.lightink.reader.module.TOAST_TYPE_FAILURE
import cn.lightink.reader.module.TOAST_TYPE_SUCCESS
import cn.lightink.reader.module.TOAST_TYPE_WARNING
import es.dmoral.toasty.Toasty

/**
 * 对话框
 */
fun ContextWrapper.dialog(message: String, button: String = "确认", callback: () -> Unit) {
    val dialog = AlertDialog.Builder(this).setMessage(message).setPositiveButton(button) { _, _ ->
        callback.invoke()
    }.create()
    dialog.window?.setDimAmount(0.2F)
    dialog.setOnShowListener {
        val positive = dialog.getButton(Dialog.BUTTON_POSITIVE)
        positive.paint.isFakeBoldText = true
        positive.setBackgroundResource(R.drawable.selected_background)
        positive.setTextColor(getColor(R.color.colorTitle))
        positive.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15F)
    }
    dialog.show()
}

/**
 * dp to px
 */
fun Context.px(dp: Int) = (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics)).toInt()

/**
 * 弹出TOAST
 */
fun Context.toast(@StringRes message: Int, type: Int = TOAST_TYPE_WARNING) {
    toast(getString(message), type)
}

/**
 * 弹出TOAST
 */
fun Context.toast(message: String, type: Int = TOAST_TYPE_WARNING) {
    when (type) {
        TOAST_TYPE_SUCCESS -> Toasty.success(this, message, Toast.LENGTH_SHORT, true).show()
        TOAST_TYPE_FAILURE -> Toasty.error(this, message, Toast.LENGTH_SHORT, true).show()
        TOAST_TYPE_WARNING -> Toasty.warning(this, message, Toast.LENGTH_SHORT, true).show()
        else -> Toasty.info(this, message, Toast.LENGTH_SHORT, true).show()
    }
}
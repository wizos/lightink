package cn.lightink.reader.widget

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import androidx.appcompat.widget.AppCompatEditText

/**
 * @class ImeEditText
 *
 * 当输入框获取焦点显示软键盘时可通过onKeyImeCallback获得OnKeyDown事件
 */
class ImeEditText @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : AppCompatEditText(context, attrs, defStyleAttr) {

    private var onKeyImeCallback: ((Int, KeyEvent?) -> Boolean)? = null

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        gravity = Gravity.CENTER_VERTICAL
    }

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent?): Boolean {
        return if (onKeyImeCallback != null) onKeyImeCallback!!.invoke(keyCode, event) else super.onKeyPreIme(keyCode, event)
    }
}
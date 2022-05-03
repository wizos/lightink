package cn.lightink.reader.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.Checkable
import androidx.appcompat.widget.AppCompatImageView

class StateImageView : AppCompatImageView, Checkable {

    private var checked = false

    constructor(context: Context, attrs: AttributeSet? = null) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr) {
        checked = attrs?.getAttributeBooleanValue("http://schemas.android.com/apk/res/android", "checked", false) ?: false
    }

    override fun isChecked(): Boolean {
        return checked
    }

    override fun setChecked(checked: Boolean) {
        this.checked = checked
        refreshDrawableState()
    }

    override fun toggle() {
        this.checked = !this.checked
    }

    override fun onCreateDrawableState(extraSpace: Int): IntArray {
        val drawableState = super.onCreateDrawableState(extraSpace + 1)
        if (checked) mergeDrawableStates(drawableState, CHECKED_STATE_SET)
        return drawableState
    }

    companion object {
        private val CHECKED_STATE_SET = intArrayOf(android.R.attr.state_checked)
    }
}
package cn.lightink.reader.widget

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/**
 * @class BoldTextView
 *
 * 画笔加粗
 */
class BoldTextView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : AppCompatTextView(context, attrs, defStyleAttr) {
    init {
        paint.isFakeBoldText = true
        includeFontPadding = false
    }
}
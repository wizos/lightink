package cn.lightink.reader.widget

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.setMargins
import cn.lightink.reader.R
import cn.lightink.reader.ktx.parentView
import cn.lightink.reader.module.FontModule
import com.scwang.smartrefresh.layout.SmartRefreshLayout
import com.scwang.smartrefresh.layout.api.RefreshLayout
import com.scwang.smartrefresh.layout.constant.RefreshState
import com.scwang.smartrefresh.layout.internal.InternalAbstract

class FlexLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : SmartRefreshLayout(context, attrs) {

}

class BookmarkHeader @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : InternalAbstract(context, attrs, defStyleAttr) {

    var title: TextView? = null
    var image: ImageView? = null
    var exist = false

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.layout_bookmark_header, this, false)
        image = view.findViewById(R.id.image)
        title = view.findViewById(R.id.title)
        title?.typeface = FontModule.mCurrentFont.typeface
        addView(view)
    }

    fun setMargin(margin: Int) {
        (title?.parentView?.layoutParams as? LayoutParams)?.setMargins((margin * 0.8F).toInt())
    }

    fun setTint(backgroundColor: Int, titleColor: Int) {
        title?.parentView?.backgroundTintList = ColorStateList.valueOf(backgroundColor)
        title?.setTextColor(titleColor)
        image?.imageTintList = ColorStateList.valueOf(titleColor)
    }

    override fun onFinish(refreshLayout: RefreshLayout, success: Boolean) = 0

    override fun onStateChanged(refreshLayout: RefreshLayout, oldState: RefreshState, newState: RefreshState) {
        when (newState) {
            RefreshState.PullDownToRefresh -> {
                title?.text = if (exist) "下拉移除书签" else "下拉添加书签"
                image?.rotation = 0F
            }
            RefreshState.ReleaseToRefresh -> {
                title?.text = if (exist) "松手移除书签" else "松手添加书签"
                image?.animate()?.rotation(180F)
            }
            else -> Unit
        }
    }
}
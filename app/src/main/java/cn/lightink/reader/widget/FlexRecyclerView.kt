package cn.lightink.reader.widget

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager

class FlexRecyclerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : RecyclerView(context, attrs, defStyleAttr) {

    private var state = STATE_IDLE
    private var onLoadMoreListener: (() -> Unit)? = null
    private val onLoadMoreScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (onLoadMoreListener != null && dy > 0 && state == STATE_IDLE) autoLoadMore()
        }
    }

    init {
        addOnScrollListener(onLoadMoreScrollListener)
    }

    fun setOnLoadMoreListener(listener: (() -> Unit)?) {
        onLoadMoreListener = listener
        post { autoLoadMore() }
    }

    /**
     * 结束加载
     */
    fun finishLoadMore(isEnd: Boolean = false) {
        postDelayed({ state = if (isEnd) STATE_NONE else STATE_IDLE }, 200)
    }

    /**
     * 自动加载更多
     */
    private fun autoLoadMore() {
        if (state != STATE_IDLE || onLoadMoreListener == null) return
        state = STATE_LOAD
        val laveItemCount = when (val layout = layoutManager) {
            is LinearLayoutManager -> layout.itemCount - layout.findLastVisibleItemPosition()
            is GridLayoutManager -> layout.itemCount - layout.findLastVisibleItemPosition()
            is StaggeredGridLayoutManager -> layout.itemCount - IntArray(layout.spanCount).apply {
                layout.findLastVisibleItemPositions(this)
            }.first()
            else -> Int.MAX_VALUE
        }
        if (laveItemCount > 3) {
            state = STATE_IDLE
            return
        }
        onLoadMoreListener?.invoke()
    }

    companion object {
        private const val STATE_IDLE = 0
        private const val STATE_LOAD = 1
        private const val STATE_NONE = 2
    }
}
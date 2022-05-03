package cn.lightink.reader.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import androidx.annotation.DimenRes
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import cn.lightink.reader.R
import kotlin.math.roundToInt

class VerticalDividerItemDecoration(context: Context, @DimenRes val margin: Int) : DividerItemDecoration(context, RecyclerView.VERTICAL) {

    private val mBounds = Rect()
    private val mDivider = context.getDrawable(R.drawable.bg_divider_item_decoration)!!
    private val mMargin = context.resources.getDimensionPixelSize(margin)

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (parent.layoutManager == null) {
            return
        }
        draw(c, parent)
    }

    private fun draw(canvas: Canvas, parent: RecyclerView) {
        canvas.save()
        val left: Int
        val right: Int
        if (parent.clipToPadding) {
            left = parent.paddingLeft + mMargin
            right = parent.width - parent.paddingRight - mMargin
            canvas.clipRect(left, parent.paddingTop, right, parent.height - parent.paddingBottom)
        } else {
            left = mMargin
            right = parent.width - mMargin
        }
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            parent.getDecoratedBoundsWithMargins(child, mBounds)
            val bottom = mBounds.bottom + child.translationY.roundToInt()
            val top = bottom - mDivider.intrinsicHeight
            mDivider.setBounds(left, top, right, bottom)
            mDivider.draw(canvas)
        }
        canvas.restore()
    }
}
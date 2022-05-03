package cn.lightink.reader.ktx

import androidx.recyclerview.widget.RecyclerView

fun RecyclerView.notifyItemAllChanged() {
    adapter?.notifyItemRangeChanged(0, adapter!!.itemCount)
}

//清除动画
fun RecyclerView.clearAnimator() {
    itemAnimator?.addDuration = 0
    itemAnimator?.changeDuration = 0
    itemAnimator?.moveDuration = 0
    itemAnimator?.removeDuration = 0
    (itemAnimator as androidx.recyclerview.widget.SimpleItemAnimator).supportsChangeAnimations = false
}
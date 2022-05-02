package cn.lightink.reader.module

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.*
import androidx.recyclerview.widget.ListAdapter

class BaseAdapter<T>(val list: MutableList<T>, @LayoutRes private val layoutResId: Int, private val onBindImpl: (item: VH, T) -> Unit) : RecyclerView.Adapter<VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(LayoutInflater.from(parent.context).inflate(layoutResId, parent, false))
    }

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        onBindImpl.invoke(holder, list[position])
    }

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        onBindImpl.invoke(holder, list[position])
    }

}

class PageListAdapter<T>(@LayoutRes private val layoutResId: Int, equalContent: (T, T) -> Boolean = { old, new -> old == new }, equalItem: (T, T) -> Boolean = { old, new -> old == new }, private val onBindImpl: (item: VH, T?) -> Unit) : PagedListAdapter<T, VH>(ItemDiffUtil<T>(equalItem, equalContent)) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(LayoutInflater.from(parent.context).inflate(layoutResId, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        onBindImpl.invoke(holder, getItem(position))
    }

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        onBindImpl.invoke(holder, getItem(position))
    }

}

class ListAdapter<T>(@LayoutRes private val layoutResId: Int, equalContent: (T, T) -> Boolean = { old, new -> old == new }, equalItem: (T, T) -> Boolean = { old, new -> old == new }, private val onBindImpl: (item: VH, T) -> Unit) : ListAdapter<T, VH>(ItemDiffUtil<T>(equalItem, equalContent)) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(LayoutInflater.from(parent.context).inflate(layoutResId, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        onBindImpl.invoke(holder, getItem(position))
    }

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        onBindImpl.invoke(holder, getItem(position))
    }

}

class VH(val view: View) : RecyclerView.ViewHolder(view)

class ItemDiffUtil<T>(private val equalItem: (T, T) -> Boolean, private val equalContent: (T, T) -> Boolean) : DiffUtil.ItemCallback<T>() {

    override fun areItemsTheSame(oldItem: T, newItem: T): Boolean {
        return equalItem.invoke(oldItem, newItem)
    }

    override fun areContentsTheSame(oldItem: T, newItem: T): Boolean {
        return equalContent.invoke(oldItem, newItem)
    }

}

class ItemDiffCallback<T>(private val oldList: List<T>, private val newList: List<T>, val equalItem: (T, T) -> Boolean, val equalContent: (T, T) -> Boolean) : DiffUtil.Callback() {

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) = equalItem.invoke(oldList[oldItemPosition], newList[newItemPosition])

    override fun getOldListSize() = oldList.size

    override fun getNewListSize() = newList.size

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) = equalContent.invoke(oldList[oldItemPosition], newList[newItemPosition])

}

class RVGridLayoutManager(context: Context?, spanCount: Int) : GridLayoutManager(context, spanCount) {

    override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
        try {
            super.onLayoutChildren(recycler, state)
        } catch (e: Exception) {
        }
    }
}

class RVLinearLayoutManager(context: Context?, orientation: Int, reverseLayout: Boolean) : LinearLayoutManager(context, orientation, reverseLayout) {

    var scrollable = true

    constructor(context: Context?) : this(context, VERTICAL, false)

    constructor(context: Context?, orientation: Int) : this(context, orientation, false)

    override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
        try {
            super.onLayoutChildren(recycler, state)
        } catch (e: Exception) {
        }
    }

    override fun canScrollHorizontally(): Boolean {
        return scrollable && orientation == HORIZONTAL
    }

    override fun canScrollVertically(): Boolean {
        return scrollable && orientation == VERTICAL
    }

}
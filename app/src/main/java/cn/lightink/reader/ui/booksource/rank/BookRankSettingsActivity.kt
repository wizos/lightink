package cn.lightink.reader.ui.booksource.rank

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import cn.lightink.reader.R
import cn.lightink.reader.controller.BookRankController
import cn.lightink.reader.model.BookRank
import cn.lightink.reader.module.ListAdapter
import cn.lightink.reader.module.RVLinearLayoutManager
import cn.lightink.reader.ui.base.LifecycleActivity
import kotlinx.android.synthetic.main.activity_book_rank_settings.*
import kotlinx.android.synthetic.main.item_book_rank_list.view.*
import java.util.*

class BookRankSettingsActivity : LifecycleActivity() {

    private val controller by lazy { ViewModelProvider(this)[BookRankController::class.java] }
    private val mItemTouchHelper by lazy { ItemTouchHelper(itemTouchHelperCallback) }
    private val ranks = mutableListOf<BookRank>()
    private var isUpdated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_rank_settings)
        ranks.addAll(controller.getBookRanks())
        mBookRankSettingsRecycler.layoutManager = RVLinearLayoutManager(this)
        mBookRankSettingsRecycler.adapter = adapter.apply { submitList(ranks) }
        mItemTouchHelper.attachToRecyclerView(mBookRankSettingsRecycler)
    }

    //构建数据适配器
    private val adapter = ListAdapter<BookRank>(R.layout.item_book_rank_list) { item, bookRank ->
        item.view.mBookRankTitle.text = bookRank.name
        item.view.mBookRankVisible.isChecked = bookRank.visible
        item.view.mBookRankVisible.setOnCheckedChangeListener { _, isChecked ->
            bookRank.visible = isChecked
            isUpdated = true
        }
        item.view.mBookRankTouch.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                mItemTouchHelper.startDrag(item)
            }
            return@setOnTouchListener false
        }
    }

    //构建拖拽回调
    private val itemTouchHelperCallback = object : ItemTouchHelper.Callback() {

        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
            return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
        }

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            val fromPosition = viewHolder.adapterPosition
            val toPosition = target.adapterPosition
            val next = if (fromPosition > toPosition) -1 else 1
            for (position in if (fromPosition > toPosition) fromPosition downTo toPosition + 1 else fromPosition until toPosition) {
                (ranks[position].timestamp to ranks[position + next].timestamp).run {
                    ranks[position].timestamp = second
                    ranks[position + next].timestamp = first
                }
                Collections.swap(ranks, position, position + next)
            }
            recyclerView.adapter?.notifyItemMoved(fromPosition, toPosition)
            isUpdated = true
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        }

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState != ItemTouchHelper.ACTION_STATE_IDLE && viewHolder != null) {
                viewHolder.itemView.setBackgroundColor(getColor(R.color.colorForeground))
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            viewHolder.itemView.setBackgroundColor(Color.TRANSPARENT)
        }

        override fun isLongPressDragEnabled(): Boolean {
            return false
        }
    }

    override fun onBackPressed() {
        if (isUpdated) {
            controller.updateBookRanks(ranks)
            setResult(Activity.RESULT_OK)
        }
        super.onBackPressed()
    }
}
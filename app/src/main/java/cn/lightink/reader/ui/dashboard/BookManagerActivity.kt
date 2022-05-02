package cn.lightink.reader.ui.dashboard

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import cn.lightink.reader.R
import cn.lightink.reader.controller.MainController
import cn.lightink.reader.model.Bookshelf
import cn.lightink.reader.model.StateBook
import cn.lightink.reader.module.INTENT_BOOKSHELF
import cn.lightink.reader.module.ListAdapter
import cn.lightink.reader.module.RVGridLayoutManager
import cn.lightink.reader.module.Room
import cn.lightink.reader.ui.base.BottomSelectorDialog
import cn.lightink.reader.ui.base.LifecycleActivity
import cn.lightink.reader.ui.book.BookDeleteDialog
import kotlinx.android.synthetic.main.activity_book_manager.*
import kotlinx.android.synthetic.main.item_book_manager.view.*
import java.util.*
import kotlin.math.max
import kotlin.math.min

class BookManagerActivity : LifecycleActivity() {

    private val controller by lazy { ViewModelProvider(this).get(MainController::class.java) }
    private val touchHelper by lazy { ItemTouchHelper(buildItemTouchHelper()) }
    private lateinit var bookshelf: Bookshelf
    private val adapter by lazy { buildAdapter() }
    private var books = listOf<StateBook>()

    //封面尺寸
    private var span = 3
    private var size = 0
    private val edge by lazy { calculateEdge() }
    private var isMoved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_manager)
        bookshelf = intent.getParcelableExtra(INTENT_BOOKSHELF) ?: return
        mBookManagerRecycler.setPadding(edge, edge, edge, edge)
        mBookManagerRecycler.layoutManager = RVGridLayoutManager(this, span)
        mBookManagerRecycler.adapter = adapter
        mBookManagerMove.setOnClickListener { move() }
        mBookManagerDelete.setOnClickListener { delete() }
        touchHelper.attachToRecyclerView(mBookManagerRecycler)
        controller.queryBooksByBookshelf(bookshelf).observe(this, Observer {
            books = it.map { book -> StateBook(book) }
            adapter.submitList(books)
            checkAdapterStatus()
        })
    }

    /**
     * 检查选中状态
     */
    private fun checkAdapterStatus() {
        val checkedCount = books.count { it.checked }
        mTopbar.text = if (checkedCount > 0) getString(R.string.book_select_count, checkedCount) else bookshelf.name
        mBookManagerMove.isEnabled = checkedCount > 0 && Room.bookshelf().count() > 1
        mBookManagerMove.alpha = if (mBookManagerMove.isEnabled) 1F else 0.2F
        mBookManagerDelete.isEnabled = checkedCount > 0
        mBookManagerDelete.alpha = if (mBookManagerDelete.isEnabled) 1F else 0.2F
    }

    /**
     * 移动选中图书
     */
    private fun move() = books.filter { it.checked }.run {
        if (isEmpty()) return
        BottomSelectorDialog(this@BookManagerActivity, getString(R.string.select_bookshelf), Room.bookshelf().getAllImmediately().filter { it.id != controller.bookshelfLive.value?.id }) { it.name }.callback { bookshelf ->
            controller.moveBooks(this.map { it.book }, bookshelf)
        }.show()
    }

    /**
     * 删除选中图书
     */
    private fun delete() = books.filter { it.checked }.run {
        if (isEmpty()) return
        BookDeleteDialog(this@BookManagerActivity) { withResource ->
            controller.deleteBooks(this.map { it.book }, withResource)
        }.show()
    }

    /**
     * 计算边缘
     */
    private fun calculateEdge(): Int {
        val width = resources.displayMetrics.widthPixels
        size = min(resources.getDimensionPixelSize(R.dimen.dimenBookshelfCoverSize), (width * 0.24444444).toInt())
        span = max(span, width / (size + resources.getDimensionPixelSize(R.dimen.dimen4x)))
        return (width - size * span) / (span * 2 + 2)
    }

    /**
     * 构建网格数据适配器
     */
    private fun buildAdapter() = ListAdapter<StateBook>(R.layout.item_book_manager) { item, book ->
        item.view.setPadding(edge, 0, edge, edge * 2)
        item.view.mBookCover.layoutParams.width = size
        item.view.mBookCover.layoutParams.height = (size * 1.4F).toInt()
        item.view.mBookCover.privacy().stroke().hint(book.book.name).load(book.book.cover)
        item.view.mBookOverlayView.layoutParams = item.view.mBookCover.layoutParams
        item.view.mBookOverlayView.isVisible = book.checked
        item.view.mBookCheckBox.isChecked = book.checked
        item.view.setOnClickListener {
            book.checked = book.checked.not()
            mBookManagerRecycler.adapter?.notifyItemChanged(item.adapterPosition, book)
            checkAdapterStatus()
        }
    }

    /**
     * 构建ItemTouchHelper
     */
    private fun buildItemTouchHelper() = object : ItemTouchHelper.Callback() {

        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
            return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0)
        }

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            val fromPosition = viewHolder.adapterPosition
            val toPosition = target.adapterPosition
            val next = if (fromPosition > toPosition) -1 else 1
            if (controller.bookshelfLive.value?.sort == 1) {
                //拖拽排序
                for (position in if (fromPosition > toPosition) fromPosition downTo toPosition + 1 else fromPosition until toPosition) {
                    (books[position].book.createdAt to books[position + next].book.createdAt).run {
                        books[position].book.createdAt = second
                        books[position + next].book.createdAt = first
                    }
                    Collections.swap(books, position, position + next)
                }
            } else {
                //最后阅读时间排序
                for (position in if (fromPosition > toPosition) fromPosition downTo toPosition + 1 else fromPosition until toPosition) {
                    (books[position].book.updatedAt to books[position + next].book.updatedAt).run {
                        books[position].book.updatedAt = second
                        books[position + next].book.updatedAt = first
                    }
                    Collections.swap(books, position, position + next)
                }
            }
            adapter.notifyItemMoved(fromPosition, toPosition)
            isMoved = true
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

    }

    override fun onDestroy() {
        super.onDestroy()
        //退出页面是更新并保存顺序
        if (isMoved) {
            Room.book().update(*books.map { it.book }.toTypedArray())
        }
    }
}
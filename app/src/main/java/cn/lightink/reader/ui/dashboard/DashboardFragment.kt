package cn.lightink.reader.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import cn.lightink.reader.R
import cn.lightink.reader.controller.MainController
import cn.lightink.reader.ktx.dialog
import cn.lightink.reader.ktx.setDrawableStart
import cn.lightink.reader.ktx.toast
import cn.lightink.reader.model.Book
import cn.lightink.reader.model.Bookshelf
import cn.lightink.reader.module.*
import cn.lightink.reader.ui.base.BottomSelectorDialog
import cn.lightink.reader.ui.base.LifecycleFragment
import cn.lightink.reader.ui.bookshelf.BookshelfEditActivity
import cn.lightink.reader.ui.reader.ReaderActivity
import kotlinx.android.synthetic.main.fragment_dashboard.view.*
import kotlinx.android.synthetic.main.item_dashboard_book.view.*
import kotlinx.android.synthetic.main.item_dashboard_bookshelf.view.*

class DashboardFragment : LifecycleFragment() {

    private val controller by lazy { ViewModelProvider(requireActivity())[MainController::class.java] }
    private val adapter by lazy { buildAdapter() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.mDashboardCreate.setOnClickListener { openEditActivity() }
        view.mDashboardRecycler.layoutManager = RVLinearLayoutManager(requireContext())
        view.mDashboardRecycler.adapter = adapter
        controller.bookshelfLive.observe(viewLifecycleOwner, Observer { adapter.notifyDataSetChanged() })
        controller.queryBookshelves().observe(viewLifecycleOwner, Observer { adapter.submitList(it) })
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildAdapter() = ListAdapter<Bookshelf>(R.layout.item_dashboard_bookshelf, equalItem = { old, new -> old.id == new.id }) { item, bookshelf ->
        item.view.mBookshelfName.text = bookshelf.name
        item.view.mBookshelfName.setDrawableStart(if (bookshelf.id == controller.bookshelfLive.value?.id) R.drawable.ic_dashboard_bookshelf_drawer_opened else R.drawable.ic_dashboard_bookshelf_drawer)
        item.view.mBookRecycler.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        val bindingId = item.view.mBookRecycler.tag as? Long
        if (bindingId != bookshelf.id) {
            item.view.mBookRecycler.adapter = buildChildAdapter().apply {
                controller.queryBooksByBookshelf(bookshelf).observe(viewLifecycleOwner, Observer { books ->
                    submitList(books.sortedByDescending { it.state }) { item.view.mBookRecycler.scrollToPosition(0) }
                })
            }
            item.view.mBookRecycler.tag = bookshelf.id
        }
        item.view.setOnClickListener { changedBookshelf(bookshelf) }
        item.view.mBookshelfEdit.setOnClickListener { showActionDialog(bookshelf) }
    }

    private fun buildChildAdapter() = ListAdapter<Book>(R.layout.item_dashboard_book, { old, new -> old.same(new) }, { old, new -> old.objectId == new.objectId }) { item, book ->
        item.view.mBookCover.radius(1F).hint(book.name).load(book.cover)
        item.view.mBookCover.setOnClickListener { v -> openBook(v, book) }
        item.view.mBookState.isVisible = book.state == BOOK_STATE_UPDATE
    }

    private fun changedBookshelf(bookshelf: Bookshelf) {
        if (controller.bookshelfLive.value?.id != bookshelf.id) {
            controller.changedBookshelf(bookshelf)
        }
    }

    /**
     * 打开图书
     */
    private fun openBook(view: View, book: Book) {
        startActivity(Intent(activity, ReaderActivity::class.java).putExtra(INTENT_BOOK, book))
    }

    /**
     * 显示菜单
     */
    private fun showActionDialog(bookshelf: Bookshelf) {
        BottomSelectorDialog(requireContext(), bookshelf.name, listOf(R.string.menu_manage_book, R.string.menu_edit_bookshelf, R.string.menu_delete_bookshelf)) { getString(it) }.callback { item ->
            when (item) {
                R.string.menu_manage_book -> openManageActivity(bookshelf)
                R.string.menu_edit_bookshelf -> openEditActivity(bookshelf)
                R.string.menu_delete_bookshelf -> deleteBookshelf(bookshelf)
            }
        }.show()
    }

    /**
     * 打开编辑页面
     */
    private fun openManageActivity(bookshelf: Bookshelf): Boolean {
        val intent = Intent(activity, BookManagerActivity::class.java)
        intent.putExtra(INTENT_BOOKSHELF, bookshelf)
        activity?.startActivity(intent)
        return true
    }

    /**
     * 删除书架
     */
    private fun deleteBookshelf(bookshelf: Bookshelf) {
        if (Room.bookshelf().count() == 1) return requireActivity().toast(R.string.bookshelf_not_delete_all)
        activity?.dialog(getString(R.string.bookshelf_delete_content, bookshelf.name)) {
            controller.deleteBookshelf(bookshelf)
            activity?.toast(R.string.bookshelf_already_delete, TOAST_TYPE_SUCCESS)
        }
    }

    /**
     * 打开编辑页面
     */
    private fun openEditActivity(bookshelf: Bookshelf? = null): Boolean {
        val intent = Intent(activity, BookshelfEditActivity::class.java)
        intent.putExtra(INTENT_BOOKSHELF, bookshelf)
        activity?.startActivity(intent)
        return true
    }

}
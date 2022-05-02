package cn.lightink.reader.ui.booksource.rank

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import cn.lightink.reader.R
import cn.lightink.reader.controller.BookRankController
import cn.lightink.reader.model.BookRank
import cn.lightink.reader.module.*
import cn.lightink.reader.module.booksource.BookSourceJson
import cn.lightink.reader.module.booksource.BookSourceParser
import cn.lightink.reader.module.booksource.SearchMetadata
import cn.lightink.reader.ui.base.BottomSelectorDialog
import cn.lightink.reader.ui.base.LifecycleFragment
import cn.lightink.reader.ui.book.BookDetailActivity
import cn.lightink.reader.widget.VerticalDividerItemDecoration
import kotlinx.android.synthetic.main.fragment_book_rank.view.*
import kotlinx.android.synthetic.main.item_book_rank_group.view.*
import kotlinx.android.synthetic.main.item_simple_book.view.*

class BookRankFragment : LifecycleFragment() {

    private val controller by lazy { ViewModelProvider(this)[BookRankController::class.java] }
    private val bookRank by lazy { arguments!!.getParcelable<BookRank>(INTENT_BOOK_RANK)!! }
    private val bookSource by lazy { Room.bookSource().get(bookRank.url)!! }
    private val bookSourceJson by lazy { bookSource.json }
    private val bookSourceParser by lazy { BookSourceParser(bookSourceJson) }
    private var group: BookSourceJson.Rank? = null
    private var category: BookSourceJson.Category? = null
    private var page = -1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_book_rank, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        group = bookSourceJson.rank.getOrElse(bookRank.preferred) { bookSourceJson.rank.firstOrNull().apply { bookRank.preferred = 0 } }
        page = group?.page ?: -1
        view.mBookRankGroupRecycler.addItemDecoration(VerticalDividerItemDecoration(view.context, R.dimen.padding_horizontal_half))
        view.mBookRankGroupRecycler.isVisible = bookSourceJson.rank.isNotEmpty()
        view.mBookRankGroupRecycler.adapter = groupAdapter.apply { submitList(bookSourceJson.rank) }
        view.mBookRankCategory.isVisible = group?.categories?.isNotEmpty() == true
        view.mBookRankCategory.setOnClickListener { showCategoryDialog() }
        if (group?.categories?.isNotEmpty() == true) {
            category = group!!.categories.firstOrNull { it.key == bookRank.category } ?: group!!.categories.firstOrNull()
            view.mBookRankCategory.text = category?.value
        }
        view.mBookRankRecycler.layoutManager = RVLinearLayoutManager(activity)
        view.mBookRankRecycler.adapter = adapter
        view.mBookRankRecycler.setOnLoadMoreListener { onLoadMore() }
    }

    private fun showCategoryDialog() {
        BottomSelectorDialog(activity!!, getString(R.string.select_category), group?.categories.orEmpty()) { it.value }.callback { selected ->
            if (category != selected) {
                bookRank.apply { category = selected.key }
                category = selected
                onRefresh()
            }
        }.show()
    }

    private fun onRefresh() {
        Room.bookRank().update(bookRank)
        view?.mBookRankCategory?.isVisible = category != null
        view?.mBookRankCategory?.text = category?.value
        page = group?.page ?: -1
        controller.refresh()
        adapter.submitList(emptyList())
        groupAdapter.notifyItemRangeChanged(0, groupAdapter.itemCount)
        onLoadMore()
    }

    private fun onLoadMore() {
        if (view?.mBookRankLoading == null) return
        view?.mBookRankLoading?.isVisible = true
        controller.loadMore(bookSourceParser, group!!, page, category?.key).observe(viewLifecycleOwner, Observer { list ->
            view?.mBookRankRecycler?.finishLoadMore(page < 0 || list.isNullOrEmpty())
            view?.mBookRankLoading?.isVisible = false
            if (list.isNotEmpty()) {
                page += (group?.unit ?: 0)
                adapter.submitList(list)
                if (list.size < group!!.size) onLoadMore()
            }
        })
    }

    private val adapter = ListAdapter<SearchMetadata>(R.layout.item_simple_book) { item, book ->
        item.view.mSimpleBookCover.hint(book.name).load(book.cover)
        item.view.mSimpleBookNo.text = (item.adapterPosition + 1).toString()
        item.view.mSimpleBookName.text = book.name
        item.view.mSimpleBookAuthor.text = book.author
        item.view.setOnClickListener { openBookDetail(item.view.mSimpleBookCover, book) }
    }

    private val groupAdapter = ListAdapter<BookSourceJson.Rank>(R.layout.item_book_rank_group) { item, rank ->
        item.view.mBookRankTitle.text = rank.title
        item.view.mBookRankTitle.paint.isFakeBoldText = item.adapterPosition == bookRank.preferred
        item.view.mBookRankTitle.setTextColor(item.view.context.getColor(if (item.adapterPosition == bookRank.preferred) R.color.colorAccent else R.color.colorContent))
        item.view.setOnClickListener {
            Room.bookRank().update(bookRank.apply { preferred = item.adapterPosition })
            group = rank
            category = rank.categories.firstOrNull()
            onRefresh()
        }
    }

    /**
     * 打开图书详情
     */
    private fun openBookDetail(view: View, metadata: SearchMetadata) {
        controller.search(metadata, bookSource).observe(this, Observer { book ->
            val intent = Intent(activity, BookDetailActivity::class.java)
            intent.putExtra(INTENT_BOOK, book?.objectId())
            val options = ActivityOptions.makeSceneTransitionAnimation(activity, view, getString(R.string.transition))
            startActivity(intent, options.toBundle())
        })
    }

    companion object {
        fun newInstance(rank: BookRank) = BookRankFragment().apply {
            arguments = Bundle().apply { putParcelable(INTENT_BOOK_RANK, rank) }
        }
    }

}
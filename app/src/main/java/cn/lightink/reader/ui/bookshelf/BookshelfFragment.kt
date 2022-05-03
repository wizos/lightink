package cn.lightink.reader.ui.bookshelf

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import cn.lightink.reader.R
import cn.lightink.reader.controller.FeedController
import cn.lightink.reader.controller.MainController
import cn.lightink.reader.ktx.alpha
import cn.lightink.reader.ktx.dominant
import cn.lightink.reader.ktx.px
import cn.lightink.reader.ktx.startActivity
import cn.lightink.reader.model.Book
import cn.lightink.reader.model.Bookshelf
import cn.lightink.reader.model.FeedGroup
import cn.lightink.reader.model.Flow
import cn.lightink.reader.module.*
import cn.lightink.reader.ui.base.BottomSelectorDialog
import cn.lightink.reader.ui.base.LifecycleFragment
import cn.lightink.reader.ui.book.BookDeleteDialog
import cn.lightink.reader.ui.book.BookSummaryActivity
import cn.lightink.reader.ui.discover.help.BookshelfHelpFragment
import cn.lightink.reader.ui.feed.FlowActivity
import cn.lightink.reader.ui.main.MainActivity
import cn.lightink.reader.ui.main.SearchActivity
import cn.lightink.reader.ui.reader.ReaderActivity
import kotlinx.android.synthetic.main.fragment_bookshelf.view.*
import kotlinx.android.synthetic.main.item_banner.view.*
import kotlinx.android.synthetic.main.item_bookshelf_grid.view.*
import kotlinx.android.synthetic.main.item_bookshelf_linear.view.*
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import kotlin.math.max
import kotlin.math.min

class BookshelfFragment : LifecycleFragment() {

    private val controller by lazy { ViewModelProvider(activity!!).get(MainController::class.java) }
    private val feedController by lazy { ViewModelProvider(this).get(FeedController::class.java) }

    //封面尺寸
    private var span = 3
    private var size = 0
    private val edge by lazy { getRecyclerItemEdge() }
    private var padding = 0

    //数据适配器
    private val books = mutableListOf<Book>()
    private var adapter = buildGridAdapter(books)
    private var isCreated = false

    //书架当前的LiveData
    private var mLiveData: LiveData<List<Book>>? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_bookshelf, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.mBookshelfName.setPadding(edge * 2, 0, edge * 2, 0)
        view.mBookshelfName.setOnClickListener { (activity as? MainActivity)?.openDashboardPage() }
        view.mBookshelfSearch.setOnClickListener { startActivity(SearchActivity::class) }
        view.mBookshelfAccount.updateLayoutParams<RelativeLayout.LayoutParams> { marginEnd = edge * 2 - view.px(6) }
        view.mBookshelfAccount.setOnClickListener { (activity as? MainActivity)?.openDiscoverPage() }
        view.mBookshelfBanner.updateLayoutParams<LinearLayout.LayoutParams> {
            height = edge * 9
            marginStart = edge * 2
            marginEnd = edge * 2
        }
        view.mBookshelfFlexibleLayout.setOnRefreshListener { controller.checkBooksUpdate() }
        view.container.updateLayoutParams<RelativeLayout.LayoutParams> {
            marginStart = max(edge * 2 - view.px(22), 0)
            marginEnd = max(edge * 2 - view.px(22), 0)
        }
        //书架切换
        controller.bookshelfLive.observe(viewLifecycleOwner, Observer { setupBookshelf(it) })
        //书架检查更新
        controller.bookshelfCheckUpdateLiveData.observe(viewLifecycleOwner, Observer { view.mBookshelfFlexibleLayout.finishRefresh() })
    }

    /**
     * 设置书架
     */
    private fun setupBookshelf(bookshelf: Bookshelf) {
        view?.mBookshelfName?.text = bookshelf.name
        Preferences.put(Preferences.Key.BOOKSHELF, bookshelf.id)
        padding = if (bookshelf.layout == 0) edge else 0
        adapter = if (bookshelf.layout == 0) buildGridAdapter(books) else buildLinearAdapter(books)
        view?.mBookshelfRecycler?.layoutManager = if (bookshelf.layout == 0) RVGridLayoutManager(activity, span) else RVLinearLayoutManager(activity)
        view?.mBookshelfRecycler?.adapter = adapter
        view?.mBookshelfRecycler?.itemAnimator?.changeDuration = 0
        view?.mBookshelfRecycler?.setPadding(padding, if (bookshelf.layout == 0) resources.getDimensionPixelSize(R.dimen.padding_vertical) else 0, padding, 0)
        mLiveData?.removeObservers(viewLifecycleOwner)
        mLiveData = controller.queryBooksByBookshelf(bookshelf)
        mLiveData?.observe(viewLifecycleOwner, Observer { list ->
            calculateDiff(list)
            checkHelpView(list.isNullOrEmpty() && view?.mBookshelfBanner?.isVisible == false)
        })
        //查询书架绑定的RSS分组
        Room.feedGroup().getByBookshelf(bookshelf.id).observe(viewLifecycleOwner, Observer { feed ->
            view?.mBookshelfBanner?.isVisible = feed != null
            feed?.run { setupFeed(this) }
        })
    }

    /**
     * 设置RSS分组
     */
    private fun setupFeed(group: FeedGroup) {
        checkHelpView(false)
        Room.feed().getByGroupId(group.id).observe(viewLifecycleOwner, Observer { feeds ->
            Room.flow().getForBookshelf(feeds.map { it.id }).observe(viewLifecycleOwner, Observer { results ->
                val flows = if (results.isNullOrEmpty()) listOf(Flow(EMPTY, "${group.name} | 暂无内容", EMPTY, EMPTY, null, 0, 0, EMPTY)) else results
                view?.mBookshelfBanner?.setData(R.layout.item_banner, flows, arrayListOf())
                view?.mBookshelfBanner?.setAdapter { _, itemView, flow, _ ->
                    itemView.mBannerTitle.text = (flow as Flow).title
                    itemView.mBannerCover.radius(1F).load(if (flow.cover.isNullOrBlank()) R.drawable.rss_none else flow.cover) { drawable ->
                        drawable.dominant { dominant ->
                            //防止取色返回结果时因页面关闭引起的IllegalStateException
                            if (context == null) return@dominant
                            itemView.mBannerOverlay.background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(dominant.rgb, dominant.rgb.alpha(200), dominant.rgb.alpha(60))).apply { cornerRadius = resources.getDimension(R.dimen.dimen1) }
                            itemView.mBannerTitle.setTextColor(dominant.bodyTextColor)
                        }
                    }
                }
                view?.mBookshelfBanner?.setDelegate { _, _, flow, _ ->
                    startActivity(Intent(activity, FlowActivity::class.java).putExtra(INTENT_FEED_GROUP, group.id).putExtra(INTENT_FEED_FLOW, (flow as Flow).link))
                }
            })
        })
    }

    /**
     * 更新数据适配器
     */
    private fun calculateDiff(list: List<Book>) {
        val diff = DiffUtil.calculateDiff(ItemDiffCallback(books, list, equalItem = { old, new -> old.objectId == new.objectId }) { old, new -> old.same(new) })
        books.clear()
        books.addAll(list)
        diff.dispatchUpdatesTo(adapter)
    }

    /**
     * 检查是否显示使用指南
     */
    private fun checkHelpView(isVisible: Boolean) {
        view?.container?.isVisible = isVisible
        val transaction = childFragmentManager.beginTransaction()
        if (isVisible && childFragmentManager.fragments.isEmpty()) {
            transaction.replace(R.id.container, BookshelfHelpFragment()).commitAllowingStateLoss()
        } else if (!isVisible && childFragmentManager.fragments.isNotEmpty()) {
            transaction.remove(childFragmentManager.fragments.first()).commitAllowingStateLoss()
        }
    }

    /**
     * 计算边缘
     */
    private fun getRecyclerItemEdge(): Int {
        val width = resources.displayMetrics.widthPixels
        size = min(resources.getDimensionPixelSize(R.dimen.dimenBookshelfCoverSize), (width * 0.24444444).toInt())
        span = max(3, width / (size + resources.getDimensionPixelSize(R.dimen.dimen4x)))
        return (width - size * span) / (span * 2 + 2)
    }

    /**
     * 构建网格数据适配器
     */
    private fun buildGridAdapter(list: MutableList<Book>) = BaseAdapter(list, R.layout.item_bookshelf_grid) { item, book ->
        item.view.setPadding(edge, 0, edge, edge * 2)
        item.view.mBookGridCoverLayout.layoutParams.width = size
        item.view.mBookGridCoverLayout.layoutParams.height = (size * 1.4F).toInt()
        item.view.mBookGridName.text = book.name
        item.view.mBookGridName.isVisible = controller.bookshelfLive.value?.info == 1
        item.view.mBookGridState.setImageResource(book.stateResId)
        item.view.mBookGridState.isVisible = book.stateResId != 0
        item.view.mBookGridCover.privacy().force().stroke().hint(book.name).load(book.cover)
        item.view.setOnLongClickListener { onBookLongClicked(book) }
        item.view.setOnClickListener { openBook(item.view.mBookGridCover, book) }
    }

    /**
     * 构建线性数据适配器
     */
    private fun buildLinearAdapter(list: MutableList<Book>) = BaseAdapter(list, R.layout.item_bookshelf_linear) { item, book ->
        item.view.setPadding(edge * 2, edge, edge * 2, edge)
        item.view.mBookLinearName.text = book.name
        item.view.mBookLinearState.text = book.status
        item.view.mBookLinearState.isVisible = book.status.isNotBlank()
        item.view.mBookLinearState.setBackgroundResource(book.statusResId)
        item.view.mBookLinearAuthor.text = book.author
        item.view.mBookLinearStatus.text = when (book.state) {
            BOOK_STATE_END -> getString(R.string.book_end, book.lastChapter)
            else -> getString(R.string.book_not_end, book.lastChapter)
        }
        item.view.mBookLinearCover.privacy().stroke().hint(book.name).load(book.cover)
        item.view.mBookLinearProgress.text = run {
            val progress = if (book.chapter == 0) getString(R.string.bookshelf_progress_none) else getString(R.string.bookshelf_progress, min((book.chapter + 1) / max(1, book.catalog).toFloat() * 100, 100F))
            when {
                controller.bookshelfLive.value?.info != 1 -> progress
                book.catalog <= book.chapter + 1 -> getString(R.string.bookshelf_progress, 100F)
                else -> getString(R.string.bookshelf_progress_full, progress, book.catalog - book.chapter - 1)
            }
        }
        item.view.setOnLongClickListener { onBookLongClicked(book) }
        item.view.setOnClickListener { openBook(item.view.mBookLinearCover, book) }
    }

    /**
     * 打开图书
     */
    private fun openBook(view: View, book: Book) {
        startActivity(Intent(activity, ReaderActivity::class.java).putExtra(INTENT_BOOK, book))
    }

    /**
     * Item长按事件
     */
    private fun onBookLongClicked(book: Book): Boolean {
        BottomSelectorDialog(requireContext(), "《${book.name}》${book.author}", listOf(R.string.menu_summary, R.string.menu_move_bookshelf, R.string.menu_delete_book)) { getString(it) }.callback { item ->
            when (item) {
                //编辑
                R.string.menu_summary -> startActivity(Intent(activity, BookSummaryActivity::class.java).putExtra(INTENT_BOOK, book))
                //移动
                R.string.menu_move_bookshelf -> BottomSelectorDialog(requireActivity(), getString(R.string.select_bookshelf), Room.bookshelf().getAllImmediately().filter { it.id != controller.bookshelfLive.value?.id }) { it.name }.callback { bookshelf ->
                    controller.moveBooks(listOf(book), bookshelf)
                }.show()
                //删除
                R.string.menu_delete_book -> BookDeleteDialog(requireActivity()) { withResource ->
                    controller.deleteBooks(listOf(book), withResource)
                }.show()
            }
        }.show()
        return true
    }

    override fun onResume() {
        super.onResume()
        checkBooksUpdate()
    }

    private fun checkBooksUpdate() {
        //检查图书更新
        var checkUpdateTime = controller.getBookCheckUpdateType()
        if (checkUpdateTime in 1..30) checkUpdateTime = 30
        if ((!isCreated && 0 == checkUpdateTime) || checkUpdateTime > 0 && System.currentTimeMillis() - Preferences.get(Preferences.Key.BOOK_CHECK_UPDATE_TIME, 0L) >= checkUpdateTime * 60 * 1000) {
            isCreated = true
            Preferences.put(Preferences.Key.BOOK_CHECK_UPDATE_TIME, System.currentTimeMillis())
            controller.checkBooksUpdate()
            feedController.checkUpdate()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: Notify.Event) {
        when (event) {
            //当封面切换是自动刷新封面
            is Notify.BookCoverChangedEvent -> {
                val indexOf = books.indexOfFirst { it.objectId == event.book.objectId }
                if (indexOf > -1) {
                    adapter.notifyItemChanged(indexOf, books[indexOf])
                }
            }
        }
    }

}
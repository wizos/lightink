package cn.lightink.reader.ui.reader

import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PointF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.*
import android.webkit.URLUtil
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.util.containsKey
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import cn.lightink.reader.R
import cn.lightink.reader.controller.ReaderController
import cn.lightink.reader.ktx.*
import cn.lightink.reader.model.*
import cn.lightink.reader.module.*
import cn.lightink.reader.module.booksource.BookSourceParser
import cn.lightink.reader.ui.base.LifecycleActivity
import cn.lightink.reader.ui.booksource.BookSourceAuthActivity
import cn.lightink.reader.ui.reader.popup.ReaderPurifyCreateDialog
import cn.lightink.reader.widget.ImageUriView
import cn.lightink.reader.widget.JustifyView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.*
import com.google.android.material.tabs.TabLayoutMediator
import com.gyf.immersionbar.BarHide
import com.gyf.immersionbar.ktx.immersionBar
import com.gyf.immersionbar.ktx.navigationBarHeight
import com.gyf.immersionbar.ktx.statusBarHeight
import kotlinx.android.synthetic.main.activity_reader.*
import kotlinx.android.synthetic.main.item_page_article.view.*
import kotlinx.android.synthetic.main.item_page_auth.view.*
import kotlinx.android.synthetic.main.item_page_end.view.*
import kotlinx.android.synthetic.main.item_page_error.view.*
import kotlinx.android.synthetic.main.item_page_illustration.view.*
import kotlinx.android.synthetic.main.item_page_loading.view.*
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import kotlin.math.max
import kotlin.math.min

class ReaderActivity : LifecycleActivity() {

    private val controller by lazy { ViewModelProvider(this)[ReaderController::class.java] }
    private val catalogLayoutManager by lazy { RVLinearLayoutManager(this) }
    private val layoutManager by lazy { RVLinearLayoutManager(this) }
    private val pagerHelper by lazy { androidx.recyclerview.widget.PagerSnapHelper() }
    private val adapter by lazy { buildAdapter() }
    private val behavior by lazy { from(mReaderMenuLayout) }
    private var initialized: Pair<Int, Int>? = null
    private val titleResIds = arrayOf(R.string.summary, R.string.catalog, R.string.all)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerReceiver(controller.timeReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
        if (controller.attach(this).not()) toast("读取异常，请重新打开图书").run { return super.onBackPressed() }
        lifecycle.addObserver(controller)
        setupWindowPreference()
        setContentView(R.layout.activity_reader)
        setupView(savedInstanceState)
        setupTheme()
        controller.initializedLive.observe(this, initializer)
        controller.autoSynchronize().observe(this) { showProgressDialog(it) }
        if (controller.preview) window.decorView.post { ReaderPreviewPopup(this) }
        mReaderPager.post {
            setupDisplay(mReaderPager.height)
            setupMenuView()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                controller.loadChapter(savedInstanceState == null || isInMultiWindowMode).observe(this, loader)
            } else {
                controller.loadChapter(savedInstanceState == null).observe(this, loader)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mReaderPager.post {
            setupDisplay(px(newConfig.screenHeightDp))
            setupMenuView()
            controller.loadChapter(true).observe(this, loader)
        }
    }

    private fun setupView(savedInstanceState: Bundle?) {
        mReaderLoadingTitle.typeface = FontModule.mCurrentFont.typeface
        mReaderLoadingTitle.text = controller.book.chapterName
        mReaderLoading.isVisible = savedInstanceState == null
        mReaderLoading.setOnClickListener { showOrHideMenu(View.VISIBLE) }
        //下拉监听
        mReaderFlexLayout.setOnRefreshListener {
            addOrRemoveBookmark()
            mReaderFlexLayout.finishRefresh(0)
        }
        mReaderFlexLayout.post { mReaderFlexLayout.setDragRate(mReaderBookmarkHeader.height * 2F / mReaderFlexLayout.height) }
        //阅读器
        mReaderPager.clearAnimator()
        mReaderPager.layoutManager = layoutManager
        mReaderPager.adapter = adapter
        mReaderPager.addOnScrollListener(onPageChangeCallback())
        layoutManager.orientation = if (Preferences.get(Preferences.Key.TURN_VERTICAL, false)) LinearLayoutManager.VERTICAL else LinearLayoutManager.HORIZONTAL
        if (layoutManager.orientation == LinearLayoutManager.HORIZONTAL) {
            mReaderPager.setPadding(0)
            pagerHelper.attachToRecyclerView(mReaderPager)
        } else {
            mReaderPager.setPadding(0, controller.display.top, 0, 0)
            pagerHelper.attachToRecyclerView(null)
        }
        controller.pullBookmarkEnableLiveData.observe(this, Observer { mReaderFlexLayout.setEnableRefresh(it) })
        behavior.state = STATE_HIDDEN
    }

    private fun setupWindowPreference() {
        val statusBarVisible = Preferences.get(Preferences.Key.STATUS_BAR, false).not()
        val navigationBarVisible = Preferences.get(Preferences.Key.NAVIGATION_BAR, false).not()
        immersionBar {
            hideBar(when {
                statusBarVisible && navigationBarVisible -> BarHide.FLAG_HIDE_BAR
                statusBarVisible -> BarHide.FLAG_HIDE_STATUS_BAR
                navigationBarVisible -> BarHide.FLAG_HIDE_NAVIGATION_BAR
                else -> BarHide.FLAG_SHOW_BAR
            })
            statusBarDarkFont(!UIModule.isNightMode(this@ReaderActivity))
            navigationBarDarkIcon(!UIModule.isNightMode(this@ReaderActivity))
            navigationBarColorInt(controller.theme.background)
        }
    }

    private fun setupDisplay(height: Int) {
        controller.setupDisplay(this, height)
        setupTheme()
    }

    /**
     * 设置主题
     */
    private fun setupTheme() {
        //设置颜色
        when {
            Preferences.get(Preferences.Key.MIPMAP_FOLLOW, false) -> {
                mReaderFlexLayout.parentView.setBackgroundColor(controller.theme.background)
                if (controller.theme.mipmap.isNotBlank()) {
                    mReaderLoading.background = UIModule.getMipmapByTheme(controller.theme)
                } else {
                    mReaderLoading.setBackgroundColor(controller.theme.background)
                }
            }
            controller.theme.mipmap.isNotBlank() -> {
                mReaderFlexLayout.parentView.background = UIModule.getMipmapByTheme(controller.theme)
                mReaderLoading.background = UIModule.getMipmapByTheme(controller.theme)
            }
            else -> {
                mReaderFlexLayout.parentView.setBackgroundColor(controller.theme.background)
                mReaderLoading.setBackgroundColor(controller.theme.background)
            }
        }
        val hasVerticalAndStatusBar = layoutManager.orientation == RecyclerView.VERTICAL && Preferences.get(Preferences.Key.STATUS_BAR, false)
        mReaderFlexLayout.parentView.setPadding(0, if (hasVerticalAndStatusBar) statusBarHeight else 0, 0, 0)
        //Loading
        (mReaderLoading.getChildAt(0) as ProgressBar).indeterminateTintList = ColorStateList.valueOf(controller.theme.control)
        mReaderLoadingTitle.typeface = controller.paint.typeface
        mReaderLoadingTitle.setTextColor(controller.theme.content)
        //书签
        mReaderBookmarkHeader.setMargin(controller.display.horizontal)
        mReaderBookmarkHeader.setTint(controller.theme.foreground, controller.theme.control)
        setupMenuTheme()
    }

    /**
     * 章节记录
     */
    private val initializer = Observer<List<Page>?> { list ->
        //正在读取
        if (list.isNullOrEmpty()) {
            adapter.submitList(emptyList())
            mReaderLoading.isVisible = true
            mReaderLoadingTitle.text = controller.catalog.getOrNull(controller.book.chapter)?.title
            return@Observer
        }
        //读取完成
        initialized = controller.book.chapter to list.indexOfLast { it.chapter.index == controller.book.chapter && controller.book.chapterProgress in it.start until it.end }
        adapter.submitList(list) {
            scrollToPosition(initialized?.second ?: 0)
        }
    }

    /**
     * 章节追加
     */
    private val loader = Observer<List<Page>?> { list ->
        adapter.submitList(list) {
            if (list?.firstOrNull()?.chapter?.index == controller.book.chapter) {
                adapter.notifyItemRangeChanged(0, adapter.itemCount, adapter.currentList)
            }
            if (initialized == null) return@submitList
            val position = list?.indexOfFirst { it.chapter.index == initialized!!.first } ?: -1
            if (position >= 0) {
                adapter.notifyItemRangeChanged(0, adapter.itemCount, adapter.currentList)
                scrollToPosition(position + initialized!!.second)
            }
        }
    }

    private fun scrollToPosition(position: Int) {
        layoutManager.scrollToPosition(min(position, layoutManager.itemCount))
        if (layoutManager.orientation == LinearLayoutManager.HORIZONTAL) {
            mReaderLoading.isVisible = false
            initialized = null
        } else {
            val progress = controller.book.chapterProgress
            mReaderPager.postDelayed({
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
                layoutManager.findViewByPosition(firstVisibleItemPosition)?.run {
                    if (this is ViewGroup) {
                        val justifyView = this.children.firstOrNull { it is JustifyView } as? JustifyView ?: return@run
                        val current = adapter.currentList.getOrNull(firstVisibleItemPosition) ?: return@run
                        val index = max(progress - current.start, 0)
                        mReaderPager.requestFocus()
                        mReaderPager.scrollBy(0, justifyView.top + justifyView.findVerticalByIndex(index))
                    }
                    mReaderLoading.isVisible = false
                    initialized = null
                }
            }, 100)
        }
    }

    /**
     * 新建或移除书签
     */
    private fun addOrRemoveBookmark() {
        controller.addOrRemoveBookmark().observe(this, Observer {
            mReaderBookmarkHeader.exist = it
            controller.currentPage?.run { adapter.notifyItemChanged(adapter.currentList.indexOf(this), this) }
        })
    }

    /**
     * 翻页
     */
    private fun onPageChangeCallback() = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            val isHorizontal = layoutManager.orientation == LinearLayoutManager.HORIZONTAL
            val position = if (isHorizontal) layoutManager.findLastCompletelyVisibleItemPosition() else layoutManager.findFirstVisibleItemPosition()
            if (position !in 0 until adapter.currentList.size || !controller.initialized()) return
            val page = adapter.currentList[position]
            if (page.type != PageType.BOOKLET && page.type != PageType.ARTICLE) return
            //可下拉添加书签
            if (layoutManager.orientation == LinearLayoutManager.HORIZONTAL && controller.pullBookmarkEnableLiveData.value == true) {
                mReaderFlexLayout.setEnableRefresh(page.type == PageType.ARTICLE)
                controller.hasBookmark(page).observe(this@ReaderActivity, Observer { mReaderBookmarkHeader.exist = it })
            }
            var offset = max((page.end - page.start) / 2, 0)
            if (!isHorizontal) {
                //计算上下翻页的滚动偏移量索引
                layoutManager.findViewByPosition(position)?.run {
                    if (this !is ViewGroup) return@run
                    val justifyView = children.firstOrNull { it is JustifyView } as? JustifyView ?: return@run
                    if (-top < justifyView.top) return@run
                    offset = justifyView.findIndexByVertical(-(top.toFloat() + justifyView.top))
                }
            }
            controller.onPageChanged(page, offset)
        }
    }

    /**
     * 展示进度对话框
     * @param progress 进度
     */
    private fun showProgressDialog(progress: BookSyncProgress) {
        SyncProgressDialog.newInstance(progress).show(supportFragmentManager)
    }

    /***********************************************************************************************
     * 阅读菜单
     **********************************************************************************************/
    private fun setupMenuView() {
        behavior.state = STATE_HIDDEN
        behavior.isFitToContents = false
        behavior.halfExpandedRatio = controller.defaultHalfExpandedRatio
        behavior.expandedOffset = controller.defaultExpandedOffset.toInt()
        behavior.addBottomSheetCallback(buildBottomSheetCallback())
        behavior.setPeekHeight((resources.getDimension(R.dimen.topbarDefaultSize) * 2 + controller.defaultCatalogHeight + getRealNavigationBarHeight() + px(8)).toInt(), false)
        mReaderMenuTabLine.updateLayoutParams<RelativeLayout.LayoutParams> { setMargins(0, (behavior.peekHeight - controller.defaultMenuActionHeight - getRealNavigationBarHeight()).toInt(), 0, 0) }
        mReaderMenuPager.isUserInputEnabled = false
        mReaderMenuPager.isSaveEnabled = false
        mReaderMenuPager.offscreenPageLimit = 2
        mReaderMenuPager.adapter = FragmentPagerAdapter(this)
        mReaderMenuPager.setCurrentItem(1, false)
        TabLayoutMediator(mReaderMenuTab, mReaderMenuPager, true, false) { tab, position -> tab.apply { setText(titleResIds[position]) }.setTypeface(controller.paint.typeface) }.attach()
        //菜单默认底部Padding
        controller.defaultMenuPaddingBottom = getRealNavigationBarHeight() + resources.getDimensionPixelSize(R.dimen.topbarDefaultSize) + controller.defaultExpandedOffset.toInt()
        //目录本章页码SEEK回调
        controller.pageSeekCallback = { index -> mReaderPager.scrollToPosition(index) }
        controller.menuHiddenStateLiveData.observe(this, Observer { showOrHideMenu(it) })
    }

    /**
     * Bottom Sheet Callback
     */
    private fun buildBottomSheetCallback() = object : BottomSheetBehavior.BottomSheetCallback() {

        override fun onSlide(bottomSheet: View, offset: Float) {
            if (offset in 0.0F..1.0F) {
                val bottomSheetHeight = bottomSheet.height - controller.defaultExpandedOffset
                controller.bottomSheetOffsetCallbacks.forEach { it.invoke((bottomSheetHeight - behavior.peekHeight) * offset, offset) }
                mReaderMenuTabLine.updateLayoutParams<RelativeLayout.LayoutParams> { setMargins(0, ((bottomSheetHeight - behavior.peekHeight) * offset + behavior.peekHeight - controller.defaultMenuActionHeight - getRealNavigationBarHeight()).toInt(), 0, 0) }
            }
            if (offset in -1F..0F) {
                catalogLayoutManager.scrollToPositionWithOffset(max(controller.book.chapter - 1, 0), 0)
            }
        }

        override fun onStateChanged(bottomSheet: View, newState: Int) {
            if (newState == STATE_HALF_EXPANDED || newState == STATE_HIDDEN) showOrHideMenu(View.INVISIBLE)
            controller.bottomSheetStateLiveData.postValue(newState)
        }

    }

    private fun setupMenuTheme() {
        mTopIndicator.backgroundTintList = ColorStateList.valueOf(controller.theme.secondary)
        mReaderMenuLayout.backgroundTintList = ColorStateList.valueOf(controller.theme.foreground)
        mReaderMenuTabLine.setBackgroundColor(controller.theme.content)
        mReaderMenuTab.setBackgroundColor(controller.theme.foreground)
        mReaderMenuTab.setTabTextColors(controller.theme.secondary, controller.theme.control)
        (0 until mReaderMenuTab.tabCount).map { mReaderMenuTab.getTabAt(it) }.forEach { tab -> tab.setTypeface(controller.paint.typeface) }
    }

    /**
     * 展示菜单
     */
    private fun showOrHideMenu(visibility: Int) {
        if (visibility == View.VISIBLE) {
            if (behavior.state != STATE_COLLAPSED) behavior.state = STATE_COLLAPSED
            immersionBar {
                hideBar(BarHide.FLAG_SHOW_BAR)
                if (Preferences.get(Preferences.Key.NAVIGATION_BAR, false).not()) transparentNavigationBar()
                navigationBarColorInt(controller.theme.foreground)
            }
        } else {
            if (behavior.state != STATE_HIDDEN) behavior.state = STATE_HIDDEN
            setupWindowPreference()
            mReaderMenuPager.setCurrentItem(1, false)
        }
    }

    private fun getRealNavigationBarHeight() = if (Preferences.get(Preferences.Key.HAS_NAVIGATION, false) && Preferences.get(Preferences.Key.NAVIGATION_BAR, false).not()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.decorView.rootWindowInsets.systemWindowInsetBottom
        } else {
            navigationBarHeight
        }
    } else 0

    /***********************************************************************************************
     * TOUCH
     **********************************************************************************************/
    //点击范围
    private val menuXRange by lazy { (resources.displayMetrics.widthPixels / 3).let { IntRange(it, it * 2) } }

    //点击范围
    private val menuYRange by lazy { (resources.displayMetrics.heightPixels / 3).let { IntRange(it, it * 2) } }

    //按下坐标
    private val touchPoint = PointF()

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        //记录按下坐标
        if (ev?.action == MotionEvent.ACTION_DOWN) touchPoint.set(ev.rawX, ev.rawY)
        return super.dispatchTouchEvent(ev)
    }

    /**
     * 点击屏幕
     */
    fun onTouchClicked() {
        if (behavior.state != STATE_HIDDEN) {
            showOrHideMenu(View.INVISIBLE).run { return }
        }
        when {
            //上下翻页 或 非点击翻页
            Preferences.get(Preferences.Key.TURN_VERTICAL, false) || !Preferences.get(Preferences.Key.TURN_CLICK, true) -> if (touchPoint.x.toInt() in menuXRange && touchPoint.y.toInt() in menuYRange) {
                showOrHideMenu(View.VISIBLE)
            }
            //全屏下一页
            Preferences.get(Preferences.Key.ONLY_NEXT, false) -> when {
                //识别菜单区域
                touchPoint.y.toInt() in menuYRange -> when {
                    //下一页
                    touchPoint.x < menuXRange.first || touchPoint.x > menuXRange.last -> flipNext()
                    //菜单
                    else -> showOrHideMenu(View.VISIBLE)
                }
                else -> flipNext()
            }
            else -> when {
                //识别菜单区域
                touchPoint.y.toInt() in menuYRange -> when {
                    //上一页
                    touchPoint.x < menuXRange.first -> flipPrevious()
                    //下一页
                    touchPoint.x > menuXRange.last -> flipNext()
                    //菜单
                    else -> showOrHideMenu(View.VISIBLE)
                }
                else -> if (touchPoint.x < menuXRange.last - menuXRange.first / 2) flipPrevious() else flipNext()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP -> if (Preferences.get(Preferences.Key.VOLUME_KEY, false)) {
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) flipNext() else flipPrevious()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> flipPrevious()
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> flipNext()
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * 屏蔽三星机型的音量按键声音
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (Preferences.get(Preferences.Key.VOLUME_KEY, false) && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) return true
        return super.onKeyUp(keyCode, event)
    }

    /**
     * 翻页：下一页
     */
    private fun flipNext() {
        if (Preferences.get(Preferences.Key.TURN_ANIMATE, true)) {
            mReaderPager.smoothScrollBy(mReaderPager.width, 0)
        } else {
            mReaderPager.scrollBy(mReaderPager.width, 0)
        }
    }

    /**
     * 翻页：上一页
     */
    private fun flipPrevious() {
        if (Preferences.get(Preferences.Key.TURN_ANIMATE, true)) {
            mReaderPager.smoothScrollBy(-mReaderPager.width, 0)
        } else {
            mReaderPager.scrollBy(-mReaderPager.width, 0)
        }
    }

    override fun onResume() {
        super.onResume()
        windowBrightness = Preferences.get(Preferences.Key.BRIGHTNESS, -1F)
        if (Preferences.get(Preferences.Key.SCREEN_BRIGHT, false)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onPause() {
        super.onPause()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycle.removeObserver(controller)
        unregisterReceiver(controller.timeReceiver)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != Activity.RESULT_FIRST_USER) return
        when (resultCode) {
            READER_RESULT_RESTART -> recreate()
            READER_RESULT_BOOKMARK -> {
                val chapter = data?.getIntExtra(INTENT_BOOKMARK_CHAPTER, -1) ?: return
                val progress = data.getIntExtra(INTENT_BOOKMARK_PROGRESS, -1)
                if (chapter > -1 && progress > -1) {
                    controller.jump(chapter, progress)
                }
            }
        }
    }

    /**
     * 复写重建方法
     */
    override fun recreate() {
        intent.setClass(this, ReaderActivity::class.java)
        intent.putExtra(INTENT_BOOK, controller.book)
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        if (behavior.state == STATE_EXPANDED) {
            behavior.state = STATE_COLLAPSED
            return
        }
        super.onBackPressed()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: Notify.Event) {
        when (event) {
            //点击事件
            is Notify.ReaderViewClickedEvent -> onTouchClicked()
            //长按事件
            is Notify.ReaderViewLongClickedEvent -> {
                layoutManager.scrollable = !event.isLongClicked
                mReaderFlexLayout.setEnableRefresh(!event.isLongClicked)
            }
            //净化内容
            is Notify.ReaderViewPurifyEvent -> ReaderPurifyCreateDialog(this, event.content).show()
            //添加书签
            is Notify.ReaderViewBookmarkEvent -> addOrRemoveBookmark()
            //字体变化
            is Notify.FontChangedEvent -> {
                controller.setupDisplay(this).jump()
                adapter.notifyDataSetChanged()
                setupMenuTheme()
            }
        }
    }

    /***********************************************************************************************
     * ADAPTER
     ***********************************************************************************************/
    /**
     * 构建多布局数据适配器
     */
    private fun buildAdapter() = object : androidx.recyclerview.widget.ListAdapter<Page, VH>(PageDiffUtil()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(LayoutInflater.from(this@ReaderActivity).inflate(viewType, parent, false))
        }

        override fun getItemViewType(position: Int): Int {
            return when (getItem(position).type) {
                PageType.BOOKLET -> R.layout.item_page_booklet
                PageType.ILLUSTRATION -> R.layout.item_page_illustration
                PageType.LOADING -> R.layout.item_page_loading
                PageType.ERROR -> R.layout.item_page_error
                PageType.AUTH, PageType.AUTH_BUY -> R.layout.item_page_auth
                PageType.END -> R.layout.item_page_end
                else -> if (controller.display.orientation == ViewPager2.ORIENTATION_HORIZONTAL) R.layout.item_page_article else R.layout.item_page_article_vertical
            }
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            when (getItem(position).type) {
                PageType.BOOKLET -> bindBookletPage(holder.view, getItem(position))
                PageType.ILLUSTRATION -> bindIllustrationPage(holder.view, getItem(position))
                PageType.LOADING -> bindLoadingPage(holder.view, getItem(position))
                PageType.ARTICLE -> if (controller.display.orientation == ViewPager2.ORIENTATION_HORIZONTAL) {
                    bindArticlePage(holder.view, getItem(position))
                } else {
                    bindArticleVerticalPage(holder.view, getItem(position))
                }
                PageType.ERROR -> bindErrorPage(holder.view, getItem(position))
                PageType.AUTH, PageType.AUTH_BUY -> bindAuthPage(holder.view, getItem(position))
                PageType.END -> buildEndPage(holder.view)
            }
            when {
                !Preferences.get(Preferences.Key.MIPMAP_FOLLOW, false) -> holder.view.setBackgroundColor(Color.TRANSPARENT)
                controller.theme.mipmap.isNotBlank() -> holder.view.background = UIModule.getMipmapByTheme(controller.theme)
                else -> holder.view.setBackgroundColor(controller.theme.background)
            }
            holder.view.setOnClickListener { onTouchClicked() }
        }

    }

    /**
     * 分卷页
     */
    private fun bindBookletPage(holder: View, page: Page) {
        holder.setPadding(controller.display.horizontal, controller.display.top, controller.display.horizontal, controller.display.bottom)
        (holder as TextView).text = page.chapter.title
        holder.setTextColor(controller.theme.content)
        holder.typeface = controller.paint.typeface
    }

    /**
     * 插画页
     */
    private fun bindIllustrationPage(holder: View, page: Page) {
        val image = page.cells.first().image
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(image, options)
        holder.mIllustrationView.layoutParams.width = controller.display.width
        holder.mIllustrationView.layoutParams.height = options.outHeight * controller.display.width / options.outWidth
        holder.mIllustrationView.none().load(image)
    }

    /**
     * 文章页
     */
    private fun bindArticlePage(view: View, page: Page) {
        view.mPageLayout.setPadding(controller.display.horizontal, controller.display.top, controller.display.horizontal, controller.display.bottom)
        arrayOf(view.mPageTitle, view.mPageSchedule, view.mPageTime).forEach { child -> child.setTextColor(controller.theme.secondary) }
        //书签
        (view.mPageBookmark.layoutParams as RelativeLayout.LayoutParams).marginEnd = controller.display.horizontal
        controller.hasBookmark(page).observe(this, Observer { view.mPageBookmark.isVisible = it })
        view.mPageBookmark.imageTintList = ColorStateList.valueOf(controller.theme.control)
        //标题
        view.mPageTitle.isVisible = Preferences.get(Preferences.Key.SHOW_TITLE, true)
        view.mPageTitle.setPadding(0, 0, 0, 0)
        view.mPageTitle.text = if (page.cells.any { it.type == CellType.TITLE }) controller.book.name else page.chapter.title
        view.mPageTitle.typeface = controller.paint.typeface
        view.mPageTitle.setTextColor(controller.theme.secondary)
        //内置状态栏
        view.mPageStatusBar.isVisible = Preferences.get(Preferences.Key.CUSTOM_STATUS_BAR, true)
        //时间
        view.mPageTime.typeface = controller.paint.typeface
        view.mPageTime.paint.isFakeBoldText = controller.paint.isFakeBoldText
        view.mPageTime.setTextColor(controller.theme.secondary)
        //电量
        view.mPageBattery.imageTintList = ColorStateList.valueOf(controller.theme.secondary)
        //进度
        val list = adapter.currentList.filter { it.chapter == page.chapter }
        view.mPageSchedule.isVisible = Preferences.get(Preferences.Key.SHOW_TITLE, true)
        view.mPageSchedule.typeface = controller.paint.typeface
        view.mPageSchedule.setTextColor(controller.theme.secondary)
        view.mPageSchedule.text = if (controller.pageNumber.containsKey(page.chapter.index)) String.format("%d/%d", list.indexOf(page) + 1, controller.pageNumber.get(page.chapter.index)) else EMPTY
        //内容
        view.container.removeAllViews()
        view.container.setPadding(0, controller.display.titleSpacingHeight, 0, 0)
        page.cells.forEach { cell ->
            when (cell.type) {
                CellType.TITLE -> view.container.addView(buildTitle(cell), LinearLayout.LayoutParams.MATCH_PARENT, controller.display.fixed)
                CellType.TEXT -> view.container.addView(buildText(cell, false), LinearLayout.LayoutParams.MATCH_PARENT, if (cell.size.height > 0) cell.size.height else LinearLayout.LayoutParams.WRAP_CONTENT)
                CellType.IMAGE -> view.container.addView(buildImage(cell), ViewGroup.LayoutParams.MATCH_PARENT, cell.size.height)
            }
        }
    }

    /**
     * 文章页
     */
    private fun bindArticleVerticalPage(holder: View, page: Page) {
        holder.setPadding(controller.display.horizontal, 0, controller.display.horizontal, 0)
        //内容
        holder.container.removeAllViews()
        page.cells.forEach { cell ->
            when (cell.type) {
                CellType.TITLE -> holder.container.addView(buildTitle(cell), LinearLayout.LayoutParams.MATCH_PARENT, controller.display.fixed)
                CellType.TEXT -> holder.container.addView(buildText(cell, true), LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                CellType.IMAGE -> holder.container.addView(buildImage(cell), ViewGroup.LayoutParams.MATCH_PARENT, cell.size.height)
            }
        }
    }

    /**
     * 加载页
     */
    private fun bindLoadingPage(holder: View, page: Page) {
        holder.setPadding(controller.display.horizontal, controller.display.top, controller.display.horizontal, controller.display.bottom)
        holder.mLoadingBar.indeterminateTintList = ColorStateList.valueOf(controller.theme.control)
        holder.mLoadingTitle.text = page.chapter.title
        holder.mLoadingTitle.typeface = controller.paint.typeface
        holder.mLoadingTitle.setTextColor(controller.theme.content)
    }

    /**
     * 错误页
     */
    private fun bindErrorPage(holder: View, page: Page) {
        holder.setPadding(controller.display.horizontal, controller.display.top, controller.display.horizontal, controller.display.bottom)
        holder.mErrorImage.imageTintList = ColorStateList.valueOf(controller.theme.secondary)
        holder.mErrorTitle.text = buildTitleSpannable(page.chapter.title)
        holder.mErrorTitle.setTextColor(controller.theme.content)
        holder.mErrorTitle.typeface = controller.paint.typeface
        holder.mErrorMenu.backgroundTintList = ColorStateList.valueOf(controller.theme.foreground)
        holder.mErrorUrl.text = page.chapter.href
        holder.mErrorUrl.typeface = controller.paint.typeface
        holder.mErrorUrl.setTextColor(controller.theme.secondary)
        holder.mErrorWeb.setTextColor(controller.theme.secondary)
        holder.mErrorWeb.typeface = controller.paint.typeface
        holder.mErrorWeb.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(page.chapter.href)))
            } catch (e: Exception) {
                toast(if (URLUtil.isNetworkUrl(page.chapter.href)) "未安装浏览器应用" else "无法访问非法的网页")
            }
        }
        holder.mErrorTry.setTextColor(controller.theme.content)
        holder.mErrorTry.typeface = controller.paint.typeface
        holder.mErrorTry.setOnClickListener { controller.refresh() }
    }

    /**
     * 登录页
     */
    private fun bindAuthPage(holder: View, page: Page) {
        holder.setPadding(controller.display.horizontal, controller.display.top, controller.display.horizontal, controller.display.bottom)
        holder.mAuthTitle.text = buildTitleSpannable(page.chapter.title)
        holder.mAuthTitle.setTextColor(controller.theme.content)
        holder.mAuthTitle.typeface = controller.paint.typeface
        holder.mAuthMenu.backgroundTintList = ColorStateList.valueOf(controller.theme.foreground)
        holder.mAuthTips.setText(if (page.type == PageType.AUTH) R.string.reader_auth_tips else R.string.reader_auth_buy_tips)
        holder.mAuthTips.setTextColor(controller.theme.secondary)
        holder.mAuthTips.typeface = controller.paint.typeface
        holder.mAuthLogin.setText(if (page.type == PageType.AUTH) R.string.reader_auth_login else R.string.reader_auth_buy_login)
        holder.mAuthLogin.setTextColor(controller.theme.content)
        holder.mAuthLogin.typeface = controller.paint.typeface
        holder.mAuthLogin.setOnClickListener {
            if (controller.book.getBookSource() is BookSourceParser) {
                startActivityForResult(Intent(this, BookSourceAuthActivity::class.java).putExtra(INTENT_BOOK_SOURCE, (controller.book.getBookSource() as BookSourceParser).bookSource.url), Activity.RESULT_FIRST_USER)
            } else {
                holder.mAuthLogin.setText(R.string.reader_auth_deprecated)
            }
        }
    }

    /**
     * 结束页
     */
    private fun buildEndPage(holder: View) {
        holder.setPadding(controller.display.horizontal, controller.display.top, controller.display.horizontal, controller.display.bottom)
        holder.mEndTitle.typeface = controller.paint.typeface
        holder.mEndTitle.setTextColor(controller.theme.content)
        holder.mEndTitle.text = controller.book.name
        holder.mEndAuthor.typeface = controller.paint.typeface
        holder.mEndAuthor.setTextColor(controller.theme.content)
        holder.mEndAuthor.text = controller.book.author
        holder.mEndCover.load(controller.book.cover)
        holder.mEndCount.typeface = controller.paint.typeface
        holder.mEndCount.setTextColor(controller.theme.secondary)
        holder.mEndCount.text = getString(R.string.read_end_count, controller.book.time / 60, controller.book.speed.toInt())
        holder.mEndChapter.typeface = controller.paint.typeface
        holder.mEndChapter.setTextColor(controller.theme.secondary)
        holder.mEndChapter.text = getString(R.string.read_end_chapter, controller.catalog.size, min(Room.bookRecord().count(controller.book.objectId), controller.catalog.size))
        holder.mEndStatus.typeface = controller.paint.typeface
        holder.mEndStatus.setTextColor(controller.theme.control)
        holder.mEndStatus.backgroundTintList = ColorStateList.valueOf(controller.theme.foreground)
        holder.mEndStatus.text = when {
            controller.preview -> R.string.read_status_preview
            controller.book.finishedAt > 0 -> R.string.read_status_end
            controller.isHaveRead() && controller.book.state == BOOK_STATE_END -> {
                Room.book().update(controller.book.apply { finishedAt = System.currentTimeMillis() })
                R.string.read_status_end
            }
            controller.isHaveRead() -> R.string.read_status_to_be_continued
            else -> R.string.read_status_ing
        }.let { getString(it) }
    }

    /**
     * 构建标题元素
     */
    private fun buildTitle(cell: Cell): TextView {
        val title = TextView(this)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28F)
        title.setTextColor(controller.theme.content)
        title.includeFontPadding = false
        title.gravity = Gravity.CENTER
        title.text = buildTitleSpannable(cell.value.trim())
        title.typeface = controller.paint.typeface
        title.paint.isFakeBoldText = controller.paint.isFakeBoldText
        title.setPadding(0, 0, 0, controller.display.bottom)
        return title
    }

    /**
     * 构建正文元素
     */
    private fun buildText(cell: Cell, vertical: Boolean): JustifyView {
        val text = JustifyView(this)
        text.paint.isFakeBoldText = controller.paint.isFakeBoldText
        text.paint.letterSpacing = controller.paint.letterSpacing
        text.paragraphSpacing = controller.display.paragraphSpacing
        text.textColor = controller.theme.content
        text.lineSpacing = controller.display.lineSpacing
        text.textSize = controller.paint.textSize
        text.firstLineIndent = controller.firstLineIndent
        text.highlightColor = controller.theme.control
        text.menuBackgroundColor = controller.theme.foreground
        text.text = cell.value
        text.typeface = controller.paint.typeface
        if (vertical) {
            text.setPadding(0, 0, 0, controller.display.lineSpacingHeight + controller.display.paragraphSpacing)
        }
        return text
    }

    /**
     * 构建图片元素
     */
    private fun buildImage(cell: Cell): LinearLayout {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(cell.image, options)
        val layout = LinearLayout(this)
        layout.gravity = Gravity.CENTER
        layout.orientation = LinearLayout.VERTICAL
        val image = ImageUriView(this)
        image.none().load(cell.image)
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            layout.addView(image, cell.size.width, cell.size.height)
        } else {
            layout.addView(image, cell.size.width, options.outHeight * cell.size.width / options.outWidth)
        }
        if (cell.value.isNotBlank()) {
            val text = TextView(this)
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14F)
            text.setPadding(0, resources.getDimensionPixelSize(R.dimen.dimen1x), 0, 0)
            text.includeFontPadding = false
            text.typeface = controller.paint.typeface
            text.setTextColor(controller.theme.secondary)
            text.setLineSpacing(0F, 1.2F)
            text.gravity = Gravity.CENTER
            text.text = cell.value
            layout.addView(text, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        return layout
    }

    private fun buildTitleSpannable(title: String): SpannableStringBuilder {
        val spannable = SpannableStringBuilder(title.replaceFirst(Regex("""\s+"""), "\n"))
        val separator = spannable.indexOf("\n")
        if (separator > 0) {
            spannable.setSpan(RelativeSizeSpan(0.58F), 0, separator, SpannableStringBuilder.SPAN_INCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(controller.theme.secondary), 0, separator, SpannableStringBuilder.SPAN_INCLUSIVE_EXCLUSIVE)
        }
        return spannable
    }

    /***********************************************************************************************
     * CLASS
     ***********************************************************************************************
     **
     * 页差异比对工具
     */
    class PageDiffUtil : DiffUtil.ItemCallback<Page>() {
        override fun areItemsTheSame(old: Page, new: Page) = old.chapter == new.chapter && old.type == new.type && old.start == new.start

        override fun areContentsTheSame(old: Page, new: Page) = old.chapter == new.chapter && old.type == new.type && old.start == new.start
    }


    /**
     * 菜单页适配器
     */
    class FragmentPagerAdapter(activity: LifecycleActivity) : FragmentStateAdapter(activity) {

        private val fragments = listOf(ReaderSummaryFragment::class, ReaderCatalogFragment::class, ReaderMoreFragment::class)

        override fun getItemCount() = fragments.size

        override fun createFragment(position: Int) = fragments[position].java.newInstance()

    }

}
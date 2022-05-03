package cn.lightink.reader.widget

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.*
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.children
import androidx.core.view.contains
import androidx.core.view.get
import cn.lightink.reader.R
import cn.lightink.reader.ktx.px
import cn.lightink.reader.ktx.tint

class TopbarView : RelativeLayout {

    private val defaultSize by lazy { resources.getDimensionPixelSize(R.dimen.topbarDefaultSize) }
    private val toolbar by lazy { Toolbar(context) }
    private val title by lazy { BoldTextView(context) }
    private val progress by lazy { LayoutInflater.from(context).inflate(R.layout.view_progress_bar, this, false) }

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs, defStyleAttr, R.style.AppTheme_Topbar)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.TopbarView, defStyleAttr, defStyleRes)
        val text = typedArray.getString(R.styleable.TopbarView_android_text)
        val menuResId = typedArray.getResourceId(R.styleable.TopbarView_menu, 0)
        val navigationEnabled = typedArray.getBoolean(R.styleable.TopbarView_navigationEnabled, true)
        val progressEnabled = typedArray.getBoolean(R.styleable.TopbarView_progressEnabled, false)
        val backgroundColor = typedArray.getColor(R.styleable.TopbarView_android_background, 0)
        val textColor = typedArray.getColor(R.styleable.TopbarView_android_textColor, 0)
        typedArray.recycle()
        setBackgroundColor(backgroundColor)
        toolbar.setPadding(-px(4), toolbar.paddingTop, toolbar.paddingRight, toolbar.paddingBottom)
        if (navigationEnabled) toolbar.setNavigationIcon(R.drawable.ic_back)
        if (menuResId != 0) toolbar.inflateMenu(menuResId)
        if (context is Activity) toolbar.setNavigationOnClickListener { context.onBackPressed() }
        addView(toolbar, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, defaultSize))
        toolbar.contentInsetStartWithNavigation = 0
        toolbar.titleMarginStart = 0
        toolbar.contentInsetStartWithNavigation = 0
        toolbar.setContentInsetsAbsolute(0, 0)
        title.setTextColor(textColor)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16F)
        title.text = text
        title.gravity = Gravity.CENTER
        title.isSingleLine = true
        title.includeFontPadding = false
        title.ellipsize = TextUtils.TruncateAt.MIDDLE
        addView(title, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = defaultSize
            marginEnd = defaultSize
            addRule(CENTER_IN_PARENT)
        })
        if (progressEnabled) {
            addView(progress, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, resources.getDimensionPixelSize(R.dimen.dimenLx)).apply {
                topMargin = (defaultSize - height * 0.64F).toInt()
            })
        }
    }

    var text: String = ""
        set(value) {
            field = value
            title.text = value
        }

    /**
     * 设置字体
     */
    fun setTypeface(typeface: Typeface) {
        title.typeface = typeface
    }

    /**
     * 设置导航点击事件
     */
    fun setNavigationOnClickListener(listener: () -> Unit) {
        toolbar.setNavigationOnClickListener { listener.invoke() }
    }

    /**
     * 设置按钮点击事件
     */
    fun setOnMenuClickListener(listener: (MenuItem) -> Unit) {
        toolbar.setOnMenuItemClickListener { item ->
            listener.invoke(item)
            return@setOnMenuItemClickListener true
        }
    }

    /**
     * 设置按钮是否可见
     */
    fun setMenu(menuRes: Int) {
        toolbar.menu.clear()
        toolbar.inflateMenu(menuRes)
    }

    /**
     * 设置按钮是否可见
     */
    fun setMenuVisible(isVisible: Boolean) {
        toolbar.menu[0].isVisible = isVisible
    }

    /**
     * 设置标题颜色
     */
    fun setTitleColor(color: Int) {
        (toolbar.children.firstOrNull { it is TextView } as? TextView)?.setTextColor(color)
    }

    /**
     * 着色
     */
    fun setTint(color: Int) {
        toolbar.navigationIcon = toolbar.navigationIcon?.tint(color)
        toolbar.menu.children.forEach { it.icon = it.icon.tint(color) }
    }

    /**
     * 设置进度条是否可见
     */
    fun setProgressVisible(isVisible: Boolean) {
        if (contains(progress)) {
            progress.visibility = if (isVisible) View.VISIBLE else View.INVISIBLE
        } else if (isVisible) {
            addView(progress, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, resources.getDimensionPixelSize(R.dimen.dimenLx)).apply {
                topMargin = (defaultSize - height * 0.64F).toInt()
            })
        }
    }

    fun menu() = toolbar.menu!!

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), defaultSize)
    }
}
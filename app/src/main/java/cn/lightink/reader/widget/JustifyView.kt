package cn.lightink.reader.widget

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.*
import android.os.Build
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.*
import android.widget.Magnifier
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.cardview.widget.CardView
import androidx.core.view.children
import cn.lightink.reader.R
import cn.lightink.reader.ktx.equal
import cn.lightink.reader.ktx.px
import cn.lightink.reader.ktx.toast
import cn.lightink.reader.module.EMPTY
import cn.lightink.reader.module.Notify
import cn.lightink.reader.module.Preferences
import cn.lightink.reader.module.TOAST_TYPE_SUCCESS
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class JustifyView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {

    //最小移动距离
    private val scaledTouchSlop by lazy { ViewConfiguration.get(context).scaledTouchSlop }
    private val clipboard by lazy { context.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    //画笔
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    //内容
    var text: String = EMPTY

    //行间距
    var lineSpacing: Float = 1.0F

    //菜单背景色
    var menuBackgroundColor = Color.WHITE

    //首行缩进
    var firstLineIndent = "\u3000\u3000"

    //字号
    var textSize: Float = 16F
        set(value) {
            field = value
            paint.textSize = value
        }

    //字色
    @ColorInt
    var textColor: Int = Color.BLACK
        set(value) {
            field = value
            paint.color = value
        }

    //高亮
    @ColorInt
    var highlightColor: Int = Color.LTGRAY

    //字体
    var typeface: Typeface = Typeface.DEFAULT
        set(value) {
            field = value
            paint.typeface = field
        }

    //段落间隔
    var paragraphSpacing = 0

    //布局
    private var layout: StaticLayout? = null

    //触摸点记录
    private var touchPoint: PointF = PointF()

    //选中区间
    private var selectedRange: IntRange = IntRange(-1, -1)

    //选中矩形
    private val selectedRect: RectF = RectF()
    private val popupMenu by lazy { buildPopupMenu() }
    private val cursorLeft by lazy { CursorView(context, true) }
    private val cursorRight by lazy { CursorView(context, false) }

    private var state = STATE_IDLE

    private var magnifier: Magnifier? = null

    init {
        text = attrs?.getAttributeValue("http://schemas.android.com/apk/res/android", "text").orEmpty()
        setOnLongClickListener { selectParagraph().let { true } }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (Preferences.get(Preferences.Key.TEXT_LONG_CLICKABLE, true)) {
            when (event?.action) {
                //记录触摸坐标
                MotionEvent.ACTION_DOWN -> touchPoint.set(event.x, event.y)
                //长按聚焦 移动选中句子
                MotionEvent.ACTION_MOVE -> if (state == STATE_LONG_CLICKED_FOCUS && !touchPoint.equal(event.x, event.y, scaledTouchSlop)) selectSentence(event)
                //取消聚焦
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (state == STATE_LONG_CLICKED_FOCUS) state = STATE_LONG_CLICKED
                    if (state == STATE_LONG_CLICKED) showActionsMenu()
                }
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * 点击事件
     */
    override fun performClick(): Boolean {
        when (state) {
            STATE_IDLE -> Notify.post(Notify.ReaderViewClickedEvent())
            STATE_LONG_CLICKED -> popupMenu.dismiss()
        }
        return super.performClick()
    }

    /**
     * 长按触发 选中当前段落
     */
    private fun selectParagraph() {
        if (!Preferences.get(Preferences.Key.TEXT_LONG_CLICKABLE, true)) return
        val offset = findIndexByTouch() ?: return
        if (state == STATE_IDLE) {
            state = STATE_LONG_CLICKED_FOCUS
            Notify.post(Notify.ReaderViewLongClickedEvent(true))
        }
        val start = text.substring(0, offset).lastIndexOf(firstLineIndent).let { index -> if (index >= 0) index + 2 else 0 }
        val end = text.substring(offset).indexOf("\n").let { index -> if (index > 0) offset + index else text.length }
        updateSelectedRange(start, end)
    }

    /**
     * 移动选中句子
     */
    private fun selectSentence(event: MotionEvent) {
        touchPoint.set(event.x, event.y)
        val offset = findIndexByTouch() ?: return
        val start = text.substring(0, offset).indexOfLast { SENTENCE_CHARS.contains(it.toString()) }.let { index -> if (index >= 0) index + 1 else 0 }
        val end = text.substring(offset).indexOfFirst { SENTENCE_CHARS.contains(it.toString()) }.let { index -> if (index > 0) offset + index + 1 else text.length }
        updateSelectedRange(start, end)
    }

    /**
     * 更新选中区域
     * @param start 起始位置
     * @param end   结束位置
     */
    private fun updateSelectedRange(start: Int, end: Int) {
        if (start == selectedRange.first && end == selectedRange.last) return
        selectedRange = if (start <= end) {
            IntRange(start, end)
        } else {
            cursorLeft.reverse()
            cursorRight.reverse()
            IntRange(end, start)
        }
        invalidate()
    }

    /**
     * 查找按下坐标对应文字的索引
     */
    private fun findIndexByTouch(point: PointF = touchPoint): Int? {
        val lineCount = layout?.lineCount ?: return null
        var paragraphSpacingTotal = 0F
        for (line in 0 until lineCount) {
            val top = layout!!.getLineTop(line).toFloat() + paragraphSpacingTotal
            val bottom = layout!!.getLineBottom(line).toFloat() + paragraphSpacingTotal
            if ((point.y - paddingTop) in top..bottom) {
                if (layout!!.getLineRight(line) < point.x) return layout!!.getLineEnd(line)
                return layout!!.getOffsetForHorizontal(line, point.x)
            }
            //修正段落间隔产生的偏差
            if (text[max(0, layout!!.getLineEnd(line) - 1)].code == '\n'.code && paragraphSpacing > 0) {
                paragraphSpacingTotal += paragraphSpacing
            }
        }
        return null
    }

    /**
     * 查询索引对应的Y轴偏移量
     */
    fun findIndexByVertical(y: Float): Int {
        val lineCount = layout?.lineCount ?: return 0
        var paragraphSpacingTotal = 0F
        for (line in 0 until lineCount) {
            val top = layout!!.getLineTop(line).toFloat() + paragraphSpacingTotal
            val bottom = layout!!.getLineBottom(line).toFloat() + paragraphSpacingTotal
            if ((y - paddingTop) in top..bottom) {
                return layout!!.getLineStart(line + 1)
            }
            //修正段落间隔产生的偏差
            if (text[max(0, layout!!.getLineEnd(line) - 1)].code == '\n'.code && paragraphSpacing > 0) {
                paragraphSpacingTotal += paragraphSpacing
            }
        }
        return 0
    }

    /**
     * 查询索引对应的Y轴偏移量
     */
    fun findVerticalByIndex(index: Int): Int {
        val lineCount = layout?.lineCount ?: return 0
        var paragraphSpacingTotal = 0F
        for (line in 0 until lineCount) {
            if (index in layout!!.getLineStart(line) until layout!!.getLineEnd(line)) {
                return (layout!!.getLineTop(line) + paragraphSpacingTotal).roundToInt()
            }
            //修正段落间隔产生的偏差
            if (text[max(0, layout!!.getLineEnd(line) - 1)].code == '\n'.code && paragraphSpacing > 0) {
                paragraphSpacingTotal += paragraphSpacing
            }
        }
        return 0
    }

    /**
     * 显示菜单
     */
    @SuppressLint("InflateParams")
    private fun showActionsMenu() {
        try {
            //计算显示的位置
            var y = 0
            for (line in 0..layout!!.lineCount) {
                if (selectedRange.first in layout!!.getLineStart(line) until layout!!.getLineEnd(line)) {
                    y += (layout!!.getLineTop(line) + paddingTop)
                    break
                }
                //修正段落间隔产生的偏差
                if (text[max(0, layout!!.getLineEnd(line) - 1)].code == '\n'.code && paragraphSpacing > 0) {
                    y += paragraphSpacing
                }
            }
            if (popupMenu.isShowing) {
                popupMenu.update(this, 0, top - bottom - px(52) + y, -1, -1)
            } else {
                popupMenu.showAsDropDown(this, 0, top - bottom - px(52) + y)
            }
            cursorLeft.show()
            cursorRight.show()
        } catch (e: Exception) {

        }
    }

    /**
     * 构建浮动菜单
     */
    @SuppressLint("InflateParams")
    private fun buildPopupMenu() = PopupWindow(this, -1, -2).apply {
        isClippingEnabled = false
        animationStyle = R.style.AppTheme_Popup
        contentView = LayoutInflater.from(context).inflate(R.layout.popup_justify_menu, null)
        contentView.findViewById<CardView>(R.id.justify_card).setCardBackgroundColor(highlightColor)
        contentView.findViewById<ViewGroup>(R.id.justify_menu).children.forEach { child ->
            (child as TextView).setOnClickListener {
                onMenuItemClicked(child.id)
                dismiss()
            }
            child.typeface = typeface
            child.setTextColor(menuBackgroundColor)
        }
        contentView.setOnClickListener { dismiss() }
        setOnDismissListener { onMenuDismiss() }
    }

    /**
     * 菜单关闭
     */
    private fun onMenuDismiss() {
        if (state == STATE_CURSOR_MOVED) return
        state = STATE_IDLE
        selectedRange = IntRange(-1, -1)
        Notify.post(Notify.ReaderViewLongClickedEvent(false))
        cursorLeft.dismiss()
        cursorRight.dismiss()
        invalidate()
    }

    /**
     * 菜单选中事件
     */
    private fun onMenuItemClicked(id: Int) {
        if (selectedRange.first !in text.indices) return
        val selectedText = text.substring(selectedRange.first, min(selectedRange.last, text.length)).removeSuffix("\n")
        when (id) {
            //复制
            R.id.justify_copy -> {
                clipboard.setPrimaryClip(ClipData.newPlainText(resources.getString(R.string.app_name), selectedText))
                context.toast("选中内容已复制到粘贴板", TOAST_TYPE_SUCCESS)
            }
            //书签
            R.id.justify_bookmark -> Notify.post(Notify.ReaderViewBookmarkEvent())
            //净化
            R.id.justify_purify -> Notify.post(Notify.ReaderViewPurifyEvent(selectedText.trim()))
        }
    }

    /**
     * 测量高度
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (text.isBlank()) return super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val contentWidth = MeasureSpec.getSize(widthMeasureSpec)
        val contentHeight = when (MeasureSpec.getMode(heightMeasureSpec)) {
            //计算高度
            MeasureSpec.AT_MOST, MeasureSpec.UNSPECIFIED -> {
                val measureLayout = StaticLayout.Builder.obtain(text, 0, text.length, paint, contentWidth - paddingStart - paddingEnd).setLineSpacing(0F, lineSpacing).setIncludePad(false).build()
                val extraHeight = if (paragraphSpacing > 0 && text.length > 2 && text.contains("\n")) {
                    text.substring(1, text.lastIndex).count { it == '\n' } * paragraphSpacing
                } else 0
                measureLayout.height + extraHeight + paddingTop + paddingBottom
            }
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            else -> MeasureSpec.getSize(heightMeasureSpec)
        }
        setMeasuredDimension(contentWidth, contentHeight)
    }

    override fun onDraw(canvas: Canvas) {
        if (text.isBlank()) return super.onDraw(canvas)
        paint.color = textColor
        val lineWidth = width - paddingLeft - paddingRight
        layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, lineWidth).setLineSpacing(0F, lineSpacing).setIncludePad(false).build()
        val lineHeight = StaticLayout.Builder.obtain(firstLineIndent, 0, firstLineIndent.length, paint, lineWidth).setIncludePad(false).build().height
        val firstLineIndent = max(getTextWidths(firstLineIndent).sum(), lineWidth / 4F)
        val maxLine = layout!!.lineCount - 1
        var paragraphSpacingTotal = 0F
        for (line in 0..maxLine) {
            val lineStartIndex = layout!!.getLineStart(line)
            val lineText = text.substring(lineStartIndex, layout!!.getLineEnd(line))
            val lineTop = paddingTop + layout!!.getLineBaseline(line).toFloat() + paragraphSpacingTotal
            var lineLeft = paddingLeft.toFloat()
            val lineTextWidths = getTextWidths(lineText)
            val lineBlankWidth = max(lineWidth - lineTextWidths.sum(), 0F)
            val isNotJustify = lineText.endsWith(ENTER) || (line == maxLine && (lineText.isBlank() || isPunctuation(lineText.last()) || lineBlankWidth > firstLineIndent))
            val multilingualLength = multilingualLength(lineText)
            var offset = if (isNotJustify) 0F else lineBlankWidth / multilingualLength
            if (offset * multilingualLength > firstLineIndent) offset = 0F
            for (index in 0..lineTextWidths.lastIndex) {
                if (index > 0 && lineTextWidths[index] == 0F) {
                    (lineTextWidths[index - 1] / 2).run {
                        lineTextWidths[index - 1] = this
                        lineTextWidths[index] = this
                    }
                }
            }

            val chars = lineText.toCharArray()
            chars.forEachIndexed { index, char ->
                if (offset > 0 && isNeedFill(index, chars)) lineLeft += offset
                if (lineStartIndex + index in selectedRange.first until selectedRange.last) {
                    val top = layout!!.getLineTop(line).toFloat() + paddingTop + paragraphSpacingTotal
                    paint.color = highlightColor
                    paint.alpha = 0x5F
                    selectedRect.set(lineLeft - offset, top, lineLeft + lineTextWidths[index], top + lineHeight)
                    canvas.drawRect(selectedRect, paint)
                    paint.color = textColor
                    paint.alpha = 0xFF
                    if (lineStartIndex + index == selectedRange.first) {
                        (if (cursorLeft.isLeft) cursorLeft else cursorRight).setPoint(lineLeft - offset, -(height - top - lineHeight))
                    } else if (lineStartIndex + index == selectedRange.last - 1) {
                        (if (cursorLeft.isLeft) cursorRight else cursorLeft).setPoint(lineLeft + lineTextWidths[index], -(height - top - lineHeight))
                    }
                }
                canvas.drawText(char.toString(), lineLeft, lineTop, paint)
                lineLeft += lineTextWidths[index]
            }

            if (line != maxLine) {
                if (chars.last() == '\n' && paragraphSpacing > 0) {
                    paragraphSpacingTotal += paragraphSpacing
                }
            }
        }
    }

    /**
     * 读取文字宽度
     */
    private fun getTextWidths(text: String?): FloatArray {
        return FloatArray(text.orEmpty().length).apply { paint.getTextWidths(text, this) }
    }

    /**
     * 统计留白个数
     */
    private fun multilingualLength(text: String): Int {
        var multilingualLength = 0
        var isEnglishWord = false
        var isEllipsis = false
        var isDash = false
        for (c in text.toCharArray()) {
            isEnglishWord = if (isLetter(c.code)) {
                if (isEnglishWord) continue
                true
            } else {
                if (c.code == 12288) continue
                false
            }
            //省略号
            if (c.code == 8230 && isEllipsis) continue
            if (c.code == 8212 && isDash) continue
            isEllipsis = c.code == 8230
            isDash = c.code == 8212
            multilingualLength++
        }
        return multilingualLength - 1
    }

    /**
     * 是否为英文字符
     */
    private fun isLetter(c: Int) = c in 65..90 || c in 97..122 || c in 48..57 || c == 45 || c == 39 || c == 46

    /**
     * 是否以标点符号结尾
     */
    private fun isPunctuation(c: Char) = c.toString().matches(Regex("""[a-zA-Z0-9\u4e00-\u9fa5]""")).not()

    /**
     * 是否需要填充
     * 特殊存在不需要填充，比如：......  ————
     */
    private fun isNeedFill(i: Int, chars: CharArray) = i != 0 &&
            !(i == 1 && chars.size == 2) &&
            !(chars[i].code == 12288 && chars[i - 1].code == 12288) &&
            !(chars[i].code == 8230 && chars[i - 1].code == 8230) &&
            !(chars[i].code == 8212 && chars[i - 1].code == 8212) &&
            !((0 until i).all { chars[it].code == 12288 }) &&
            !(isLetter(chars[i - 1].code) && isLetter(chars[i].code))

    companion object {
        private const val ENTER = "\n"
        private const val STATE_IDLE = 0
        private const val STATE_LONG_CLICKED = 7
        private const val STATE_LONG_CLICKED_FOCUS = 9
        private const val STATE_CURSOR_MOVED = 10
        private val SENTENCE_CHARS = arrayOf("。", "！", "？", "\u3000")
    }

    /**
     * 游标
     */
    inner class CursorView(context: Context, var isLeft: Boolean) : View(context) {

        private val radius by lazy { textSize * 0.54F }
        private val popup by lazy { PopupWindow(this) }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val point = Point(0, 0)
        private val location = IntArray(2)
        private val padding = 10F

        init {
            paint.color = highlightColor
            popup.animationStyle = R.style.AppTheme_Popup
            popup.isClippingEnabled = false
            popup.width = ((radius + padding) * 2).toInt()
            popup.height = ((radius + padding) * 2).toInt()
            this@JustifyView.getLocationInWindow(location)
        }

        /**
         * 更新游标坐标
         */
        fun setPoint(x: Float, y: Float) = point.set(x.toInt(), y.toInt())

        fun reverse() {
            isLeft = isLeft.not()
            invalidate()
        }

        /**
         * 显示游标
         */
        fun show() {
            val x = if (isLeft) {
                point.x - popup.width + padding.toInt()
            } else {
                point.x - padding.toInt()
            }
            if (popup.isShowing) {
                //更新游标
                popup.update(this@JustifyView, x, point.y, -1, -1)
            } else {
                popup.showAsDropDown(this@JustifyView, x, point.y)
            }
        }

        /**
         * 隐藏游标
         */
        fun dismiss() {
            popup.dismiss()
        }

        @Suppress("DEPRECATION")
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent?): Boolean {
            when (event?.action) {
                //标记游标移动状态
                MotionEvent.ACTION_DOWN -> {
                    state = STATE_CURSOR_MOVED
                    //Android P+ 创建放大镜
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        magnifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Magnifier.Builder(this@JustifyView).build() else Magnifier(this@JustifyView)
                        showMagnifier(event.rawX - location[0], event.rawY - location[1])
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    popupMenu.dismiss()
                    select(event)
                    showMagnifier(event.rawX - location[0], event.rawY - location[1])
                }
                //取消游标移动状态
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                    state = STATE_LONG_CLICKED
                    showActionsMenu()
                    //Android P+ 隐藏放大镜
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        magnifier?.dismiss()
                    }
                }
            }
            return true
        }

        //Android P+ 更新放大镜位置
        private fun showMagnifier(x: Float, y: Float) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    magnifier?.show(x, y)
                }
            } catch (e: Exception) {
                //Attempt to read from field 'long android.view.Surface.mNativeObject' on a null object reference
            }
        }

        private fun select(event: MotionEvent) {
            val offset = findIndexByTouch(PointF(event.rawX - location[0], event.rawY - location[1])) ?: return
            if (isLeft) {
                updateSelectedRange(offset, selectedRange.last)
            } else {
                updateSelectedRange(selectedRange.first, offset)
            }
            show()
        }

        /**
         * 绘制游标
         */
        override fun onDraw(canvas: Canvas?) {
            canvas?.drawCircle(radius + padding, radius + padding, radius, paint)
            if (isLeft) {
                canvas?.drawRect(radius + padding, padding, radius * 2 + padding, radius + padding, paint)
            } else {
                canvas?.drawRect(padding, padding, radius + padding, radius + padding, paint)
            }
        }
    }

}
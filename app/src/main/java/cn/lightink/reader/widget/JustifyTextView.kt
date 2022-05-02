package cn.lightink.reader.widget

import android.content.Context
import android.graphics.Canvas
import android.text.StaticLayout
import android.text.TextUtils
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import kotlin.math.max
import kotlin.math.min

//排版视图 - 两端对齐
open class JustifyTextView @JvmOverloads constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int = 0) : AppCompatTextView(context, attrs, defStyleAttr) {

    init {
        super.setEllipsize(TextUtils.TruncateAt.END)
        includeFontPadding = false
    }

    /**
     * 测量高度
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (text.isBlank()) return super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val contentWidth = MeasureSpec.getSize(widthMeasureSpec)
        val contentHeight = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.AT_MOST, MeasureSpec.UNSPECIFIED -> {
                val lineSpacingHeight = StaticLayout.Builder.obtain("T\nT", 0, 3, paint, contentWidth).setLineSpacing(lineSpacingExtra, lineSpacingMultiplier).setIncludePad(false).build().height - StaticLayout.Builder.obtain("T", 0, 1, paint, contentWidth).setIncludePad(false).build().height * 2
                val measureLayout = StaticLayout.Builder.obtain(text, 0, text.length, paint, contentWidth - paddingStart - paddingEnd).setLineSpacing(lineSpacingExtra, lineSpacingMultiplier).setIncludePad(false).build()
                val maxLine = min(measureLayout.lineCount - 1, maxLines - 1)
                measureLayout.getLineBottom(maxLine) + paddingTop + paddingBottom - if (measureLayout.lineCount - 1 != maxLine) lineSpacingHeight else 0
            }
            else -> MeasureSpec.getSize(heightMeasureSpec)
        }
        setMeasuredDimension(contentWidth, contentHeight)
    }

    override fun onDraw(canvas: Canvas) {
        if (text.isNullOrBlank() || !text.contains("""[\u4e00-\u9fa5]""".toRegex())) return super.onDraw(canvas)
        paint.color = currentTextColor
        val lineWidth = width - paddingLeft - paddingRight
        val firstLineIndent = max(getTextWidth(), lineWidth / 4F)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, lineWidth).setLineSpacing(lineSpacingExtra, lineSpacingMultiplier).setIncludePad(false).build()
        val maxLine = layout.lineCount - 1
        for (line in 0..maxLine) {
            val lineText = if (line == maxLines - 1) {
                TextUtils.ellipsize(text.substring(layout.getLineStart(line)), paint, lineWidth.toFloat(), TextUtils.TruncateAt.END)
            } else {
                text.substring(layout.getLineStart(line), layout.getLineEnd(line))
            }.toString()
            val lineTop = paddingTop + layout.getLineBaseline(line).toFloat()
            var lineLeft = paddingLeft.toFloat()
            val lineTextWidths = getTextWidths(lineText)
            val lineBlankWidth = max(lineWidth - lineTextWidths.sum(), 0F)
            val isNotJustify = lineText.endsWith("\n") || (line == maxLine && (lineText.isBlank() || isPunctuation(lineText.last()) || lineBlankWidth > firstLineIndent))
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
                canvas.drawText(char.toString(), lineLeft, lineTop, paint)
                lineLeft += lineTextWidths[index]
            }
            if (line >= maxLines - 1) break
        }
    }


    /**
     * 读取文字总宽度
     */
    private fun getTextWidth() = getTextWidths("　　").sum()

    /**
     * 读取文字宽度
     */
    private fun getTextWidths(text: String?): FloatArray {
        return FloatArray(text.orEmpty().length).apply { paint.getTextWidths(text, this) }
    }

    /**
     * if need fill offset return true
     *
     * @param i     position
     * @param chars line text
     * @return true/false
     */
    private fun isNeedFill(i: Int, chars: CharArray) = i != 0
            && !(i == 1 && chars.size == 2)
            && !(i <= 2 && chars[0].code == 12288 && chars[1].code == 12288)
            && !(isLetter(chars[i - 1].code) && isLetter(chars[i].code))
            && !(chars[i].code == 8230 && chars[i - 1].code == 8230)
            && !(chars[i].code == 8212 && chars[i - 1].code == 8212)

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
     * if the character is 'a'-'z' or 'A'-'Z' return true
     *
     * @param c character
     * @return true/false
     */
    private fun isLetter(c: Int) = c in 65..90 || c in 97..122 || c in 48..57 || c == 45 || c == 39 || c == 46

    private fun isPunctuation(c: Char): Boolean {
        return c.toString().matches(Regex("""[a-zA-Z0-9\u4e00-\u9fa5]""")).not()
    }

}
package cn.lightink.reader.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.webkit.URLUtil
import androidx.appcompat.widget.AppCompatImageView
import cn.lightink.reader.GlideApp
import cn.lightink.reader.R
import cn.lightink.reader.ktx.dominant
import cn.lightink.reader.ktx.px
import cn.lightink.reader.module.EMPTY
import cn.lightink.reader.module.UIModule
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import kotlin.math.ceil
import kotlin.math.min

class ImageUriView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : AppCompatImageView(context, attrs, defStyleAttr) {

    private var paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private var padding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16F, resources.displayMetrics).toInt()

    private var failure: (() -> Unit)? = null
    private var privacy = false
    private var force = false
    private var radius = 2F
    private var hint = EMPTY
    private var stroke = false
    private var strokeColor = 0

    private var uri: Any? = null

    init {
        paint.isDither = true
        paint.isAntiAlias = true
        paint.alpha = 102
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12F, resources.displayMetrics)
        alpha = if (UIModule.isNightMode(context)) 0.6F else 1F
    }

    /**
     * 圆角
     */
    fun radius(radius: Float): ImageUriView {
        this.radius = radius
        return this
    }

    /**
     * 防盗链
     */
    fun privacy(): ImageUriView {
        this.privacy = true
        return this
    }

    /**
     * 绘制边框
     */
    fun stroke(): ImageUriView {
        this.stroke = true
        return this
    }

    /**
     * 强制刷新 忽略相同uri
     */
    fun force(): ImageUriView {
        this.force = true
        return this
    }

    /**
     * 无样式
     */
    fun none(): ImageUriView {
        this.hint = EMPTY
        this.radius = 0F
        return this
    }

    /**
     * 加载失败时显示的文字
     */
    fun hint(hint: String): ImageUriView {
        this.contentDescription = hint
        this.hint = hint
        return this
    }

    /**
     * 加载回调
     */
    fun failure(failure: () -> Unit): ImageUriView {
        this.failure = failure
        return this
    }

    /**
     * 加载Uri
     */
    fun load(uri: Any?, onResourceReady: ((Drawable) -> Unit)? = null) {
        if (uri == null) return setImageURI(null)
        if (!force && this.uri == uri) return
        this.uri = uri
        isEnabled = true
        try {
            val isLocalUri = !URLUtil.isNetworkUrl(uri.toString())
            val glide =  GlideApp.with(context.applicationContext).load(uri)
            glide.skipMemoryCache(isLocalUri)
                    .diskCacheStrategy(if (isLocalUri) DiskCacheStrategy.NONE else DiskCacheStrategy.AUTOMATIC)
                    .apply(RequestOptions().transform(CenterCrop(), RoundWithBorderTransform(context.applicationContext, radius)))
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                            failure?.invoke()
                            isEnabled = false
                            return hint.isBlank()
                        }

                        override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                            resource?.run { onResourceReady(this, onResourceReady) }
                            return false
                        }
                    })
                    .into(this)
        } catch (e: Exception) {
            //捕获生命周期导致的问题
        }
    }

    private fun onResourceReady(resource: Drawable, onResourceReady: ((Drawable) -> Unit)? = null) {
        onResourceReady?.invoke(resource)
        if (stroke) {
            resource.dominant { dominant -> strokeColor = dominant.population }
        }
    }

    override fun onDraw(canvas: Canvas?) {
        try {
            super.onDraw(canvas)
        } catch (e: Exception) {
            //捕捉因图片过大引起的崩溃
        }
        if (!isEnabled && hint.isNotBlank() && (width - padding > 0) && radius > 0) {
            paint.style = Paint.Style.FILL
            paint.color = context.getColor(R.color.colorContent)
            paint.alpha = 25
            canvas?.drawRoundRect(0F, 0F, width.toFloat(), height.toFloat(), px(radius), px(radius), paint)
            paint.alpha = 200
            val content = buildContent(width - padding)
            val layout = StaticLayout.Builder.obtain(content, 0, content.length, paint, width - padding).build()
            canvas?.save()
            canvas?.translate(width / 2F, (height - layout.height) / 2F)
            layout.draw(canvas)
            canvas?.restore()
        }
        if (stroke && strokeColor > 0) {
            paint.color = strokeColor
            paint.strokeWidth = px(0.5F)
            paint.alpha = 25
            paint.style = Paint.Style.STROKE
            canvas?.drawRoundRect(0F, 0F, width.toFloat(), height.toFloat(), px(radius), px(radius), paint)
        }
    }

    /**
     * 将Hint自动换行
     */
    private fun buildContent(max: Int): String {
        val lines = mutableListOf<String>()
        var total = 0
        var start = 0
        hint.forEachIndexed { index, c ->
            val width = FloatArray(1)
            paint.getTextWidths(c.toString(), width)
            total += ceil(width[0]).toInt()
            if (total > max) {
                lines.add(hint.substring(start, index))
                start = index
                total = ceil(width[0]).toInt()
            }
            if (index == hint.lastIndex) {
                lines.add(hint.substring(start, hint.length))
            }
        }
        val builder = StringBuilder()
        lines.subList(0, min(3, lines.size)).forEach { builder.append("$it\n") }
        return builder.toString().removeSuffix("\n")
    }
}
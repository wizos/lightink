package cn.lightink.reader.widget

import android.content.Context
import android.graphics.*
import cn.lightink.reader.R
import cn.lightink.reader.module.UIModule
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.bumptech.glide.util.Util
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min


class RoundWithBorderTransform @JvmOverloads constructor(val context: Context, private val withRadius: Float = 0F) : BitmapTransformation() {

    private val dp = context.resources.getDimension(R.dimen.dimen1)
    private val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun transform(pool: BitmapPool, source: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
        val result: Bitmap
        when {
            withRadius < 0 -> {
                val size = min(source.width, source.height)
                val square = Bitmap.createBitmap(source, (source.width - size) / 2, (source.height - size) / 2, size, size)
                result = pool.get(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(result)
                val radius = size / 2F
                shaderPaint.shader = BitmapShader(square, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                canvas.drawCircle(radius, radius, radius - 1, shaderPaint)
                square.recycle()
            }
            withRadius > 0 -> {
                result = pool.get(source.width, source.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(result)
                val radius = withRadius * dp
                val rect = RectF(0F, 0F, source.width.toFloat(), source.height.toFloat())
                shaderPaint.shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                canvas.drawRoundRect(rect, radius, radius, shaderPaint)
            }
            else -> {
                result = pool.get(source.width, source.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(result)
                shaderPaint.shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                canvas.drawRect(0F, 0F, source.width.toFloat(), source.height.toFloat(), shaderPaint)
            }
        }
        return result
    }

    private fun buildTag(): Float {
        return "${if (withRadius < 0) 3 else 1}${max(0F, withRadius)}${if (UIModule.isNightMode(context)) 1 else 0}".toFloat()
    }

    override fun equals(other: Any?): Boolean {
        if (other is RoundWithBorderTransform) {
            return other.buildTag() == buildTag()
        }
        return false
    }

    override fun hashCode(): Int {
        return Util.hashCode(ID.hashCode(), Util.hashCode(buildTag()))
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(ID.toByteArray())
        messageDigest.update(ByteBuffer.allocate(12).putFloat(buildTag()).array())
    }

    companion object {
        const val ID = "cn.lightink.glide.transform"
    }
}
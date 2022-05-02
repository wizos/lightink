package cn.lightink.reader.ktx

import cn.lightink.reader.module.EMPTY
import org.mozilla.universalchardet.UniversalDetector
import java.nio.charset.Charset

/**
 * 字节数组转字符串
 */
fun ByteArray?.string() = if (this != null) String(this) else EMPTY

/**
 * 字节数组字符集
 */
fun ByteArray.charset() = UniversalDetector(null).apply {
    handleData(this@charset, 0, this@charset.size)
    dataEnd()
}.detectedCharset?.let { Charset.forName(it) }


package cn.lightink.reader.ktx

import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.math.BigInteger
import java.security.MessageDigest

fun File?.total(): Long {
    if (this == null) return 0L
    if (isFile) return length()
    var total = 0L
    try {
        listFiles()?.forEach { file -> total += file.total() }
    } catch (e: Exception) {
        //可能不存在文件列表
    }
    return total
}

fun File.only(): File {
    if (exists()) deleteRecursively()
    return this
}

/**
 * 文件MD5值
 */
fun File.md5(): String {
    val md5 = MessageDigest.getInstance("MD5")
    val buffer = ByteArray(2048)
    val inputStream: InputStream = FileInputStream(this)
    var index = inputStream.read(buffer)
    while (index > 0) {
        md5.update(buffer, 0, index)
        index = inputStream.read(buffer)
    }
    inputStream.close()
    return BigInteger(1, md5.digest()).toString(16)
}

/**
 * 文件体积
 */
fun File.size(): String {
    val size = length()
    return when {
        //Byte
        size < 1000 -> "${size}B"
        //KB
        size < 1000 * 1024 -> "${size / 1000}K"
        //MB
        else -> "${String.format("%.1f", size / 1024000F)}M"
    }
}

//大小
val DocumentFile.size: String
    get() = length().let {
        when {
            //Byte
            it < 1000 -> "${it}B"
            //KB
            it < 1000 * 1024 -> "${it / 1000}K"
            //MB
            else -> "${String.format("%.1f", it / 1024000F)}M"
        }
    }

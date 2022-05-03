package cn.lightink.reader.module

import android.content.Context
import cn.lightink.reader.ktx.md5
import cn.lightink.reader.model.*
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset

object FontModule {

    private var mFontPath = File(EMPTY)
    var mCurrentFont = Font(SystemFont.System.display, File(EMPTY))

    fun attach(context: Context) {
        mFontPath = context.getDir("font", Context.MODE_PRIVATE)
        resetCurrentFont()
    }

    /**
     * 重置当前字体
     */
    fun resetCurrentFont() {
        mCurrentFont = try {
            val family = Preferences.get(Preferences.Key.FONT_FAMILY, EMPTY)
            val fontFile = if (family.startsWith("fonts/")) File(mFontPath, File(family).name) else File(family)
            if (fontFile.exists()) Font(getFontName(fontFile), fontFile) else Font(SystemFont.System.display, File(EMPTY))
        } catch (e: Exception) {
            Font(SystemFont.System.display, File(EMPTY))
        }
    }

    /**
     * 内置字体是否已经安装在路径里，且MD5校验完整
     */
    fun isInstalled(font: SystemFont): Boolean {
        return font.display == SystemFont.System.display || (File(mFontPath, File(font.demo).name).exists() && File(
                mFontPath,
                File(font.demo).name
        ).md5() == font.md5)
    }

    /**
     * 安装内置字体
     */
    fun install(font: SystemFont, data: ByteArray) {
        File(mFontPath, File(font.demo).name).writeBytes(data)
    }

    /**
     * 获取字体名字
     */
    fun getFontName(font: File): String {
        if (font.extension.equals("otf", true)) return font.nameWithoutExtension
        var fontName = EMPTY
        try {
            RandomAccessFile(font.absolutePath, "r").use { file ->
                val majorVersion = file.readShort()
                val minorVersion = file.readShort()
                val numOfTables = file.readShort()
                if (majorVersion != 1.toShort() || minorVersion != 0.toShort()) return@use
                file.seek(12)
                var found = false
                val buff = ByteArray(4)
                val tableDirectory = TableDirectory()
                for (i in 0 until numOfTables) {
                    file.read(buff)
                    tableDirectory.name = String(buff)
                    tableDirectory.checkSum = file.readInt()
                    tableDirectory.offset = file.readInt()
                    tableDirectory.length = file.readInt()
                    if ("name".equals(tableDirectory.name!!, ignoreCase = true)) {
                        found = true
                        break
                    } else if (tableDirectory.name == null || tableDirectory.name!!.isEmpty()) {
                        break
                    }
                }
                // not found table of name
                if (!found) return@use
                file.seek(tableDirectory.offset.toLong())
                val nameTableHeader =
                        NameTableHeader(file.readShort().toInt(), file.readShort().toInt(), file.readShort().toInt())
                val nameRecord = NameRecord()
                for (i in 0 until nameTableHeader.nRCount) {
                    nameRecord.platformID = file.readShort().toInt()
                    nameRecord.encodingID = file.readShort().toInt()
                    nameRecord.languageID = file.readShort().toInt()
                    nameRecord.nameID = file.readShort().toInt()
                    nameRecord.stringLength = file.readShort().toInt()
                    nameRecord.stringOffset = file.readShort().toInt()
                    val pos = file.filePointer
                    val bf = ByteArray(nameRecord.stringLength)
                    val index =
                            (tableDirectory.offset + nameRecord.stringOffset + nameTableHeader.storageOffset).toLong()
                    file.seek(index)
                    file.read(bf)
                    if (nameRecord.nameID == 1 || nameRecord.nameID == 4) {
                        fontName = String(bf, Charset.forName("utf-16"))
                    }
                    file.seek(pos)
                }
            }
        } catch (e: Exception) {
            return font.nameWithoutExtension
        }
        return if (fontName.isBlank()) font.nameWithoutExtension else fontName
    }
}
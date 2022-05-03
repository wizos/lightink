package cn.lightink.reader.controller

import android.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.palette.graphics.Palette
import cn.lightink.reader.MIPMAP_PATH
import cn.lightink.reader.model.Theme
import cn.lightink.reader.module.EMPTY
import cn.lightink.reader.module.Room
import cn.lightink.reader.module.UIModule
import java.io.File

class ThemeController : ViewModel() {

    lateinit var theme: Theme

    fun setupTheme(themeId: Long, isNight: Boolean) {
        theme = Room.theme().get(themeId) ?: Theme.custom(isNight)
    }

    /**
     * 通过取色更新主题配色
     */
    fun updateThemeByPalette(mipmap: File, palette: Palette) {
        val background = palette.dominantSwatch?.rgb?.let { Color.rgb((Color.red(it) * 0.95F).toInt(), (Color.green(it) * 0.95F).toInt(), (Color.blue(it) * 0.95F).toInt()) } ?: palette.dominantSwatch?.rgb
        theme.background = background ?: theme.background
        theme.foreground = palette.dominantSwatch?.rgb ?: theme.foreground
        theme.content = palette.dominantSwatch?.bodyTextColor ?: theme.content
        theme.secondary = palette.dominantSwatch?.titleTextColor ?: theme.secondary
        theme.control = palette.mutedSwatch?.rgb ?: theme.control
        theme.mipmap = mipmap.nameWithoutExtension
    }

    /**
     * 保存主题
     */
    fun saveTheme(): String {
        when {
            //更新主题
            Room.theme().get(theme.id) != null -> {
                if (theme.mipmap.endsWith("mipmap")) {
                    File(UIModule.getMipmapPath(theme)).renameTo(File(MIPMAP_PATH, theme.id.toString()))
                    theme.mipmap = theme.id.toString()
                }
                Room.theme().update(theme)
            }
            Room.theme().isExistName(theme.name) -> return "存在同名主题"
            else -> {
                if (theme.mipmap.endsWith("mipmap")) {
                    File(UIModule.getMipmapPath(theme)).renameTo(File(MIPMAP_PATH, theme.id.toString()))
                    theme.mipmap = theme.id.toString()
                }
                Room.theme().insert(theme)
            }
        }
        return EMPTY
    }

}
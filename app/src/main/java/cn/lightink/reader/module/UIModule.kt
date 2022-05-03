package cn.lightink.reader.module

import android.content.Context
import android.graphics.drawable.Drawable
import cn.lightink.reader.MIPMAP_PATH
import cn.lightink.reader.R
import cn.lightink.reader.model.THEME_LIGHT
import cn.lightink.reader.model.THEME_NIGHT
import cn.lightink.reader.model.Theme
import java.io.File

object UIModule {

    /***
     * 深色模式 通过识别某个色值
     */
    fun isNightMode(context: Context) = context.getColor(R.color.colorStroke) == 0xFF101010.toInt()

    /**
     * 获取配置中的主题
     */
    fun getConfiguredTheme(context: Context) = if (isNightMode(context)) getNightTheme() else getLightTheme()


    /**
     * 获取配置中的亮色主题
     */
    private fun getLightTheme() = Room.theme().get(Preferences.get(Preferences.Key.THEME_LIGHT_ID, THEME_LIGHT.id)) ?: THEME_LIGHT

    /**
     * 获取配置中的暗色主题
     */
    private fun getNightTheme() = Room.theme().get(Preferences.get(Preferences.Key.THEME_NIGHT_ID, THEME_NIGHT.id)) ?: THEME_NIGHT

    /**
     * 获取主题的纹理图
     */
    fun getMipmapByTheme(theme: Theme) = Drawable.createFromPath(getMipmapPath(theme))

    /**
     * 获取主题的纹理图路径
     */
    fun getMipmapPath(theme: Theme) = File(MIPMAP_PATH, theme.mipmap).absolutePath

}
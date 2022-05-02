package cn.lightink.reader.module

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Base64
import androidx.core.content.edit
import cn.lightink.reader.ktx.decode
import cn.lightink.reader.model.SettingsResponse
import java.io.File
import java.util.*

object Preferences : SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var preferencesLocal: SharedPreferences
    private lateinit var preferencesCloud: SharedPreferences
    private lateinit var configPath: File

    fun attach(context: Context) {
        preferencesLocal = context.getSharedPreferences("settings_1", Context.MODE_PRIVATE)
        preferencesCloud = context.getSharedPreferences("settings_2", Context.MODE_PRIVATE)
        preferencesCloud.registerOnSharedPreferenceChangeListener(this)
        configPath = File(context.cacheDir.parent, "shared_prefs/settings_2.xml")
        if (preferencesLocal.getString(Key.UUID.name, null).isNullOrBlank()) {
            put(Key.UUID,
                "${System.currentTimeMillis()}${((Math.random() * 7 + 1) * 1000).toInt()}:${Build.MODEL}:${Build.MANUFACTURER}".uppercase(
                    Locale.CHINESE
                )
            )
        }
    }

    fun update(response: SettingsResponse) {
        configPath.writeBytes(Base64.decode(response.config.decode(), Base64.DEFAULT))
        put(Key.SETTINGS_SYNC_VERSION, response.version)
        Notify.post(Notify.RestartEvent())
    }

    fun put(key: Key, value: Any) = if (key in ignoreKeys) {
        put(preferencesLocal, key, value)
    } else {
        put(preferencesCloud, key, value)
    }

    private fun put(preferences: SharedPreferences, key: Key, value: Any) = preferences.edit(true) {
        when (value) {
            is Boolean -> putBoolean(key.name, value)
            is Int -> putInt(key.name, value)
            is Long -> putLong(key.name, value)
            is Float -> putFloat(key.name, value)
            else -> putString(key.name, value.toString())
        }
    }

    fun <T> get(key: Key, defaultValue: T) = if (key in ignoreKeys) {
        get(preferencesLocal, key, defaultValue)
    } else {
        get(preferencesCloud, key, defaultValue)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> get(preferences: SharedPreferences, key: Key, defaultValue: T): T {
        return try {
            when (defaultValue) {
                is Boolean -> preferences.getBoolean(key.name, defaultValue) as T
                is Int -> preferences.getInt(key.name, defaultValue) as T
                is Long -> preferences.getLong(key.name, defaultValue) as T
                is Float -> preferences.getFloat(key.name, defaultValue) as T
                else -> preferences.getString(key.name, defaultValue.toString()) as T
            }
        } catch (e: Exception) {
            defaultValue
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
    }

    private val ignoreKeys = listOf(Key.JWT, Key.TOKEN, Key.UUID, Key.USER, Key.HAS_NAVIGATION, Key.HAS_NOTCH, Key.READER_HEIGHT, Key.BOOKSHELF, Key.LAST_FEED, Key.SIGN, Key.BOOKSHELF_SYNC_VERSION,
            Key.BOOKSOURCE_SYNC_VERSION, Key.SETTINGS_SYNC_VERSION, Key.LIGHT, Key.BOOKSHELF_SYNC_TIME, Key.BOOK_CHECK_UPDATE_TIME, Key.FEED_CHECK_UPDATE_TIME, Key.BOOKSHELF_TIPS)

    enum class Key {
        //JWT
        JWT,
        //TOKEN
        TOKEN,
        //UUID
        UUID,
        //用户ID
        USER,
        //存在导航栏
        HAS_NAVIGATION,
        //存在异形屏
        HAS_NOTCH,
        //阅读器高度
        READER_HEIGHT,
        //书架
        BOOKSHELF,
        //书架Tips
        BOOKSHELF_TIPS,
        //最后浏览的时刻
        LAST_FEED,
        //签到奖励
        SIGN,
        //首次换源提示
        FIRST_CHANGE_BOOK_SOURCE,
        //同步书架版本号
        BOOKSHELF_SYNC_VERSION,
        //同步书源版本号
        BOOKSOURCE_SYNC_VERSION,
        //同步配置
        SETTINGS_SYNC_VERSION,

        //日间模式
        LIGHT,
        //自动模式
        FOLLOW_SYSTEM,
        //中等字号
        MIDDLE_FONT_SIZE,
        //图书详情目录排序
        BOOK_DETAIL_SORT,

        //书架同步时间
        BOOKSHELF_SYNC_TIME,

        //书架更新机制
        BOOK_CHECK_UPDATE_TYPE,
        //书架更新时间
        BOOK_CHECK_UPDATE_TIME,

        //时刻更新时间
        FEED_CHECK_UPDATE_TIME,

        //阅读偏好 字体
        FONT_FAMILY,
        //阅读偏好 字号
        FONT_SIZE,
        //阅读偏好 行间距
        LINE_SPACING,
        //阅读偏好 亮度
        BRIGHTNESS,
        //阅读偏好 首行缩进
        FIRST_LINE_INDENT,
        //阅读偏好 段落间距
        PARAGRAPH_DISTANCE,
        //阅读偏好 字符间隔
        LETTER_SPACING,
        //阅读偏好 日间主题
        THEME_LIGHT_ID,
        //阅读偏好 夜间主题
        THEME_NIGHT_ID,
        //阅读偏好 状态栏
        STATUS_BAR,
        //阅读偏好 导航栏
        NAVIGATION_BAR,
        //阅读偏好 异形屏
        NOTCH_BAR,
        //阅读偏好 音量键翻页
        VOLUME_KEY,
        //阅读偏好 全屏下一页
        ONLY_NEXT,
        //阅读偏好 点击翻页
        TURN_CLICK,
        //阅读偏好 上下翻页
        TURN_VERTICAL,
        //阅读偏好 翻页动画
        TURN_ANIMATE,
        //阅读偏好 屏幕常亮
        SCREEN_BRIGHT,
        //阅读偏好 字体加粗
        FONT_BOLD,
        //阅读偏好 文本长按
        TEXT_LONG_CLICKABLE,
        //阅读偏好 下拉书签
        TEXT_PULL_BOOKMARK,
        //阅读偏好 背景跟随
        MIPMAP_FOLLOW,
        //阅读偏好 朗读时间
        TTS_TIMER,
        //阅读偏好 内置状态栏
        CUSTOM_STATUS_BAR,
        //阅读偏好 显示标题
        SHOW_TITLE
    }
}
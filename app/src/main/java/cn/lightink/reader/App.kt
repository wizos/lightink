package cn.lightink.reader

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import cn.lightink.reader.module.*
import cn.lightink.reader.net.Http
import com.blankj.ALog
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.Excludes
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpLibraryGlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.Option
import com.jayway.jsonpath.spi.json.GsonJsonProvider
import com.jayway.jsonpath.spi.mapper.GsonMappingProvider
import es.dmoral.toasty.Toasty
import java.io.File
import java.io.InputStream
import java.util.*

@Suppress("unused")
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        //配置Log
        ALog.init(this).setLogSwitch(BuildConfig.DEBUG).setGlobalTag("轻墨")
        //配置Toast
        Toasty.Config.getInstance().setTextSize(15).apply()
        //配置JsonPath使用Gson解析
        Configuration.setDefaults(object : Configuration.Defaults {
            override fun jsonProvider() = GsonJsonProvider()
            override fun mappingProvider() = GsonMappingProvider()
            override fun options() = EnumSet.noneOf(Option::class.java)
        })
        Toasty.Config.getInstance().setTextSize(14).apply()
        //配置GL
        registerActivityLifecycleCallbacks(GL)
        //配置路径
        BOOK_PATH = getDir("book", Context.MODE_PRIVATE)
        BOOK_CACHE_PATH = cacheDir
        File(BOOK_CACHE_PATH, "book").mkdirs()
        MIPMAP_PATH = File(filesDir, "mipmap").apply { mkdirs() }
        //配置数据库
        Preferences.attach(applicationContext)
        Room.attach(applicationContext)
        //配置主题
        setupTheme(false)
        //配置字体
        FontModule.attach(this)
        //配置通知渠道
        NotificationHelper.createNotificationChannels(applicationContext)
    }

    /**
     * 配置主题
     * @param fromUser 用户手动切换时忽略跟随系统选项
     */
    fun setupTheme(fromUser: Boolean = true) = if (!fromUser && Preferences.get(Preferences.Key.FOLLOW_SYSTEM, false)) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    } else {
        AppCompatDelegate.setDefaultNightMode(if (Preferences.get(Preferences.Key.LIGHT, true)) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES)
    }
}

//图书存储目录
var BOOK_PATH = File(EMPTY)

//图书缓存目录
var BOOK_CACHE_PATH = File(EMPTY)

//主题图片目录
var MIPMAP_PATH = File(EMPTY)

@Excludes(value = [OkHttpLibraryGlideModule::class])
@GlideModule
class MyAppGlideModule : AppGlideModule() {

    //使用内置的网络客户端
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        registry.replace(GlideUrl::class.java, InputStream::class.java, OkHttpUrlLoader.Factory(Http.client))
    }

}
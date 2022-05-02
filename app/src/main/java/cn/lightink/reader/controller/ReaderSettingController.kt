package cn.lightink.reader.controller

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.lightink.reader.model.Font
import cn.lightink.reader.model.SystemFont
import cn.lightink.reader.module.FontModule
import cn.lightink.reader.module.Notify
import cn.lightink.reader.module.Preferences
import cn.lightink.reader.net.RESTful
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileFilter

class ReaderSettingController : ViewModel() {

    /**
     * 查询外置字体
     */
    fun queryFont(folder: File?): LiveData<List<Font>> {
        val result = MutableLiveData<List<Font>>()
        viewModelScope.launch(Dispatchers.IO) {
            if (folder?.exists() == false) {
                folder.mkdirs()
            }
            if (folder?.exists() == true) {
                result.postValue(folder.listFiles(buildFileFilter())?.map { Font(FontModule.getFontName(it), it) })
            }
        }
        return result
    }

    /**
     * 使用字体
     */
    fun useFont(font: Any) {
        when (font) {
            is SystemFont -> Preferences.put(Preferences.Key.FONT_FAMILY, font.demo).run {
                FontModule.resetCurrentFont()
                Notify.post(Notify.FontChangedEvent())
            }
            is Font -> Preferences.put(Preferences.Key.FONT_FAMILY, font.path.absolutePath).run {
                FontModule.resetCurrentFont()
                Notify.post(Notify.FontChangedEvent())
            }
        }
    }

    /**
     * 下载字体
     */
    fun downloadFont(font: SystemFont): LiveData<Boolean> {
        val result = MutableLiveData<Boolean>()
        viewModelScope.launch(Dispatchers.IO) {
            result.postValue(true)
            val response = RESTful.get<ByteArray>(font.link)
            if (response.isSuccessful && response.data?.isNotEmpty() == true) {
                FontModule.install(font, response.data)
            }
            result.postValue(false)
        }
        return result
    }

    /**
     * 构建文件过滤器，只支持.ttf或.otf
     */
    private fun buildFileFilter() = FileFilter { file -> file?.isFile == true && (file.extension.equals("ttf", true) || file.extension.equals("otf", true)) }
}
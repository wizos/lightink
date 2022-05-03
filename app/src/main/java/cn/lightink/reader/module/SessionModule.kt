package cn.lightink.reader.module

import cn.lightink.reader.ktx.md5

object Session {

    fun uuid() = Preferences.get(Preferences.Key.UUID, EMPTY).md5()

}
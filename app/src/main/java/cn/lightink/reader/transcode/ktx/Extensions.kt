package cn.lightink.reader.transcode.ktx

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

val json = Json {
    //非空属性强制设置默认值
    coerceInputValues = true
    //忽略未知属性
    ignoreUnknownKeys = true
    //设置默认值
    encodeDefaults = true
    //格式限定宽泛
    isLenient = true
}

/**
 * Json字符串转实体
 */
inline fun <reified T> String.decodeJson(): T {
    return Json {
        //非空属性强制设置默认值
        coerceInputValues = true
        //忽略未知属性
        ignoreUnknownKeys = true
        //设置默认值
        encodeDefaults = true
        //格式限定宽泛
        isLenient = true
    }.decodeFromString<T>(this)
}
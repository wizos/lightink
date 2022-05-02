package cn.lightink.reader.model

/**
 * Http Client 请求结果
 */
data class Result<T>(val code: Int, val data: T?, val message: String) {

    val isSuccessful: Boolean
        get() = code in 200..299

}

/**
 * 设置
 */
data class SettingsResponse(val version: Long, val config: String)
package cn.lightink.reader.transcode.entity

import kotlinx.serialization.Serializable

/**
 * 错误信息
 * @property code 错误号
 * @property message 消息
 */
@Serializable
data class TranscodeException(val code: Int, override val message: String) : kotlin.Exception(message)
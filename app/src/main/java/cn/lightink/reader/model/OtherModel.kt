package cn.lightink.reader.model

import cn.lightink.reader.module.EMPTY

/**
 * 开源库声明
 * @property name       名字
 * @property link       链接
 * @property license    协议
 */
data class OpenSource(val name: String, val link: String, val license: String)

/**
 * 封面
 */
data class Cover(val url: String, val author: String = EMPTY)
package cn.lightink.reader.ktx

import okhttp3.Response

//请求地址
val Response.url: String
    get() = request.url.toString()
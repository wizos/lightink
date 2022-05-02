package cn.lightink.reader.net

import cn.lightink.reader.BuildConfig
import cn.lightink.reader.ktx.string
import cn.lightink.reader.model.Result
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.min

object RESTful {

    private const val URL = "https://xxx.xxx.xxx"

    private val client by lazy {
        OkHttpClient.Builder().addInterceptor(HttpLoggingInterceptor().apply { level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE }).connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).writeTimeout(10, TimeUnit.SECONDS).build()
    }


    /**
     * GET请求
     */
    internal inline fun <reified T> get(api: String, params: Map<String, Any>? = null): Result<T> {
        val data = signature(Request.Builder().url(if (params != null) {
            val query = StringBuilder().apply {
                params.map { "&${it.key}=${it.value}" }.forEach { append(it) }
            }.toString()
            "$URL$api?${query.substring(min(1, query.length))}"
        } else {
            "$URL$api"
        }).get(), params?.toMutableMap() ?: mutableMapOf())
        return onResponse(data)
    }

    /**
     * POST请求
     */
    internal inline fun <reified T> post(api: String, params: Map<String, Any>? = null): Result<T> {
        val body = FormBody.Builder().apply { params?.forEach { add(it.key, it.value.toString()) } }.build()
        val data = signature(Request.Builder().url("$URL$api").post(body), params?.toMutableMap() ?: mutableMapOf())
        return onResponse(data)
    }

    /**
     * POST JSON
     */
    internal inline fun <reified T> json(api: String, params: String): Result<T> {
        val data = signature(Request.Builder().url("$URL$api").post(params.toRequestBody("application/json".toMediaType())), mutableMapOf())
        return onResponse(data)
    }

    /**
     * POST MULTIPART
     */
    internal inline fun <reified T> multipart(api: String, params: Map<String, Any>? = null): Result<T> {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
        params?.forEach { entry ->
            if (entry.value is File) {
                body.addFormDataPart(entry.key, (entry.value as File).name, (entry.value as File).asRequestBody("application/zip".toMediaType()))
            } else {
                body.addFormDataPart(entry.key, entry.value.toString())
            }
        }
        val data = signature(Request.Builder().url("$URL$api").post(body.build()), params?.toMutableMap() ?: mutableMapOf())
        return onResponse(data)
    }

    /**
     * PUT请求
     */
    internal inline fun <reified T> put(api: String, params: Map<String, Any>? = null): Result<T> {
        val body = FormBody.Builder().apply { params?.forEach { add(it.key, it.value.toString()) } }.build()
        val data = signature(Request.Builder().url("$URL$api").put(body), params?.toMutableMap() ?: mutableMapOf())
        return onResponse(data)
    }

    /**
     * DELETE请求
     */
    internal inline fun <reified T> delete(api: String, params: Map<String, Any>? = null): Result<T> {
        val body = FormBody.Builder().apply { params?.forEach { add(it.key, it.value.toString()) } }.build()
        val data = signature(Request.Builder().url("$URL$api").delete(body), params?.toMutableMap() ?: mutableMapOf())
        return onResponse(data)
    }

    /**
     * 处理结果
     */
    private inline fun <reified T> onResponse(result: Result<ByteArray>): Result<T> {
        val body = try {
            if (result.isSuccessful && result.data != null) {
                when (T::class) {
                    Unit::class -> Unit as T
                    ByteArray::class -> result.data as T
                    String::class -> result.data.string() as T
                    List::class -> Gson().fromJson(result.data.string(), object : TypeToken<T>() {}.type)
                    else -> Gson().fromJson(result.data.string(), T::class.java)
                }
            } else null
        } catch (e: Exception) {
            //可能存在Gson类型转化错误
            null
        }
        return Result(result.code, body, result.message)
    }

    private fun signature(request: Request.Builder, params: MutableMap<String, Any>): Result<ByteArray> {
        return execute(request)
    }

    /**
     * 执行请求并将结果包装成Result对象
     */
    private fun execute(requestBuilder: Request.Builder, autoRefresh: Boolean = true): Result<ByteArray> {
        try {
            val request = requestBuilder.build()
            val response = client.newCall(request).execute()
            val data = response.body?.bytes()
            return Result(response.code, data, data.string())
        } catch (e: Exception) {
            return Result(4000, null, "网络异常")
        }
    }

}


package cn.lightink.reader.net

import android.annotation.SuppressLint
import android.util.Base64
import cn.lightink.reader.BuildConfig
import cn.lightink.reader.ktx.encode
import cn.lightink.reader.ktx.string
import cn.lightink.reader.model.BookSource
import cn.lightink.reader.model.Result
import cn.lightink.reader.module.LIMIT
import cn.lightink.reader.module.Session
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object BookSourceStore {

    //BASE URL
    private const val URL = "https://api.booksource.store"
    //列表
    private const val LIST = "/repo/list"
    //详情
    private const val DETAIL = "/repo/%d"

    //客户端
    private val client by lazy {
        OkHttpClient.Builder().addInterceptor(HttpLoggingInterceptor().apply { level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE }).connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).writeTimeout(10, TimeUnit.SECONDS).build()
    }

    /**
     * 查询列表
     */
    fun query(size: Int, sort: String, key: String? = null): Result<List<BookSource>> {
        return onResponse(execute(Request.Builder().url("$URL$LIST?page=${size / LIMIT}&size=$LIMIT&sort=$sort&key=$key")))
    }

    /**
     * 下载书源
     */
    fun download(id: Int, version: Int? = null): Result<BookSource> {
        return onResponse(execute(Request.Builder().url("$URL${DETAIL.format(id)}?version=${version ?: 0}&uuid=${Session.uuid()}")))
    }

    /**
     * 评分
     */
    fun score(id: Int, score: Float): Result<Unit> {
        val body = FormBody.Builder().add("score", score.toString()).add("uuid", Session.uuid()).build()
        return onResponse(execute(Request.Builder().url("$URL${DETAIL.format(id)}").put(body)))
    }

    /**
     * 执行请求并将结果包装成Result对象
     */
    private fun execute(request: Request.Builder): Result<ByteArray> = try {
        val timestamp = System.currentTimeMillis()
        request.addHeader("now", timestamp.toString())
        request.addHeader("key", encode(timestamp))
        val response = client.newCall(request.build()).execute()
        val data = response.body?.bytes()
        Result(response.code, data, data.string())
    } catch (e: Exception) {
        Result(4000, null, "网络异常")
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

    @SuppressLint("GetInstance")
    private fun encode(timestamp: Long): String {
        val cipher = Cipher.getInstance("AES")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(timestamp.toString(2).substring(2..17).toByteArray(), "AES"))
        return Base64.encodeToString(cipher.doFinal(timestamp.toString(8).toByteArray()), Base64.NO_PADDING).encode()
    }

}
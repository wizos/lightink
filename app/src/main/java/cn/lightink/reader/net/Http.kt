package cn.lightink.reader.net

import cn.lightink.reader.BuildConfig
import cn.lightink.reader.ktx.fromJson
import cn.lightink.reader.ktx.string
import cn.lightink.reader.model.Result
import cn.lightink.reader.module.EMPTY
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.resume

/**
 * 网络请求客户端
 */
object Http {

    internal val client by lazy {
        val x509TrustManager = buildX509TrustManager()
        OkHttpClient.Builder()
                .addInterceptor(HttpLoggingInterceptor().apply { level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE })
                .protocols(listOf(Protocol.HTTP_1_1))
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .hostnameVerifier(HostnameVerifier { _, _ -> true })
                .sslSocketFactory(buildSSLSocketFactory(x509TrustManager), x509TrustManager)
                .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT))
                .build()
    }

    /**
     * 下载请求
     */
    suspend fun download(url: String) = suspendCancellableCoroutine<Result<ByteArray>> { continuation ->
        val request = client.newCall(Request.Builder().url(url).get().build())
        continuation.invokeOnCancellation {
            request.cancel()
            continuation.resume(Result(400, byteArrayOf(), EMPTY))
        }
        val response = try {
            request.execute()
        } catch (e: Exception) {
            //捕捉请求地址错误或客户端网络错误
            return@suspendCancellableCoroutine continuation.resume(Result(400, byteArrayOf(), e.message.orEmpty()))
        }
        continuation.resume(Result(response.code, response.body?.bytes(), response.message))
    }

    /**
     * GET请求
     */
    internal suspend inline fun <reified T> get(url: String) = suspendCancellableCoroutine<Result<T>> { continuation ->
        try {
            val request = client.newCall(Request.Builder().url(url).get().build())
            continuation.invokeOnCancellation {
                request.cancel()
                continuation.resume(Result(400, null as? T, EMPTY))
            }
            val response = request.execute()
            val body = response.body?.bytes()
            val data = if (response.isSuccessful && body?.isNotEmpty() == true) try {
                when (T::class) {
                    Unit::class -> Unit as T
                    ByteArray::class -> body as T
                    String::class -> body.string() as T
                    List::class -> Gson().fromJson(body.string(), object : TypeToken<T>() {}.type)
                    else -> body.string().fromJson()
                }
            } catch (e: Exception) {
                //范型转换失败说明本次请求结果错误
                null
            } else null
            continuation.resume(Result(response.code, data, body.string()))
        } catch (e: Exception) {
            //捕捉请求地址错误或客户端网络错误
            continuation.resume(Result(400, null as? T, e.message ?: "网络访问异常"))
        }
    }

    //无suspend
    internal inline fun <reified T> getImmediateness(url: String): Result<T> {
        try {
            val request = client.newCall(Request.Builder().url(url).get().build())
            val response = request.execute()
            val body = response.body?.bytes()
            val data = if (response.isSuccessful && body?.isNotEmpty() == true) try {
                when (T::class) {
                    Unit::class -> Unit as T
                    ByteArray::class -> body as T
                    String::class -> body.string() as T
                    List::class -> Gson().fromJson(body.string(), object : TypeToken<T>() {}.type)
                    else -> Gson().fromJson(body.string(), T::class.java)
                }
            } catch (e: Exception) {
                //范型转换失败说明本次请求结果错误
                null
            } else null
            return Result(response.code, data, body.string())
        } catch (e: Exception) {
            //捕捉请求地址错误或客户端网络错误
            return Result(400, null as? T, "网址无效")
        }
    }

    private fun buildSSLSocketFactory(x509TrustManager: X509TrustManager): SSLSocketFactory {
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf(x509TrustManager), SecureRandom())
        return context.socketFactory
    }

    private fun buildX509TrustManager() = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers() = arrayOf<X509Certificate>()
    }

}
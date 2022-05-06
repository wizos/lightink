package cn.lightink.reader.transcode

import android.content.Context
import android.content.res.AssetManager
import androidx.startup.Initializer
import java.io.File

object DependenciesManager {

    private lateinit var asset: AssetManager
    private lateinit var cache: File

    fun initialize(context: Context): DependenciesManager {
        asset = context.applicationContext.assets
        cache = context.cacheDir
        return this
    }

    fun load(dependency: String) = when (dependency) {
        CRYPTO_JS -> asset.open("crypto-js.min.js").reader().readText()
        else -> ""
    }

    fun buildZipFile(filename: String): File {
        return File(cache, filename)
    }

    const val CRYPTO_JS = "crypto-js"

}

class DependenciesManagerInitializer : Initializer<DependenciesManager> {

    override fun create(context: Context): DependenciesManager {
        return DependenciesManager.initialize(context)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }

}
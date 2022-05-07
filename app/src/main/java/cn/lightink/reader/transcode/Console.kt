package cn.lightink.reader.transcode

import android.util.Log

object Console {

    var callback: ((String, Type) -> Unit)? = this::printlnImpl

    private fun printlnImpl(message: String, type: Console.Type = Console.Type.Log) {
        when (type) {
            Console.Type.Build -> Log.i("Console Build", message)
            Console.Type.Headers -> Log.i("Console Headers", message)
            Console.Type.Response -> Log.i("Console Response", message)
            Console.Type.Log -> {
                Log.i("Console Log", message)
            }
        }
    }

    fun println(message: String, type: Type = Type.Log) {
        callback?.invoke(message, type)
    }

    enum class Type {
        Log, Build, Headers, Response,
    }

}
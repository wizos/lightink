package cn.lightink.reader.transcode

object Console {

    var callback: ((String, Type) -> Unit)? = null

    fun println(message: String, type: Type = Type.Log) {
        callback?.invoke(message, type)
    }

    enum class Type {
        Log, Build, Headers, Response,
    }

}
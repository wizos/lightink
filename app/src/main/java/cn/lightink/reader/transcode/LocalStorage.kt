package cn.lightink.reader.transcode

object LocalStorage {

    var callback: Callback? = null

    interface Callback {
        fun getItem(host: String, key: String): String
        fun setItem(host: String, key: String, value: String)
        fun removeItem(host: String, key: String)
        fun key(host: String): Array<String>
        fun length(host: String): Int
        fun clear(host: String)
    }

}
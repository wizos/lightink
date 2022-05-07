package cn.lightink.reader.transcode

import cn.lightink.reader.model.ThirdAccount
import cn.lightink.reader.module.Room
import org.json.JSONObject

object LocalStorage {

    var callback: Callback? = Callback()

    class Callback {
        fun getItem(host: String, key: String): String {
            val third = Room.thirdAccount()
                .getByUrl(ThirdAccount.CATEGORY_SESSION, host) ?: return ""
            try {
                val json = JSONObject(third.password)
                return if (json.has(key)) json.getString(key) else ""
            } catch (e: Exception) {
                return ""
            }
        }
        fun setItem(host: String, key: String, value: String) {
            val third =
                Room.thirdAccount().getByUrl(ThirdAccount.CATEGORY_SESSION, host)
            try {
                val json = if (third != null) JSONObject(third.password) else JSONObject()
                json.put(key, value)
                if (third != null) {
                    Room.thirdAccount().update(third.apply { password = json.toString() })
                } else {
                    Room.thirdAccount().insert(
                        ThirdAccount(
                            "",
                            json.toString(),
                            host,
                            ThirdAccount.CATEGORY_SESSION,
                        )
                    )
                }
            } catch (e: Exception) {
                //
            }
        }
        fun removeItem(host: String, key: String) {
            val third = Room.thirdAccount()
                .getByUrl(ThirdAccount.CATEGORY_SESSION, host) ?: return
            try {
                val json = JSONObject(third.password)
                if (json.has(key)) json.remove(key)
                Room.thirdAccount().update(third.apply { password = json.toString() })
            } catch (e: Exception) {
                //
            }
        }
        fun key(host: String): Array<String> {
            val third = Room.thirdAccount()
                .getByUrl(ThirdAccount.CATEGORY_SESSION, host) ?: return arrayOf<String>()
            try {
                val json = JSONObject(third.password)
                val keys = mutableListOf<String>()
                json.keys().forEach { keys.add(it) }
                return keys.toTypedArray()
            } catch (e: Exception) {
                return arrayOf()
            }
        }
        fun length(host: String): Int {
            val third = Room.thirdAccount()
                .getByUrl(ThirdAccount.CATEGORY_SESSION, host) ?: return 0
            try {
                return JSONObject(third.password).keys().asSequence().toList().size
            } catch (e: Exception) {
                return 0
            }
        }
        fun clear(host: String) {
            val third = Room.thirdAccount()
                .getByUrl(ThirdAccount.CATEGORY_SESSION, host) ?: return
            Room.thirdAccount().delete(third)
        }
    }

}
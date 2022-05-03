package cn.lightink.reader.module

import android.net.ConnectivityManager
import android.net.Network

/**
 * 网络监听
 */
object NetworkCallback : ConnectivityManager.NetworkCallback() {

    private var isAvailable = true

    override fun onLost(network: Network) {
        super.onLost(network)
        isAvailable = false
    }

    override fun onAvailable(network: Network) {
        super.onAvailable(network)
        if (isAvailable) return
        isAvailable = true
    }

}
package com.calorie.diary.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局网络状态监听。
 *
 * 维护「当前具备上网能力的网络集合」，任一网络可用即视为在线，
 * 避免 WiFi/蜂窝切换时状态抖动。UI 通过 [online] 订阅，请求前用 [isOnline] 快速失败。
 */
object NetworkMonitor {

    /** 乐观初值：未 start 时不做离线误判。 */
    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private val capableNetworks = HashMap<Network, Boolean>()

    @Volatile
    private var started = false

    @Volatile
    private var manager: ConnectivityManager? = null

    /** 在 Application#onCreate 调用一次。 */
    fun start(context: Context) {
        if (started) return
        started = true
        val cm = runCatching {
            context.applicationContext.getSystemService(ConnectivityManager::class.java)
        }.getOrNull() ?: return
        manager = cm
        seed(cm)
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    synchronized(capableNetworks) {
                        capableNetworks[network] = hasInternet(cm, network)
                    }
                    publish()
                }

                override fun onLost(network: Network) {
                    synchronized(capableNetworks) { capableNetworks.remove(network) }
                    publish()
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    synchronized(capableNetworks) {
                        capableNetworks[network] =
                            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    }
                    publish()
                }
            })
        }
    }

    /** 同步快照，供请求发起前快速失败。 */
    fun isOnline(): Boolean = _online.value

    /** 主动刷新一次（例如回到前台时）。 */
    fun refresh(context: Context) {
        val cm = manager ?: runCatching {
            context.applicationContext.getSystemService(ConnectivityManager::class.java)
        }.getOrNull() ?: return
        manager = cm
        seed(cm)
    }

    private fun seed(cm: ConnectivityManager) {
        synchronized(capableNetworks) {
            capableNetworks.clear()
            cm.activeNetwork?.let { capableNetworks[it] = hasInternet(cm, it) }
        }
        publish()
    }

    private fun publish() {
        val value = synchronized(capableNetworks) { capableNetworks.values.any { it } }
        if (_online.value != value) _online.value = value
    }

    private fun hasInternet(cm: ConnectivityManager, network: Network): Boolean =
        runCatching {
            cm.getNetworkCapabilities(network)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }.getOrDefault(true)
}

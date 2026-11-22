package com.calorie.diary.data.network

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * 统一的网络故障类型。表现层据此给出「检查网络 / 稍后重试 / 去改密钥」等可操作提示，
 * 而不是把 UnknownHostException 之类的技术信息直接抛给用户。
 */
enum class NetworkErrorKind {
    OFFLINE,       // 设备当前没有可用网络
    TIMEOUT,       // 连接或读取超时
    DNS,           // 域名解析失败
    CONNECTION,    // 连接被拒绝 / 中断 / TLS 失败
    SERVER,        // 服务端 5xx
    RATE_LIMIT,    // 请求过于频繁（429）
    AUTH,          // 401 / 403
    NOT_FOUND,     // 404
    BAD_RESPONSE,  // 返回内容无法解析
    UNKNOWN
}

/** 带分类信息的网络异常；继承 IOException，兼容既有的 is IOException 判断。 */
open class NetworkException(
    val kind: NetworkErrorKind,
    override val message: String? = null,
    cause: Throwable? = null
) : IOException(message, cause)

/** 把任意异常归类成 [NetworkErrorKind]；设备离线时统一优先归为 OFFLINE。 */
fun Throwable.toNetworkErrorKind(online: Boolean = NetworkMonitor.isOnline()): NetworkErrorKind =
    when (this) {
        is NetworkException -> kind
        is SocketTimeoutException, is InterruptedIOException -> NetworkErrorKind.TIMEOUT
        is UnknownHostException -> if (online) NetworkErrorKind.DNS else NetworkErrorKind.OFFLINE
        is ConnectException, is SocketException, is NoRouteToHostException ->
            if (online) NetworkErrorKind.CONNECTION else NetworkErrorKind.OFFLINE
        is SSLException -> NetworkErrorKind.CONNECTION
        is IOException -> if (online) NetworkErrorKind.CONNECTION else NetworkErrorKind.OFFLINE
        else -> NetworkErrorKind.UNKNOWN
    }

/** 瞬时故障值得自动重试：离线、鉴权、参数错误重试无意义。 */
val NetworkErrorKind.isTransient: Boolean
    get() = this == NetworkErrorKind.TIMEOUT ||
        this == NetworkErrorKind.CONNECTION ||
        this == NetworkErrorKind.DNS ||
        this == NetworkErrorKind.SERVER

/**
 * 瞬时故障自动重试（指数退避）。
 *
 * @param attempts 总尝试次数，含首次
 * @param initialDelayMs 首次退避时长，之后按 2 倍递增
 */
suspend fun <T> retryOnTransient(
    attempts: Int = 2,
    initialDelayMs: Long = 500L,
    online: () -> Boolean = { NetworkMonitor.isOnline() },
    block: suspend () -> T
): T {
    require(attempts >= 1) { "attempts 至少为 1" }
    var backoff = initialDelayMs
    var attempt = 0
    while (true) {
        try {
            return block()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            attempt++
            val retryable = attempt < attempts && e.toNetworkErrorKind(online()).isTransient
            if (!retryable) throw e
            delay(backoff)
            backoff *= 2
        }
    }
}

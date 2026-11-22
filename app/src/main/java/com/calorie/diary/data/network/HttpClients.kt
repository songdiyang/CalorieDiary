package com.calorie.diary.data.network

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * 统一 OkHttp 客户端：给所有外网请求设置可控超时，避免弱网下无限等待。
 * vision 面向大图推理，读超时放宽；sync 面向轻量 JSON 接口，快速失败。
 *
 * 超时链路约定（云端代理模式）：App read 100s > FC 函数超时 90s > 上游 urlopen 80s，
 * 保证客户端总是最后放弃，用户拿到的是可读错误码而不是连接被提前掐断。
 */
object HttpClients {

    val vision: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(100, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val sync: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

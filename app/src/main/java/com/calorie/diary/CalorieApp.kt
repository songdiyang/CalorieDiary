package com.calorie.diary

import android.app.Application
import com.calorie.diary.data.network.NetworkMonitor

class CalorieApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 全局网络状态监听，供离线提示与请求前快速失败使用
        NetworkMonitor.start(this)
    }
}

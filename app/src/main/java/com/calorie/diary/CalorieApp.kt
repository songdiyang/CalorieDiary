package com.calorie.diary

import android.app.Application
import com.calorie.diary.data.network.NetworkMonitor
import com.calorie.diary.ui.theme.ThemeController

class CalorieApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 全局网络状态监听，供离线提示与请求前快速失败使用
        NetworkMonitor.start(this)
        // 首帧前同步恢复主题，避免开屏闪烁
        ThemeController.init(this)
    }
}

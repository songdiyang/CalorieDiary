package com.calorie.diary

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.calorie.diary.data.LocaleManager
import com.calorie.diary.data.network.NetworkMonitor
import com.calorie.diary.ui.navigation.AppScaffold
import com.calorie.diary.ui.splash.SplashScreen
import kotlinx.coroutines.delay
import com.calorie.diary.ui.theme.CalorieDiaryTheme
import com.calorie.diary.ui.theme.Cream

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CalorieDiaryTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Cream) {
                    var showSplash by remember { mutableStateOf(true) }
                    LaunchedEffect(Unit) {
                        delay(1200L)
                        showSplash = false
                    }
                    // 隐私同意改为在登录/注册操作时弹出，不再开屏拦截。
                    if (showSplash) SplashScreen() else AppScaffold()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 后台长时间挂起后网络回调可能不准，回到前台重新校准一次
        NetworkMonitor.refresh(this)
    }
}

package com.calorie.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.calorie.diary.data.supabase.AuthAccount
import com.calorie.diary.data.supabase.CloudProfile
import com.calorie.diary.ui.components.SoftCard
import com.calorie.diary.ui.profile.LoggedInAccount
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.ScreenBg
import com.calorie.diary.viewmodel.AuthUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 登录账户卡回归：SoftCard 容器是 Box，内容若不用 Column 串联会全部堆叠在左上角。 */
class LoggedInAccountLayoutTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        showSystemUi = false
    )

    @Test
    fun loggedInAccountCard() {
        var cardHeightPx = 0
        var minStackFreeHeightPx = 0f
        paparazzi.snapshot {
            minStackFreeHeightPx = with(LocalDensity.current) { 110.dp.toPx() }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(ScreenBg)
                    .padding(24.dp)
            ) {
                SoftCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { cardHeightPx = it.size.height },
                    fill = CardWhite
                ) {
                    LoggedInAccount(
                        state = AuthUiState(
                            restoring = false,
                            account = AuthAccount("04462317", "test@example.com"),
                            cloudProfile = CloudProfile(
                                id = "04462317",
                                nickname = "不吃胡萝卜",
                                avatarEmoji = "default:fox",
                                calorieGoal = 1800,
                                waterGoal = 2000
                            )
                        ),
                        onRefresh = {},
                        onSignOut = {}
                    )
                }
            }
        }
        assertTrue(
            "登录账户卡内容堆叠重叠：卡片高度仅 $cardHeightPx px，应大于 $minStackFreeHeightPx px",
            cardHeightPx > minStackFreeHeightPx
        )
    }
}
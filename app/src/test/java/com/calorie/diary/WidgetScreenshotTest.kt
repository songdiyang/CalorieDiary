package com.calorie.diary

import android.widget.FrameLayout
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.calorie.diary.widget.CheckInWidgetSnapshot
import com.calorie.diary.widget.CheckInWidgets
import org.junit.Rule
import org.junit.Test

/** 连胜签到桌面组件的状态快照：待签到 / 已签到翻倍 / 未登录 / 紧凑尺寸。 */
class WidgetScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 1080, screenHeight = 1080),
        showSystemUi = false
    )

    @Test
    fun pendingShowcase() = render(pendingSnapshot(), 300, 300)

    @Test
    fun checkedDoubledShowcase() = render(
        CheckInWidgetSnapshot(
            loggedIn = true,
            streak = 14,
            lastCheckInDate = "2026-11-26",
            serverDate = "2026-11-26",
            coinReward = 5,
            streakDays = 7,
            multiplier = 2,
            syncedAt = System.currentTimeMillis()
        ),
        300,
        300
    )

    @Test
    fun loggedOutShowcase() = render(CheckInWidgetSnapshot(), 300, 300)

    @Test
    fun compactShowcase() = render(pendingSnapshot(), 280, 230)

    private fun pendingSnapshot() = CheckInWidgetSnapshot(
        loggedIn = true,
        streak = 6,
        lastCheckInDate = "2026-11-25",
        serverDate = "2026-11-26",
        coinReward = 5,
        streakDays = 7,
        multiplier = 2,
        syncedAt = System.currentTimeMillis()
    )

    private fun render(snapshot: CheckInWidgetSnapshot, widthDp: Int, heightDp: Int) {
        val context = paparazzi.context
        val density = context.resources.displayMetrics.density
        val margin = (20 * density).toInt()
        val container = FrameLayout(context).apply {
            setBackgroundColor(context.getColor(R.color.cream))
            setPadding(margin, margin, margin, margin)
        }
        val widget = CheckInWidgets.buildViews(context, snapshot, withClicks = false)
            .apply(context, container)
        container.addView(
            widget,
            FrameLayout.LayoutParams((widthDp * density).toInt(), (heightDp * density).toInt())
        )
        paparazzi.snapshot(container)
    }
}
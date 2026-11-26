package com.calorie.diary.widget

import android.content.Context
import com.calorie.diary.data.game.CheckInReward
import com.calorie.diary.data.supabase.CloudGameState
import java.time.LocalDate

/** 桌面组件用的签到快照：由应用内云端状态与组件内签到共同维护。 */
data class CheckInWidgetSnapshot(
    val loggedIn: Boolean = false,
    val streak: Int = 0,
    val lastCheckInDate: String = "",
    val serverDate: String = "",
    val coinReward: Int = 5,
    val streakDays: Int = 7,
    val multiplier: Int = 2,
    val syncedAt: Long = 0L,
    val notice: String = "",
    val noticeAt: Long = 0L
) {
    /** 以服务端日期作为“今天”，与 App 内的签到判定保持一致。 */
    val todayDate: LocalDate
        get() = runCatching { LocalDate.parse(serverDate) }.getOrElse { LocalDate.now() }

    val checkedInToday: Boolean
        get() = loggedIn && lastCheckInDate.isNotEmpty() && lastCheckInDate == todayDate.toString()

    fun freshNotice(now: Long = System.currentTimeMillis()): String? =
        notice.takeIf { it.isNotBlank() && now - noticeAt < NOTICE_TTL_MS }

    fun isStale(now: Long = System.currentTimeMillis()): Boolean =
        !loggedIn || now - syncedAt > SYNC_INTERVAL_MS

    companion object {
        /** 组件最多每小时联网校准一次，避免频繁打扰服务端。 */
        const val SYNC_INTERVAL_MS = 60L * 60L * 1000L
        const val NOTICE_TTL_MS = 10L * 60L * 1000L
    }
}

class CheckInWidgetStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): CheckInWidgetSnapshot = CheckInWidgetSnapshot(
        loggedIn = prefs.getBoolean(KEY_LOGGED_IN, false),
        streak = prefs.getInt(KEY_STREAK, 0),
        lastCheckInDate = prefs.getString(KEY_LAST_CHECK_IN, "").orEmpty(),
        serverDate = prefs.getString(KEY_SERVER_DATE, "").orEmpty(),
        coinReward = prefs.getInt(KEY_COIN_REWARD, 5),
        streakDays = prefs.getInt(KEY_STREAK_DAYS, 7),
        multiplier = prefs.getInt(KEY_MULTIPLIER, 2),
        syncedAt = prefs.getLong(KEY_SYNCED_AT, 0L),
        notice = prefs.getString(KEY_NOTICE, "").orEmpty(),
        noticeAt = prefs.getLong(KEY_NOTICE_AT, 0L)
    )

    /** 应用内拉到最新云端状态后覆盖快照，并清掉一次性提示。 */
    fun save(state: CloudGameState) {
        write(
            CheckInWidgetSnapshot(
                loggedIn = true,
                streak = state.checkInStreak.coerceAtLeast(0),
                lastCheckInDate = state.lastCheckInDate,
                serverDate = state.serverDate,
                coinReward = state.checkInCoinReward,
                streakDays = state.checkInStreakDays,
                multiplier = state.checkInMultiplier,
                syncedAt = System.currentTimeMillis()
            )
        )
    }

    /** 组件内签到成功后，用服务端返回的连签天数刷新快照。 */
    fun saveCheckedIn(state: CloudGameState, reward: CheckInReward) {
        write(
            CheckInWidgetSnapshot(
                loggedIn = true,
                streak = reward.streak.coerceAtLeast(1),
                lastCheckInDate = state.serverDate,
                serverDate = state.serverDate,
                coinReward = state.checkInCoinReward,
                streakDays = state.checkInStreakDays,
                multiplier = state.checkInMultiplier,
                syncedAt = System.currentTimeMillis()
            )
        )
    }

    fun setLoggedOut() {
        if (!prefs.getBoolean(KEY_LOGGED_IN, false)) return
        write(CheckInWidgetSnapshot(loggedIn = false))
    }

    fun setNotice(text: String) {
        if (text.isBlank()) return
        prefs.edit()
            .putString(KEY_NOTICE, text)
            .putLong(KEY_NOTICE_AT, System.currentTimeMillis())
            .apply()
    }

    private fun write(snapshot: CheckInWidgetSnapshot) {
        prefs.edit()
            .putBoolean(KEY_LOGGED_IN, snapshot.loggedIn)
            .putInt(KEY_STREAK, snapshot.streak)
            .putString(KEY_LAST_CHECK_IN, snapshot.lastCheckInDate)
            .putString(KEY_SERVER_DATE, snapshot.serverDate)
            .putInt(KEY_COIN_REWARD, snapshot.coinReward)
            .putInt(KEY_STREAK_DAYS, snapshot.streakDays)
            .putInt(KEY_MULTIPLIER, snapshot.multiplier)
            .putLong(KEY_SYNCED_AT, snapshot.syncedAt)
            .putString(KEY_NOTICE, snapshot.notice)
            .putLong(KEY_NOTICE_AT, snapshot.noticeAt)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "checkin_widget"
        const val KEY_LOGGED_IN = "logged_in"
        const val KEY_STREAK = "streak"
        const val KEY_LAST_CHECK_IN = "last_check_in_date"
        const val KEY_SERVER_DATE = "server_date"
        const val KEY_COIN_REWARD = "coin_reward"
        const val KEY_STREAK_DAYS = "streak_days"
        const val KEY_MULTIPLIER = "multiplier"
        const val KEY_SYNCED_AT = "synced_at"
        const val KEY_NOTICE = "notice"
        const val KEY_NOTICE_AT = "notice_at"
    }
}
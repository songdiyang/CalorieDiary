package com.calorie.diary.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.calorie.diary.MainActivity
import com.calorie.diary.R
import java.time.LocalDate
import java.time.YearMonth

/** 组件界面渲染：所有内容来自本地签到快照，保证桌面更新不依赖网络。 */
internal object CheckInWidgets {

    private val calendarCellIds = intArrayOf(
        R.id.widget_cell_1, R.id.widget_cell_2, R.id.widget_cell_3, R.id.widget_cell_4,
        R.id.widget_cell_5, R.id.widget_cell_6, R.id.widget_cell_7, R.id.widget_cell_8,
        R.id.widget_cell_9, R.id.widget_cell_10, R.id.widget_cell_11, R.id.widget_cell_12,
        R.id.widget_cell_13, R.id.widget_cell_14, R.id.widget_cell_15, R.id.widget_cell_16,
        R.id.widget_cell_17, R.id.widget_cell_18, R.id.widget_cell_19, R.id.widget_cell_20,
        R.id.widget_cell_21, R.id.widget_cell_22, R.id.widget_cell_23, R.id.widget_cell_24,
        R.id.widget_cell_25, R.id.widget_cell_26, R.id.widget_cell_27, R.id.widget_cell_28,
        R.id.widget_cell_29, R.id.widget_cell_30, R.id.widget_cell_31, R.id.widget_cell_32,
        R.id.widget_cell_33, R.id.widget_cell_34, R.id.widget_cell_35, R.id.widget_cell_36,
        R.id.widget_cell_37, R.id.widget_cell_38, R.id.widget_cell_39, R.id.widget_cell_40,
        R.id.widget_cell_41, R.id.widget_cell_42
    )

    fun updateAll(context: Context) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        val ids = manager.getAppWidgetIds(ComponentName(app, CheckInWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val views = buildViews(app, CheckInWidgetStore(app).read())
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    internal fun buildViews(
        context: Context,
        snapshot: CheckInWidgetSnapshot,
        withClicks: Boolean = true
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_checkin)
        val today = snapshot.todayDate
        val checkedToday = snapshot.checkedInToday
        val notice = snapshot.freshNotice()

        views.setTextViewText(R.id.widget_streak, snapshot.streak.coerceAtLeast(0).toString())
        views.setTextColor(
            R.id.widget_streak,
            context.getColor(
                if (snapshot.loggedIn) R.color.widget_primary_deep else R.color.widget_ink_faint
            )
        )

        val subtitle = when {
            !snapshot.loggedIn -> context.getString(R.string.widget_checkin_subtitle_logged_out)
            notice != null -> notice
            checkedToday -> context.getString(
                if (isDoubled(snapshot, snapshot.streak)) R.string.widget_checkin_subtitle_checked_double
                else R.string.widget_checkin_subtitle_checked
            )
            else -> {
                val doubled = isDoubled(snapshot, nextStreak(snapshot, today))
                val reward = snapshot.coinReward * if (doubled) snapshot.multiplier else 1
                context.getString(
                    if (doubled) R.string.widget_checkin_subtitle_pending_double
                    else R.string.widget_checkin_subtitle_pending,
                    reward
                )
            }
        }
        views.setTextViewText(R.id.widget_subtitle, subtitle)

        views.setTextViewText(
            R.id.widget_action,
            context.getString(
                when {
                    !snapshot.loggedIn -> R.string.widget_checkin_action_login
                    checkedToday -> R.string.widget_checkin_action_done
                    else -> R.string.widget_checkin_action
                }
            )
        )
        views.setTextColor(
            R.id.widget_action,
            context.getColor(if (checkedToday) R.color.widget_ink else R.color.widget_button_text)
        )
        views.setInt(
            R.id.widget_action,
            "setBackgroundResource",
            if (checkedToday) R.drawable.widget_checkin_button_done
            else R.drawable.widget_checkin_button
        )

        views.setTextViewText(R.id.widget_quote, quoteOfTheDay(context, today))
        applyCalendar(views, context, snapshot, today)

        if (withClicks) {
            val openApp = openAppIntent(context)
            views.setOnClickPendingIntent(R.id.widget_root, openApp)
            views.setOnClickPendingIntent(
                R.id.widget_action,
                if (snapshot.loggedIn && !checkedToday) checkInIntent(context) else openApp
            )
        }
        return views
    }

    /** 本月日历：标记连签日期与今天，非本月格子留空。 */
    private fun applyCalendar(
        views: RemoteViews,
        context: Context,
        snapshot: CheckInWidgetSnapshot,
        today: LocalDate
    ) {
        views.setTextViewText(
            R.id.widget_month,
            context.getString(R.string.widget_checkin_month, today.year, today.monthValue)
        )
        val month = YearMonth.from(today)
        val leadingBlanks = month.atDay(1).dayOfWeek.value - 1
        val daysInMonth = month.lengthOfMonth()
        val checkedDays = checkedDaysInMonth(snapshot, month)
        val checkedTextColor = context.getColor(R.color.widget_button_text)
        val todayTextColor = context.getColor(R.color.widget_ink)
        val normalTextColor = context.getColor(R.color.widget_ink_light)
        calendarCellIds.forEachIndexed { index, cellId ->
            val dayNumber = index - leadingBlanks + 1
            if (dayNumber !in 1..daysInMonth) {
                views.setTextViewText(cellId, "")
                views.setInt(cellId, "setBackgroundResource", 0)
                return@forEachIndexed
            }
            views.setTextViewText(cellId, dayNumber.toString())
            val date = month.atDay(dayNumber)
            when {
                checkedDays.contains(date) -> {
                    views.setTextColor(cellId, checkedTextColor)
                    views.setInt(
                        cellId,
                        "setBackgroundResource",
                        R.drawable.widget_calendar_checked
                    )
                }
                date == today -> {
                    views.setTextColor(cellId, todayTextColor)
                    views.setInt(
                        cellId,
                        "setBackgroundResource",
                        R.drawable.widget_calendar_today
                    )
                }
                else -> {
                    views.setTextColor(cellId, normalTextColor)
                    views.setInt(cellId, "setBackgroundResource", 0)
                }
            }
        }
    }

    /** 当前连签链条落在本月内的日期。 */
    private fun checkedDaysInMonth(
        snapshot: CheckInWidgetSnapshot,
        month: YearMonth
    ): Set<LocalDate> {
        if (!snapshot.loggedIn || snapshot.streak <= 0) return emptySet()
        val last = runCatching { LocalDate.parse(snapshot.lastCheckInDate) }.getOrNull()
            ?: return emptySet()
        val chainStart = last.minusDays((snapshot.streak - 1).toLong())
        val start = maxOf(chainStart, month.atDay(1))
        val end = minOf(last, month.atEndOfMonth())
        if (start.isAfter(end)) return emptySet()
        val days = mutableSetOf<LocalDate>()
        var day = start
        while (!day.isAfter(end)) {
            days.add(day)
            day = day.plusDays(1)
        }
        return days
    }

    private fun nextStreak(snapshot: CheckInWidgetSnapshot, today: LocalDate): Int {
        val last = runCatching { LocalDate.parse(snapshot.lastCheckInDate) }.getOrNull()
            ?: return 1
        return if (last == today.minusDays(1)) snapshot.streak + 1 else 1
    }

    private fun isDoubled(snapshot: CheckInWidgetSnapshot, streak: Int): Boolean =
        snapshot.streakDays > 0 && snapshot.multiplier > 1 && streak > 0 &&
            streak % snapshot.streakDays == 0

    /** 每天固定一句鸡汤，跨天自动更换。 */
    private fun quoteOfTheDay(context: Context, today: LocalDate): String {
        val quotes = context.resources.getStringArray(R.array.widget_checkin_quotes)
        if (quotes.isEmpty()) return ""
        return quotes[Math.floorMod(today.toEpochDay(), quotes.size.toLong()).toInt()]
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun checkInIntent(context: Context): PendingIntent {
        val intent = Intent(context, CheckInWidgetProvider::class.java)
            .setAction(CheckInWidgetProvider.ACTION_CHECK_IN)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CHECK_IN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private const val REQUEST_OPEN_APP = 101
    private const val REQUEST_CHECK_IN = 102
}
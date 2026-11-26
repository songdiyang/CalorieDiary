package com.calorie.diary.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.calorie.diary.R
import com.calorie.diary.data.network.NetworkMonitor
import com.calorie.diary.data.supabase.CloudGameRepository
import com.calorie.diary.data.supabase.SupabaseApiException
import com.calorie.diary.data.supabase.SupabaseAuthRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** 连胜签到桌面组件：展示连签天数与本周进度，点击可直接完成当日签到。 */
class CheckInWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        CheckInWidgets.updateAll(context)
        scheduleDailyRefresh(context)
    }

    override fun onEnabled(context: Context) {
        scheduleDailyRefresh(context)
    }

    override fun onDisabled(context: Context) {
        cancelDailyRefresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_CHECK_IN -> {
                checkIn(context)
                return
            }
            ACTION_DAILY_REFRESH -> {
                CheckInWidgets.updateAll(context)
                scheduleDailyRefresh(context)
                syncFromCloud(context, force = true)
                return
            }
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                CheckInWidgets.updateAll(context)
                scheduleDailyRefresh(context)
                return
            }
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                syncFromCloud(context, force = false)
                super.onReceive(context, intent)
                return
            }
        }
        super.onReceive(context, intent)
    }

    /** 组件内签到：拉取最新云端状态，未签到则直接领取，然后刷新桌面展示。 */
    private fun checkIn(context: Context) {
        val app = context.applicationContext
        val store = CheckInWidgetStore(app)
        if (!NetworkMonitor.isOnline()) {
            store.setNotice(app.getString(R.string.widget_checkin_offline))
            CheckInWidgets.updateAll(app)
            return
        }
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (SupabaseAuthRepository.get(app).account.first() == null) {
                    store.setLoggedOut()
                    return@launch
                }
                val repo = CloudGameRepository.get(app)
                val state = withTimeout(NETWORK_TIMEOUT_MS) { repo.loadState() }
                if (state.lastCheckInDate == state.serverDate) {
                    store.save(state)
                    CheckInWidgetEvents.notifyCheckedIn()
                } else {
                    val reward = withTimeout(NETWORK_TIMEOUT_MS) { repo.claimDailyCheckIn() }
                    if (reward.success) {
                        store.saveCheckedIn(state, reward)
                        CheckInWidgetEvents.notifyCheckedIn()
                    } else {
                        // 与 App 内自动签到撞车时，重新拉一次以拿到真实连签天数。
                        store.save(withTimeout(NETWORK_TIMEOUT_MS) { repo.loadState() })
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (e is SupabaseApiException && e.statusCode == 401) {
                    store.setLoggedOut()
                } else {
                    store.setNotice(app.getString(R.string.widget_checkin_failed))
                }
            } finally {
                CheckInWidgets.updateAll(app)
                result.finish()
            }
        }
    }

    /** 定期（或跨天时）用云端状态校准快照；失败时静默保留缓存。 */
    private fun syncFromCloud(context: Context, force: Boolean) {
        val app = context.applicationContext
        val store = CheckInWidgetStore(app)
        if (!force && !store.read().isStale()) return
        if (!NetworkMonitor.isOnline()) return
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (SupabaseAuthRepository.get(app).account.first() == null) {
                    store.setLoggedOut()
                } else {
                    store.save(withTimeout(NETWORK_TIMEOUT_MS) {
                        CloudGameRepository.get(app).loadState()
                    })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // 保留上次快照，等下一次刷新再试。
            } finally {
                CheckInWidgets.updateAll(app)
                result.finish()
            }
        }
    }

    /** 每天零点后刷新一次，让“今天还没签到”和本周圆点在跨天后及时翻转。 */
    private fun scheduleDailyRefresh(context: Context) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = LocalDate.now()
            .plusDays(1)
            .atStartOfDay()
            .plusMinutes(1)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        alarm.set(AlarmManager.RTC, triggerAt, dailyRefreshIntent(context))
    }

    private fun cancelDailyRefresh(context: Context) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        alarm.cancel(dailyRefreshIntent(context))
    }

    private fun dailyRefreshIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_DAILY_REFRESH,
            Intent(context, CheckInWidgetProvider::class.java)
                .setAction(ACTION_DAILY_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    companion object {
        const val ACTION_CHECK_IN = "com.calorie.diary.widget.action.CHECK_IN"
        const val ACTION_DAILY_REFRESH = "com.calorie.diary.widget.action.DAILY_REFRESH"
        private const val NETWORK_TIMEOUT_MS = 6_000L
        private const val REQUEST_DAILY_REFRESH = 103
    }
}

/** 组件内完成签到后通知前台页面刷新；仅进程存活时有效，冷启动会重新拉取。 */
object CheckInWidgetEvents {
    private val _checkedIn = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val checkedIn = _checkedIn.asSharedFlow()

    fun notifyCheckedIn() {
        _checkedIn.tryEmit(Unit)
    }
}
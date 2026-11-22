package com.calorie.diary.viewmodel

import android.content.Context
import com.calorie.diary.R
import com.calorie.diary.data.UiMessenger
import com.calorie.diary.data.game.NewbieTaskSyncResult

/** 新手任务奖励统一提示：一次可能同时完成多个任务，按合计奖励展示。 */
internal fun notifyNewbieTaskResult(context: Context, result: NewbieTaskSyncResult) {
    if (result.awarded.isEmpty()) return
    val text = if (result.leveledUp) {
        context.getString(R.string.newbie_task_level_up, result.level)
    } else {
        context.getString(R.string.newbie_task_success, result.coinsAwarded, result.expAwarded)
    }
    UiMessenger.show(text, false)
}
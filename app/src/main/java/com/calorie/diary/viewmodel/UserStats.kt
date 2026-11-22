package com.calorie.diary.viewmodel

/**
 * 「我的」页统计概览。
 * @param activeDays 有饮食记录的天数
 * @param entryCount 累计记录条数
 * @param totalCalories 累计摄入热量（千卡）
 * @param streak 连续打卡天数（今天未记录则从昨天起算，不中断）
 */
data class UserStats(
    val activeDays: Int = 0,
    val entryCount: Int = 0,
    val totalCalories: Int = 0,
    val streak: Int = 0
)

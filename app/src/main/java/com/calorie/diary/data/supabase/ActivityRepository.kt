package com.calorie.diary.data.supabase

import android.content.Context

class ActivityRepository private constructor(context: Context) {
    private val auth = SupabaseAuthRepository.get(context)

    suspend fun load(userId: String): ActivityProfileResult = auth.withValidSession { api, session ->
        api.getActivityProfile(session, userId)
    }

    suspend fun syncAndLoadOwn(days: List<ActivityDay>): ActivityProfileResult =
        auth.withValidSession { api, session ->
            api.syncActivityDailyTotals(session, days)
            api.getActivityProfile(session, session.userId)
        }

    companion object {
        @Volatile
        private var instance: ActivityRepository? = null

        fun get(context: Context): ActivityRepository =
            instance ?: synchronized(this) {
                instance ?: ActivityRepository(context.applicationContext).also { instance = it }
            }
    }
}

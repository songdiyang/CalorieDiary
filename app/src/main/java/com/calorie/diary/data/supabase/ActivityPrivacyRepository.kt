package com.calorie.diary.data.supabase

import android.content.Context

class ActivityPrivacyRepository private constructor(context: Context) {
    private val auth = SupabaseAuthRepository.get(context)

    suspend fun load(): ActivityPrivacyInfo = auth.withValidSession { api, session ->
        api.fetchActivityPrivacy(session)
    }

    suspend fun setActivityVisibility(value: String): String = auth.withValidSession { api, session ->
        api.setActivityVisibility(session, value)
    }

    companion object {
        @Volatile private var instance: ActivityPrivacyRepository? = null

        fun get(context: Context): ActivityPrivacyRepository =
            instance ?: synchronized(this) {
                instance ?: ActivityPrivacyRepository(context.applicationContext).also { instance = it }
            }
    }
}
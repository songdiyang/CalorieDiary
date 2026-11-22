package com.calorie.diary.data.supabase

import android.content.Context

class SocialRepository private constructor(context: Context) {
    private val auth = SupabaseAuthRepository.get(context)

    suspend fun loadSummary(userId: String): SocialSummary =
        auth.withValidSession { api, session -> api.getSocialSummary(session, userId) }

    suspend fun loadFollowing(userId: String): List<SocialUserEntry> =
        auth.withValidSession { api, session -> api.listFollowing(session, userId) }

    suspend fun loadFollowers(userId: String): List<SocialUserEntry> =
        auth.withValidSession { api, session -> api.listFollowers(session, userId) }

    suspend fun loadPublicProfile(userId: String): PublicProfile? =
        auth.withValidSession { api, session -> api.getPublicProfile(session, userId) }

    suspend fun isProfileDeleted(userId: String): Boolean =
        auth.withValidSession { api, session -> api.isProfileDeleted(session, userId) }

    suspend fun follow(userId: String): FriendAddResult =
        auth.withValidSession { api, session -> api.addFriend(session, userId) }

    /** 取消关注：互关时降级为对方单向关注我，对方仍留在我的粉丝列表里。 */
    suspend fun unfollow(userId: String) {
        auth.withValidSession { api, session -> api.unfollowUser(session, userId) }
    }

    companion object {
        @Volatile
        private var instance: SocialRepository? = null

        fun get(context: Context): SocialRepository =
            instance ?: synchronized(this) {
                instance ?: SocialRepository(context.applicationContext).also { instance = it }
            }
    }
}

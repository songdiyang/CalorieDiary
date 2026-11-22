package com.calorie.diary.data.supabase

import android.content.Context

class FriendsRepository private constructor(context: Context) {

    private val auth = SupabaseAuthRepository.get(context)

    suspend fun loadOverview(): FriendsOverview = auth.withValidSession { api, session ->
        FriendsOverview(
            myUserId = api.fetchMyUserId(session),
            friends = api.listFriends(session),
            requests = api.listFriendRequests(session)
        )
    }

    suspend fun addFriend(userId: String): FriendAddResult =
        auth.withValidSession { api, session -> api.addFriend(session, userId) }

    suspend fun respondFriendRequest(requesterId: String, accept: Boolean): String =
        auth.withValidSession { api, session ->
            api.respondFriendRequest(session, requesterId, accept)
        }

    suspend fun searchUsers(query: String): List<UserSearchResult> =
        auth.withValidSession { api, session -> api.searchUsers(session, query) }

    suspend fun removeFriend(friendId: String) {
        auth.withValidSession { api, session -> api.removeFriend(session, friendId) }
    }

    companion object {
        @Volatile
        private var instance: FriendsRepository? = null

        fun get(context: Context): FriendsRepository =
            instance ?: synchronized(this) {
                instance ?: FriendsRepository(context.applicationContext).also { instance = it }
            }
    }
}

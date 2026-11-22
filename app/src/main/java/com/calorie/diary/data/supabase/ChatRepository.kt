package com.calorie.diary.data.supabase

import android.content.Context

class ChatRepository private constructor(context: Context) {
    private val auth = SupabaseAuthRepository.get(context)

    suspend fun loadChat(friendId: String): ChatData = auth.withValidSession { api, session ->
        val friend = api.listFriends(session).firstOrNull { it.userId == friendId }
            ?: throw SupabaseApiException(404, "friend_not_found")
        val relation = api.getFriendRelation(session, friendId)
        ChatData(
            friend = friend,
            messages = api.listFriendMessages(session, friendId),
            currentUserId = session.userId,
            relationType = relation?.type,
            relationSince = relation?.since
        )
    }

    /** 聊天内容加载失败（如未互关）时单独取好友资料，保证页面上的头像/昵称与列表一致。 */
    suspend fun findFriend(friendId: String): FriendEntry? =
        auth.withValidSession { api, session ->
            api.listFriends(session).firstOrNull { it.userId == friendId }
        }

    suspend fun sendMessage(friendId: String, content: String): FriendMessage =
        auth.withValidSession { api, session ->
            api.sendFriendMessage(session, friendId, content)
        }

    suspend fun sendCalorieShare(
        friendId: String,
        calories: Int,
        entryDate: String,
        items: List<SharedFood> = emptyList()
    ): FriendMessage = auth.withValidSession { api, session ->
        api.sendFriendMessage(
            session = session,
            friendId = friendId,
            content = "今日热量分享",
            messageType = "calorie_share",
            calories = calories,
            entryDate = entryDate,
            items = items
        )
    }

    suspend fun setRelation(friendId: String, relation: String): RelationBinding? =
        auth.withValidSession { api, session ->
            api.setFriendRelation(session, friendId, relation)
        }

    suspend fun loadMessages(friendId: String): List<FriendMessage> =
        auth.withValidSession { api, session -> api.listFriendMessages(session, friendId) }

    companion object {
        @Volatile private var instance: ChatRepository? = null

        fun get(context: Context): ChatRepository =
            instance ?: synchronized(this) {
                instance ?: ChatRepository(context.applicationContext).also { instance = it }
            }
    }
}

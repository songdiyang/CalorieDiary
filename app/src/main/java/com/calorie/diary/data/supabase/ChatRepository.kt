package com.calorie.diary.data.supabase

import android.content.Context
import com.calorie.diary.data.db.AppDatabase
import com.calorie.diary.data.db.ChatCacheDao
import kotlinx.coroutines.flow.first

class ChatRepository private constructor(context: Context) {
    private val auth = SupabaseAuthRepository.get(context)
    private val cache: ChatCacheDao = AppDatabase.get(context).chatCacheDao()

    /** 本地缓存的消息与好友快照，用于进入聊天页时立即展示。 */
    data class CachedChatData(
        val ownerId: String,
        val friend: FriendEntry?,
        val messages: List<FriendMessage>
    )

    suspend fun loadCachedChat(friendId: String): CachedChatData? {
        val ownerId = auth.account.first()?.userId ?: return null
        val messages = cache.friendMessages(ownerId, friendId).map { it.toFriendMessage() }
        if (messages.isEmpty()) return null
        val friend = cache.friends(ownerId)
            .firstOrNull { it.userId == friendId }
            ?.toFriendEntry()
        return CachedChatData(ownerId, friend, messages)
    }

    suspend fun loadChat(friendId: String): ChatData = auth.withValidSession { api, session ->
        val friend = api.listFriends(session).firstOrNull { it.userId == friendId }
            ?: throw SupabaseApiException(404, "friend_not_found")
        val relation = api.getFriendRelation(session, friendId)
        val messages = api.listFriendMessages(session, friendId)
        runCatching {
            cache.replaceFriendMessages(
                ownerId = session.userId,
                friendId = friendId,
                messages = messages.map { it.toCacheEntity(session.userId, friendId) }
            )
        }
        ChatData(
            friend = friend,
            messages = messages,
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
            api.sendFriendMessage(session, friendId, content).also {
                cacheSent(session.userId, friendId, it)
            }
        }

    suspend fun sendCalorieShare(
        friendId: String,
        calories: Int,
        entryDate: String,
        items: List<SharedFood> = emptyList(),
        messageType: String = "calorie_share"
    ): FriendMessage = auth.withValidSession { api, session ->
        api.sendFriendMessage(
            session = session,
            friendId = friendId,
            content = "今日热量分享",
            messageType = messageType,
            calories = calories,
            entryDate = entryDate,
            items = items
        ).also { cacheSent(session.userId, friendId, it) }
    }

    suspend fun setRelation(friendId: String, relation: String): RelationBinding? =
        auth.withValidSession { api, session ->
            api.setFriendRelation(session, friendId, relation)
        }

    suspend fun loadMessages(friendId: String): List<FriendMessage> =
        auth.withValidSession { api, session ->
            api.listFriendMessages(session, friendId).also { messages ->
                runCatching {
                    cache.replaceFriendMessages(
                        ownerId = session.userId,
                        friendId = friendId,
                        messages = messages.map { it.toCacheEntity(session.userId, friendId) }
                    )
                }
            }
        }

    private suspend fun cacheSent(ownerId: String, friendId: String, message: FriendMessage) {
        runCatching {
            cache.insertFriendMessages(listOf(message.toCacheEntity(ownerId, friendId)))
        }
    }

    companion object {
        @Volatile private var instance: ChatRepository? = null

        fun get(context: Context): ChatRepository =
            instance ?: synchronized(this) {
                instance ?: ChatRepository(context.applicationContext).also { instance = it }
            }
    }
}
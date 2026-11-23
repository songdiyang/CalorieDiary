package com.calorie.diary.data.supabase

import android.content.Context
import com.calorie.diary.data.db.AppDatabase
import com.calorie.diary.data.db.ChatCacheDao
import kotlinx.coroutines.flow.first

class FriendsRepository private constructor(context: Context) {

    private val auth = SupabaseAuthRepository.get(context)
    private val cache: ChatCacheDao = AppDatabase.get(context).chatCacheDao()

    /** 上次登录时缓存的好友会话，进入消息页可先展示。 */
    suspend fun loadCachedFriends(): List<FriendEntry> {
        val ownerId = auth.account.first()?.userId ?: return emptyList()
        return cache.friends(ownerId).map { it.toFriendEntry() }
    }

    suspend fun loadOverview(): FriendsOverview = auth.withValidSession { api, session ->
        val myUserId = api.fetchMyUserId(session)
        val friends = api.listFriends(session)
        val requests = api.listFriendRequests(session)
        val friendIds = friends.mapTo(hashSetOf()) { it.userId }
        val requestIds = requests.mapTo(hashSetOf()) { it.userId }
        // 关注区只保留“还没成为好友、也没有待处理好友申请”的粉丝：
        // 单向粉丝显示“关注了你 + 回关”，互关粉丝显示“已互关 + 申请好友”。
        val followerNotices = if (myUserId.isBlank()) {
            emptyList()
        } else {
            api.listFollowers(session, myUserId)
                .filterNot { it.userId in friendIds || it.userId in requestIds }
                .map { follower ->
                    FollowNoticeEntry(
                        userId = follower.userId,
                        nickname = follower.nickname,
                        avatarEmoji = follower.avatarEmoji,
                        followedAt = follower.followedAt,
                        mutual = follower.mutual
                    )
                }
        }
        if (myUserId.isNotBlank()) {
            runCatching {
                cache.replaceFriends(myUserId, friends.map { it.toCacheEntity(myUserId) })
            }
        }
        FriendsOverview(
            myUserId = myUserId,
            friends = friends,
            followNotices = followerNotices,
            requests = requests
        )
    }

    /** 关注某人：对方会在消息页看到“xxx 关注了你”。 */
    suspend fun follow(userId: String): String =
        auth.withValidSession { api, session -> api.followUser(session, userId) }

    /** 好友申请：服务端要求双方已互相关注。 */
    suspend fun applyFriend(userId: String): FriendAddResult =
        auth.withValidSession { api, session -> api.addFriend(session, userId) }

    suspend fun respondFriendRequest(requesterId: String, accept: Boolean): String =
        auth.withValidSession { api, session ->
            api.respondFriendRequest(session, requesterId, accept)
        }

    suspend fun searchUsers(query: String): List<UserSearchResult> =
        auth.withValidSession { api, session -> api.searchUsers(session, query) }

    suspend fun removeFriend(friendId: String) {
        auth.withValidSession { api, session ->
            api.removeFriend(session, friendId)
            // 好友关系解除后不再保留本地聊天记录，避免绕过云端的好友校验。
            runCatching {
                cache.deleteFriend(session.userId, friendId)
                cache.clearFriendMessages(session.userId, friendId)
            }
        }
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
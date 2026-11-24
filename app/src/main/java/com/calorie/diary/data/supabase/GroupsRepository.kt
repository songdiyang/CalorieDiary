package com.calorie.diary.data.supabase

import android.content.Context
import com.calorie.diary.data.db.AppDatabase
import com.calorie.diary.data.db.ChatCacheDao
import kotlinx.coroutines.flow.first

class GroupsRepository private constructor(context: Context) {
    private val auth = SupabaseAuthRepository.get(context)
    private val cache: ChatCacheDao = AppDatabase.get(context).chatCacheDao()

    /** 本地缓存的群聊消息与群资料，用于进入群聊页立即展示。 */
    data class CachedGroupChatData(
        val ownerId: String,
        val group: GroupConversation?,
        val messages: List<GroupMessage>
    )

    /** 上次登录时缓存的群会话，进入消息页可先展示。 */
    suspend fun loadCachedGroups(): List<GroupConversation> {
        val ownerId = auth.account.first()?.userId ?: return emptyList()
        return cache.groups(ownerId).map { it.toGroupConversation() }
    }

    suspend fun loadCachedChat(groupId: String): CachedGroupChatData? {
        val ownerId = auth.account.first()?.userId ?: return null
        val messages = cache.groupMessages(ownerId, groupId).map { it.toGroupMessage() }
        val group = cache.groups(ownerId)
            .firstOrNull { it.groupId == groupId }
            ?.toGroupConversation()
        if (messages.isEmpty() && group == null) return null
        return CachedGroupChatData(ownerId, group, messages)
    }

    suspend fun listGroups(): List<GroupConversation> = auth.withValidSession { api, session ->
        api.listGroups(session).also { groups ->
            runCatching {
                cache.replaceGroups(session.userId, groups.map { it.toCacheEntity(session.userId) })
            }
        }
    }

    suspend fun createGroup(name: String): GroupConversation =
        auth.withValidSession { api, session -> api.createGroup(session, name) }

    /** 邀请好友进群，返回实际新增的邀请数；邀请需对方确认后才入群。 */
    suspend fun inviteMembers(groupId: String, userIds: List<String>): Int =
        auth.withValidSession { api, session ->
            api.inviteGroupMembers(session, groupId, userIds)
        }

    suspend fun listInvitations(): List<GroupInvitation> =
        auth.withValidSession { api, session -> api.listGroupInvitations(session) }

    suspend fun respondInvitation(invitationId: Long, accept: Boolean): String =
        auth.withValidSession { api, session ->
            api.respondGroupInvitation(session, invitationId, accept)
        }

    /** 群聊邀请候选人的来源：我的好友列表。 */
    suspend fun listFriends(): List<FriendEntry> =
        auth.withValidSession { api, session -> api.listFriends(session) }

    /** 群聊主页：基础信息 + 成员列表。 */
    suspend fun loadInfo(groupId: String): Pair<GroupInfo, List<GroupInfoMember>> =
        auth.withValidSession { api, session ->
            api.getGroupInfo(session, groupId) to api.listGroupMembers(session, groupId)
        }

    /** 仅群主可修改，返回保存后的（名称, 公告）。 */
    suspend fun updateInfo(
        groupId: String,
        name: String? = null,
        announcement: String? = null
    ): Pair<String, String> = auth.withValidSession { api, session ->
        api.updateGroupInfo(session, groupId, name, announcement)
    }

    /** 当前登录账号 id，用于判断群主页的群主身份。 */
    suspend fun currentUserId(): String =
        auth.withValidSession { _, session -> session.userId }

    /** 群主设置群号是否可被搜索加入。 */
    suspend fun setGroupSearchable(groupId: String, searchable: Boolean): Boolean =
        auth.withValidSession { api, session ->
            api.setGroupSearchable(session, groupId, searchable)
        }

    /** 群主解散群聊。 */
    suspend fun dissolveGroup(groupId: String): String =
        auth.withValidSession { api, session ->
            api.dissolveGroup(session, groupId).also { clearGroupCache(session.userId, groupId) }
        }

    /** 群主把群转让给群内成员。 */
    suspend fun transferOwnership(groupId: String, newOwnerId: String): String =
        auth.withValidSession { api, session ->
            api.transferGroupOwnership(session, groupId, newOwnerId)
        }

    /** 普通成员退出群聊。 */
    suspend fun leaveGroup(groupId: String): String =
        auth.withValidSession { api, session ->
            api.leaveGroup(session, groupId).also { clearGroupCache(session.userId, groupId) }
        }

    suspend fun loadChat(groupId: String, date: String): GroupChatData =
        auth.withValidSession { api, session ->
            val group = api.listGroups(session).first { it.groupId == groupId }
            val messages = api.listGroupMessages(session, groupId)
            runCatching {
                cache.insertGroups(listOf(group.toCacheEntity(session.userId)))
                cache.replaceGroupMessages(
                    ownerId = session.userId,
                    groupId = groupId,
                    messages = messages.map { it.toCacheEntity(session.userId, groupId) }
                )
            }
            GroupChatData(
                group = group,
                messages = messages,
                members = api.getGroupDailyTotals(session, groupId, date),
                currentUserId = session.userId
            )
        }

    suspend fun sendMessage(
        groupId: String,
        content: String,
        messageType: String,
        calories: Int?,
        entryDate: String?,
        items: List<SharedFood> = emptyList()
    ): GroupMessage = auth.withValidSession { api, session ->
        api.sendGroupMessage(session, groupId, content, messageType, calories, entryDate, items)
            .also { sent ->
                runCatching {
                    cache.insertGroupMessages(
                        listOf(sent.toCacheEntity(session.userId, groupId))
                    )
                }
            }
    }

    private suspend fun clearGroupCache(ownerId: String, groupId: String) {
        runCatching {
            cache.deleteGroup(ownerId, groupId)
            cache.clearGroupMessages(ownerId, groupId)
        }
    }

    companion object {
        @Volatile private var instance: GroupsRepository? = null

        fun get(context: Context): GroupsRepository =
            instance ?: synchronized(this) {
                instance ?: GroupsRepository(context.applicationContext).also { instance = it }
            }
    }
}

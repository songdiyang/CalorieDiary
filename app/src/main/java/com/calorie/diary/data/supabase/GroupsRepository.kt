package com.calorie.diary.data.supabase

import android.content.Context

class GroupsRepository private constructor(context: Context) {
    private val auth = SupabaseAuthRepository.get(context)

    suspend fun listGroups(): List<GroupConversation> = auth.withValidSession { api, session ->
        api.listGroups(session)
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

    /** 群主解散群聊。 */
    suspend fun dissolveGroup(groupId: String): String =
        auth.withValidSession { api, session -> api.dissolveGroup(session, groupId) }

    /** 群主把群转让给群内成员。 */
    suspend fun transferOwnership(groupId: String, newOwnerId: String): String =
        auth.withValidSession { api, session ->
            api.transferGroupOwnership(session, groupId, newOwnerId)
        }

    /** 普通成员退出群聊。 */
    suspend fun leaveGroup(groupId: String): String =
        auth.withValidSession { api, session -> api.leaveGroup(session, groupId) }

    suspend fun loadChat(groupId: String, date: String): GroupChatData =
        auth.withValidSession { api, session ->
            GroupChatData(
                group = api.listGroups(session).first { it.groupId == groupId },
                messages = api.listGroupMessages(session, groupId),
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
    }

    companion object {
        @Volatile private var instance: GroupsRepository? = null

        fun get(context: Context): GroupsRepository =
            instance ?: synchronized(this) {
                instance ?: GroupsRepository(context.applicationContext).also { instance = it }
            }
    }
}

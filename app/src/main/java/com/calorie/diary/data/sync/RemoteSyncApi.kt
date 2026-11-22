package com.calorie.diary.data.sync

import com.calorie.diary.data.db.UserIdentity
import com.calorie.diary.data.network.NetworkErrorKind
import com.calorie.diary.data.network.NetworkException

// —— 网络传输对象（与本地 Room 实体解耦，便于未来协议演进）——
data class RemoteGroup(
    val groupId: String,
    val name: String,
    val ownerId: String,
    val calorieGoal: Int,
    val createdAt: Long
)

data class RemoteMember(
    val userId: String,
    val nickname: String,
    val avatarEmoji: String,
    val lastActiveAt: Long
)

data class RemoteEntry(
    val id: Long = 0,
    val date: String,
    val mealType: Int,
    val foodName: String,
    val emoji: String,
    val grams: Int,
    val calories: Int,
    val timestamp: Long,
    val ownerId: String,
    val ownerName: String
)

/**
 * 多人协作云端同步抽象。
 * 本地模拟实现与 HTTP 实现遵循同一契约，UI/Repository 只依赖此接口，
 * 服务器就绪后切换实现即可，无需改动业务层。
 */
interface RemoteSyncApi {

    suspend fun createGroup(name: String, goal: Int, owner: UserIdentity): RemoteGroup

    suspend fun joinGroup(groupId: String, identity: UserIdentity): RemoteGroup

    suspend fun getGroup(groupId: String): RemoteGroup?

    suspend fun listMembers(groupId: String): List<RemoteMember>

    suspend fun pushEntry(groupId: String, entry: RemoteEntry): RemoteEntry

    suspend fun pullEntries(groupId: String, date: String): List<RemoteEntry>

    suspend fun leaveGroup(groupId: String, userId: String)
}

/**
 * 云端操作失败（如房间不存在、网络错误）。
 * [kind] 供表现层映射成可操作的中英文提示，[message] 仅作为服务端补充说明。
 */
class SyncException(
    kind: NetworkErrorKind = NetworkErrorKind.UNKNOWN,
    message: String? = null,
    cause: Throwable? = null
) : NetworkException(kind, message, cause)

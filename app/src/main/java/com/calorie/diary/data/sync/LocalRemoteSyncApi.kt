package com.calorie.diary.data.sync

import com.calorie.diary.data.db.GroupDao
import com.calorie.diary.data.db.MealDao
import com.calorie.diary.data.db.UserIdentity
import com.calorie.diary.data.network.NetworkErrorKind
import kotlinx.coroutines.flow.first
import kotlin.random.Random

/**
 * 本地模拟云端实现：以 Room 作为“服务器”，在没有真实后端时跑通完整协作流程。
 * 行为与未来 HTTP 实现一致，切换时业务层无感。
 */
class LocalRemoteSyncApi(
    private val groupDao: GroupDao,
    private val mealDao: MealDao
) : RemoteSyncApi {

    override suspend fun createGroup(name: String, goal: Int, owner: UserIdentity): RemoteGroup {
        val code = generateRoomCode()
        val now = System.currentTimeMillis()
        groupDao.upsertGroup(
            com.calorie.diary.data.db.GroupEntity(
                groupId = code,
                name = name.ifBlank { "我们的小组" },
                ownerId = owner.userId,
                calorieGoal = goal,
                createdAt = now
            )
        )
        groupDao.upsertMember(
            com.calorie.diary.data.db.GroupMemberEntity(
                groupId = code,
                userId = owner.userId,
                nickname = owner.nickname,
                avatarEmoji = owner.avatarEmoji,
                joinedAt = now,
                lastActiveAt = now
            )
        )
        return RemoteGroup(code, name.ifBlank { "我们的小组" }, owner.userId, goal, now)
    }

    override suspend fun joinGroup(groupId: String, identity: UserIdentity): RemoteGroup {
        val group = groupDao.getGroup(groupId)
            ?: throw SyncException(NetworkErrorKind.NOT_FOUND, "房间不存在，请检查邀请码")
        val now = System.currentTimeMillis()
        groupDao.upsertMember(
            com.calorie.diary.data.db.GroupMemberEntity(
                groupId = groupId,
                userId = identity.userId,
                nickname = identity.nickname,
                avatarEmoji = identity.avatarEmoji,
                joinedAt = now,
                lastActiveAt = now
            )
        )
        return RemoteGroup(
            group.groupId, group.name, group.ownerId, group.calorieGoal, group.createdAt
        )
    }

    override suspend fun getGroup(groupId: String): RemoteGroup? =
        groupDao.getGroup(groupId)?.let {
            RemoteGroup(it.groupId, it.name, it.ownerId, it.calorieGoal, it.createdAt)
        }

    override suspend fun listMembers(groupId: String): List<RemoteMember> =
        groupDao.observeMembers(groupId).first().map {
            RemoteMember(it.userId, it.nickname, it.avatarEmoji, it.lastActiveAt)
        }

    override suspend fun pushEntry(groupId: String, entry: RemoteEntry): RemoteEntry {
        val id = mealDao.insert(
            com.calorie.diary.data.db.MealEntryEntity(
                date = entry.date,
                mealType = entry.mealType,
                foodName = entry.foodName,
                emoji = entry.emoji,
                grams = entry.grams,
                calories = entry.calories,
                timestamp = entry.timestamp,
                ownerId = entry.ownerId,
                ownerName = entry.ownerName,
                groupId = groupId
            )
        )
        return entry.copy(id = id)
    }

    override suspend fun pullEntries(groupId: String, date: String): List<RemoteEntry> =
        groupDao.getGroupEntries(groupId, date).map {
            RemoteEntry(
                id = it.id, date = it.date, mealType = it.mealType, foodName = it.foodName,
                emoji = it.emoji, grams = it.grams, calories = it.calories,
                timestamp = it.timestamp, ownerId = it.ownerId, ownerName = it.ownerName
            )
        }

    override suspend fun leaveGroup(groupId: String, userId: String) {
        groupDao.removeMember(groupId, userId)
    }

    private fun generateRoomCode(): String {
        // 6 位数字邀请码，尽量避免与现有房间冲突（本地校验由调用方宽松处理）
        return String.format("%06d", Random.nextInt(1_000_000))
    }
}

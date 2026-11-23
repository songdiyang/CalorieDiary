package com.calorie.diary.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "foods")
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val caloriesPer100g: Int,
    val category: String,
    val emoji: String,
    val isCustom: Boolean = false
)

@Entity(tableName = "meal_entries")
data class MealEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,          // yyyy-MM-dd
    val mealType: Int,         // 0 早餐 1 午餐 2 晚餐 3 加餐
    val foodName: String,
    val emoji: String,
    val grams: Int,
    val calories: Int,
    val timestamp: Long,
    val ownerId: String = "",
    val ownerName: String = "",
    val groupId: String = "",
    /** 云端幂等标识，本地记录为 android:<本表 id>；拉取后保留云端值以支持跨设备合并。 */
    val clientRef: String = ""
)

/** 本地已删除、等待云端删除的记录；同步时成功后移除。 */
@Entity(tableName = "meal_tombstones")
data class MealTombstoneEntity(
    @PrimaryKey val clientRef: String,
    val ownerId: String
)

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val groupId: String,     // 房间号/邀请码
    val name: String,
    val ownerId: String,
    val calorieGoal: Int,
    val createdAt: Long
)

@Entity(tableName = "group_members", primaryKeys = ["groupId", "userId"])
data class GroupMemberEntity(
    val groupId: String,
    val userId: String,
    val nickname: String,
    val avatarEmoji: String,
    val joinedAt: Long,
    val lastActiveAt: Long
)

@Entity(tableName = "water_logs", primaryKeys = ["date", "ownerId"])
data class WaterEntity(
    val date: String,
    val ml: Int,
    val ownerId: String = ""
)

/** 消息页会话列表缓存：按当前账号隔离，登录后可先展示上次的好友会话。 */
@Entity(tableName = "cached_friends", primaryKeys = ["ownerId", "userId"])
data class CachedFriendEntity(
    val ownerId: String,
    val userId: String,
    val nickname: String,
    val avatarEmoji: String,
    val friendsSince: String,
    val lastMessage: String?,
    val lastMessageAt: String?,
    val publicId: String,
    val sparkCount: Int
)

/** 私聊消息缓存：每个会话保留云端最近 200 条。 */
@Entity(
    tableName = "cached_friend_messages",
    primaryKeys = ["ownerId", "messageId"],
    indices = [
        Index(
            name = "index_cached_friend_messages_owner_friend_time",
            value = ["ownerId", "friendId", "createdAt"]
        )
    ]
)
data class CachedFriendMessageEntity(
    val ownerId: String,
    val messageId: Long,
    val friendId: String,
    val senderId: String,
    val content: String,
    val messageType: String,
    val calories: Int?,
    val entryDate: String?,
    val itemsJson: String,
    val createdAt: String
)

/** 群会话列表缓存。 */
@Entity(tableName = "cached_groups", primaryKeys = ["ownerId", "groupId"])
data class CachedGroupEntity(
    val ownerId: String,
    val groupId: String,
    val name: String,
    val groupOwnerId: String,
    val calorieGoal: Int,
    val createdAt: String,
    val lastMessage: String?,
    val lastMessageAt: String?,
    val sparkDays: Int
)

/** 群聊消息缓存：每个群保留云端最近 200 条。 */
@Entity(
    tableName = "cached_group_messages",
    primaryKeys = ["ownerId", "messageId"],
    indices = [
        Index(
            name = "index_cached_group_messages_owner_group_time",
            value = ["ownerId", "groupId", "createdAt"]
        )
    ]
)
data class CachedGroupMessageEntity(
    val ownerId: String,
    val messageId: Long,
    val groupId: String,
    val senderId: String,
    val content: String,
    val messageType: String,
    val calories: Int?,
    val entryDate: String?,
    val itemsJson: String,
    val createdAt: String
)

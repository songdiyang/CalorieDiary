package com.calorie.diary.data.db

import androidx.room.Entity
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

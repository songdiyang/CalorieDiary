package com.calorie.diary.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    @Query("SELECT * FROM foods ORDER BY isCustom ASC, name ASC")
    fun observeAll(): Flow<List<FoodEntity>>

    @Query("SELECT * FROM foods WHERE category = :category ORDER BY name ASC")
    fun observeByCategory(category: String): Flow<List<FoodEntity>>

    @Query("SELECT * FROM foods WHERE name LIKE '%' || :keyword || '%' ORDER BY isCustom ASC, name ASC")
    fun search(keyword: String): Flow<List<FoodEntity>>

    @Query("SELECT DISTINCT category FROM foods")
    fun observeCategories(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(food: FoodEntity): Long

    @Query("SELECT COUNT(*) FROM foods")
    suspend fun count(): Int
}

@Dao
interface MealDao {
    @Query("SELECT * FROM meal_entries WHERE date = :date AND ownerId = :ownerId ORDER BY timestamp ASC")
    fun observeByDate(date: String, ownerId: String): Flow<List<MealEntryEntity>>

    @Query("SELECT * FROM meal_entries WHERE date = :date AND ownerId = :ownerId ORDER BY timestamp ASC")
    suspend fun entriesForDate(date: String, ownerId: String): List<MealEntryEntity>

    @Query("SELECT * FROM meal_entries WHERE id = :id")
    suspend fun entryById(id: Long): MealEntryEntity?

    /** 云端同步按 clientRef 匹配同一条记录；未归属的历史记录也可匹配，避免重复插入。 */
    @Query(
        "SELECT * FROM meal_entries WHERE clientRef = :clientRef " +
            "AND (ownerId = :ownerId OR ownerId = '') LIMIT 1"
    )
    suspend fun findByClientRef(clientRef: String, ownerId: String): MealEntryEntity?

    /** 清理同一账号下 clientRef 重复的本地行，保留最早插入的一条。 */
    @Query(
        "DELETE FROM meal_entries WHERE clientRef != '' AND id NOT IN " +
            "(SELECT MIN(id) FROM meal_entries WHERE clientRef != '' GROUP BY clientRef, ownerId)"
    )
    suspend fun dedupeByClientRef()

    @Query("UPDATE meal_entries SET clientRef = :clientRef WHERE id = :id")
    suspend fun updateClientRef(id: Long, clientRef: String)

    /** 拉取云端后覆盖本地同一条记录的字段。 */
    @Query(
        "UPDATE meal_entries SET date = :date, mealType = :mealType, foodName = :foodName, " +
            "emoji = :emoji, grams = :grams, calories = :calories, timestamp = :timestamp WHERE id = :id"
    )
    suspend fun updateFromRemote(
        id: Long,
        date: String,
        mealType: Int,
        foodName: String,
        emoji: String,
        grams: Int,
        calories: Int,
        timestamp: Long
    )

    @Query("SELECT * FROM meal_entries WHERE date = :date AND ownerId = :ownerId AND foodName LIKE '%' || :name || '%'")
    suspend fun entriesByDateAndName(date: String, name: String, ownerId: String): List<MealEntryEntity>

    @Query("SELECT * FROM meal_entries ORDER BY timestamp ASC")
    suspend fun allEntries(): List<MealEntryEntity>

    @Query("UPDATE meal_entries SET ownerId = :ownerId WHERE id IN (:ids)")
    suspend fun updateOwnerIds(ids: List<Long>, ownerId: String)

    @Query("UPDATE meal_entries SET ownerId = :ownerId WHERE ownerId = ''")
    suspend fun claimUnowned(ownerId: String)

    @Query("SELECT * FROM meal_entries WHERE date = :date AND mealType = :mealType AND ownerId = :ownerId ORDER BY timestamp ASC")
    fun observeByDateAndMeal(date: String, mealType: Int, ownerId: String): Flow<List<MealEntryEntity>>

    @Query("SELECT COALESCE(SUM(calories), 0) FROM meal_entries WHERE date = :date AND ownerId = :ownerId")
    fun observeTotalCalories(date: String, ownerId: String): Flow<Int>

    @Query("SELECT date, COALESCE(SUM(calories), 0) AS total FROM meal_entries WHERE date BETWEEN :start AND :end AND ownerId = :ownerId GROUP BY date")
    fun observeRangeTotals(start: String, end: String, ownerId: String): Flow<List<DateTotal>>

    @Query(
        "SELECT date, SUM(calories) AS totalCalories, COUNT(*) AS entryCount " +
            "FROM meal_entries WHERE ownerId = :ownerId GROUP BY date ORDER BY date DESC LIMIT 30"
    )
    fun observeRecentDailyTotals(ownerId: String): Flow<List<DailyActivityTotal>>

    @Insert
    suspend fun insert(entry: MealEntryEntity): Long

    @Query("DELETE FROM meal_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE meal_entries SET grams = :grams, calories = :calories WHERE id = :id")
    suspend fun updateGrams(id: Long, grams: Int, calories: Int)

    @Query("DELETE FROM meal_entries WHERE date = :date AND ownerId = :ownerId AND foodName LIKE '%' || :name || '%'")
    suspend fun deleteByDateAndName(date: String, name: String, ownerId: String)

    // 我的页统计
    @Query("SELECT COUNT(DISTINCT date) FROM meal_entries WHERE ownerId = :ownerId")
    fun observeActiveDays(ownerId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM meal_entries WHERE ownerId = :ownerId")
    fun observeEntryCount(ownerId: String): Flow<Int>

    @Query("SELECT COALESCE(SUM(calories), 0) FROM meal_entries WHERE ownerId = :ownerId")
    fun observeTotalCaloriesAll(ownerId: String): Flow<Int>

    @Query("SELECT DISTINCT date FROM meal_entries WHERE ownerId = :ownerId ORDER BY date ASC")
    fun observeRecordedDates(ownerId: String): Flow<List<String>>
}

/** 待云端删除的本地记录墓碑；同步成功后移除。 */
@Dao
interface MealTombstoneDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tombstone: MealTombstoneEntity)

    @Query("DELETE FROM meal_tombstones WHERE clientRef = :clientRef")
    suspend fun delete(clientRef: String)

    @Query("SELECT * FROM meal_tombstones WHERE ownerId = :ownerId")
    suspend fun byOwner(ownerId: String): List<MealTombstoneEntity>

    /** 拉取时跳过已删除记录，避免本地删除后被云端数据复活。 */
    @Query("SELECT clientRef FROM meal_tombstones")
    suspend fun allRefs(): List<String>
}

data class DateTotal(val date: String, val total: Int)
data class DailyActivityTotal(
    val date: String,
    val totalCalories: Int,
    val entryCount: Int
)

@Dao
interface WaterDao {
    @Query("SELECT * FROM water_logs WHERE date = :date AND ownerId = :ownerId")
    fun observeByDate(date: String, ownerId: String): Flow<WaterEntity?>

    @Query("SELECT * FROM water_logs WHERE date = :date AND ownerId = :ownerId")
    suspend fun getByDateAndOwner(date: String, ownerId: String): WaterEntity?

    @Query("SELECT * FROM water_logs WHERE ownerId = ''")
    suspend fun unowned(): List<WaterEntity>

    @Query("DELETE FROM water_logs WHERE date = :date AND ownerId = ''")
    suspend fun deleteUnowned(date: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: WaterEntity)

    @Transaction
    suspend fun claimUnowned(ownerId: String) {
        unowned().forEach { legacy ->
            val existing = getByDateAndOwner(legacy.date, ownerId)
            upsert(
                WaterEntity(
                    date = legacy.date,
                    ml = maxOf(existing?.ml ?: 0, legacy.ml),
                    ownerId = ownerId
                )
            )
            deleteUnowned(legacy.date)
        }
    }
}

@Dao
interface GroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroup(group: GroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMember(member: GroupMemberEntity)

    @Query("SELECT * FROM groups ORDER BY createdAt DESC")
    fun observeGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE groupId = :id")
    fun observeGroup(id: String): Flow<GroupEntity?>

    @Query("SELECT * FROM groups WHERE groupId = :id")
    suspend fun getGroup(id: String): GroupEntity?

    @Query("SELECT * FROM group_members WHERE groupId = :id ORDER BY joinedAt ASC")
    fun observeMembers(id: String): Flow<List<GroupMemberEntity>>

    @Query("SELECT * FROM meal_entries WHERE groupId = :id AND date = :date ORDER BY timestamp ASC")
    fun observeGroupEntries(id: String, date: String): Flow<List<MealEntryEntity>>

    @Query("SELECT * FROM meal_entries WHERE groupId = :id AND date = :date ORDER BY timestamp ASC")
    suspend fun getGroupEntries(id: String, date: String): List<MealEntryEntity>

    @Query("SELECT ownerId, ownerName, COALESCE(SUM(calories),0) AS memberTotal FROM meal_entries WHERE groupId = :id AND date = :date GROUP BY ownerId")
    fun observeMemberTotals(id: String, date: String): Flow<List<MemberTotal>>

    @Query("DELETE FROM group_members WHERE groupId = :id AND userId = :userId")
    suspend fun removeMember(id: String, userId: String)

    @Query("DELETE FROM groups WHERE groupId = :id")
    suspend fun deleteGroup(id: String)
}

data class MemberTotal(
    val ownerId: String,
    val ownerName: String,
    val memberTotal: Int
)

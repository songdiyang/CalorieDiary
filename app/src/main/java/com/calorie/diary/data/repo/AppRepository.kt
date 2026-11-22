package com.calorie.diary.data.repo

import android.content.Context
import com.calorie.diary.data.DateUtils
import com.calorie.diary.data.db.AppDatabase
import com.calorie.diary.data.db.DailyActivityTotal
import com.calorie.diary.data.db.DateTotal
import com.calorie.diary.data.db.FoodEntity
import com.calorie.diary.data.db.GroupEntity
import com.calorie.diary.data.db.GroupMemberEntity
import com.calorie.diary.data.db.MealEntryEntity
import com.calorie.diary.data.db.MealTombstoneEntity
import com.calorie.diary.data.db.MemberTotal
import com.calorie.diary.data.db.UserPreferences
import com.calorie.diary.data.db.UserIdentity
import com.calorie.diary.data.db.UserProfile
import com.calorie.diary.data.db.WaterEntity
import com.calorie.diary.data.sync.RemoteGroup
import com.calorie.diary.data.supabase.DefaultAvatarCatalog
import com.calorie.diary.data.supabase.SupabaseApi
import com.calorie.diary.data.supabase.SupabaseAuthRepository
import com.calorie.diary.data.supabase.SupabaseSession
import com.calorie.diary.data.sync.SyncProvider
import com.calorie.diary.data.vision.scaledCalories
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun canSyncMealEntry(ownerId: String, userId: String): Boolean =
    ownerId.isBlank() || ownerId == userId

@OptIn(ExperimentalCoroutinesApi::class)
class AppRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val db = AppDatabase.get(context)
    private val prefs = UserPreferences(context)
    private val auth = SupabaseAuthRepository.get(appContext)
    private val activeOwnerId = MutableStateFlow("")
    /** 串行化饮食同步：避免同一时刻并发 pull 造成重复插入。 */
    private val mealSyncMutex = Mutex()

    val profile: Flow<UserProfile> = prefs.profile

    suspend fun setAvatarSelection(avatarId: String) {
        val marker = DefaultAvatarCatalog.marker(avatarId) ?: return
        prefs.setAvatarSelection(marker)
    }

    fun setActiveUser(userId: String) {
        activeOwnerId.value = userId
        prefs.setActiveUserId(userId)
    }

    suspend fun prepareAccount(userId: String) {
        prefs.migrateLegacyProfile(userId)
        db.mealDao().claimUnowned(userId)
        db.waterDao().claimUnowned(userId)
    }

    // 食物
    fun observeAllFoods(): Flow<List<FoodEntity>> = db.foodDao().observeAll()
    fun searchFoods(keyword: String): Flow<List<FoodEntity>> =
        if (keyword.isBlank()) db.foodDao().observeAll() else db.foodDao().search(keyword.trim())

    fun observeFoodsByCategory(category: String): Flow<List<FoodEntity>> =
        db.foodDao().observeByCategory(category)

    suspend fun addCustomFood(name: String, caloriesPer100g: Int, emoji: String, category: String): Long =
        db.foodDao().insert(
            FoodEntity(
                name = name,
                caloriesPer100g = caloriesPer100g,
                category = category,
                emoji = emoji,
                isCustom = true
            )
        )

    // 饮食记录：所有页面只读取当前账号的数据。
    fun observeEntries(date: String): Flow<List<MealEntryEntity>> =
        activeOwnerId.flatMapLatest { db.mealDao().observeByDate(date, it) }
    fun observeEntries(date: String, mealType: Int): Flow<List<MealEntryEntity>> =
        activeOwnerId.flatMapLatest { db.mealDao().observeByDateAndMeal(date, mealType, it) }

    fun observeTotalCalories(date: String): Flow<Int> =
        activeOwnerId.flatMapLatest { db.mealDao().observeTotalCalories(date, it) }
    fun observeRecentDailyTotals(): Flow<List<DailyActivityTotal>> =
        activeOwnerId.flatMapLatest { db.mealDao().observeRecentDailyTotals(it) }

    fun observeMonthTotals(anchor: String): Flow<List<DateTotal>> {
        val (start, end) = DateUtils.monthRange(anchor)
        return activeOwnerId.flatMapLatest { db.mealDao().observeRangeTotals(start, end, it) }
    }

    // 我的页统计
    fun observeActiveDays(): Flow<Int> =
        activeOwnerId.flatMapLatest { db.mealDao().observeActiveDays(it) }
    fun observeEntryCount(): Flow<Int> =
        activeOwnerId.flatMapLatest { db.mealDao().observeEntryCount(it) }
    fun observeTotalCaloriesAll(): Flow<Int> =
        activeOwnerId.flatMapLatest { db.mealDao().observeTotalCaloriesAll(it) }
    fun observeRecordedDates(): Flow<List<String>> =
        activeOwnerId.flatMapLatest { db.mealDao().observeRecordedDates(it) }

    suspend fun addEntry(
        date: String,
        mealType: Int,
        food: FoodEntity,
        grams: Int
    ) {
        val calories = Math.round(food.caloriesPer100g * grams / 100.0).toInt()
        val entry = MealEntryEntity(
            date = date,
            mealType = mealType,
            foodName = food.name,
            emoji = food.emoji,
            grams = grams,
            calories = calories,
            timestamp = System.currentTimeMillis(),
            ownerId = auth.account.first()?.userId.orEmpty()
        )
        val stored = entry.copy(id = db.mealDao().insert(entry))
        uploadMealEntrySafely(stored)
    }

    suspend fun addQuickEntry(
        date: String,
        mealType: Int,
        name: String,
        emoji: String,
        grams: Int,
        calories: Int
    ) {
        val entry = MealEntryEntity(
            date = date,
            mealType = mealType,
            foodName = name,
            emoji = emoji,
            grams = grams,
            calories = calories,
            timestamp = System.currentTimeMillis(),
            ownerId = auth.account.first()?.userId.orEmpty()
        )
        val stored = entry.copy(id = db.mealDao().insert(entry))
        uploadMealEntrySafely(stored)
    }

    private suspend fun uploadMealEntrySafely(entry: MealEntryEntity) {
        try {
            auth.withValidSession { api, session ->
                if (!canSyncMealEntry(entry.ownerId, session.userId)) {
                    return@withValidSession
                }
                // 首次上传时固化 clientRef，后续同步与删除都依赖它。
                val clientRef = entry.clientRef.ifBlank { "android:${entry.id}" }
                if (entry.clientRef.isBlank()) {
                    db.mealDao().updateClientRef(entry.id, clientRef)
                }
                api.upsertMealEntries(session, listOf(entry.copy(clientRef = clientRef)))
                if (entry.ownerId.isBlank()) {
                    db.mealDao().updateOwnerIds(listOf(entry.id), session.userId)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // 云端失败不影响本地保存；下次登录或启动时按 client_ref 幂等重试。
        }
    }

    /** 双向同步日历每一天的食物：先补做云端删除，再拉取合并到本地，最后上传本地记录。 */
    suspend fun syncMealEntries() {
        mealSyncMutex.withLock {
            auth.withValidSession { api, session ->
                // 清理历史并发/迁移遗留的重复行，保证 clientRef 唯一。
                db.mealDao().dedupeByClientRef()
                flushMealTombstones(api, session)
                pullMealEntries(api, session)
                pushMealEntries(api, session)
            }
        }
    }

    /** 补做云端删除；只处理当前账号的墓碑。 */
    private suspend fun flushMealTombstones(api: SupabaseApi, session: SupabaseSession) {
        db.mealTombstoneDao().byOwner(session.userId).forEach { tombstone ->
            try {
                api.deleteMealEntryByRef(session, tombstone.clientRef)
                db.mealTombstoneDao().delete(tombstone.clientRef)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // 下次同步继续重试。
            }
        }
    }

    /** 拉取云端记录合并到本地；已删除记录不复活，同 clientRef 以云端为准覆盖。 */
    private suspend fun pullMealEntries(api: SupabaseApi, session: SupabaseSession) {
        val tombstones = db.mealTombstoneDao().allRefs().toSet()
        api.listMealEntries(session).forEach { item ->
            if (item.clientRef.isBlank() || item.clientRef in tombstones) return@forEach
            val local = db.mealDao().findByClientRef(item.clientRef, session.userId)
            if (local == null) {
                db.mealDao().insert(
                    MealEntryEntity(
                        date = item.date,
                        mealType = item.mealType,
                        foodName = item.foodName,
                        emoji = item.emoji,
                        grams = item.grams,
                        calories = item.calories,
                        timestamp = item.recordedAtMillis,
                        ownerId = session.userId,
                        clientRef = item.clientRef
                    )
                )
            } else {
                db.mealDao().updateFromRemote(
                    id = local.id,
                    date = item.date,
                    mealType = item.mealType,
                    foodName = item.foodName,
                    emoji = item.emoji,
                    grams = item.grams,
                    calories = item.calories,
                    timestamp = item.recordedAtMillis
                )
            }
        }
    }

    /** 上传当前账号的本地记录；历史未归属记录只在首次同步时归属当前账号。 */
    private suspend fun pushMealEntries(api: SupabaseApi, session: SupabaseSession) {
        val entries = db.mealDao().allEntries().filter {
            canSyncMealEntry(it.ownerId, session.userId)
        }
        if (entries.isEmpty()) return
        val prepared = entries.map { entry ->
            if (entry.clientRef.isBlank()) {
                val ref = "android:${entry.id}"
                db.mealDao().updateClientRef(entry.id, ref)
                entry.copy(clientRef = ref)
            } else {
                entry
            }
        }
        // 个人记录量级不大，分批上传避免单次请求过大。
        prepared.chunked(200).forEach { api.upsertMealEntries(session, it) }
        val unownedIds = entries.filter { it.ownerId.isBlank() }.map { it.id }
        if (unownedIds.isNotEmpty()) {
            db.mealDao().updateOwnerIds(unownedIds, session.userId)
        }
    }

    suspend fun deleteEntry(id: Long) {
        val entry = db.mealDao().entryById(id)
        db.mealDao().deleteById(id)
        entry?.let { deleteMealEntryRemotely(it) }
    }

    /** 同步删除云端记录；失败时记墓碑，等下次同步补删。 */
    private suspend fun deleteMealEntryRemotely(entry: MealEntryEntity) {
        // 未归属或从未上传的记录云端本来就没有。
        if (entry.ownerId.isBlank() || entry.clientRef.isBlank()) return
        try {
            auth.withValidSession { api, session ->
                if (!canSyncMealEntry(entry.ownerId, session.userId)) return@withValidSession
                try {
                    api.deleteMealEntryByRef(session, entry.clientRef)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    db.mealTombstoneDao().insert(
                        MealTombstoneEntity(clientRef = entry.clientRef, ownerId = session.userId)
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // 未登录时无需补删，记录已从本地删除。
        }
    }

    /** 调整已记录食物的克数，按原记录每克热量等比重算热量后再上传云端覆盖同一条记录。 */
    suspend fun updateEntryGrams(entry: MealEntryEntity, grams: Int) {
        val targetGrams = grams.coerceAtLeast(1)
        val targetCalories = scaledCalories(entry.grams, entry.calories, targetGrams)
        db.mealDao().updateGrams(entry.id, targetGrams, targetCalories)
        uploadMealEntrySafely(entry.copy(grams = targetGrams, calories = targetCalories))
    }

    suspend fun entriesForDate(date: String): List<MealEntryEntity> =
        db.mealDao().entriesForDate(date, activeOwnerId.value)

    suspend fun deleteByDateAndName(date: String, name: String) {
        val ownerId = activeOwnerId.value
        val deleted = db.mealDao().entriesByDateAndName(date, name, ownerId)
        db.mealDao().deleteByDateAndName(date, name, ownerId)
        deleted.forEach { deleteMealEntryRemotely(it) }
    }

    // —— 多人协作 ——
    val identity: Flow<UserIdentity> = prefs.identity

    fun observeGroups(): Flow<List<GroupEntity>> = db.groupDao().observeGroups()
    fun observeGroup(id: String): Flow<GroupEntity?> = db.groupDao().observeGroup(id)
    fun observeMembers(id: String): Flow<List<GroupMemberEntity>> =
        db.groupDao().observeMembers(id)
    fun observeGroupEntries(id: String, date: String): Flow<List<MealEntryEntity>> =
        db.groupDao().observeGroupEntries(id, date)
    fun observeMemberTotals(id: String, date: String): Flow<List<MemberTotal>> =
        db.groupDao().observeMemberTotals(id, date)

    suspend fun createGroup(name: String, goal: Int): RemoteGroup {
        val id = prefs.ensureUserId()
        val identity = prefs.identity.first().let { if (it.userId == id) it else it.copy(userId = id) }
        return SyncProvider.resolve(appContext, identity).createGroup(name, goal, identity)
    }

    suspend fun joinGroup(code: String): RemoteGroup {
        val id = prefs.ensureUserId()
        val identity = prefs.identity.first().let { if (it.userId == id) it else it.copy(userId = id) }
        return SyncProvider.resolve(appContext, identity).joinGroup(code, identity)
    }

    suspend fun leaveGroup(groupId: String, userId: String) {
        db.groupDao().removeMember(groupId, userId)
    }

    suspend fun setNickname(value: String) = prefs.setNickname(value)
    suspend fun setServerBaseUrl(value: String) = prefs.setServerBaseUrl(value)
    suspend fun setRemoteEnabled(value: Boolean) = prefs.setRemoteEnabled(value)

    // 饮水
    fun observeWater(date: String): Flow<WaterEntity?> =
        activeOwnerId.flatMapLatest { db.waterDao().observeByDate(date, it) }

    suspend fun adjustWater(date: String, deltaMl: Int) {
        val ownerId = activeOwnerId.value
        val current = db.waterDao().observeByDate(date, ownerId).first()?.ml ?: 0
        val next = (current + deltaMl).coerceAtLeast(0)
        db.waterDao().upsert(WaterEntity(date = date, ml = next, ownerId = ownerId))
    }

    // 设置
    suspend fun setCalorieGoal(value: Int) = prefs.setCalorieGoal(value)
    suspend fun setWaterGoal(value: Int) = prefs.setWaterGoal(value)
    suspend fun setGender(value: String) = prefs.setGender(value)
    suspend fun setHeight(value: Int) = prefs.setHeight(value)
    suspend fun setWeight(value: Float) = prefs.setWeight(value)
    suspend fun setAge(value: Int) = prefs.setAge(value)
    suspend fun setFastingStart(value: Long) = prefs.setFastingStart(value)
    suspend fun setFastingHours(value: Int) = prefs.setFastingHours(value)
    suspend fun setWaterReminder(value: Boolean) = prefs.setWaterReminder(value)

    companion object {
        @Volatile
        private var instance: AppRepository? = null

        fun get(context: Context): AppRepository =
            instance ?: synchronized(this) {
                instance ?: AppRepository(context.applicationContext).also { instance = it }
            }
    }
}

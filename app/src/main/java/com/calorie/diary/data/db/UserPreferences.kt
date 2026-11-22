package com.calorie.diary.data.db

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

data class UserProfile(
    val calorieGoal: Int,
    val waterGoal: Int,
    val gender: String,
    val heightCm: Int,
    val weightKg: Float,
    val age: Int,
    val fastingStart: Long,
    val fastingHours: Int,
    val waterReminder: Boolean
)

/**
 * 多人协作身份与云端配置。userId 首启随机生成，昵称可改。
 */
data class UserIdentity(
    val userId: String,
    val nickname: String,
    val avatarEmoji: String,
    val serverBaseUrl: String,
    val remoteEnabled: Boolean
)

internal fun scopedPreferenceName(base: String, userId: String): String =
    if (userId.isBlank()) base else "${base}_$userId"

class UserPreferences(private val context: Context) {

    private val activeUserId = MutableStateFlow("")

    fun setActiveUserId(userId: String) {
        activeUserId.value = userId
    }

    private object Keys {
        val CALORIE_GOAL = intPreferencesKey("calorie_goal")
        val WATER_GOAL = intPreferencesKey("water_goal")
        val GENDER = stringPreferencesKey("gender")
        val HEIGHT = intPreferencesKey("height")
        val WEIGHT = floatPreferencesKey("weight")
        val AGE = intPreferencesKey("age")
        val FASTING_START = longPreferencesKey("fasting_start")
        val FASTING_HOURS = intPreferencesKey("fasting_hours")
        val WATER_REMINDER = booleanPreferencesKey("water_reminder")
        val USER_ID = stringPreferencesKey("identity_user_id")
        val NICKNAME = stringPreferencesKey("identity_nickname")
        val SERVER_URL = stringPreferencesKey("server_base_url")
        val REMOTE_ENABLED = booleanPreferencesKey("remote_enabled")
    }

    val profile: Flow<UserProfile> = combine(context.dataStore.data, activeUserId) { p, userId ->
        UserProfile(
            calorieGoal = p[intPreferencesKey(scoped("calorie_goal", userId))] ?: 1800,
            waterGoal = p[intPreferencesKey(scoped("water_goal", userId))] ?: 1500,
            gender = p[stringPreferencesKey(scoped("gender", userId))] ?: "女",
            heightCm = p[intPreferencesKey(scoped("height", userId))] ?: 165,
            weightKg = p[floatPreferencesKey(scoped("weight", userId))] ?: 60f,
            age = p[intPreferencesKey(scoped("age", userId))] ?: 25,
            fastingStart = p[longPreferencesKey(scoped("fasting_start", userId))] ?: 0L,
            fastingHours = p[intPreferencesKey(scoped("fasting_hours", userId))] ?: 16,
            waterReminder = p[booleanPreferencesKey(scoped("water_reminder", userId))] ?: false
        )
    }

    private fun scoped(base: String, userId: String): String = scopedPreferenceName(base, userId)

    /** 旧版全局偏好只迁移给首个登录账号，后续账号使用独立默认值。 */
    suspend fun migrateLegacyProfile(userId: String) {
        if (userId.isBlank()) return
        val migrated = booleanPreferencesKey("legacy_profile_migrated")
        context.dataStore.edit { p ->
            if (p[migrated] == true) return@edit
            p[intPreferencesKey(scoped("calorie_goal", userId))] = p[Keys.CALORIE_GOAL] ?: 1800
            p[intPreferencesKey(scoped("water_goal", userId))] = p[Keys.WATER_GOAL] ?: 1500
            p[stringPreferencesKey(scoped("gender", userId))] = p[Keys.GENDER] ?: "女"
            p[intPreferencesKey(scoped("height", userId))] = p[Keys.HEIGHT] ?: 165
            p[floatPreferencesKey(scoped("weight", userId))] = p[Keys.WEIGHT] ?: 60f
            p[intPreferencesKey(scoped("age", userId))] = p[Keys.AGE] ?: 25
            p[longPreferencesKey(scoped("fasting_start", userId))] = p[Keys.FASTING_START] ?: 0L
            p[intPreferencesKey(scoped("fasting_hours", userId))] = p[Keys.FASTING_HOURS] ?: 16
            p[booleanPreferencesKey(scoped("water_reminder", userId))] =
                p[Keys.WATER_REMINDER] ?: false
            p[migrated] = true
        }
    }

    suspend fun setCalorieGoal(value: Int) = edit {
        it[intPreferencesKey(scoped("calorie_goal", activeUserId.value))] = value
    }
    suspend fun setWaterGoal(value: Int) = edit {
        it[intPreferencesKey(scoped("water_goal", activeUserId.value))] = value
    }
    suspend fun setGender(value: String) = edit {
        it[stringPreferencesKey(scoped("gender", activeUserId.value))] = value
    }
    suspend fun setHeight(value: Int) = edit {
        it[intPreferencesKey(scoped("height", activeUserId.value))] = value
    }
    suspend fun setWeight(value: Float) = edit {
        it[floatPreferencesKey(scoped("weight", activeUserId.value))] = value
    }
    suspend fun setAge(value: Int) = edit {
        it[intPreferencesKey(scoped("age", activeUserId.value))] = value
    }
    suspend fun setFastingStart(value: Long) = edit {
        it[longPreferencesKey(scoped("fasting_start", activeUserId.value))] = value
    }
    suspend fun setFastingHours(value: Int) = edit {
        it[intPreferencesKey(scoped("fasting_hours", activeUserId.value))] = value
    }
    suspend fun setWaterReminder(value: Boolean) = edit {
        it[booleanPreferencesKey(scoped("water_reminder", activeUserId.value))] = value
    }

    val identity: Flow<UserIdentity> = combine(context.dataStore.data, activeUserId) { p, userId ->
        val existing = p[Keys.USER_ID]
        val id = existing ?: ("u_" + java.util.UUID.randomUUID().toString().take(8))
        UserIdentity(
            userId = id,
            nickname = p[Keys.NICKNAME] ?: "我",
            avatarEmoji = p[stringPreferencesKey(scoped("avatar_selection", userId))] ?: "",
            serverBaseUrl = p[Keys.SERVER_URL] ?: "",
            remoteEnabled = p[Keys.REMOTE_ENABLED] ?: false
        )
    }

    suspend fun ensureUserId(): String {
        var id = ""
        context.dataStore.edit { p ->
            val cur = p[Keys.USER_ID]
            id = cur ?: ("u_" + java.util.UUID.randomUUID().toString().take(8)).also {
                p[Keys.USER_ID] = it
            }
        }
        return id
    }

    suspend fun setAvatarSelection(value: String) = edit {
        it[stringPreferencesKey(scoped("avatar_selection", activeUserId.value))] = value
    }
    suspend fun setNickname(value: String) = edit { it[Keys.NICKNAME] = value }
    suspend fun setServerBaseUrl(value: String) = edit { it[Keys.SERVER_URL] = value }
    suspend fun setRemoteEnabled(value: Boolean) = edit { it[Keys.REMOTE_ENABLED] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}

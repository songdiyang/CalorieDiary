package com.calorie.diary.data.supabase

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.supabaseAuthStore: DataStore<Preferences> by preferencesDataStore(
    name = "supabase_auth"
)

/** 应用私有目录中的多账号会话存储；不保存邮箱密码。 */
class SupabaseSessionStore(context: Context) {

    private val store = context.applicationContext.supabaseAuthStore

    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val EXPIRES_AT = longPreferencesKey("expires_at_millis")
        val USER_ID = stringPreferencesKey("user_id")
        val EMAIL = stringPreferencesKey("email")
        val ACCOUNTS_JSON = stringPreferencesKey("accounts_json")
    }

    val session: Flow<SupabaseSession?> = store.data.map { readActive(it) }

    val accounts: Flow<List<AuthAccount>> = store.data.map { p ->
        sessionsIncludingActive(p)
            .map { AuthAccount(it.userId, it.email) }
            .distinctBy { it.userId }
            .sortedBy { it.email }
    }

    suspend fun save(session: SupabaseSession) {
        store.edit { p ->
            val updated = sessionsIncludingActive(p)
                .filterNot { it.userId == session.userId } + session
            p[Keys.ACCOUNTS_JSON] = encodeSessions(updated)
            writeActive(p, session)
        }
    }

    suspend fun switchTo(userId: String): SupabaseSession? {
        var selected: SupabaseSession? = null
        store.edit { p ->
            selected = sessionsIncludingActive(p).firstOrNull { it.userId == userId }
            selected?.let { writeActive(p, it) }
        }
        return selected
    }

    /** 仅退出当前账号，其他账号的会话仍保留在应用私有存储中。 */
    suspend fun clear() {
        store.edit { p ->
            val currentId = p[Keys.USER_ID]
            if (!currentId.isNullOrBlank()) {
                val remaining = sessionsIncludingActive(p).filterNot { it.userId == currentId }
                p[Keys.ACCOUNTS_JSON] = encodeSessions(remaining)
            }
            clearActive(p)
        }
    }

    private fun readActive(p: Preferences): SupabaseSession? {
        val accessToken = SessionCipher.decrypt(p[Keys.ACCESS_TOKEN].orEmpty())
        val refreshToken = SessionCipher.decrypt(p[Keys.REFRESH_TOKEN].orEmpty())
        val expiresAt = p[Keys.EXPIRES_AT]
        val userId = p[Keys.USER_ID]
        if (accessToken.isNullOrBlank() || refreshToken.isNullOrBlank() ||
            expiresAt == null || userId.isNullOrBlank()
        ) {
            return null
        }
        return SupabaseSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtMillis = expiresAt,
            userId = userId,
            email = SessionCipher.decrypt(p[Keys.EMAIL].orEmpty())
        )
    }

    private fun writeActive(p: MutablePreferences, session: SupabaseSession) {
        p[Keys.ACCESS_TOKEN] = SessionCipher.encrypt(session.accessToken)
        p[Keys.REFRESH_TOKEN] = SessionCipher.encrypt(session.refreshToken)
        p[Keys.EXPIRES_AT] = session.expiresAtMillis
        p[Keys.USER_ID] = session.userId
        p[Keys.EMAIL] = SessionCipher.encrypt(session.email)
    }

    /** 把历史明文令牌原地加密；只在检测到明文时写入一次。 */
    suspend fun encryptLegacyIfNeeded() {
        val snapshot = store.data.first()
        val legacy = listOf(
            snapshot[Keys.ACCESS_TOKEN],
            snapshot[Keys.REFRESH_TOKEN],
            snapshot[Keys.EMAIL],
            snapshot[Keys.ACCOUNTS_JSON]
        ).any { it != null && it.isNotBlank() && !SessionCipher.isEncrypted(it) }
        if (!legacy) return
        store.edit { p ->
            p[Keys.ACCESS_TOKEN]?.let { p[Keys.ACCESS_TOKEN] = SessionCipher.encrypt(it) }
            p[Keys.REFRESH_TOKEN]?.let { p[Keys.REFRESH_TOKEN] = SessionCipher.encrypt(it) }
            p[Keys.EMAIL]?.let { p[Keys.EMAIL] = SessionCipher.encrypt(it) }
            p[Keys.ACCOUNTS_JSON]?.let { p[Keys.ACCOUNTS_JSON] = SessionCipher.encrypt(it) }
        }
    }

    private fun clearActive(p: MutablePreferences) {
        p.remove(Keys.ACCESS_TOKEN)
        p.remove(Keys.REFRESH_TOKEN)
        p.remove(Keys.EXPIRES_AT)
        p.remove(Keys.USER_ID)
        p.remove(Keys.EMAIL)
    }

    private fun sessionsIncludingActive(p: Preferences): List<SupabaseSession> {
        val saved = parseSessions(p[Keys.ACCOUNTS_JSON])
        val active = readActive(p) ?: return saved
        return saved.filterNot { it.userId == active.userId } + active
    }

    private fun parseSessions(raw: String?): List<SupabaseSession> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(SessionCipher.decrypt(raw))
            (0 until array.length()).mapNotNull { index ->
                val item = array.getJSONObject(index)
                val session = SupabaseSession(
                    accessToken = item.optString("access_token"),
                    refreshToken = item.optString("refresh_token"),
                    expiresAtMillis = item.optLong("expires_at_millis", 0L),
                    userId = item.optString("user_id"),
                    email = item.optString("email")
                )
                session.takeIf {
                    it.accessToken.isNotBlank() && it.refreshToken.isNotBlank() &&
                        it.userId.isNotBlank() && it.expiresAtMillis > 0L
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeSessions(sessions: List<SupabaseSession>): String {
        val array = JSONArray()
        sessions.forEach { session ->
            array.put(
                JSONObject()
                    .put("access_token", session.accessToken)
                    .put("refresh_token", session.refreshToken)
                    .put("expires_at_millis", session.expiresAtMillis)
                    .put("user_id", session.userId)
                    .put("email", session.email)
            )
        }
        return SessionCipher.encrypt(array.toString())
    }
}

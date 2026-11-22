package com.calorie.diary.data.supabase

import android.content.Context
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SupabaseAuthRepository private constructor(context: Context) {

    private val api = SupabaseApi()
    private val store = SupabaseSessionStore(context)
    private val refreshMutex = Mutex()

    val account: Flow<AuthAccount?> = store.session.map { it?.toAccount() }
    val accounts: Flow<List<AuthAccount>> = store.accounts

    val isConfigured: Boolean
        get() = api.isConfigured

    suspend fun isEmailRegistered(email: String): Boolean = api.isEmailRegistered(email)

    suspend fun isInvitationCodeValid(code: String): Boolean = api.isInvitationCodeValid(code)

    suspend fun isInvitationCodeDeleted(code: String): Boolean = api.isInvitationCodeDeleted(code)

    suspend fun applyInvitationCode(code: String): Boolean = withValidSession { api, session ->
        api.applyInvitationCode(session, code)
    }

    suspend fun sendRegisterCode(email: String) = api.sendEmailOtp(email, createUser = true)

    suspend fun sendDeletionCode(email: String) = api.sendEmailOtp(email, createUser = false)

    /** 验证码注册：先验证邮箱归属，再设置初始密码并保存会话。 */
    suspend fun register(email: String, password: String, code: String): SupabaseSession {
        val session = api.verifyEmailOtp(email, code)
        api.setPassword(session, password)
        store.save(session)
        return session
    }

    /** 注销账号：验证码必须属于当前登录账号的邮箱，防止误删其他已保存账号。 */
    suspend fun deleteAccount(email: String, code: String) {
        val current = validSession() ?: throw SupabaseApiException(401, "session_missing")
        val verified = api.verifyEmailOtp(email, code)
        if (!verified.userId.equals(current.userId, ignoreCase = true)) {
            throw SupabaseApiException(400, "email_mismatch")
        }
        api.deleteAccount(verified)
        store.clear()
    }

    suspend fun signIn(email: String, password: String): SupabaseSession =
        api.signIn(email, password).also { store.save(it) }

    suspend fun restoreSession(): SupabaseSession? = validSession()

    suspend fun currentSession(): SupabaseSession? = validSession()

    suspend fun switchAccount(userId: String): SupabaseSession {
        store.switchTo(userId) ?: throw SupabaseApiException(404, "account_not_found")
        return validSession() ?: throw SupabaseApiException(401, "session_missing")
    }

    suspend fun loadProfile(): Pair<SupabaseSession, CloudProfile?> {
        val session = validSession() ?: throw SupabaseApiException(401, "session_missing")
        return session to api.fetchProfile(session)
    }

    suspend fun updateAvatar(avatarValue: String) = withValidSession { api, session ->
        api.updateAvatar(session, avatarValue)
    }

    suspend fun updateNickname(nickname: String): NicknameUpdateResult = withValidSession { api, session ->
        api.updateNickname(session, nickname)
    }

    suspend fun loadNicknameStatus(): NicknameChangeStatus = withValidSession { api, session ->
        api.fetchNicknameStatus(session)
    }

    suspend fun signOut() {
        val session = store.session.first()
        try {
            if (session != null && api.isConfigured) api.logout(session)
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            // 离线时也允许退出本机；撤销云端会话失败不阻断本地数据清理。
        } finally {
            store.clear()
        }
    }

    suspend fun clearSession() = store.clear()

    internal suspend fun <T> withValidSession(
        block: suspend (SupabaseApi, SupabaseSession) -> T
    ): T {
        val session = validSession() ?: throw SupabaseApiException(401, "session_missing")
        return block(api, session)
    }

    private suspend fun validSession(): SupabaseSession? {
        val current = store.session.first() ?: return null
        if (!current.needsRefresh()) return current
        return refreshMutex.withLock {
            val latest = store.session.first() ?: return@withLock null
            if (!latest.needsRefresh()) return@withLock latest
            try {
                api.refresh(latest.refreshToken).also { store.save(it) }
            } catch (e: SupabaseApiException) {
                if (e.statusCode in 400..499) store.clear()
                throw e
            }
        }
    }

    companion object {
        @Volatile
        private var instance: SupabaseAuthRepository? = null

        fun get(context: Context): SupabaseAuthRepository =
            instance ?: synchronized(this) {
                instance ?: SupabaseAuthRepository(context.applicationContext).also { instance = it }
            }
    }
}

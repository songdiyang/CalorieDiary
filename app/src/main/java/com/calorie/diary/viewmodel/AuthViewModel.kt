package com.calorie.diary.viewmodel

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calorie.diary.R
import com.calorie.diary.data.network.NetworkErrorKind
import com.calorie.diary.data.network.toNetworkErrorKind
import com.calorie.diary.data.repo.AppRepository
import com.calorie.diary.data.supabase.AuthAccount
import com.calorie.diary.data.supabase.AuthInputValidator
import com.calorie.diary.data.supabase.CloudGameRepository
import com.calorie.diary.data.supabase.CloudProfile
import com.calorie.diary.data.supabase.DefaultAvatarCatalog
import com.calorie.diary.data.supabase.NicknameChangeStatus
import com.calorie.diary.data.supabase.SupabaseApiException
import com.calorie.diary.data.supabase.SupabaseAuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val configured: Boolean = true,
    val restoring: Boolean = true,
    val busy: Boolean = false,
    val account: AuthAccount? = null,
    val cloudProfile: CloudProfile? = null,
    @StringRes val messageRes: Int? = null,
    val messageIsError: Boolean = false
)

/** 用户名修改状态：剩余次数由服务端统计，弹窗里实时展示。 */
data class NicknameUiState(
    val loading: Boolean = false,
    val status: NicknameChangeStatus? = null,
    val busy: Boolean = false,
    @StringRes val errorRes: Int? = null
)

/** 注销账号状态：邮箱验证码发送与删除进度；codeSentCount 用于触发重发倒计时。 */
data class AccountDeletionUiState(
    val sendingCode: Boolean = false,
    val codeSentCount: Int = 0,
    val busy: Boolean = false,
    @StringRes val errorRes: Int? = null
)

/** 找回密码状态：验证码发送与提交进度；codeSentCount 用于触发重发倒计时。 */
data class PasswordResetUiState(
    val sendingCode: Boolean = false,
    val busy: Boolean = false,
    val codeSentCount: Int = 0,
    @StringRes val errorRes: Int? = null
)

class AuthViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SupabaseAuthRepository.get(app)
    private val appRepo = AppRepository.get(app)
    private val gameRepo = CloudGameRepository.get(app)
    private val _uiState = MutableStateFlow(AuthUiState(configured = repo.isConfigured))
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()
    val savedAccounts: StateFlow<List<AuthAccount>> = repo.accounts
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _nicknameState = MutableStateFlow(NicknameUiState())
    val nicknameState: StateFlow<NicknameUiState> = _nicknameState.asStateFlow()
    private val _deletionState = MutableStateFlow(AccountDeletionUiState())
    val deletionState: StateFlow<AccountDeletionUiState> = _deletionState.asStateFlow()
    private val _passwordResetState = MutableStateFlow(PasswordResetUiState())
    val passwordResetState: StateFlow<PasswordResetUiState> = _passwordResetState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.account.collect { account ->
                if (_uiState.value.account?.userId != account?.userId) {
                    _uiState.update { it.copy(account = account, cloudProfile = null) }
                }
            }
        }
        if (repo.isConfigured) restoreSession() else {
            _uiState.update { it.copy(restoring = false) }
        }
    }

    fun signIn(email: String, password: String) {
        if (!validInput(email, password)) return
        launchAuth {
            val session = repo.signIn(email.trim(), password)
            _uiState.update { it.copy(account = session.toAccount(), cloudProfile = null) }
            val profileLoaded = loadProfile(showError = false)
            _uiState.update {
                it.copy(
                    messageRes = if (profileLoaded) R.string.auth_success_sign_in
                    else R.string.auth_profile_read_failed,
                    messageIsError = !profileLoaded
                )
            }
        }
    }

    /** 注册第一步：校验邮箱可用性（邀请码选填，填写时校验有效性）并发送验证码。 */
    fun sendRegisterCode(email: String, invitationCode: String, onSent: () -> Unit) {
        if (!AuthInputValidator.isValidEmail(email)) {
            _uiState.update {
                it.copy(messageRes = R.string.auth_invalid_email, messageIsError = true)
            }
            return
        }
        val invite = invitationCode.trim()
        if (invite.isNotEmpty() && !AuthInputValidator.isValidInvitationCode(invite)) {
            _uiState.update {
                it.copy(messageRes = R.string.auth_invitation_invalid, messageIsError = true)
            }
            return
        }
        launchAuth {
            if (repo.isEmailRegistered(email.trim())) {
                _uiState.update {
                    it.copy(messageRes = R.string.auth_email_registered, messageIsError = true)
                }
                return@launchAuth
            }
            if (invite.isNotEmpty() && !repo.isInvitationCodeValid(invite)) {
                val deleted = runCatching { repo.isInvitationCodeDeleted(invite) }
                    .getOrDefault(false)
                _uiState.update {
                    it.copy(
                        messageRes = if (deleted) R.string.auth_invitation_deleted
                        else R.string.auth_invitation_invalid,
                        messageIsError = true
                    )
                }
                return@launchAuth
            }
            repo.sendRegisterCode(email.trim())
            _uiState.update {
                it.copy(messageRes = R.string.auth_otp_sent, messageIsError = false)
            }
            onSent()
        }
    }

    /** 注册第二步：验证邮箱验证码，设置密码并登录；填写了邀请码则自动关注邀请人。 */
    fun register(email: String, password: String, code: String, invitationCode: String) {
        if (!AuthInputValidator.isValid(email, password)) {
            _uiState.update {
                it.copy(messageRes = R.string.auth_invalid_input, messageIsError = true)
            }
            return
        }
        if (!AuthInputValidator.isValidOtpCode(code)) {
            _uiState.update {
                it.copy(messageRes = R.string.auth_otp_required, messageIsError = true)
            }
            return
        }
        val invite = invitationCode.trim()
        launchAuth {
            val session = repo.register(email.trim(), password, code.trim())
            if (invite.isNotEmpty() && AuthInputValidator.isValidInvitationCode(invite)) {
                // 加好友失败不影响注册结果，用户可稍后手动添加。
                runCatching { repo.applyInvitationCode(invite) }
            }
            _uiState.update { it.copy(account = session.toAccount(), cloudProfile = null) }
            val profileLoaded = loadProfile(showError = false)
            _uiState.update {
                it.copy(
                    messageRes = if (profileLoaded) R.string.auth_success_sign_up
                    else R.string.auth_profile_read_failed,
                    messageIsError = !profileLoaded
                )
            }
        }
    }

    fun resetDeletionState() {
        _deletionState.update { AccountDeletionUiState() }
    }

    fun clearPasswordResetState() {
        _passwordResetState.update { PasswordResetUiState() }
    }

    /** 找回密码第一步：确认邮箱已注册后发送验证码。 */
    fun sendPasswordResetCode(email: String) {
        if (!AuthInputValidator.isValidEmail(email)) {
            resetError(R.string.auth_invalid_email)
            return
        }
        if (_passwordResetState.value.sendingCode) return
        viewModelScope.launch {
            _passwordResetState.update { it.copy(sendingCode = true) }
            try {
                if (!repo.isEmailRegistered(email.trim())) {
                    resetError(R.string.auth_email_not_registered)
                    return@launch
                }
                repo.sendPasswordResetCode(email.trim())
                _passwordResetState.update {
                    it.copy(codeSentCount = it.codeSentCount + 1, errorRes = null)
                }
                _uiState.update {
                    it.copy(messageRes = R.string.auth_otp_sent, messageIsError = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                resetError(authErrorMessage(e))
            } finally {
                _passwordResetState.update { it.copy(sendingCode = false) }
            }
        }
    }

    /**
     * 找回密码第二步：邮箱验证码校验通过后设置新密码。
     * 临时会话不保存，成功后回到登录页用新密码登录。
     */
    fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
        onSuccess: () -> Unit
    ) {
        if (!AuthInputValidator.isValidEmail(email)) {
            resetError(R.string.auth_invalid_email)
            return
        }
        if (!AuthInputValidator.isValidOtpCode(code)) {
            resetError(R.string.auth_otp_required)
            return
        }
        if (newPassword.length < AuthInputValidator.MIN_PASSWORD_LENGTH) {
            resetError(R.string.auth_password_short)
            return
        }
        if (_passwordResetState.value.busy) return
        viewModelScope.launch {
            _passwordResetState.update { it.copy(busy = true, errorRes = null) }
            try {
                repo.resetPassword(email.trim(), code.trim(), newPassword)
                _passwordResetState.value = PasswordResetUiState()
                _uiState.update {
                    it.copy(messageRes = R.string.auth_reset_success, messageIsError = false)
                }
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _passwordResetState.update { it.copy(busy = false) }
                resetError(authErrorMessage(e))
            }
        }
    }

    private fun resetError(@StringRes messageRes: Int) {
        _uiState.update { it.copy(messageRes = messageRes, messageIsError = true) }
        _passwordResetState.update { it.copy(errorRes = messageRes) }
    }

    /** 注销前发送验证码到当前账号邮箱。 */
    fun sendDeletionCode() {
        val email = _uiState.value.account?.email
        if (email.isNullOrBlank()) {
            _deletionState.update { it.copy(errorRes = R.string.auth_session_expired) }
            return
        }
        if (_deletionState.value.sendingCode) return
        viewModelScope.launch {
            _deletionState.update { it.copy(sendingCode = true, errorRes = null) }
            try {
                repo.sendDeletionCode(email)
                _deletionState.update {
                    it.copy(sendingCode = false, codeSentCount = it.codeSentCount + 1)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _deletionState.update {
                    it.copy(sendingCode = false, errorRes = authErrorMessage(e))
                }
            }
        }
    }

    /** 注销确认：验证码校验通过后删除账号与云端数据。 */
    fun deleteAccount(code: String, onDeleted: () -> Unit) {
        val email = _uiState.value.account?.email
        if (email.isNullOrBlank()) {
            _deletionState.update { it.copy(errorRes = R.string.auth_session_expired) }
            return
        }
        if (!AuthInputValidator.isValidOtpCode(code)) {
            _deletionState.update { it.copy(errorRes = R.string.auth_otp_required) }
            return
        }
        if (_deletionState.value.busy) return
        viewModelScope.launch {
            _deletionState.update { it.copy(busy = true, errorRes = null) }
            try {
                repo.deleteAccount(email, code.trim())
                _deletionState.update { AccountDeletionUiState() }
                _uiState.update {
                    it.copy(
                        account = null,
                        cloudProfile = null,
                        messageRes = R.string.auth_account_deleted,
                        messageIsError = false
                    )
                }
                onDeleted()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _deletionState.update {
                    it.copy(busy = false, errorRes = authErrorMessage(e))
                }
            }
        }
    }

    fun signOut() = launchAuth {
        repo.signOut()
        _uiState.update {
            it.copy(
                account = null,
                cloudProfile = null,
                messageRes = R.string.auth_signed_out,
                messageIsError = false
            )
        }
    }

    fun switchAccount(userId: String) {
        if (_uiState.value.account?.userId == userId) return
        launchAuth {
            val session = repo.switchAccount(userId)
            _uiState.update { it.copy(account = session.toAccount(), cloudProfile = null) }
            val profileLoaded = loadProfile(showError = false)
            _uiState.update {
                it.copy(
                    messageRes = if (profileLoaded) R.string.auth_switch_success
                    else R.string.auth_profile_read_failed,
                    messageIsError = !profileLoaded
                )
            }
        }
    }

    fun refreshProfile() {
        if (_uiState.value.account == null || _uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, messageRes = null) }
            try {
                loadProfile(showError = true)
            } finally {
                _uiState.update { it.copy(busy = false) }
            }
        }
    }

    /** 打开修改用户名弹窗时刷新 7 天窗口内的剩余次数。 */
    fun loadNicknameStatus() {
        if (_uiState.value.account == null) return
        viewModelScope.launch {
            _nicknameState.update { it.copy(loading = true, errorRes = null) }
            try {
                val status = repo.loadNicknameStatus()
                _nicknameState.update { it.copy(loading = false, status = status) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _nicknameState.update {
                    it.copy(loading = false, errorRes = nicknameErrorMessage(e))
                }
            }
        }
    }

    /** 修改用户名成功后同步云端资料与剩余次数；失败时把提示留给弹窗展示。 */
    fun changeNickname(raw: String, onSuccess: () -> Unit) {
        if (_nicknameState.value.busy || _uiState.value.account == null) return
        val cleaned = raw.trim()
        if (!AuthInputValidator.isValidNickname(cleaned)) {
            _nicknameState.update { it.copy(errorRes = R.string.nickname_invalid) }
            return
        }
        viewModelScope.launch {
            _nicknameState.update { it.copy(busy = true, errorRes = null) }
            try {
                val result = repo.updateNickname(cleaned)
                val updatedProfile = _uiState.value.cloudProfile?.copy(nickname = result.nickname)
                _uiState.update {
                    it.copy(
                        cloudProfile = updatedProfile,
                        messageRes = R.string.nickname_updated,
                        messageIsError = false
                    )
                }
                // 资料尚未加载成功时补拉一次，避免设置页继续显示旧昵称。
                if (updatedProfile == null) loadProfile(showError = false)
                _nicknameState.update {
                    it.copy(
                        busy = false,
                        status = (it.status ?: NicknameChangeStatus())
                            .copy(remaining = result.remaining)
                    )
                }
                syncNicknameNewbieTask()
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _nicknameState.update {
                    it.copy(busy = false, errorRes = nicknameErrorMessage(e))
                }
            }
        }
    }

    fun clearNicknameError() {
        _nicknameState.update { it.copy(errorRes = null) }
    }

    /** 改名完成后尝试发放新手任务奖励；失败不影响改名结果。 */
    private fun syncNicknameNewbieTask() {
        viewModelScope.launch {
            try {
                val result = gameRepo.syncNewbieTasks()
                notifyNewbieTaskResult(getApplication(), result)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // 静默：任务页刷新时会补发。
            }
        }
    }

    private fun restoreSession() {
        viewModelScope.launch {
            try {
                val session = repo.restoreSession()
                _uiState.update { it.copy(account = session?.toAccount()) }
                if (session != null) loadProfile(showError = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(messageRes = authErrorMessage(e), messageIsError = true)
                }
            } finally {
                _uiState.update { it.copy(restoring = false) }
            }
        }
    }

    private suspend fun loadProfile(showError: Boolean): Boolean =
        try {
            val (session, cloudProfile) = repo.loadProfile()
            _uiState.update { it.copy(cloudProfile = cloudProfile) }
            syncAvatarWithLocal(session.userId, cloudProfile)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (e is SupabaseApiException && e.statusCode == 401) {
                repo.clearSession()
                _uiState.update { it.copy(account = null, cloudProfile = null) }
            }
            if (showError) {
                _uiState.update {
                    it.copy(messageRes = authErrorMessage(e), messageIsError = true)
                }
            }
            false
        }

    /**
     * 登录后让本机头像和云端保持一致：
     * 云端有值以云端为准写回本地（换设备/换账号都能看到同一头像）；
     * 云端为空则把本机的选择补传上去，避免好友侧和本机头像不一致。
     */
    private suspend fun syncAvatarWithLocal(userId: String, cloudProfile: CloudProfile?) {
        val cloudId = DefaultAvatarCatalog.resolve(cloudProfile?.avatarEmoji)
        val localId = try {
            appRepo.setActiveUser(userId)
            DefaultAvatarCatalog.resolve(appRepo.identity.first().avatarEmoji)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
        when {
            cloudId != null -> if (cloudId != localId) {
                try {
                    appRepo.setAvatarSelection(cloudId)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    // 本地写入失败只影响本机展示，不阻塞登录流程。
                }
            }
            localId != null -> {
                val marker = DefaultAvatarCatalog.marker(localId) ?: return
                try {
                    repo.updateAvatar(marker)
                    _uiState.update {
                        it.copy(cloudProfile = it.cloudProfile?.copy(avatarEmoji = marker))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    // 补传失败不影响登录，用户再次选择头像时会重新同步。
                }
            }
        }
    }

    private fun validInput(email: String, password: String): Boolean {
        val valid = AuthInputValidator.isValid(email, password)
        if (!valid) {
            _uiState.update {
                it.copy(messageRes = R.string.auth_invalid_input, messageIsError = true)
            }
        }
        return valid
    }

    private fun launchAuth(block: suspend () -> Unit) {
        if (_uiState.value.busy || !_uiState.value.configured) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, messageRes = null) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(messageRes = authErrorMessage(e), messageIsError = true)
                }
            } finally {
                _uiState.update { it.copy(busy = false) }
            }
        }
    }
}

internal fun nicknameErrorMessage(error: Throwable): Int {
    if (error is SupabaseApiException) {
        val code = error.errorCode.orEmpty()
        val message = error.serverMessage.orEmpty()
        if (code == "42901" || message.contains("nickname_change_limit", ignoreCase = true)) {
            return R.string.nickname_change_limit
        }
        if (code == "22023" || message.contains("invalid_nickname", ignoreCase = true)) {
            return R.string.nickname_invalid
        }
    }
    return authErrorMessage(error)
}

internal fun authErrorMessage(error: Throwable): Int {
    if (error is SupabaseApiException) {
        if (error.serverMessage?.contains("rate_limited", ignoreCase = true) == true) {
            return R.string.error_rate_limited
        }
        if (error.serverMessage?.contains("expired or is invalid", ignoreCase = true) == true) {
            return R.string.auth_otp_invalid
        }
        when (error.errorCode) {
            "otp_expired", "invalid_otp" -> return R.string.auth_otp_invalid
            "email_mismatch" -> return R.string.auth_email_mismatch
            "invalid_credentials" -> return R.string.auth_invalid_credentials
            "email_exists", "user_already_exists" -> return R.string.auth_email_exists
            "email_not_confirmed" -> return R.string.auth_email_not_confirmed
            "validation_failed", "email_address_invalid" -> return R.string.auth_invalid_input
            "weak_password" -> return R.string.auth_weak_password
            "session_missing", "refresh_token_not_found", "invalid_grant" ->
                return R.string.auth_session_expired
        }
    }
    return when (error.toNetworkErrorKind()) {
        NetworkErrorKind.OFFLINE -> R.string.error_network_offline
        NetworkErrorKind.TIMEOUT -> R.string.error_network_timeout
        NetworkErrorKind.DNS, NetworkErrorKind.CONNECTION -> R.string.error_network_unreachable
        NetworkErrorKind.SERVER -> R.string.error_server_busy
        NetworkErrorKind.RATE_LIMIT -> R.string.error_rate_limited
        NetworkErrorKind.AUTH -> R.string.auth_session_expired
        else -> R.string.error_generic
    }
}

package com.calorie.diary.viewmodel

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calorie.diary.R
import com.calorie.diary.data.network.NetworkErrorKind
import com.calorie.diary.data.network.toNetworkErrorKind
import com.calorie.diary.data.supabase.SocialRepository
import com.calorie.diary.data.supabase.SocialSummary
import com.calorie.diary.data.supabase.SocialUserEntry
import com.calorie.diary.data.supabase.SupabaseApiException
import com.calorie.diary.data.supabase.SupabaseAuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SocialListTab(val routeValue: String) {
    FOLLOWING("following"),
    FOLLOWERS("followers");

    companion object {
        fun from(value: String?): SocialListTab =
            entries.firstOrNull { it.routeValue == value } ?: FOLLOWING
    }
}

/** 我的页、个人主页共用：加载某个用户的关注/粉丝/互关统计。 */
class SocialSummaryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SocialRepository.get(app)
    private val authRepo = SupabaseAuthRepository.get(app)
    private val _summary = MutableStateFlow<SocialSummary?>(null)
    val summary = _summary.asStateFlow()
    private var loadJob: Job? = null

    fun load(userId: String?) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val target = userId?.takeIf { it.isNotBlank() } ?: authRepo.account.first()?.userId
            if (target == null) {
                _summary.value = null
                return@launch
            }
            try {
                _summary.value = repo.loadSummary(target)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // 统计加载失败不阻断页面主体，卡片保留上次数据或隐藏。
            }
        }
    }

    fun refresh(userId: String?) = load(userId)
}

data class SocialListUiState(
    val loading: Boolean = true,
    val tab: SocialListTab = SocialListTab.FOLLOWING,
    val users: List<SocialUserEntry> = emptyList(),
    /** 只有查看自己的关注/粉丝时才允许关注、取消关注。 */
    val isOwnList: Boolean = false,
    val actionUserId: String? = null,
    @StringRes val messageRes: Int? = null,
    val messageIsError: Boolean = false,
    val requiresLogin: Boolean = false,
    val failed: Boolean = false
)

class SocialListViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SocialRepository.get(app)
    private val authRepo = SupabaseAuthRepository.get(app)
    private val _uiState = MutableStateFlow(SocialListUiState())
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var currentUserId: String? = null
    private var currentTab = SocialListTab.FOLLOWING

    fun load(userId: String?, tab: SocialListTab) {
        currentUserId = userId
        currentTab = tab
        loadInternal(userId, tab, showLoading = true)
    }

    /** 回关：对方已关注我，服务端会直接建立互关。 */
    fun follow(userId: String) = runAction(userId) { repo.follow(userId) }

    /** 取消关注：互关时解除好友关系，单向关注时撤销已发出的请求。 */
    fun unfollow(userId: String) = runAction(userId) { repo.unfollow(userId) }

    private fun runAction(userId: String, action: suspend () -> Unit) {
        if (_uiState.value.actionUserId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(actionUserId = userId, messageRes = null) }
            try {
                action()
                _uiState.update { it.copy(actionUserId = null) }
                loadInternal(currentUserId, currentTab, showLoading = false)
            } catch (e: CancellationException) {
                _uiState.update { it.copy(actionUserId = null) }
                throw e
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        actionUserId = null,
                        messageRes = errorMessage(e),
                        messageIsError = true
                    )
                }
            }
        }
    }

    private fun loadInternal(userId: String?, tab: SocialListTab, showLoading: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    // 静默刷新时保留当前列表，避免操作后整页闪烁。
                    loading = showLoading || it.users.isEmpty(),
                    tab = tab,
                    requiresLogin = false,
                    failed = false
                )
            }
            val account = authRepo.account.first()
            val target = userId?.takeIf { it.isNotBlank() } ?: account?.userId
            if (target == null) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        requiresLogin = true,
                        users = emptyList(),
                        isOwnList = false
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(isOwnList = account != null && target == account.userId) }
            try {
                val users = when (tab) {
                    SocialListTab.FOLLOWING -> repo.loadFollowing(target)
                    SocialListTab.FOLLOWERS -> repo.loadFollowers(target)
                }
                _uiState.update { it.copy(loading = false, users = users) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val loginRequired = e is SupabaseApiException && e.statusCode == 401
                _uiState.update {
                    it.copy(
                        loading = false,
                        users = emptyList(),
                        requiresLogin = loginRequired,
                        failed = !loginRequired
                    )
                }
            }
        }
    }

    private fun errorMessage(error: Throwable): Int =
        when (error.toNetworkErrorKind()) {
            NetworkErrorKind.OFFLINE -> R.string.error_network_offline
            NetworkErrorKind.TIMEOUT -> R.string.error_network_timeout
            NetworkErrorKind.DNS, NetworkErrorKind.CONNECTION -> R.string.error_network_unreachable
            NetworkErrorKind.SERVER -> R.string.error_server_busy
            NetworkErrorKind.RATE_LIMIT -> R.string.error_rate_limited
            NetworkErrorKind.AUTH -> R.string.auth_session_expired
            else -> R.string.error_generic
        }
}

package com.calorie.diary.viewmodel

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calorie.diary.R
import com.calorie.diary.data.network.NetworkErrorKind
import com.calorie.diary.data.network.toNetworkErrorKind
import com.calorie.diary.data.supabase.GroupConversation
import com.calorie.diary.data.supabase.GroupInvitation
import com.calorie.diary.data.supabase.GroupsRepository
import com.calorie.diary.data.supabase.SupabaseApiException
import com.calorie.diary.data.supabase.SupabaseAuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupsUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val requiresLogin: Boolean = false,
    val groups: List<GroupConversation> = emptyList(),
    /** 待处理的群聊邀请，展示在消息列表顶部。 */
    val invitations: List<GroupInvitation> = emptyList(),
    @StringRes val messageRes: Int? = null,
    val messageIsError: Boolean = false
)

class GroupsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = GroupsRepository.get(app)
    private val auth = SupabaseAuthRepository.get(app)
    private val _uiState = MutableStateFlow(GroupsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            auth.account.map { it?.userId }.distinctUntilChanged()
                .collect(::onAccountChanged)
        }
    }

    private fun onAccountChanged(userId: String?) {
        if (userId == null) {
            _uiState.update {
                it.copy(
                    loading = false,
                    requiresLogin = true,
                    groups = emptyList(),
                    invitations = emptyList(),
                    messageRes = null
                )
            }
        } else {
            _uiState.update { it.copy(requiresLogin = false, groups = emptyList(), invitations = emptyList()) }
            refresh()
        }
    }

    fun refresh() = runOperation(showLoading = true) { loadGroups() }

    fun createGroup(name: String, friendIds: List<String> = emptyList()) {
        if (name.isBlank()) {
            showMessage(R.string.groups_name_required, true)
            return
        }
        runOperation {
            val group = repo.createGroup(name.trim())
            // 群已建好，邀请失败不回滚，后续还能在群聊里补邀。
            if (friendIds.isNotEmpty()) {
                runCatching { repo.inviteMembers(group.groupId, friendIds) }
            }
            loadGroups()
            showMessage(R.string.groups_created, false)
        }
    }

    fun respondInvitation(invitationId: Long, accept: Boolean) = runOperation {
        repo.respondInvitation(invitationId, accept)
        loadGroups()
        showMessage(
            if (accept) R.string.group_invitation_joined else R.string.group_invitation_declined,
            false
        )
    }

    private suspend fun loadGroups() {
        // 先用本地缓存把群会话显示出来，再后台拉取最新数据。
        if (_uiState.value.groups.isEmpty()) {
            val cached = runCatching { repo.loadCachedGroups() }.getOrDefault(emptyList())
            if (cached.isNotEmpty()) {
                _uiState.update {
                    it.copy(requiresLogin = false, groups = cached, loading = false)
                }
            }
        }
        val groups = repo.listGroups()
        // 邀请列表失败不影响群列表主流程。
        val invitations = runCatching { repo.listInvitations() }.getOrDefault(emptyList())
        _uiState.update {
            it.copy(requiresLogin = false, groups = groups, invitations = invitations)
        }
    }

    private fun runOperation(showLoading: Boolean = false, block: suspend () -> Unit) {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(loading = showLoading && it.groups.isEmpty(), busy = true, messageRes = null)
            }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (e is SupabaseApiException && e.statusCode == 401) {
                    _uiState.update { it.copy(requiresLogin = true, groups = emptyList()) }
                } else {
                    showMessage(errorMessage(e), true)
                }
            } finally {
                _uiState.update { it.copy(loading = false, busy = false) }
            }
        }
    }

    private fun showMessage(@StringRes message: Int, isError: Boolean) {
        _uiState.update { it.copy(messageRes = message, messageIsError = isError) }
    }

    private fun errorMessage(error: Throwable): Int {
        if (error is SupabaseApiException && error.serverMessage?.contains("invitation_not_found", true) == true) {
            return R.string.group_invitation_not_found
        }
        if (error is SupabaseApiException && error.serverMessage?.contains("group_not_found", true) == true) {
            return R.string.groups_not_found
        }
        return when (error.toNetworkErrorKind()) {
            NetworkErrorKind.OFFLINE -> R.string.error_network_offline
            NetworkErrorKind.TIMEOUT -> R.string.error_network_timeout
            NetworkErrorKind.DNS, NetworkErrorKind.CONNECTION -> R.string.error_network_unreachable
            NetworkErrorKind.SERVER -> R.string.error_server_busy
            NetworkErrorKind.RATE_LIMIT -> R.string.error_rate_limited
            else -> R.string.error_generic
        }
    }
}

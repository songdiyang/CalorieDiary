package com.calorie.diary.viewmodel

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calorie.diary.R
import com.calorie.diary.data.network.NetworkErrorKind
import com.calorie.diary.data.network.toNetworkErrorKind
import com.calorie.diary.data.supabase.FriendEntry
import com.calorie.diary.data.supabase.GroupInfo
import com.calorie.diary.data.supabase.GroupInfoMember
import com.calorie.diary.data.supabase.GroupsRepository
import com.calorie.diary.data.supabase.SupabaseApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupInfoUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val info: GroupInfo? = null,
    val members: List<GroupInfoMember> = emptyList(),
    val friends: List<FriendEntry> = emptyList(),
    val inviting: Boolean = false,
    val currentUserId: String = "",
    /** 保存成功或邀请发出后的提示，展示一次后由 UI 清除。 */
    val notice: String? = null,
    @StringRes val errorRes: Int? = null,
    /** 解散或退出成功后置为 true，UI 负责回到消息页。 */
    val closed: Boolean = false
) {
    /** 群名称与群公告仅群主可修改。 */
    val isOwner: Boolean get() = info != null && info.ownerId == currentUserId
    val memberIds: Set<String> get() = members.map { it.userId }.toSet()
}

class GroupInfoViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = GroupsRepository.get(app)
    private val _uiState = MutableStateFlow(GroupInfoUiState())
    val uiState = _uiState.asStateFlow()
    private var groupId: String? = null

    fun load(id: String) {
        groupId = id
        viewModelScope.launch {
            // 首次进入显示 loading；从公告页等返回时静默刷新，避免页面闪烁。
            _uiState.update { it.copy(loading = it.info == null, errorRes = null) }
            try {
                val (info, members) = repo.loadInfo(id)
                // 好友列表只用于邀请，加载失败不影响主页主体。
                val friends = _uiState.value.friends.ifEmpty {
                    runCatching { repo.listFriends() }.getOrDefault(emptyList())
                }
                val currentUserId = repo.currentUserId()
                _uiState.update {
                    it.copy(
                        loading = false,
                        info = info,
                        members = members,
                        friends = friends,
                        currentUserId = currentUserId
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update { it.copy(loading = false, errorRes = errorMessage(e)) }
            }
        }
    }

    fun inviteMembers(friendIds: List<String>) {
        val id = groupId ?: return
        if (friendIds.isEmpty() || _uiState.value.inviting) return
        viewModelScope.launch {
            _uiState.update { it.copy(inviting = true, errorRes = null, notice = null) }
            try {
                // 与群聊页一致：邀请先进入待确认，对方同意后才成为成员。
                repo.inviteMembers(id, friendIds)
                _uiState.update {
                    it.copy(
                        notice = getApplication<Application>()
                            .getString(R.string.group_invitation_sent)
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update { it.copy(errorRes = errorMessage(e)) }
            } finally {
                _uiState.update { it.copy(inviting = false) }
            }
        }
    }

    fun updateName(name: String) = save { repo.updateInfo(requireGroupId(), name = name.trim()) }

    fun clearNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    /** 群主解散群聊，成功后离开当前页面。 */
    fun dissolveGroup() {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, errorRes = null) }
            try {
                repo.dissolveGroup(requireGroupId())
                _uiState.update { it.copy(busy = false, closed = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update { it.copy(busy = false, errorRes = errorMessage(e)) }
            }
        }
    }

    /** 普通成员退出群聊，成功后离开当前页面。 */
    fun leaveGroup() {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, errorRes = null) }
            try {
                repo.leaveGroup(requireGroupId())
                _uiState.update { it.copy(busy = false, closed = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update { it.copy(busy = false, errorRes = errorMessage(e)) }
            }
        }
    }

    /** 群主转让后重新拉群信息，群主标记与可编辑状态随之变化。 */
    fun transferOwnership(newOwnerId: String) {
        if (_uiState.value.busy || newOwnerId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, errorRes = null) }
            try {
                repo.transferOwnership(requireGroupId(), newOwnerId)
                val (info, members) = repo.loadInfo(requireGroupId())
                _uiState.update {
                    it.copy(
                        busy = false,
                        info = info,
                        members = members,
                        notice = getApplication<Application>()
                            .getString(R.string.group_info_transferred)
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update { it.copy(busy = false, errorRes = errorMessage(e)) }
            }
        }
    }

    /** 保存名称/公告后只回写这两项，群主与成员数保持不变。 */
    private fun save(block: suspend () -> Pair<String, String>) {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, errorRes = null) }
            try {
                val (name, announcement) = block()
                _uiState.update {
                    it.copy(
                        busy = false,
                        info = it.info?.copy(name = name, announcement = announcement),
                        notice = getApplication<Application>()
                            .getString(R.string.group_info_saved)
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update { it.copy(busy = false, errorRes = errorMessage(e)) }
            }
        }
    }

    private fun requireGroupId(): String =
        groupId ?: throw IllegalStateException("Group is not loaded")

    private fun errorMessage(error: Throwable): Int {
        val message = (error as? SupabaseApiException)?.serverMessage.orEmpty()
        return when {
            message.contains("not_group_owner", true) -> R.string.group_info_only_owner
            message.contains("owner_cannot_leave", true) -> R.string.group_owner_cannot_leave
            message.contains("transfer_target_not_member", true) ->
                R.string.group_transfer_target_invalid
            message.contains("announcement_too_long", true) -> R.string.group_announcement_too_long
            message.contains("invalid_group_name", true) -> R.string.group_name_invalid
            message.contains("not_group_member", true) -> R.string.groups_not_member
            else -> when (error.toNetworkErrorKind()) {
                NetworkErrorKind.OFFLINE -> R.string.error_network_offline
                NetworkErrorKind.TIMEOUT -> R.string.error_network_timeout
                NetworkErrorKind.DNS, NetworkErrorKind.CONNECTION ->
                    R.string.error_network_unreachable
                NetworkErrorKind.SERVER -> R.string.error_server_busy
                NetworkErrorKind.RATE_LIMIT -> R.string.error_rate_limited
                else -> R.string.error_generic
            }
        }
    }
}

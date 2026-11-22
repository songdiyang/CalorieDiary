package com.calorie.diary.viewmodel

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calorie.diary.R
import com.calorie.diary.data.network.NetworkErrorKind
import com.calorie.diary.data.network.toNetworkErrorKind
import com.calorie.diary.data.supabase.FriendEntry
import com.calorie.diary.data.supabase.GroupConversation
import com.calorie.diary.data.supabase.GroupMemberTotal
import com.calorie.diary.data.supabase.GroupMessage
import com.calorie.diary.data.supabase.GroupsRepository
import com.calorie.diary.data.supabase.SharedFood
import com.calorie.diary.data.supabase.SupabaseApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupChatUiState(
    val loading: Boolean = true,
    val sending: Boolean = false,
    val group: GroupConversation? = null,
    val messages: List<GroupMessage> = emptyList(),
    val members: List<GroupMemberTotal> = emptyList(),
    val friends: List<FriendEntry> = emptyList(),
    val inviting: Boolean = false,
    val currentUserId: String = "",
    val sentCount: Int = 0,
    /** 邀请成功后的提示文案，展示一次后由 UI 清除。 */
    val notice: String? = null,
    @StringRes val errorRes: Int? = null
)

class GroupChatViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = GroupsRepository.get(app)
    private val _uiState = MutableStateFlow(GroupChatUiState())
    val uiState = _uiState.asStateFlow()
    private var groupId: String? = null
    private var date: String? = null
    private var refreshJob: Job? = null

    fun load(id: String, entryDate: String) {
        if (groupId == id && date == entryDate && _uiState.value.group != null) return
        groupId = id
        date = entryDate
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorRes = null) }
            try {
                val data = repo.loadChat(id, entryDate)
                // 好友列表只用于邀请，加载失败不影响群聊主体。
                val friends = runCatching { repo.listFriends() }.getOrDefault(emptyList())
                _uiState.update {
                    it.copy(
                        loading = false,
                        group = data.group,
                        messages = data.messages,
                        members = data.members,
                        friends = friends,
                        currentUserId = data.currentUserId
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update { it.copy(loading = false, errorRes = errorMessage(e)) }
            }
        }
    }

    fun refresh() {
        val id = groupId ?: return
        val entryDate = date ?: return
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            try {
                val data = repo.loadChat(id, entryDate)
                _uiState.update {
                    it.copy(
                        group = data.group,
                        messages = data.messages,
                        members = data.members,
                        errorRes = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // 保留当前消息，下次刷新再试。
            }
        }
    }

    fun sendText(content: String) {
        val text = content.trim()
        if (text.isEmpty()) return
        runSend {
            repo.sendMessage(requireGroupId(), text, "text", null, null)
        }
    }

    fun shareCalories(
        calories: Int,
        entryDate: String,
        items: List<SharedFood> = emptyList()
    ) {
        val content = getApplication<Application>().getString(
            R.string.group_calorie_share_text, calories
        )
        runSend {
            repo.sendMessage(
                requireGroupId(), content, "calorie_share", calories, entryDate, items
            )
        }
    }

    fun inviteMembers(friendIds: List<String>) {
        val id = groupId ?: return
        if (friendIds.isEmpty() || _uiState.value.inviting) return
        viewModelScope.launch {
            _uiState.update { it.copy(inviting = true, errorRes = null, notice = null) }
            try {
                // 邀请只生成一条待确认邀请，对方在消息页加入后才成为成员。
                repo.inviteMembers(id, friendIds)
                refresh()
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

    fun clearNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    private fun runSend(block: suspend () -> GroupMessage) {
        if (_uiState.value.sending) return
        viewModelScope.launch {
            _uiState.update { it.copy(sending = true, errorRes = null) }
            try {
                val sent = block()
                // 服务端确认后先上屏，等轮询刷新才出现会让用户以为卡片卡住了。
                _uiState.update {
                    it.copy(
                        sending = false,
                        messages = mergeSentGroupMessage(it.messages, sent),
                        sentCount = it.sentCount + 1
                    )
                }
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update { it.copy(errorRes = errorMessage(e)) }
            } finally {
                _uiState.update { it.copy(sending = false) }
            }
        }
    }

    private fun requireGroupId(): String =
        groupId ?: throw IllegalStateException("Group is not loaded")

    private fun errorMessage(error: Throwable): Int {
        if (error is SupabaseApiException &&
            error.serverMessage?.contains("not_group_member", true) == true
        ) return R.string.groups_not_member
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

internal fun mergeSentGroupMessage(
    current: List<GroupMessage>,
    sent: GroupMessage
): List<GroupMessage> =
    (current.filterNot { it.id == sent.id } + sent).sortedBy { it.createdAt }

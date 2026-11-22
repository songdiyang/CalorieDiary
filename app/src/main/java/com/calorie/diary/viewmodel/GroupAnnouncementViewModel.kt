package com.calorie.diary.viewmodel

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calorie.diary.R
import com.calorie.diary.data.network.NetworkErrorKind
import com.calorie.diary.data.network.toNetworkErrorKind
import com.calorie.diary.data.supabase.GroupInfo
import com.calorie.diary.data.supabase.GroupsRepository
import com.calorie.diary.data.supabase.SupabaseApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupAnnouncementUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val info: GroupInfo? = null,
    val currentUserId: String = "",
    /** 保存成功后的提示，展示一次后由 UI 清除。 */
    val notice: String? = null,
    @StringRes val errorRes: Int? = null
) {
    /** 只有群主能编辑并发布公告。 */
    val isOwner: Boolean get() = info != null && info.ownerId == currentUserId
}

class GroupAnnouncementViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = GroupsRepository.get(app)
    private val _uiState = MutableStateFlow(GroupAnnouncementUiState())
    val uiState = _uiState.asStateFlow()
    private var groupId: String? = null

    fun load(id: String) {
        if (groupId == id && _uiState.value.info != null) return
        groupId = id
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorRes = null) }
            try {
                val (info, _) = repo.loadInfo(id)
                _uiState.update {
                    it.copy(
                        loading = false,
                        info = info,
                        currentUserId = repo.currentUserId()
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update { it.copy(loading = false, errorRes = errorMessage(e)) }
            }
        }
    }

    fun saveAnnouncement(text: String) {
        if (_uiState.value.busy) return
        val id = groupId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, errorRes = null, notice = null) }
            try {
                val (_, announcement) = repo.updateInfo(id, announcement = text.trim())
                _uiState.update {
                    it.copy(
                        busy = false,
                        info = it.info?.copy(announcement = announcement),
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

    fun clearNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    private fun errorMessage(error: Throwable): Int {
        val message = (error as? SupabaseApiException)?.serverMessage.orEmpty()
        return when {
            message.contains("not_group_owner", true) -> R.string.group_info_only_owner
            message.contains("announcement_too_long", true) -> R.string.group_announcement_too_long
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

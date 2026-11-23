package com.calorie.diary.viewmodel

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calorie.diary.R
import com.calorie.diary.data.network.NetworkErrorKind
import com.calorie.diary.data.network.toNetworkErrorKind
import com.calorie.diary.data.supabase.CloudGameRepository
import com.calorie.diary.data.supabase.ConversationPreferencesStore
import com.calorie.diary.data.supabase.ConversationSettings
import com.calorie.diary.data.supabase.FriendAddStatus
import com.calorie.diary.data.supabase.FollowNoticeEntry
import com.calorie.diary.data.supabase.FriendEntry
import com.calorie.diary.data.supabase.FriendRequestEntry
import com.calorie.diary.data.supabase.FriendsRepository
import com.calorie.diary.data.supabase.SupabaseApiException
import com.calorie.diary.data.supabase.SupabaseAuthRepository
import com.calorie.diary.data.supabase.UserIdValidator
import com.calorie.diary.data.supabase.UserSearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FriendsUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val requiresLogin: Boolean = false,
    val myUserId: String = "",
    val friends: List<FriendEntry> = emptyList(),
    /** 单向关注我的粉丝：消息页提示“xxx 关注了你”，可直接回关。 */
    val followNotices: List<FollowNoticeEntry> = emptyList(),
    /** 好友申请：仅在双方互相关注后才能发出。 */
    val requests: List<FriendRequestEntry> = emptyList(),
    /** 本次会话中已发出好友申请的用户，用于把按钮置为“已申请”。 */
    val sentRequestIds: Set<String> = emptySet(),
    val searching: Boolean = false,
    val searchResults: List<UserSearchResult> = emptyList(),
    @StringRes val messageRes: Int? = null,
    val messageIsError: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class FriendsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = FriendsRepository.get(app)
    private val auth = SupabaseAuthRepository.get(app)
    private val gameRepo = CloudGameRepository.get(app)
    private val conversationStore = ConversationPreferencesStore(app)
    private val currentUserId = MutableStateFlow("")
    val conversationSettings: StateFlow<ConversationSettings> = currentUserId
        .flatMapLatest { conversationStore.settingsFor(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConversationSettings())
    private var searchJob: Job? = null
    private val _uiState = MutableStateFlow(FriendsUiState())
    val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            auth.account.map { it?.userId }.distinctUntilChanged()
                .collect(::onAccountChanged)
        }
    }

    private fun onAccountChanged(userId: String?) {
        if (userId == null) {
            currentUserId.value = ""
            _uiState.update {
                it.copy(
                    loading = false,
                    requiresLogin = true,
                    myUserId = "",
                    friends = emptyList(),
                    followNotices = emptyList(),
                    requests = emptyList(),
                    sentRequestIds = emptySet(),
                    messageRes = null
                )
            }
        } else {
            currentUserId.value = userId
            _uiState.update {
                it.copy(
                    requiresLogin = false,
                    myUserId = "",
                    friends = emptyList(),
                    followNotices = emptyList(),
                    requests = emptyList(),
                    sentRequestIds = emptySet()
                )
            }
            refresh()
        }
    }

    fun refresh() = runOperation(showLoading = true) { loadOverview() }

    fun searchUsers(query: String) {
        searchJob?.cancel()
        val cleaned = query.trim()
        if (cleaned.isEmpty()) {
            _uiState.update { it.copy(searching = false, searchResults = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(searching = true, searchResults = emptyList()) }
            delay(250)
            try {
                val results = repo.searchUsers(cleaned)
                _uiState.update { it.copy(searching = false, searchResults = results) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update { it.copy(searching = false) }
                showMessage(errorMessage(e), true)
            }
        }
    }

    /** 好友申请：服务端要求双方已互相关注，否则返回 follow_required。 */
    fun applyFriend(userId: String): Boolean {
        if (_uiState.value.busy) {
            showMessage(R.string.friends_busy, true)
            return false
        }
        val id = userId.trim()
        when {
            !UserIdValidator.isValid(id) -> {
                showMessage(R.string.friends_id_invalid, true)
                return false
            }
            id.equals(_uiState.value.myUserId, ignoreCase = true) -> {
                showMessage(R.string.friends_add_self, true)
                return false
            }
        }
        runOperation {
            val result = repo.applyFriend(id)
            when (result.status) {
                FriendAddStatus.NOT_FOUND -> showMessage(R.string.friends_not_found, true)
                FriendAddStatus.ALREADY_FRIENDS -> {
                    updateSearchResult(id) { it.copy(isFriend = true) }
                    showMessage(R.string.friends_already, false)
                    syncFriendNewbieTask()
                }
                FriendAddStatus.FOLLOW_REQUIRED ->
                    showMessage(R.string.friends_follow_required, true)
                FriendAddStatus.REQUEST_SENT -> {
                    updateSearchResult(id) { it.copy(hasSentFriendRequest = true) }
                    _uiState.update { it.copy(sentRequestIds = it.sentRequestIds + id) }
                    showMessage(R.string.friends_request_sent, false)
                }
                else -> {
                    loadOverview()
                    updateSearchResult(id) {
                        it.copy(
                            isFriend = true,
                            hasSentFriendRequest = false,
                            hasIncomingFriendRequest = false
                        )
                    }
                    showMessage(R.string.friends_added, false)
                    syncFriendNewbieTask()
                }
            }
        }
        return true
    }

    /** 关注用户：对方会在消息页看到“xxx 关注了你”，互关后才能申请好友。 */
    fun follow(userId: String): Boolean {
        if (_uiState.value.busy) {
            showMessage(R.string.friends_busy, true)
            return false
        }
        val id = userId.trim()
        when {
            !UserIdValidator.isValid(id) -> {
                showMessage(R.string.friends_id_invalid, true)
                return false
            }
            id.equals(_uiState.value.myUserId, ignoreCase = true) -> {
                showMessage(R.string.friends_add_self, true)
                return false
            }
        }
        runOperation {
            when (repo.follow(id)) {
                "mutual" -> {
                    updateSearchResult(id) { it.copy(isFollowing = true, isFollowedBy = true) }
                    showMessage(R.string.friends_follow_mutual, false)
                }
                "following" -> {
                    updateSearchResult(id) { it.copy(isFollowing = true) }
                    showMessage(R.string.friends_followed, false)
                }
                "not_found" -> showMessage(R.string.friends_not_found, true)
                "self" -> showMessage(R.string.friends_add_self, true)
                else -> showMessage(R.string.error_generic, true)
            }
        }
        return true
    }

    private fun updateSearchResult(
        userId: String,
        transform: (UserSearchResult) -> UserSearchResult
    ) {
        _uiState.update { state ->
            state.copy(
                searchResults = state.searchResults.map { result ->
                    if (result.userId == userId) transform(result) else result
                }
            )
        }
    }

    fun acceptRequest(requesterId: String) = respondRequest(requesterId, true)

    fun declineRequest(requesterId: String) = respondRequest(requesterId, false)

    private fun respondRequest(requesterId: String, accept: Boolean) = runOperation {
        val status = repo.respondFriendRequest(requesterId, accept)
        when (status) {
            "friends" -> {
                loadOverview()
                showMessage(R.string.friends_request_accepted, false)
                syncFriendNewbieTask()
            }
            "declined" -> {
                loadOverview()
                showMessage(R.string.friends_request_declined, false)
            }
            "not_mutual" -> {
                loadOverview()
                showMessage(R.string.friends_follow_required, true)
            }
            else -> loadOverview()
        }
    }

    fun removeFriend(friendId: String) = runOperation {
        repo.removeFriend(friendId)
        loadOverview()
        showMessage(R.string.friends_removed, false)
    }

    fun togglePinned(conversationId: String) {
        viewModelScope.launch { conversationStore.togglePinned(currentUserId.value, conversationId) }
    }

    fun toggleMuted(conversationId: String) {
        viewModelScope.launch { conversationStore.toggleMuted(currentUserId.value, conversationId) }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch { conversationStore.deleteConversation(currentUserId.value, conversationId) }
    }

    private suspend fun loadOverview() {
        // 先用本地缓存把会话列表显示出来，再后台拉取最新数据。
        if (_uiState.value.friends.isEmpty()) {
            val cached = runCatching { repo.loadCachedFriends() }.getOrDefault(emptyList())
            if (cached.isNotEmpty()) {
                _uiState.update {
                    it.copy(requiresLogin = false, friends = cached, loading = false)
                }
            }
        }
        val overview = repo.loadOverview()
        currentUserId.value = overview.myUserId
        _uiState.update {
            it.copy(
                requiresLogin = false,
                myUserId = overview.myUserId,
                friends = overview.friends,
                followNotices = overview.followNotices,
                requests = overview.requests
            )
        }
    }

    private fun runOperation(showLoading: Boolean = false, block: suspend () -> Unit) {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    loading = showLoading && it.friends.isEmpty(),
                    busy = true,
                    messageRes = null
                )
            }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (e is SupabaseApiException && e.statusCode == 401) {
                    _uiState.update {
                        it.copy(requiresLogin = true, friends = emptyList(), myUserId = "")
                    }.also {
                        currentUserId.value = ""
                    }
                } else {
                    showMessage(errorMessage(e), true)
                }
            } finally {
                _uiState.update { it.copy(loading = false, busy = false) }
            }
        }
    }

    private fun showMessage(@StringRes messageRes: Int, isError: Boolean) {
        _uiState.update { it.copy(messageRes = messageRes, messageIsError = isError) }
    }

    /** 互加好友后尝试发放新手任务奖励；失败静默，任务页刷新时会补发。 */
    private fun syncFriendNewbieTask() {
        viewModelScope.launch {
            try {
                val result = gameRepo.syncNewbieTasks()
                notifyNewbieTaskResult(getApplication(), result)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // 静默：下次刷新或打开任务页时继续尝试。
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

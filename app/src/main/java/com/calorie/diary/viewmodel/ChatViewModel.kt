package com.calorie.diary.viewmodel

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calorie.diary.R
import com.calorie.diary.data.ChatEmojis
import com.calorie.diary.data.network.NetworkErrorKind
import com.calorie.diary.data.network.toNetworkErrorKind
import com.calorie.diary.data.supabase.ChatRepository
import com.calorie.diary.data.supabase.FriendEntry
import com.calorie.diary.data.supabase.FriendMessage
import com.calorie.diary.data.supabase.SharedFood
import com.calorie.diary.data.supabase.SupabaseApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val loading: Boolean = true,
    val sending: Boolean = false,
    val friend: FriendEntry? = null,
    val messages: List<FriendMessage> = emptyList(),
    val currentUserId: String = "",
    val relationType: String? = null,
    val relationSince: String? = null,
    val sentCount: Int = 0,
    @StringRes val errorRes: Int? = null
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ChatRepository.get(app)
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState = _uiState.asStateFlow()
    private val _riceEffects = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    /** 特殊表情（米饭）触发的全屏特效事件，收到后播放一次落米动画。 */
    val riceEffects: SharedFlow<Unit> = _riceEffects.asSharedFlow()
    private var friendId: String? = null
    private var refreshJob: Job? = null

    fun load(id: String) {
        // currentUserId 缺失说明上次只加载到好友资料，必须重新拉取，
        // 否则自己发的消息会被当成对方的，气泡布局全部错位。
        if (friendId == id && _uiState.value.friend != null &&
            _uiState.value.currentUserId.isNotBlank()
        ) {
            return
        }
        friendId = id
        viewModelScope.launch {
            _uiState.update { it.copy(errorRes = null) }
            // 本地有缓存时先上屏，云端只做后台同步，避免每次进来都等接口。
            val cached = runCatching { repo.loadCachedChat(id) }.getOrNull()
            if (cached != null) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        messages = cached.messages,
                        currentUserId = cached.ownerId,
                        friend = it.friend ?: cached.friend
                    )
                }
            } else {
                _uiState.update { it.copy(loading = true) }
            }
            try {
                val data = repo.loadChat(id)
                _uiState.update {
                    it.copy(
                        loading = false,
                        friend = data.friend,
                        messages = data.messages,
                        currentUserId = data.currentUserId,
                        relationType = data.relationType,
                        relationSince = data.relationSince
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // 消息加载失败时仍尝试把好友资料带出来，头像/昵称不能退回默认值。
                val friend = _uiState.value.friend ?: try {
                    repo.findFriend(id)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Throwable) {
                    null
                }
                _uiState.update {
                    it.copy(loading = false, friend = friend, errorRes = errorMessage(e))
                }
            }
        }
    }

    fun refresh() {
        val id = friendId ?: return
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            try {
                val previousMaxId = _uiState.value.messages.maxOfOrNull { it.id } ?: 0L
                val data = repo.loadChat(id)
                _uiState.update {
                    it.copy(
                        messages = data.messages,
                        friend = data.friend,
                        currentUserId = data.currentUserId,
                        relationType = data.relationType,
                        relationSince = data.relationSince,
                        errorRes = null
                    )
                }
                // 轮询拉到新的米饭表情消息时播放一次全屏特效。
                if (data.messages.any { it.id > previousMaxId && ChatEmojis.isRice(it.content) }) {
                    _riceEffects.tryEmit(Unit)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // 后台轮询失败保留现有消息，下次进入或刷新时重试。
            }
        }
    }

    fun send(content: String) {
        val id = friendId ?: return
        val text = content.trim()
        if (text.isEmpty() || _uiState.value.sending) return
        viewModelScope.launch {
            _uiState.update { it.copy(sending = true, errorRes = null) }
            try {
                val sent = repo.sendMessage(id, text)
                // 服务端已确认发送后先上屏，后续刷新失败也不能误导用户重复发送。
                _uiState.update {
                    it.copy(
                        sending = false,
                        messages = mergeSentMessage(it.messages, sent),
                        sentCount = it.sentCount + 1
                    )
                }
                if (ChatEmojis.isRice(sent.content)) _riceEffects.tryEmit(Unit)
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update { it.copy(sending = false, errorRes = errorMessage(e)) }
            }
        }
    }

    fun shareCalories(
        calories: Int,
        entryDate: String,
        items: List<SharedFood> = emptyList(),
        messageType: String = "calorie_share"
    ) {
        val id = friendId ?: return
        if (_uiState.value.sending) return
        viewModelScope.launch {
            _uiState.update { it.copy(sending = true, errorRes = null) }
            try {
                val sent = repo.sendCalorieShare(id, calories, entryDate, items, messageType)
                _uiState.update {
                    it.copy(
                        sending = false,
                        messages = mergeSentMessage(it.messages, sent),
                        sentCount = it.sentCount + 1
                    )
                }
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update { it.copy(sending = false, errorRes = errorMessage(e)) }
            }
        }
    }

    fun bindRelation(relation: String) {
        val id = friendId ?: return
        viewModelScope.launch {
            try {
                val bound = repo.setRelation(id, relation)
                _uiState.update {
                    it.copy(
                        relationType = bound?.type,
                        relationSince = bound?.since,
                        errorRes = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update { it.copy(errorRes = relationErrorMessage(e)) }
            }
        }
    }

    private fun relationErrorMessage(error: Throwable): Int {
        val server = (error as? SupabaseApiException)?.serverMessage.orEmpty()
        return when {
            server.contains("relation_cooldown", true) -> R.string.chat_relation_cooldown
            server.contains("relation_partner_busy", true) -> R.string.chat_relation_partner_busy
            server.contains("not_friends", true) -> R.string.chat_not_friends
            else -> errorMessage(error)
        }
    }

    private fun errorMessage(error: Throwable): Int {
        if (error is SupabaseApiException) {
            if (error.serverMessage?.contains("rate_limited", ignoreCase = true) == true) {
                return R.string.error_rate_limited
            }
            if (error.errorCode == "friend_not_found" ||
                error.serverMessage?.contains("not_friends", true) == true
            ) return R.string.chat_not_friends
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

internal fun mergeSentMessage(
    current: List<FriendMessage>,
    sent: FriendMessage
): List<FriendMessage> =
    (current.filterNot { it.id == sent.id } + sent).sortedBy { it.createdAt }

package com.calorie.diary.viewmodel

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calorie.diary.R
import com.calorie.diary.data.UiMessenger
import com.calorie.diary.data.repo.AppRepository
import com.calorie.diary.data.supabase.ActivityDay
import com.calorie.diary.data.supabase.ActivityProfile
import com.calorie.diary.data.supabase.ActivityProfileResult
import com.calorie.diary.data.supabase.ActivityRepository
import com.calorie.diary.data.supabase.FriendAddStatus
import com.calorie.diary.data.supabase.PublicProfile
import com.calorie.diary.data.supabase.SocialRepository
import com.calorie.diary.data.supabase.SocialSummary
import com.calorie.diary.data.supabase.SupabaseApiException
import com.calorie.diary.data.supabase.SupabaseAuthRepository
import com.calorie.diary.data.supabase.calculateActivityStreak
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PersonalPageUiState(
    val loading: Boolean = true,
    val profile: ActivityProfile? = null,
    val publicProfile: PublicProfile? = null,
    val summary: SocialSummary? = null,
    val isOwnPage: Boolean = true,
    val status: String = "ok",
    val requiresLogin: Boolean = false,
    val localOnly: Boolean = false,
    val failed: Boolean = false,
    val friendActionBusy: Boolean = false
)

class PersonalPageViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository.get(app)
    private val activityRepo = ActivityRepository.get(app)
    private val socialRepo = SocialRepository.get(app)
    private val authRepo = SupabaseAuthRepository.get(app)
    private val _uiState = MutableStateFlow(PersonalPageUiState())
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null

    val localDays: StateFlow<List<ActivityDay>> = repo.observeRecentDailyTotals()
        .map { rows -> rows.map { ActivityDay(it.date, it.totalCalories, it.entryCount) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun load(targetUserId: String?) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(loading = true, failed = false) }
            val target = targetUserId?.takeIf { it.isNotBlank() }
            try {
                val account = authRepo.account.first()
                val ownPage = target == null || target == account?.userId
                _uiState.update { it.copy(isOwnPage = ownPage) }
                if (ownPage && account == null) {
                    _uiState.update {
                        it.copy(
                            loading = false,
                            profile = localProfile(),
                            publicProfile = null,
                            summary = null,
                            status = "ok",
                            localOnly = true,
                            requiresLogin = false
                        )
                    }
                    return@launch
                }
                if (ownPage) {
                    val result = activityRepo.syncAndLoadOwn(localDays.value)
                    applyResult(result, loadSummaryQuietly(requireNotNull(account).userId))
                } else {
                    val userId = requireNotNull(target)
                    // 他人主页只展示关注/粉丝关系，不加载任何饮食数据。
                    val summary = loadSummaryQuietly(userId)
                    val publicProfile = socialRepo.loadPublicProfile(userId)
                    val deleted = publicProfile == null && runCatching {
                        socialRepo.isProfileDeleted(userId)
                    }.getOrDefault(false)
                    _uiState.update {
                        it.copy(
                            loading = false,
                            profile = null,
                            publicProfile = publicProfile,
                            summary = summary,
                            status = when {
                                publicProfile != null -> "ok"
                                deleted -> "deleted"
                                else -> "not_found"
                            },
                            requiresLogin = false,
                            localOnly = false
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val ownPage = target == null || target == authRepo.account.first()?.userId
                if (e is SupabaseApiException && e.statusCode == 401) {
                    _uiState.update {
                        it.copy(loading = false, requiresLogin = true, profile = null)
                    }
                } else if (ownPage) {
                    _uiState.update {
                        it.copy(
                            loading = false,
                            profile = localProfile(),
                            status = "ok",
                            localOnly = true,
                            failed = true
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(loading = false, status = "error", failed = true)
                    }
                }
            }
        }
    }

    /** 关注对方；对方会在消息页收到“关注了你”的提示。 */
    fun follow(userId: String) {
        if (_uiState.value.friendActionBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(friendActionBusy = true) }
            try {
                when (socialRepo.follow(userId)) {
                    "mutual" -> toast(R.string.friends_follow_mutual)
                    "following" -> toast(R.string.friends_followed)
                    "not_found" -> toast(R.string.friends_not_found, true)
                    else -> toast(R.string.error_generic, true)
                }
                load(userId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                toast(R.string.error_generic, true)
            } finally {
                _uiState.update { it.copy(friendActionBusy = false) }
            }
        }
    }

    /** 互关后申请成为好友，仍需对方接受。 */
    fun applyFriend(userId: String) {
        if (_uiState.value.friendActionBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(friendActionBusy = true) }
            try {
                when (socialRepo.applyFriend(userId).status) {
                    FriendAddStatus.REQUEST_SENT -> toast(R.string.friends_request_sent)
                    FriendAddStatus.FRIENDS -> toast(R.string.friends_added)
                    FriendAddStatus.ALREADY_FRIENDS -> toast(R.string.friends_already)
                    FriendAddStatus.FOLLOW_REQUIRED -> toast(R.string.friends_follow_required, true)
                    FriendAddStatus.NOT_FOUND -> toast(R.string.friends_not_found, true)
                    else -> toast(R.string.error_generic, true)
                }
                load(userId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                toast(R.string.error_generic, true)
            } finally {
                _uiState.update { it.copy(friendActionBusy = false) }
            }
        }
    }

    private fun toast(@StringRes messageRes: Int, isError: Boolean = false) {
        UiMessenger.show(getApplication<Application>().getString(messageRes), isError)
    }

    private suspend fun loadSummaryQuietly(userId: String): SocialSummary? =
        try {
            socialRepo.loadSummary(userId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }

    private fun applyResult(result: ActivityProfileResult, summary: SocialSummary?) {
        _uiState.update {
            it.copy(
                loading = false,
                profile = result.profile,
                summary = summary,
                status = result.status,
                requiresLogin = false,
                localOnly = false
            )
        }
    }

    private suspend fun localProfile(): ActivityProfile {
        val days = localDays.value
        val identity = repo.identity.first()
        val preferences = repo.profile.first()
        return ActivityProfile(
            userId = identity.userId,
            nickname = identity.nickname,
            avatarEmoji = identity.avatarEmoji,
            publicId = "",
            visibility = "private",
            isSelf = true,
            calorieGoal = preferences.calorieGoal,
            activeDays = repo.observeActiveDays().first(),
            streakDays = calculateActivityStreak(days),
            totalEntries = repo.observeEntryCount().first(),
            totalCalories = repo.observeTotalCaloriesAll().first(),
            days = days
        )
    }
}

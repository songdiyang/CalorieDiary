package com.calorie.diary.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calorie.diary.data.repo.AppRepository
import com.calorie.diary.data.supabase.ActivityDay
import com.calorie.diary.data.supabase.ActivityProfile
import com.calorie.diary.data.supabase.ActivityProfileResult
import com.calorie.diary.data.supabase.ActivityRepository
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
    val failed: Boolean = false
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

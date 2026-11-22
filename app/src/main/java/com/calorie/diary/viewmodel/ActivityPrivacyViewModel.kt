package com.calorie.diary.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calorie.diary.data.supabase.ActivityPrivacyRepository
import com.calorie.diary.data.supabase.SupabaseApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActivityPrivacyUiState(
    val loading: Boolean = true,
    val requiresLogin: Boolean = false,
    val publicId: String = "",
    val activityVisibility: String = "friends",
    val savingVisibility: Boolean = false,
    val failed: Boolean = false
)

class ActivityPrivacyViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ActivityPrivacyRepository.get(app)
    private val _uiState = MutableStateFlow(ActivityPrivacyUiState())
    val uiState = _uiState.asStateFlow()

    fun refresh() {
        if (_uiState.value.loading && _uiState.value.publicId.isNotEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, failed = false) }
            try {
                val info = repo.load()
                _uiState.update {
                    it.copy(
                        loading = false,
                        publicId = info.publicId,
                        activityVisibility = info.activityVisibility,
                        requiresLogin = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SupabaseApiException) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        requiresLogin = e.statusCode == 401,
                        failed = e.statusCode != 401
                    )
                }
            } catch (e: Throwable) {
                _uiState.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    fun updateActivityVisibility(value: String) {
        if (value !in setOf("public", "friends", "private")) return
        viewModelScope.launch {
            _uiState.update { it.copy(savingVisibility = true, failed = false) }
            try {
                val saved = repo.setActivityVisibility(value)
                _uiState.update {
                    it.copy(activityVisibility = saved, savingVisibility = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        savingVisibility = false,
                        requiresLogin = e is SupabaseApiException && e.statusCode == 401,
                        failed = true
                    )
                }
            }
        }
    }
}
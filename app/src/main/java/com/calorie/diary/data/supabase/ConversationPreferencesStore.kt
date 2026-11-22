package com.calorie.diary.data.supabase

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.conversationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "conversation_preferences"
)

data class ConversationSettings(
    val pinnedIds: Set<String> = emptySet(),
    val mutedIds: Set<String> = emptySet(),
    val deletedIds: Set<String> = emptySet()
)

/** 会话展示偏好只保存在本机，并按账户 UUID 隔离。 */
class ConversationPreferencesStore(context: Context) {
    private val store = context.applicationContext.conversationDataStore

    fun settingsFor(userId: String): Flow<ConversationSettings> = store.data.map { p ->
        ConversationSettings(
            pinnedIds = p[stringSetPreferencesKey("pinned_$userId")].orEmpty(),
            mutedIds = p[stringSetPreferencesKey("muted_$userId")].orEmpty(),
            deletedIds = p[stringSetPreferencesKey("deleted_$userId")].orEmpty()
        )
    }

    suspend fun togglePinned(userId: String, conversationId: String) {
        toggle(userId, "pinned", conversationId)
    }

    suspend fun toggleMuted(userId: String, conversationId: String) {
        toggle(userId, "muted", conversationId)
    }

    suspend fun deleteConversation(userId: String, conversationId: String) {
        val key = stringSetPreferencesKey("deleted_$userId")
        store.edit { p ->
            p[key] = p[key].orEmpty() + conversationId
            p[stringSetPreferencesKey("pinned_$userId")] =
                p[stringSetPreferencesKey("pinned_$userId")].orEmpty() - conversationId
            p[stringSetPreferencesKey("muted_$userId")] =
                p[stringSetPreferencesKey("muted_$userId")].orEmpty() - conversationId
        }
    }

    private suspend fun toggle(userId: String, kind: String, conversationId: String) {
        val key = stringSetPreferencesKey("${kind}_$userId")
        store.edit { p ->
            val current = p[key].orEmpty()
            p[key] = if (conversationId in current) current - conversationId else current + conversationId
        }
    }
}

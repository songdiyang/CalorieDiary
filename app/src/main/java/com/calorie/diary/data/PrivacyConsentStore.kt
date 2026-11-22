package com.calorie.diary.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.privacyConsentDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "privacy_consent"
)

/** 首启动隐私政策同意状态；政策发生重大修订时递增 POLICY_VERSION 要求重新同意。 */
class PrivacyConsentStore(context: Context) {

    companion object {
        const val POLICY_VERSION = 1
    }

    private val store = context.applicationContext.privacyConsentDataStore

    private object Keys {
        val ACCEPTED_VERSION = intPreferencesKey("accepted_version")
    }

    val agreed: Flow<Boolean> = store.data.map { prefs ->
        (prefs[Keys.ACCEPTED_VERSION] ?: 0) >= POLICY_VERSION
    }

    suspend fun agree() {
        store.edit { prefs ->
            prefs[Keys.ACCEPTED_VERSION] = POLICY_VERSION
        }
    }
}
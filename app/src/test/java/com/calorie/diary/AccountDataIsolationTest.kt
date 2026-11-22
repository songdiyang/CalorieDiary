package com.calorie.diary

import com.calorie.diary.data.db.scopedPreferenceName
import com.calorie.diary.data.repo.canSyncMealEntry
import com.calorie.diary.data.supabase.DefaultAvatarCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDataIsolationTest {

    @Test
    fun onlyCurrentAccountOrLegacyEntriesCanUpload() {
        assertTrue(canSyncMealEntry(ownerId = "", userId = "account-a"))
        assertTrue(canSyncMealEntry(ownerId = "account-a", userId = "account-a"))
        assertFalse(canSyncMealEntry(ownerId = "account-a", userId = "account-b"))
    }

    @Test
    fun preferenceKeysAreIsolatedByAccount() {
        val first = scopedPreferenceName("calorie_goal", "account-a")
        val second = scopedPreferenceName("calorie_goal", "account-b")
        assertNotEquals(first, second)
        assertTrue(first.endsWith("_account-a"))
        assertTrue(second.endsWith("_account-b"))
        assertEquals("calorie_goal", scopedPreferenceName("calorie_goal", ""))
    }

    @Test
    fun avatarPreferencesAreIsolatedByAccount() {
        val first = scopedPreferenceName("avatar_selection", "account-a")
        val second = scopedPreferenceName("avatar_selection", "account-b")
        assertNotEquals(first, second)
        assertEquals("avatar_selection", scopedPreferenceName("avatar_selection", ""))
    }

    @Test
    fun defaultAvatarMarkersRoundTripOnlyKnownValues() {
        DefaultAvatarCatalog.ids.forEach { id ->
            assertEquals(id, DefaultAvatarCatalog.idFrom(DefaultAvatarCatalog.marker(id)))
        }
        assertNull(DefaultAvatarCatalog.marker("unknown"))
        assertNull(DefaultAvatarCatalog.idFrom("🐰"))
        assertNull(DefaultAvatarCatalog.idFrom("default:unknown"))
    }
}

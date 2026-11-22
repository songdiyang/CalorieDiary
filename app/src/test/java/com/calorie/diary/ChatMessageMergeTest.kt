package com.calorie.diary

import com.calorie.diary.data.supabase.FriendMessage
import com.calorie.diary.data.supabase.GroupMessage
import com.calorie.diary.data.supabase.SharedFood
import com.calorie.diary.viewmodel.mergeSentGroupMessage
import com.calorie.diary.viewmodel.mergeSentMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatMessageMergeTest {
    @Test
    fun sentMessageAppearsImmediatelyWithoutDuplication() {
        val existing = FriendMessage(1L, "me", "old", "2026-11-14T08:00:00Z")
        val sent = FriendMessage(2L, "me", "new", "2026-11-14T09:00:00Z")

        val merged = mergeSentMessage(listOf(existing, sent), sent)

        assertEquals(listOf(1L, 2L), merged.map { it.id })
        assertEquals(1, merged.count { it.id == sent.id })
    }

    @Test
    fun sentGroupMessageAppearsImmediatelyWithoutDuplication() {
        val existing = GroupMessage(1L, "me", "old", "text", null, null, "2026-11-14T08:00:00Z")
        val newer = GroupMessage(2L, "me", "newer", "text", null, null, "2026-11-14T08:30:00Z")
        val sent = GroupMessage(
            3L, "me", "今日热量分享", "calorie_share", 720, "2026-11-14",
            "2026-11-14T09:00:00Z",
            items = listOf(SharedFood("米饭", "🍚", 200, 232, 1))
        )

        val merged = mergeSentGroupMessage(listOf(existing, sent, newer), sent)

        assertEquals(listOf(1L, 2L, 3L), merged.map { it.id })
        assertEquals(1, merged.count { it.id == sent.id })
        assertEquals(1, merged.last().items.size)
    }
}

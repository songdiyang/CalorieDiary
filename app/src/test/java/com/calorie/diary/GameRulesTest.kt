package com.calorie.diary

import com.calorie.diary.data.game.CheckInResult
import com.calorie.diary.data.game.GameCatalog
import com.calorie.diary.data.game.GameRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameRulesTest {
    @Test
    fun checkInNeedsNoFoodEntry() {
        assertEquals(
            CheckInResult.SUCCESS,
            GameRules.checkInEligibility("", "2026-11-14")
        )
    }

    @Test
    fun checkInCannotBeClaimedTwiceOnSameDay() {
        assertEquals(
            CheckInResult.ALREADY_CLAIMED,
            GameRules.checkInEligibility("2026-11-14", "2026-11-14")
        )
    }

    @Test
    fun previousDayCheckInDoesNotBlockToday() {
        assertEquals(
            CheckInResult.SUCCESS,
            GameRules.checkInEligibility("2026-11-13", "2026-11-14")
        )
    }

    @Test
    fun levelFloorMatchesServerCurve() {
        assertEquals(0, GameRules.levelFloor(1))
        assertEquals(100, GameRules.levelFloor(2))
        assertEquals(300, GameRules.levelFloor(3))
    }

    @Test
    fun levelSpanGrowsEachLevel() {
        assertEquals(100, GameRules.levelSpan(1))
        assertEquals(200, GameRules.levelSpan(2))
        assertEquals(300, GameRules.levelSpan(3))
    }

    @Test
    fun levelProgressStaysWithinCurrentLevel() {
        assertEquals(0, GameRules.levelProgress(100, 2))
        assertEquals(99, GameRules.levelProgress(199, 2))
        assertEquals(30, GameRules.levelProgress(30, 1))
        assertEquals(0, GameRules.levelProgress(0, 1))
    }

    @Test
    fun shopCatalogHasUniquePositivePricedItems() {
        assertTrue(GameCatalog.items.isNotEmpty())
        assertEquals(GameCatalog.items.size, GameCatalog.items.map { it.id }.distinct().size)
        assertTrue(GameCatalog.items.all { it.price > 0 })
        assertNull(GameCatalog.item("missing_item"))
    }
}

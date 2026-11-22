package com.calorie.diary

import com.calorie.diary.data.network.NetworkErrorKind
import com.calorie.diary.data.network.NetworkException
import com.calorie.diary.data.vision.ProxyVisionClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class VisionParseTest {

    @Test
    fun easterEggItemParsedWithFlag() {
        val raw = """{"foods":[{"name":"核弹","grams":0,"calories":0,"easterEgg":true}]}"""
        val foods = ProxyVisionClient.parseRecognizeResponse(raw)
        assertEquals(1, foods.size)
        assertEquals("核弹", foods[0].name)
        assertTrue(foods[0].easterEgg)
    }

    @Test
    fun normalFoodHasNoEasterFlag() {
        val raw = """{"foods":[{"name":"白米饭","grams":200,"calories":232}]}"""
        val foods = ProxyVisionClient.parseRecognizeResponse(raw)
        assertEquals(1, foods.size)
        assertFalse(foods[0].easterEgg)
    }

    @Test
    fun easterFlagFalseIsTreatedAsFood() {
        val raw = """{"foods":[{"name":"燃气罐","grams":10,"calories":5,"easterEgg":false}]}"""
        val foods = ProxyVisionClient.parseRecognizeResponse(raw)
        assertFalse(foods[0].easterEgg)
    }

    @Test
    fun blankNameSkipped() {
        val raw = """{"foods":[{"name":"  ","grams":0,"calories":0,"easterEgg":true}]}"""
        assertTrue(ProxyVisionClient.parseRecognizeResponse(raw).isEmpty())
    }

    @Test
    fun invalidPayloadThrowsBadResponse() {
        try {
            ProxyVisionClient.parseRecognizeResponse("not-json")
            fail("应当抛出 BAD_RESPONSE")
        } catch (e: NetworkException) {
            assertEquals(NetworkErrorKind.BAD_RESPONSE, e.kind)
        }
    }
}

package com.calorie.diary

import com.calorie.diary.data.db.FoodEntity
import com.calorie.diary.data.parser.ParseIntent
import com.calorie.diary.data.vision.ProxyVisionClient
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionAuthRequestTest {

    @Test
    fun recognitionRequestCarriesSupabaseBearerToken() {
        val request = ProxyVisionClient.createRequest(byteArrayOf(1, 2, 3), "supabase-token")

        assertEquals("Bearer supabase-token", request.header("Authorization"))
        assertTrue(request.url.encodedPath.endsWith("/recognize"))
        val buffer = Buffer()
        request.body?.writeTo(buffer)
        val payload = JSONObject(buffer.readUtf8())
        assertTrue(payload.getString("image").isNotBlank())
        assertTrue(payload.has("appKey"))
    }

    @Test
    fun textRequestUsesAuthenticationAndStructuredEndpoint() {
        val request = ProxyVisionClient.createTextRequest(" 一碗米饭 ", "supabase-token")

        assertEquals("Bearer supabase-token", request.header("Authorization"))
        assertTrue(request.url.encodedPath.endsWith("/parse-food"))
        val buffer = Buffer()
        request.body?.writeTo(buffer)
        assertEquals("一碗米饭", JSONObject(buffer.readUtf8()).getString("text"))
    }

    @Test
    fun textResponseMapsIntentDateMealAndFoods() {
        val raw = """
            {"intent":"add","dateOffset":-1,"mealType":1,
            "foods":[{"name":"米饭","grams":200,"calories":232},
            {"name":"未知汤","grams":300,"calories":150}],
            "directCalories":null,"removeNames":[],"unmatched":[]}
        """.trimIndent()
        val known = FoodEntity(1, "米饭", 116, "主食", "🍚")

        val result = ProxyVisionClient.parseTextResponse(raw, listOf(known))

        assertEquals(ParseIntent.ADD, result.intent)
        assertEquals(-1, result.dateOffset)
        assertEquals(1, result.mealFromText)
        assertEquals(2, result.addItems.size)
        assertEquals("🍚", result.addItems[0].food.emoji)
        assertEquals(50, result.addItems[1].food.caloriesPer100g)
    }
}

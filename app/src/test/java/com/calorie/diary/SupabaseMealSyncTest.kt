package com.calorie.diary

import com.calorie.diary.data.db.MealEntryEntity
import com.calorie.diary.data.supabase.SupabaseApi
import com.calorie.diary.data.supabase.SupabaseSession
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

class SupabaseMealSyncTest {

    @Test
    fun uploadsEntriesWithUserAndStableClientReference() = runBlocking {
        var capturedRequest: Request? = null
        var capturedBody = ""
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            capturedRequest = chain.request()
            val buffer = Buffer()
            chain.request().body?.writeTo(buffer)
            capturedBody = buffer.readUtf8()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("[]".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val api = SupabaseApi("https://supabase.test", "test-key", client)
        val session = SupabaseSession("access", "refresh", Long.MAX_VALUE, "user-1", "user@test.cn")
        val entry = MealEntryEntity(
            id = 42,
            date = "2026-11-16",
            mealType = 1,
            foodName = "米饭",
            emoji = "🍚",
            grams = 200,
            calories = 232,
            timestamp = 0L
        )

        api.upsertMealEntries(session, listOf(entry))

        val request = capturedRequest ?: throw AssertionError("No upload request")
        assertEquals("/rest/v1/meal_entries", request.url.encodedPath)
        assertEquals("user_id,client_ref", request.url.queryParameter("on_conflict"))
        assertEquals("resolution=merge-duplicates,return=minimal", request.header("Prefer"))
        val row = JSONArray(capturedBody).getJSONObject(0)
        assertEquals("user-1", row.getString("user_id"))
        assertEquals("android:42", row.getString("client_ref"))
        assertEquals("2026-11-16", row.getString("entry_date"))
        assertEquals(1, row.getInt("meal_type"))
        assertEquals("米饭", row.getString("food_name"))
        assertEquals(200, row.getInt("grams"))
        assertEquals(232, row.getInt("calories"))
        assertEquals("1970-01-01T00:00:00Z", row.getString("recorded_at"))
    }

    @Test
    fun reusesStoredClientReferenceWhenUploading() = runBlocking {
        var capturedBody = ""
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val buffer = Buffer()
            chain.request().body?.writeTo(buffer)
            capturedBody = buffer.readUtf8()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("[]".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val api = SupabaseApi("https://supabase.test", "test-key", client)
        val session = SupabaseSession("access", "refresh", Long.MAX_VALUE, "user-1", "user@test.cn")
        // 云端拉取后保留的 clientRef 不能被本地 id 覆盖，否则跨设备会产生重复行。
        val entry = MealEntryEntity(
            id = 9,
            date = "2026-11-10",
            mealType = 0,
            foodName = "苹果",
            emoji = "🍎",
            grams = 150,
            calories = 78,
            timestamp = 0L,
            clientRef = "android:7"
        )

        api.upsertMealEntries(session, listOf(entry))

        val row = JSONArray(capturedBody).getJSONObject(0)
        assertEquals("android:7", row.getString("client_ref"))
    }

    @Test
    fun pullsAllMealEntriesWithPagination() = runBlocking {
        var capturedRequest: Request? = null
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            capturedRequest = chain.request()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(
                    ("[{\"client_ref\":\"android:7\",\"entry_date\":\"2026-11-10\"," +
                        "\"meal_type\":2,\"food_name\":\"苹果\",\"emoji\":\"🍎\"," +
                        "\"grams\":150,\"calories\":78,\"recorded_at\":\"2026-11-10T03:00:00.123456+00:00\"}]")
                        .toResponseBody("application/json".toMediaType())
                )
                .build()
        }.build()
        val api = SupabaseApi("https://supabase.test", "test-key", client)
        val session = SupabaseSession("access", "refresh", Long.MAX_VALUE, "user-1", "user@test.cn")

        val entries = api.listMealEntries(session)

        assertEquals(1, entries.size)
        val item = entries.first()
        assertEquals("android:7", item.clientRef)
        assertEquals("2026-11-10", item.date)
        assertEquals(2, item.mealType)
        assertEquals("苹果", item.foodName)
        assertEquals(150, item.grams)
        assertEquals(78, item.calories)
        assertEquals(
            java.time.Instant.parse("2026-11-10T03:00:00.123Z").toEpochMilli(),
            item.recordedAtMillis
        )
        val request = capturedRequest ?: throw AssertionError("No pull request")
        val url = request.url
        assertEquals("/rest/v1/meal_entries", url.encodedPath)
        assertEquals("1000", url.queryParameter("limit"))
        assertEquals("0", url.queryParameter("offset"))
        assertEquals("Bearer access", request.header("Authorization"))
    }

    @Test
    fun deletesMealEntryByClientReference() = runBlocking {
        var capturedRequest: Request? = null
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            capturedRequest = chain.request()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(204)
                .message("No Content")
                .body("".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val api = SupabaseApi("https://supabase.test", "test-key", client)
        val session = SupabaseSession("access", "refresh", Long.MAX_VALUE, "user-1", "user@test.cn")

        api.deleteMealEntryByRef(session, "android:42")

        val request = capturedRequest ?: throw AssertionError("No delete request")
        assertEquals("DELETE", request.method)
        assertEquals("/rest/v1/meal_entries", request.url.encodedPath)
        assertEquals("eq.android:42", request.url.queryParameter("client_ref"))
    }
}

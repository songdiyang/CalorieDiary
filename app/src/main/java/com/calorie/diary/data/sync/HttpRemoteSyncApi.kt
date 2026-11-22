package com.calorie.diary.data.sync

import com.calorie.diary.data.db.UserIdentity
import com.calorie.diary.data.network.HttpClients
import com.calorie.diary.data.network.NetworkErrorKind
import com.calorie.diary.data.network.NetworkException
import com.calorie.diary.data.network.retryOnTransient
import com.calorie.diary.data.network.toNetworkErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * 真实云端 REST 实现（OkHttp）。服务器就绪后，在设置中填入 BaseUrl 并启用即可。
 *
 * 约定的后端接口（JSON）：
 *  POST   /groups                       创建房间 {name, goal}
 *  POST   /groups/{id}/members          加入房间 {userId, nickname, avatarEmoji}
 *  GET    /groups/{id}                  房间详情
 *  GET    /groups/{id}/members          成员列表
 *  POST   /groups/{id}/entries          推送一条记录
 *  GET    /groups/{id}/entries?date=    拉取某日记录
 *  DELETE /groups/{id}/members/{userId} 退出房间
 */
class HttpRemoteSyncApi(
    private val baseUrl: String,
    private val client: OkHttpClient = HttpClients.sync
) : RemoteSyncApi {

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    override suspend fun createGroup(name: String, goal: Int, owner: UserIdentity): RemoteGroup {
        val body = JSONObject()
            .put("name", name)
            .put("goal", goal)
            .put("ownerId", owner.userId)
            .toString()
        val resp = post("$baseUrl/groups", body)
        return parseGroup(JSONObject(resp))
    }

    override suspend fun joinGroup(groupId: String, identity: UserIdentity): RemoteGroup {
        val body = JSONObject()
            .put("userId", identity.userId)
            .put("nickname", identity.nickname)
            .put("avatarEmoji", identity.avatarEmoji)
            .toString()
        post("$baseUrl/groups/$groupId/members", body)
        return parseGroup(JSONObject(get("$baseUrl/groups/$groupId")))
    }

    override suspend fun getGroup(groupId: String): RemoteGroup? =
        runCatching { parseGroup(JSONObject(get("$baseUrl/groups/$groupId"))) }.getOrNull()

    override suspend fun listMembers(groupId: String): List<RemoteMember> {
        val arr = JSONArray(get("$baseUrl/groups/$groupId/members"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            RemoteMember(
                o.getString("userId"),
                o.getString("nickname"),
                o.optString("avatarEmoji", ""),
                o.optLong("lastActiveAt")
            )
        }
    }

    override suspend fun pushEntry(groupId: String, entry: RemoteEntry): RemoteEntry {
        val body = JSONObject()
            .put("date", entry.date)
            .put("mealType", entry.mealType)
            .put("foodName", entry.foodName)
            .put("emoji", entry.emoji)
            .put("grams", entry.grams)
            .put("calories", entry.calories)
            .put("timestamp", entry.timestamp)
            .put("ownerId", entry.ownerId)
            .put("ownerName", entry.ownerName)
            .toString()
        val resp = post("$baseUrl/groups/$groupId/entries", body)
        val o = JSONObject(resp)
        return entry.copy(id = o.optLong("id"))
    }

    override suspend fun pullEntries(groupId: String, date: String): List<RemoteEntry> {
        val arr = JSONArray(get("$baseUrl/groups/$groupId/entries?date=$date"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            RemoteEntry(
                id = o.optLong("id"),
                date = o.getString("date"),
                mealType = o.getInt("mealType"),
                foodName = o.getString("foodName"),
                emoji = o.optString("emoji", "🍴"),
                grams = o.getInt("grams"),
                calories = o.getInt("calories"),
                timestamp = o.getLong("timestamp"),
                ownerId = o.getString("ownerId"),
                ownerName = o.getString("ownerName")
            )
        }
    }

    override suspend fun leaveGroup(groupId: String, userId: String) {
        delete("$baseUrl/groups/$groupId/members/$userId")
    }

    // —— 阻塞 OkHttp 调用切到 IO；读请求可安全重试，写请求不自动重试 ——
    private suspend fun get(url: String): String =
        execute(Request.Builder().url(url).get().build(), retryable = true)

    private suspend fun post(url: String, body: String): String =
        execute(Request.Builder().url(url).post(body.toRequestBody(jsonType)).build())

    private suspend fun delete(url: String): String =
        execute(Request.Builder().url(url).delete().build())

    private suspend fun execute(request: Request, retryable: Boolean = false): String {
        val call: suspend () -> String = {
            withContext(Dispatchers.IO) {
                val response = try {
                    client.newCall(request).execute()
                } catch (e: IOException) {
                    throw NetworkException(e.toNetworkErrorKind(), e.message, e)
                }
                response.use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw httpError(resp.code, text)
                    text
                }
            }
        }
        return if (retryable) retryOnTransient(attempts = 2) { call() } else call()
    }

    private fun httpError(code: Int, body: String): Throwable {
        val serverMsg = runCatching {
            JSONObject(body).optString("message").takeIf { it.isNotBlank() }
                ?: JSONObject(body).optString("error").takeIf { it.isNotBlank() }
        }.getOrNull()
        return when (code) {
            401, 403 -> NetworkException(NetworkErrorKind.AUTH, serverMsg)
            404 -> SyncException(NetworkErrorKind.NOT_FOUND, serverMsg)
            429 -> NetworkException(NetworkErrorKind.RATE_LIMIT, serverMsg)
            in 500..599 -> NetworkException(NetworkErrorKind.SERVER, serverMsg)
            else -> NetworkException(NetworkErrorKind.UNKNOWN, serverMsg)
        }
    }

    private fun parseGroup(o: JSONObject) = RemoteGroup(
        groupId = o.getString("groupId"),
        name = o.getString("name"),
        ownerId = o.getString("ownerId"),
        calorieGoal = o.getInt("calorieGoal"),
        createdAt = o.optLong("createdAt")
    )
}

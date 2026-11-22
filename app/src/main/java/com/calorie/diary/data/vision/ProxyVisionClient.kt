package com.calorie.diary.data.vision

import android.util.Base64
import com.calorie.diary.BuildConfig
import com.calorie.diary.data.db.FoodEntity
import com.calorie.diary.data.network.HttpClients
import com.calorie.diary.data.network.NetworkErrorKind
import com.calorie.diary.data.network.NetworkException
import com.calorie.diary.data.network.toNetworkErrorKind
import com.calorie.diary.data.parser.ParseIntent
import com.calorie.diary.data.parser.ParsedFood
import com.calorie.diary.data.parser.TextParseResult
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/**
 * 云端识别代理客户端（对应 cloud/app.py）。
 *
 * 构建时通过 buildConfigField 注入代理地址与 appKey：
 * - local.properties 里的 vision.proxyUrl / vision.appKey
 * - 或 gradle -Pvision.proxyUrl=... / 环境变量 VISION_PROXY_URL
 * App 不保存任何 DeepSeek 密钥，识别一律经由云端代理（密钥只存在服务端）。
 * 未配置代理地址时 [recognize] 直接抛 [VisionServiceException]。
 */
object ProxyVisionClient {

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    /** 是否启用云端代理（未配置地址时为 false）。 */
    val enabled: Boolean
        get() = BuildConfig.VISION_PROXY_URL.isNotBlank()

    fun recognize(jpegBytes: ByteArray, accessToken: String): List<RecognizedFood> {
        if (!enabled) {
            throw VisionServiceException("云端识别服务未配置（VISION_PROXY_URL 为空）")
        }
        val request = createRequest(jpegBytes, accessToken)

        val response = try {
            HttpClients.vision.newCall(request).execute()
        } catch (e: IOException) {
            throw NetworkException(e.toNetworkErrorKind(), e.message, e)
        }

        response.use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw proxyError(resp.code, raw)
            return parseRecognizeResponse(raw)
        }
    }

    internal fun parseRecognizeResponse(raw: String): List<RecognizedFood> {
        val foods = runCatching {
            JSONObject(raw).getJSONArray("foods")
        }.getOrElse {
            throw NetworkException(NetworkErrorKind.BAD_RESPONSE, "代理返回格式异常")
        }
        val result = ArrayList<RecognizedFood>(foods.length())
        for (i in 0 until foods.length()) {
            val item = foods.optJSONObject(i) ?: continue
            val name = item.optString("name").trim()
            if (name.isEmpty()) continue
            result.add(
                RecognizedFood(
                    name = name,
                    grams = item.optInt("grams").coerceAtLeast(0),
                    calories = item.optInt("calories").coerceAtLeast(0),
                    easterEgg = item.optBoolean("easterEgg", false)
                )
            )
        }
        return result
    }

    fun parseText(
        text: String,
        accessToken: String,
        availableFoods: List<FoodEntity>
    ): TextParseResult {
        if (!enabled) {
            throw VisionServiceException("云端识别服务未配置（VISION_PROXY_URL 为空）")
        }
        val request = createTextRequest(text, accessToken)
        val response = try {
            HttpClients.vision.newCall(request).execute()
        } catch (e: IOException) {
            throw NetworkException(e.toNetworkErrorKind(), e.message, e)
        }
        response.use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw proxyError(resp.code, raw)
            return parseTextResponse(raw, availableFoods)
        }
    }

    internal fun createTextRequest(text: String, accessToken: String): Request {
        val payload = JSONObject()
            .put("appKey", BuildConfig.VISION_APP_KEY)
            .put("text", text.trim().take(500))
        return Request.Builder()
            .url(BuildConfig.VISION_PROXY_URL.trimEnd('/') + "/parse-food")
            .header("Authorization", "Bearer $accessToken")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()
    }

    internal fun parseTextResponse(raw: String, availableFoods: List<FoodEntity>): TextParseResult {
        val json = runCatching { JSONObject(raw) }.getOrElse {
            throw NetworkException(NetworkErrorKind.BAD_RESPONSE, "文字解析响应格式异常")
        }
        val intent = runCatching {
            ParseIntent.valueOf(json.optString("intent", "add").uppercase())
        }.getOrDefault(ParseIntent.UNKNOWN)
        val items = mutableListOf<ParsedFood>()
        val foods = json.optJSONArray("foods")
        if (foods != null) {
            for (i in 0 until foods.length()) {
                val item = foods.optJSONObject(i) ?: continue
                val name = item.optString("name").trim()
                if (name.isEmpty()) continue
                val grams = item.optInt("grams").coerceAtLeast(1)
                val calories = item.optInt("calories").coerceAtLeast(0)
                val knownFood = availableFoods.firstOrNull { it.name.equals(name, ignoreCase = true) }
                val food = knownFood ?: FoodEntity(
                    name = name,
                    caloriesPer100g = Math.round(calories * 100.0 / grams).toInt(),
                    category = "其他",
                    emoji = "🍴"
                )
                items.add(ParsedFood(food, 1, grams, grams, calories, estimated = true))
            }
        }
        return TextParseResult(
            intent = intent,
            dateOffset = json.optInt("dateOffset", 0),
            mealFromText = if (json.isNull("mealType")) null else json.optInt("mealType"),
            addItems = items,
            removeNames = stringList(json, "removeNames"),
            directCalories = if (json.isNull("directCalories")) null else json.optInt("directCalories"),
            unmatched = stringList(json, "unmatched")
        )
    }

    private fun stringList(json: JSONObject, key: String): List<String> {
        val array = json.optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optString(index).trim().takeIf { it.isNotEmpty() }
        }
    }

    internal fun createRequest(jpegBytes: ByteArray, accessToken: String): Request {
        val payload = JSONObject()
            .put("appKey", BuildConfig.VISION_APP_KEY)
            .put("image", Base64.encodeToString(jpegBytes, Base64.NO_WRAP))
        return Request.Builder()
            .url(endpoint())
            .header("Authorization", "Bearer $accessToken")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()
    }

    private fun endpoint(): String = BuildConfig.VISION_PROXY_URL.trimEnd('/') + "/recognize"

    /**
     * 把代理错误码翻译成上层可分类的异常，与 App 的容错文案体系对齐。
     * 403 / not_configured / upstream_auth 属于服务端配置问题，重试无意义。
     */
    private fun proxyError(code: Int, raw: String): Throwable {
        val errCode = runCatching { JSONObject(raw).optString("error") }.getOrDefault("")
        return when {
            code == 401 || errCode == "unauthorized" ->
                NetworkException(NetworkErrorKind.AUTH, "登录状态已失效")

            errCode == "not_configured" || errCode == "upstream_auth" || code == 403 ->
                VisionServiceException("云端识别服务配置异常（HTTP $code, $errCode）")

            errCode == "upstream_timeout" ->
                NetworkException(NetworkErrorKind.TIMEOUT, "云端识别超时（HTTP $code）")

            code == 429 || errCode == "rate_limited" || errCode == "upstream_rate_limited" ->
                NetworkException(NetworkErrorKind.RATE_LIMIT, "请求过于频繁（HTTP $code）")

            errCode == "bad_model_output" ->
                NetworkException(NetworkErrorKind.BAD_RESPONSE, "识别结果解析失败（HTTP $code）")

            code == 413 ->
                NetworkException(NetworkErrorKind.BAD_RESPONSE, "图片数据过大（HTTP 413）")

            code in 500..599 ->
                NetworkException(NetworkErrorKind.SERVER, "识别服务繁忙（HTTP $code）")

            code == 400 ->
                NetworkException(NetworkErrorKind.BAD_RESPONSE, "请求参数错误（HTTP 400）")

            else ->
                NetworkException(NetworkErrorKind.UNKNOWN, "代理请求失败（HTTP $code）")
        }
    }
}

package com.calorie.diary

import com.calorie.diary.data.network.NetworkErrorKind
import com.calorie.diary.data.network.NetworkException
import com.calorie.diary.data.network.isTransient
import com.calorie.diary.data.network.retryOnTransient
import com.calorie.diary.data.network.toNetworkErrorKind
import com.calorie.diary.data.supabase.SupabaseApi
import com.calorie.diary.data.supabase.SupabaseApiException
import com.calorie.diary.data.supabase.SupabaseSession
import com.calorie.diary.viewmodel.authErrorMessage
import com.calorie.diary.viewmodel.nicknameErrorMessage
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkErrorTest {

    @Test
    fun networkExceptionKeepsItsKind() {
        val e = NetworkException(NetworkErrorKind.RATE_LIMIT)
        assertEquals(NetworkErrorKind.RATE_LIMIT, e.toNetworkErrorKind(online = true))
    }

    @Test
    fun timeoutExceptionMapsToTimeout() {
        assertEquals(
            NetworkErrorKind.TIMEOUT,
            SocketTimeoutException("timeout").toNetworkErrorKind(online = true)
        )
    }

    @Test
    fun unknownHostDependsOnConnectivity() {
        val e = UnknownHostException("api.deepseek.com")
        assertEquals(NetworkErrorKind.DNS, e.toNetworkErrorKind(online = true))
        assertEquals(NetworkErrorKind.OFFLINE, e.toNetworkErrorKind(online = false))
    }

    @Test
    fun connectExceptionWithoutNetworkIsOffline() {
        assertEquals(
            NetworkErrorKind.OFFLINE,
            ConnectException("refused").toNetworkErrorKind(online = false)
        )
    }

    @Test
    fun genericIoExceptionFallsBackToConnection() {
        assertEquals(
            NetworkErrorKind.CONNECTION,
            IOException("broken pipe").toNetworkErrorKind(online = true)
        )
    }

    @Test
    fun transientKindsAreRetryable() {
        assertTrue(NetworkErrorKind.TIMEOUT.isTransient)
        assertTrue(NetworkErrorKind.SERVER.isTransient)
        assertFalse(NetworkErrorKind.OFFLINE.isTransient)
        assertFalse(NetworkErrorKind.AUTH.isTransient)
        assertFalse(NetworkErrorKind.RATE_LIMIT.isTransient)
    }

    @Test
    fun retryRecoversFromTransientFailure() = runBlocking {
        var attempts = 0
        val value = retryOnTransient(attempts = 3, initialDelayMs = 1, online = { true }) {
            attempts++
            if (attempts < 2) throw SocketTimeoutException("timeout")
            "ok"
        }
        assertEquals("ok", value)
        assertEquals(2, attempts)
    }

    @Test
    fun retryStopsImmediatelyWhenOffline() = runBlocking {
        var attempts = 0
        runCatching {
            retryOnTransient(attempts = 3, initialDelayMs = 1, online = { false }) {
                attempts++
                throw UnknownHostException("offline")
            }
        }
        assertEquals(1, attempts)
    }

    @Test
    fun retryGivesUpAfterAllAttempts() = runBlocking {
        var attempts = 0
        val result = runCatching {
            retryOnTransient(attempts = 3, initialDelayMs = 1, online = { true }) {
                attempts++
                throw NetworkException(NetworkErrorKind.SERVER)
            }
        }
        assertTrue(result.isFailure)
        assertEquals(3, attempts)
    }

    @Test
    fun mapsUnconfirmedEmailToDedicatedMessage() {
        val error = SupabaseApiException(400, "email_not_confirmed")
        assertEquals(R.string.auth_email_not_confirmed, authErrorMessage(error))
    }

    @Test
    fun mapsInvalidAuthParametersToInputMessage() {
        listOf("validation_failed", "email_address_invalid").forEach { code ->
            assertEquals(
                R.string.auth_invalid_input,
                authErrorMessage(SupabaseApiException(400, code))
            )
        }
    }

    @Test
    fun mapsInvalidOtpToDedicatedMessage() {
        listOf(
            SupabaseApiException(403, "otp_expired"),
            SupabaseApiException(400, "invalid_otp"),
            SupabaseApiException(403, null, "Token has expired or is invalid")
        ).forEach { error ->
            assertEquals(R.string.auth_otp_invalid, authErrorMessage(error))
        }
    }

    @Test
    fun mapsEmailMismatchToDedicatedMessage() {
        assertEquals(
            R.string.auth_email_mismatch,
            authErrorMessage(SupabaseApiException(400, "email_mismatch"))
        )
    }

    @Test
    fun mapsNicknameLimitsToDedicatedMessages() {
        val limitErrors = listOf(
            SupabaseApiException(400, "42901", "nickname_change_limit"),
            SupabaseApiException(400, null, "nickname_change_limit")
        )
        limitErrors.forEach { error ->
            assertEquals(R.string.nickname_change_limit, nicknameErrorMessage(error))
        }
        listOf(
            SupabaseApiException(400, "22023", "invalid_nickname"),
            SupabaseApiException(400, null, "invalid_nickname")
        ).forEach { error ->
            assertEquals(R.string.nickname_invalid, nicknameErrorMessage(error))
        }
    }

    @Test
    fun updateNicknameParsesRemainingCount() = runBlocking {
        val api = SupabaseApi(
            baseUrl = "https://supabase.test",
            publishableKey = "test-key",
            client = staticClient("""[{"nickname":"新名字","remaining":2}]""")
        )

        val result = api.updateNickname(testSession(), "新名字")

        assertEquals("新名字", result.nickname)
        assertEquals(2, result.remaining)
    }

    @Test
    fun nicknameStatusParsesUsedAndRemaining() = runBlocking {
        val api = SupabaseApi(
            baseUrl = "https://supabase.test",
            publishableKey = "test-key",
            client = staticClient("""[{"used":2,"remaining":1,"next_available_at":null}]""")
        )

        val status = api.fetchNicknameStatus(testSession())

        assertEquals(2, status.used)
        assertEquals(1, status.remaining)
        assertEquals(null, status.nextAvailableAt)
    }

    @Test
    fun gameStateParsesExpLevelAndDailyTask() = runBlocking {
        val api = SupabaseApi(
            baseUrl = "https://supabase.test",
            publishableKey = "test-key",
            client = staticClient(
                """{"user_id":"00000000-0000-0000-0000-000000000000","coins":20,"exp":130,
                "level":2,"level_exp":30,"level_next":200,"last_check_in_date":"2026-11-17",
                "check_in_streak":3,"checkin_coin_reward":5,"checkin_exp_reward":10,
                "checkin_streak_days":7,"checkin_streak_multiplier":2,
                "daily_task_done":true,"today_food_count":2,"server_date":"2026-11-17","items":[]}"""
            )
        )

        val state = api.getGameState(testSession())

        assertEquals(20, state.coins)
        assertEquals(130, state.exp)
        assertEquals(2, state.level)
        assertEquals(30, state.levelExp)
        assertEquals(200, state.levelNext)
        assertEquals(3, state.checkInStreak)
        assertEquals(5, state.checkInCoinReward)
        assertEquals(10, state.checkInExpReward)
        assertEquals(7, state.checkInStreakDays)
        assertEquals(2, state.checkInMultiplier)
        assertTrue(state.dailyTaskDone)
        assertEquals("2026-11-17", state.serverDate)
    }

    @Test
    fun claimCheckInParsesDoubledReward() = runBlocking {
        val api = SupabaseApi(
            baseUrl = "https://supabase.test",
            publishableKey = "test-key",
            client = staticClient(
                """{"status":"success","coins_awarded":10,"exp_awarded":20,"streak":7,
                "doubled":true,"level":2,"leveled_up":true}"""
            )
        )

        val reward = api.claimGameCheckIn(testSession())

        assertTrue(reward.success)
        assertEquals(10, reward.coins)
        assertEquals(20, reward.exp)
        assertEquals(7, reward.streak)
        assertTrue(reward.doubled)
        assertEquals(2, reward.level)
    }

    @Test
    fun completePhotoTaskParsesRewardAndLevelUp() = runBlocking {
        val api = SupabaseApi(
            baseUrl = "https://supabase.test",
            publishableKey = "test-key",
            client = staticClient(
                """{"status":"success","exp":130,"level":2,"leveled_up":true,"exp_awarded":30}"""
            )
        )

        val result = api.completePhotoTask(testSession(), "photo-task-1", "2026-11-17")

        assertTrue(result.success)
        assertEquals(130, result.exp)
        assertEquals(2, result.level)
        assertTrue(result.leveledUp)
    }

    @Test
    fun completePhotoTaskAlreadyDoneIsNotSuccess() = runBlocking {
        val api = SupabaseApi(
            baseUrl = "https://supabase.test",
            publishableKey = "test-key",
            client = staticClient(
                """{"status":"already_done","exp":60,"level":1,"leveled_up":false}"""
            )
        )

        val result = api.completePhotoTask(testSession(), "photo-task-2", "2026-11-17")

        assertTrue(!result.success)
        assertTrue(!result.leveledUp)
    }

    @Test
    fun gameStateParsesNewbieTasks() = runBlocking {
        val api = SupabaseApi(
            baseUrl = "https://supabase.test",
            publishableKey = "test-key",
            client = staticClient(
                """{"user_id":"00000000-0000-0000-0000-000000000000","coins":0,
                "server_date":"2026-11-22","items":[],
                "newbie_tasks":[
                  {"task_id":"newbie_nickname","done":true,"coin_reward":10,"exp_reward":20},
                  {"task_id":"newbie_first_friend","done":false,"coin_reward":20,"exp_reward":30}
                ]}"""
            )
        )

        val state = api.getGameState(testSession())

        assertEquals(2, state.newbieTasks.size)
        assertTrue(state.newbieTasks[0].done)
        assertEquals(10, state.newbieTasks[0].coinReward)
        assertEquals(20, state.newbieTasks[0].expReward)
        assertFalse(state.newbieTasks[1].done)
        assertEquals(20, state.newbieTasks[1].coinReward)
    }

    @Test
    fun syncNewbieTasksParsesAwards() = runBlocking {
        val api = SupabaseApi(
            baseUrl = "https://supabase.test",
            publishableKey = "test-key",
            client = staticClient(
                """{"status":"success","awarded":[
                  {"task_id":"newbie_nickname","coin_reward":10,"exp_reward":20},
                  {"task_id":"newbie_first_food","coin_reward":15,"exp_reward":25}
                ],"coins_awarded":25,"exp_awarded":45,"level":2,"leveled_up":true}"""
            )
        )

        val result = api.syncNewbieTasks(testSession())

        assertEquals("success", result.status)
        assertEquals(2, result.awarded.size)
        assertEquals("newbie_nickname", result.awarded[0].taskId)
        assertEquals(15, result.awarded[1].coins)
        assertEquals(25, result.coinsAwarded)
        assertEquals(45, result.expAwarded)
        assertEquals(2, result.level)
        assertTrue(result.leveledUp)
    }

    @Test
    fun sendEmailOtpPostsToOtpEndpoint() = runBlocking {
        val requests = mutableListOf<String>()
        val api = SupabaseApi(
            baseUrl = "https://supabase.test",
            publishableKey = "test-key",
            client = otpClient(requests)
        )

        api.sendEmailOtp("user@example.com", createUser = true)

        assertEquals(listOf("/auth/v1/otp"), requests)
    }

    @Test
    fun verifyEmailOtpReturnsSession() = runBlocking {
        val requests = mutableListOf<String>()
        val api = SupabaseApi(
            baseUrl = "https://supabase.test",
            publishableKey = "test-key",
            client = otpClient(requests)
        )

        val session = api.verifyEmailOtp("user@example.com", "123456")

        assertEquals(listOf("/auth/v1/verify"), requests)
        assertEquals("user-1", session.userId)
    }

    @Test
    fun verifyFallsBackToSignupTemplateToken() = runBlocking {
        val api = SupabaseApi(
            baseUrl = "https://supabase.test",
            publishableKey = "test-key",
            client = signupFallbackClient()
        )

        val session = api.verifyEmailOtp("user@example.com", "12345678")

        assertEquals("user-1", session.userId)
    }

    @Test
    fun isEmailRegisteredParsesBoolean() = runBlocking {
        val api = SupabaseApi(
            baseUrl = "https://supabase.test",
            publishableKey = "test-key",
            client = staticClient("true")
        )

        assertTrue(api.isEmailRegistered("user@example.com"))
    }

    private fun testSession() = SupabaseSession(
        accessToken = "token",
        refreshToken = "refresh",
        expiresAtMillis = Long.MAX_VALUE,
        userId = "00000000-0000-0000-0000-000000000000",
        email = "user@example.com"
    )

    private fun staticClient(payload: String): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(payload.toResponseBody("application/json; charset=utf-8".toMediaType()))
                .build()
        }
        .build()

    /** 模拟新用户注册：type=email 校验失败（otp_expired），type=signup 校验成功。 */
    private fun signupFallbackClient(): OkHttpClient =
        OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val buffer = okio.Buffer()
            request.body?.writeTo(buffer)
            val type = runCatching {
                org.json.JSONObject(buffer.readUtf8()).optString("type")
            }.getOrDefault("")
            val forbidden = type != "signup"
            val payload = if (forbidden) {
                """{"code":403,"error_code":"otp_expired","msg":"Token has expired or is invalid"}"""
            } else {
                """{"access_token":"token","refresh_token":"refresh","expires_in":3600,""" +
                    """"user":{"id":"user-1","email":"user@example.com"}}"""
            }
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(if (forbidden) 403 else 200)
                .message(if (forbidden) "Forbidden" else "OK")
                .body(payload.toResponseBody("application/json; charset=utf-8".toMediaType()))
                .build()
        }.build()

    private fun otpClient(requests: MutableList<String>): OkHttpClient =
        OkHttpClient.Builder().addInterceptor { chain ->
            val path = chain.request().url.encodedPath
            requests += path
            val payload = if (path.endsWith("/auth/v1/verify")) {
                """{"access_token":"token","refresh_token":"refresh","expires_in":3600,""" +
                    """"user":{"id":"user-1","email":"user@example.com"}}"""
            } else {
                "{}"
            }
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(payload.toResponseBody("application/json; charset=utf-8".toMediaType()))
                .build()
        }.build()
}

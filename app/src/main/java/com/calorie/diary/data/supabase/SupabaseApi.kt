package com.calorie.diary.data.supabase

import com.calorie.diary.BuildConfig
import com.calorie.diary.data.db.MealEntryEntity
import com.calorie.diary.data.game.CheckInReward
import com.calorie.diary.data.game.GameInventoryItem
import com.calorie.diary.data.game.GameRules
import com.calorie.diary.data.game.NewbieTaskReward
import com.calorie.diary.data.game.NewbieTaskState
import com.calorie.diary.data.game.NewbieTaskSyncResult
import com.calorie.diary.data.game.PhotoTaskResult
import com.calorie.diary.data.network.HttpClients
import com.calorie.diary.data.network.NetworkErrorKind
import com.calorie.diary.data.network.NetworkException
import com.calorie.diary.data.network.retryOnTransient
import com.calorie.diary.data.network.toNetworkErrorKind
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class SupabaseApiException(
    val statusCode: Int,
    val errorCode: String? = null,
    val serverMessage: String? = null
) : NetworkException(
    when (statusCode) {
        401, 403 -> NetworkErrorKind.AUTH
        404 -> NetworkErrorKind.NOT_FOUND
        429 -> NetworkErrorKind.RATE_LIMIT
        in 500..599 -> NetworkErrorKind.SERVER
        else -> NetworkErrorKind.UNKNOWN
    }
)

/** 轻量 Supabase HTTPS 客户端，只使用项目已有的 OkHttp 与 JSON。 */
class SupabaseApi(
    private val baseUrl: String = BuildConfig.SUPABASE_URL.trimEnd('/'),
    private val publishableKey: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
    private val client: OkHttpClient = HttpClients.sync
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    val isConfigured: Boolean
        get() = baseUrl.startsWith("https://") && publishableKey.isNotBlank()

    suspend fun isInvitationCodeValid(code: String): Boolean {
        val response = post(
            "/rest/v1/rpc/is_invitation_code_valid",
            JSONObject().put("p_code", code.trim())
        )
        return response.trim().toBooleanStrictOrNull()
            ?: throw NetworkException(NetworkErrorKind.BAD_RESPONSE, "邀请码校验响应异常")
    }

    /** 邀请码失效是否因为邀请人已注销。 */
    suspend fun isInvitationCodeDeleted(code: String): Boolean {
        val response = post(
            "/rest/v1/rpc/is_invitation_code_deleted",
            JSONObject().put("p_code", code.trim())
        )
        return response.trim().toBooleanStrictOrNull()
            ?: throw NetworkException(NetworkErrorKind.BAD_RESPONSE, "邀请码状态响应异常")
    }

    /** 注册完成后应用邀请码：自动关注邀请人，成为好友仍需互关与好友申请。 */
    suspend fun applyInvitationCode(session: SupabaseSession, code: String): Boolean {
        val response = post(
            "/rest/v1/rpc/apply_invitation_code",
            JSONObject().put("p_code", code.trim()),
            session.accessToken
        )
        return response.trim().toBooleanStrictOrNull()
            ?: throw NetworkException(NetworkErrorKind.BAD_RESPONSE, "邀请码应用响应异常")
    }

    suspend fun isEmailRegistered(email: String): Boolean {
        val response = post(
            "/rest/v1/rpc/is_email_registered",
            JSONObject().put("p_email", email.trim())
        )
        return response.trim().toBooleanStrictOrNull()
            ?: throw NetworkException(NetworkErrorKind.BAD_RESPONSE, "邮箱校验响应异常")
    }

    /** 发送邮箱验证码；邮件模板需包含 {{ .Token }} 才会展示 6 位验证码。 */
    suspend fun sendEmailOtp(email: String, createUser: Boolean) {
        val body = JSONObject()
            .put("email", email.trim())
            .put("create_user", createUser)
        post("/auth/v1/otp", body)
    }

    /**
     * 校验邮箱验证码，成功后邮箱即完成验证并返回会话。
     * 新用户注册的验证码来自 Confirm signup 邮件（type=signup），
     * 已有用户的验证码来自 Magic Link 邮件（type=email），这里自动兼容。
     */
    suspend fun verifyEmailOtp(email: String, token: String): SupabaseSession {
        suspend fun verify(type: String): SupabaseSession {
            val body = JSONObject()
                .put("type", type)
                .put("email", email.trim())
                .put("token", token.trim())
            return parseSession(parseObject(post("/auth/v1/verify", body)))
        }
        return try {
            verify("email")
        } catch (e: SupabaseApiException) {
            val tokenRejected = e.statusCode == 403 ||
                e.errorCode == "otp_expired" || e.errorCode == "invalid_otp"
            if (tokenRejected) verify("signup") else throw e
        }
    }

    /** 验证码注册的用户在会话创建后设置初始密码。 */
    suspend fun setPassword(session: SupabaseSession, password: String) {
        put("/auth/v1/user", JSONObject().put("password", password), session.accessToken)
    }

    /** 注销当前账号：服务端级联删除账号与全部业务数据。 */
    suspend fun deleteAccount(session: SupabaseSession) {
        post("/rest/v1/rpc/delete_my_account", JSONObject(), session.accessToken)
    }

    suspend fun signIn(email: String, password: String): SupabaseSession {
        val body = JSONObject()
            .put("email", email.trim())
            .put("password", password)
        val response = post("/auth/v1/token?grant_type=password", body)
        return parseSession(parseObject(response))
    }

    suspend fun refresh(refreshToken: String): SupabaseSession {
        val body = JSONObject().put("refresh_token", refreshToken)
        val response = post("/auth/v1/token?grant_type=refresh_token", body)
        return parseSession(parseObject(response))
    }

    suspend fun logout(session: SupabaseSession) {
        post("/auth/v1/logout", JSONObject(), session.accessToken)
    }

    /** 本地记录使用固定 client_ref，重复上传时由 (user_id, client_ref) 唯一索引去重。 */
    suspend fun upsertMealEntries(session: SupabaseSession, entries: List<MealEntryEntity>) {
        if (entries.isEmpty()) return
        val rows = JSONArray()
        entries.forEach { entry ->
            rows.put(
                JSONObject()
                    .put("user_id", session.userId)
                    .put("client_ref", entry.clientRef.ifBlank { "android:${entry.id}" })
                    .put("entry_date", entry.date)
                    .put("meal_type", entry.mealType)
                    .put("food_name", entry.foodName)
                    .put("emoji", entry.emoji)
                    .put("grams", entry.grams)
                    .put("calories", entry.calories)
                    .put("recorded_at", Instant.ofEpochMilli(entry.timestamp).toString())
            )
        }
        postArray(
            path = "/rest/v1/meal_entries?on_conflict=user_id,client_ref",
            body = rows,
            accessToken = session.accessToken,
            prefer = "resolution=merge-duplicates,return=minimal"
        )
    }

    /** 拉取当前账号的全部饮食记录（日历每一天），PostgREST 默认单页最多 1000 行，这里主动分页。 */
    suspend fun listMealEntries(session: SupabaseSession): List<RemoteMealEntry> {
        val pageSize = 1000
        val result = mutableListOf<RemoteMealEntry>()
        var offset = 0
        while (true) {
            val response = execute(
                request(
                    "/rest/v1/meal_entries" +
                        "?select=client_ref,entry_date,meal_type,food_name,emoji,grams,calories,recorded_at" +
                        "&order=recorded_at.asc&limit=$pageSize&offset=$offset",
                    session.accessToken
                ).get().build(),
                retryable = true
            )
            val page = runCatching { JSONArray(response) }.getOrElse {
                throw NetworkException(NetworkErrorKind.BAD_RESPONSE, null, it)
            }
            for (index in 0 until page.length()) {
                val item = page.getJSONObject(index)
                result.add(
                    RemoteMealEntry(
                        clientRef = item.optString("client_ref"),
                        date = item.optString("entry_date"),
                        mealType = item.optInt("meal_type"),
                        foodName = item.optString("food_name"),
                        emoji = item.optString("emoji"),
                        grams = item.optInt("grams"),
                        calories = item.optInt("calories"),
                        // PostgREST 的 timestamptz 带 +00:00 偏移与微秒，OffsetDateTime 兜底。
                        recordedAtMillis = runCatching {
                            OffsetDateTime.parse(item.optString("recorded_at")).toInstant().toEpochMilli()
                        }.recoverCatching {
                            Instant.parse(item.optString("recorded_at")).toEpochMilli()
                        }.getOrDefault(0L)
                    )
                )
            }
            if (page.length() < pageSize) break
            offset += pageSize
        }
        return result
    }

    /** 删除云端单条记录；RLS 限定只能删自己的数据。 */
    suspend fun deleteMealEntryByRef(session: SupabaseSession, clientRef: String) {
        delete(
            "/rest/v1/meal_entries?client_ref=eq.${java.net.URLEncoder.encode(clientRef, "UTF-8")}",
            session.accessToken
        )
    }

    suspend fun fetchProfile(session: SupabaseSession): CloudProfile? {
        val response = get(
            "/rest/v1/profiles?select=id,nickname,avatar_emoji,calorie_goal,water_goal",
            session.accessToken
        )
        try {
            val rows = JSONArray(response)
            if (rows.length() == 0) return null
            val row = rows.getJSONObject(0)
            return CloudProfile(
                id = row.getString("id"),
                nickname = row.optString("nickname", "我"),
                avatarEmoji = row.optString("avatar_emoji", ""),
                calorieGoal = row.optInt("calorie_goal", 1800),
                waterGoal = row.optInt("water_goal", 1500)
            )
        } catch (e: JSONException) {
            throw NetworkException(NetworkErrorKind.BAD_RESPONSE, null, e)
        }
    }

    suspend fun updateAvatar(session: SupabaseSession, avatarValue: String) {
        patch(
            "/rest/v1/profiles?id=eq.${session.userId}",
            JSONObject().put("avatar_emoji", avatarValue),
            session.accessToken
        )
    }

    /** 修改用户名；次数限制由服务端 update_nickname 强制执行，客户端不能绕过。 */
    suspend fun updateNickname(session: SupabaseSession, nickname: String): NicknameUpdateResult {
        val response = post(
            "/rest/v1/rpc/update_nickname",
            JSONObject().put("p_nickname", nickname),
            session.accessToken
        )
        try {
            val rows = JSONArray(response)
            val row = rows.getJSONObject(0)
            return NicknameUpdateResult(
                nickname = row.getString("nickname"),
                remaining = row.optInt("remaining", 0)
            )
        } catch (e: JSONException) {
            throw NetworkException(NetworkErrorKind.BAD_RESPONSE, null, e)
        }
    }

    suspend fun fetchNicknameStatus(session: SupabaseSession): NicknameChangeStatus {
        val response = post(
            "/rest/v1/rpc/nickname_change_status",
            JSONObject(),
            session.accessToken
        )
        try {
            val rows = JSONArray(response)
            if (rows.length() == 0) return NicknameChangeStatus()
            val row = rows.getJSONObject(0)
            return NicknameChangeStatus(
                used = row.optInt("used", 0),
                remaining = row.optInt("remaining", 3),
                nextAvailableAt = row.optTextOrNull("next_available_at")
            )
        } catch (e: JSONException) {
            throw NetworkException(NetworkErrorKind.BAD_RESPONSE, null, e)
        }
    }

    suspend fun fetchMyUserId(session: SupabaseSession): String {
        val response = get(
            "/rest/v1/profiles?select=id",
            session.accessToken
        )
        try {
            val rows = JSONArray(response)
            return if (rows.length() == 0) "" else rows.getJSONObject(0).getString("id")
        } catch (e: JSONException) {
            throw NetworkException(NetworkErrorKind.BAD_RESPONSE, null, e)
        }
    }

    suspend fun listFriends(session: SupabaseSession): List<FriendEntry> =
        parseFriends(post("/rest/v1/rpc/list_friends", JSONObject(), session.accessToken))

    suspend fun fetchActivityPrivacy(session: SupabaseSession): ActivityPrivacyInfo {
        val response = get(
            "/rest/v1/profiles?select=public_id,activity_visibility",
            session.accessToken
        )
        try {
            val rows = JSONArray(response)
            if (rows.length() == 0) return ActivityPrivacyInfo("")
            val row = rows.getJSONObject(0)
            return ActivityPrivacyInfo(
                publicId = row.optString("public_id", ""),
                activityVisibility = row.optString("activity_visibility", "friends")
            )
        } catch (e: JSONException) {
            throw NetworkException(NetworkErrorKind.BAD_RESPONSE, null, e)
        }
    }

    suspend fun addFriend(session: SupabaseSession, userId: String): FriendAddResult =
        parseFriendAddResult(
            post(
                "/rest/v1/rpc/add_friend",
                JSONObject().put("p_user_id", userId),
                session.accessToken
            )
        )

    /** 关注某人：返回 following（单方面关注）或 mutual（对方也已关注我）。 */
    suspend fun followUser(session: SupabaseSession, userId: String): String {
        val response = post(
            "/rest/v1/rpc/follow_user",
            JSONObject().put("p_user_id", userId),
            session.accessToken
        ).trim()
        return runCatching {
            when {
                response.startsWith("[") -> JSONArray(response).optString(0, "")
                response.startsWith("\"") -> response.trim('"')
                else -> response
            }
        }.getOrDefault("")
    }

    suspend fun listFriendRequests(session: SupabaseSession): List<FriendRequestEntry> =
        parseFriendRequests(post("/rest/v1/rpc/list_friend_requests", JSONObject(), session.accessToken))

    suspend fun respondFriendRequest(
        session: SupabaseSession,
        requesterId: String,
        accept: Boolean
    ): String {
        val response = post(
            "/rest/v1/rpc/respond_friend_request",
            JSONObject().put("p_requester_id", requesterId).put("p_accept", accept),
            session.accessToken
        ).trim()
        return runCatching {
            when {
                response.startsWith("[") -> JSONArray(response).optString(0, "")
                response.startsWith("\"") -> response.trim('"')
                else -> response
            }
        }.getOrDefault("")
    }

    suspend fun removeFriend(session: SupabaseSession, friendId: String) {
        post(
            "/rest/v1/rpc/remove_friend",
            JSONObject().put("p_friend_id", friendId),
            session.accessToken
        )
    }

    /** 单向取消关注：对方对我的关注会保留，仍留在我的粉丝列表里。 */
    suspend fun unfollowUser(session: SupabaseSession, userId: String) {
        post(
            "/rest/v1/rpc/unfollow_user",
            JSONObject().put("p_user_id", userId),
            session.accessToken
        )
    }

    suspend fun searchUsers(session: SupabaseSession, query: String): List<UserSearchResult> {
        val response = post(
            "/rest/v1/rpc/search_users",
            JSONObject().put("p_query", query.trim()),
            session.accessToken
        )
        try {
            val rows = JSONArray(response)
            return (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                UserSearchResult(
                    userId = row.getString("user_id"),
                    nickname = row.getString("nickname"),
                    avatarEmoji = row.optTextOrNull("avatar_emoji") ?: "",
                    publicId = row.optTextOrNull("public_id") ?: "",
                    isFollowing = row.optBoolean("is_following", false),
                    isFollowedBy = row.optBoolean("is_followed_by", false),
                    isFriend = row.optBoolean("is_friend", false),
                    hasSentFriendRequest = row.optBoolean("has_sent_request", false),
                    hasIncomingFriendRequest = row.optBoolean("has_incoming_request", false)
                )
            }
        } catch (e: JSONException) {
            throw NetworkException(NetworkErrorKind.BAD_RESPONSE, null, e)
        }
    }

    suspend fun listFriendMessages(
        session: SupabaseSession,
        friendId: String
    ): List<FriendMessage> = parseMessages(
        post(
            "/rest/v1/rpc/list_friend_messages",
            JSONObject().put("p_friend_id", friendId),
            session.accessToken
        )
    )

    suspend fun sendFriendMessage(
        session: SupabaseSession,
        friendId: String,
        content: String,
        messageType: String = "text",
        calories: Int? = null,
        entryDate: String? = null,
        items: List<SharedFood> = emptyList()
    ): FriendMessage {
        val body = JSONObject()
            .put("p_friend_id", friendId)
            .put("p_content", content)
            .put("p_message_type", messageType)
        if (calories != null) body.put("p_calories", calories)
        if (entryDate != null) body.put("p_entry_date", entryDate)
        if (items.isNotEmpty()) body.put("p_items", sharedFoodsJson(items))
        return parseMessages(
            post("/rest/v1/rpc/send_friend_message", body, session.accessToken)
        ).first()
    }

    suspend fun getFriendRelation(session: SupabaseSession, friendId: String): RelationBinding? =
        parseRelation(
            post(
                "/rest/v1/rpc/get_friend_relation",
                JSONObject().put("p_friend_id", friendId),
                session.accessToken
            )
        )

    suspend fun setFriendRelation(
        session: SupabaseSession,
        friendId: String,
        relation: String
    ): RelationBinding? = parseRelation(
        post(
            "/rest/v1/rpc/set_friend_relation",
            JSONObject().put("p_friend_id", friendId).put("p_relation", relation),
            session.accessToken
        )
    )

    private fun parseRelation(response: String): RelationBinding? {
        try {
            val rows = JSONArray(response)
            if (rows.length() == 0) return null
            val row = rows.getJSONObject(0)
            val type = row.optTextOrNull("relation_type") ?: return null
            return RelationBinding(type = type, since = row.optTextOrNull("bound_at"))
        } catch (e: JSONException) {
            throw NetworkException(NetworkErrorKind.BAD_RESPONSE, null, e)
        }
    }

    private fun parseMessages(response: String): List<FriendMessage> {
        try {
            val rows = JSONArray(response)
            return (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                FriendMessage(
                    id = row.getLong("message_id"),
                    senderId = row.getString("sender_id"),
                    content = row.getString("content"),
                    createdAt = row.optString("created_at"),
                    messageType = row.optString("message_type", "text").ifBlank { "text" },
                    calories = if (row.isNull("calories")) null else row.optInt("calories"),
                    entryDate = row.optTextOrNull("entry_date"),
                    items = parseSharedFoods(row)
                )
            }
        } catch (e: JSONException) {
            throw NetworkException(NetworkErrorKind.BAD_RESPONSE, null, e)
        }
    }

    private fun sharedFoodsJson(items: List<SharedFood>): JSONArray {
        val array = JSONArray()
        items.forEach { food ->
            array.put(
                JSONObject()
                    .put("name", food.name)
                    .put("emoji", food.emoji)
                    .put("grams", food.grams)
                    .put("calories", food.calories)
                    .put("meal", food.meal)
            )
        }
        return array
    }

    private fun parseSharedFoods(row: JSONObject): List<SharedFood> {
        val array = row.optJSONArray("items") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { food ->
                SharedFood(
                    name = food.optString("name"),
                    emoji = food.optString("emoji"),
                    grams = food.optInt("grams"),
                    calories = food.optInt("calories"),
                    meal = food.optInt("meal")
                )
            }
        }
    }

    // 群聊不再由创建者设统一目标：成员各自有个人目标，RPC 的 p_calorie_goal 走默认值。
    suspend fun createGroup(session: SupabaseSession, name: String): GroupConversation =
        parseGroups(
            post(
                "/rest/v1/rpc/create_group",
                JSONObject().put("p_name", name),
                session.accessToken
            )
        ).first()

    suspend fun listGroups(session: SupabaseSession): List<GroupConversation> =
        parseGroups(post("/rest/v1/rpc/list_groups", JSONObject(), session.accessToken))

    /** 邀请好友进群，返回新建的邀请数；非好友、已在群里或已有待处理邀请会被忽略。 */
    suspend fun inviteGroupMembers(
        session: SupabaseSession,
        groupId: String,
        userIds: List<String>
    ): Int {
        val ids = JSONArray()
        userIds.forEach { ids.put(it) }
        val response = post(
            "/rest/v1/rpc/invite_group_members",
            JSONObject().put("p_group_id", groupId).put("p_user_ids", ids),
            session.accessToken
        ).trim()
        return response.toIntOrNull() ?: 0
    }

    /** 当前用户收到的待处理群聊邀请。 */
    suspend fun listGroupInvitations(session: SupabaseSession): List<GroupInvitation> =
        parseGroupInvitations(
            post("/rest/v1/rpc/list_group_invitations", JSONObject(), session.accessToken)
        )

    /** 接受或拒绝群聊邀请，返回 accepted / declined。 */
    suspend fun respondGroupInvitation(
        session: SupabaseSession,
        invitationId: Long,
        accept: Boolean
    ): String {
        val response = post(
            "/rest/v1/rpc/respond_group_invitation",
            JSONObject().put("p_invitation_id", invitationId).put("p_accept", accept),
            session.accessToken
        ).trim()
        return runCatching { JSONArray(response).getJSONObject(0).optString("response_status") }
            .getOrDefault("")
    }

    /** 群聊主页的基础信息，仅群成员可读。 */
    suspend fun getGroupInfo(session: SupabaseSession, groupId: String): GroupInfo =
        parseGroupInfo(
            post(
                "/rest/v1/rpc/get_group_info",
                JSONObject().put("p_group_id", groupId),
                session.accessToken
            )
        )

    /** 群聊主页的成员列表，群主排在最前。 */
    suspend fun listGroupMembers(
        session: SupabaseSession,
        groupId: String
    ): List<GroupInfoMember> = parseGroupMembers(
        post(
            "/rest/v1/rpc/list_group_members",
            JSONObject().put("p_group_id", groupId),
            session.accessToken
        )
    )

    /** 群主修改群名称或群公告，返回保存后的最新值。 */
    suspend fun updateGroupInfo(
        session: SupabaseSession,
        groupId: String,
        name: String? = null,
        announcement: String? = null
    ): Pair<String, String> {
        val payload = JSONObject().put("p_group_id", groupId)
        if (name != null) payload.put("p_name", name)
        if (announcement != null) payload.put("p_announcement", announcement)
        val row = JSONArray(post("/rest/v1/rpc/update_group_info", payload, session.accessToken))
            .getJSONObject(0)
        return row.optString("updated_name") to row.optString("updated_announcement")
    }

    /** 群主设置群号是否可被搜索加入，返回保存后的状态。 */
    suspend fun setGroupSearchable(
        session: SupabaseSession,
        groupId: String,
        searchable: Boolean
    ): Boolean {
        val response = post(
            "/rest/v1/rpc/set_group_searchable",
            JSONObject().put("p_group_id", groupId).put("p_searchable", searchable),
            session.accessToken
        )
        val json = parseObject(response)
        return json.optString("status") == "success" &&
            json.optBoolean("searchable", searchable)
    }

    /** 群主解散群聊，返回 dissolved。 */
    suspend fun dissolveGroup(session: SupabaseSession, groupId: String): String =
        parseGroupActionResult(
            post(
                "/rest/v1/rpc/dissolve_group",
                JSONObject().put("p_group_id", groupId),
                session.accessToken
            )
        )

    /** 群主转让群聊，返回 transferred。 */
    suspend fun transferGroupOwnership(
        session: SupabaseSession,
        groupId: String,
        newOwnerId: String
    ): String = parseGroupActionResult(
        post(
            "/rest/v1/rpc/transfer_group_ownership",
            JSONObject().put("p_group_id", groupId).put("p_new_owner", newOwnerId),
            session.accessToken
        )
    )

    /** 普通成员退出群聊，返回 left。 */
    suspend fun leaveGroup(session: SupabaseSession, groupId: String): String =
        parseGroupActionResult(
            post(
                "/rest/v1/rpc/leave_group",
                JSONObject().put("p_group_id", groupId),
                session.accessToken
            )
        )

    suspend fun listGroupMessages(session: SupabaseSession, groupId: String): List<GroupMessage> =
        parseGroupMessages(
            post(
                "/rest/v1/rpc/list_group_messages",
                JSONObject().put("p_group_id", groupId),
                session.accessToken
            )
        )

    suspend fun sendGroupMessage(
        session: SupabaseSession,
        groupId: String,
        content: String,
        messageType: String,
        calories: Int?,
        entryDate: String?,
        items: List<SharedFood> = emptyList()
    ): GroupMessage = parseGroupMessages(
        post(
            "/rest/v1/rpc/send_group_message",
            JSONObject()
                .put("p_group_id", groupId)
                .put("p_content", content)
                .put("p_message_type", messageType)
                .apply {
                    if (calories != null) put("p_calories", calories)
                    if (entryDate != null) put("p_entry_date", entryDate)
                    if (items.isNotEmpty()) put("p_items", sharedFoodsJson(items))
                },
            session.accessToken
        )
    ).first()

    suspend fun getGroupDailyTotals(
        session: SupabaseSession,
        groupId: String,
        date: String
    ): List<GroupMemberTotal> {
        val response = post(
            "/rest/v1/rpc/get_group_daily_totals",
            JSONObject().put("p_group_id", groupId).put("p_date", date),
            session.accessToken
        )
        try {
            val rows = JSONArray(response)
            return (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                GroupMemberTotal(
                    userId = row.getString("user_id"),
                    nickname = row.getString("nickname"),
                    avatarEmoji = row.optString("avatar_emoji", ""),
                    totalCalories = row.optInt("total_calories", 0)
                )
            }
        } catch (e: JSONException) {
            throw NetworkException(NetworkErrorKind.BAD_RESPONSE, null, e)
        }
    }

    suspend fun setActivityVisibility(session: SupabaseSession, visibility: String): String =
        parseObject(
            post(
                "/rest/v1/rpc/set_activity_visibility",
                JSONObject().put("p_visibility", visibility),
                session.accessToken
            )
        ).optString("visibility", "friends")

    suspend fun syncActivityDailyTotals(session: SupabaseSession, days: List<ActivityDay>) {
        val values = JSONArray()
        days.take(31).forEach { day ->
            values.put(
                JSONObject()
                    .put("date", day.date)
                    .put("total_calories", day.totalCalories)
                    .put("entry_count", day.entryCount)
            )
        }
        post(
            "/rest/v1/rpc/sync_activity_daily_totals",
            JSONObject().put("p_days", values),
            session.accessToken
        )
    }

    suspend fun getActivityProfile(session: SupabaseSession, userId: String): ActivityProfileResult {
        val response = post(
            "/rest/v1/rpc/get_activity_profile",
            JSONObject().put("p_user_id", userId),
            session.accessToken
        )
        try {
            val json = parseObject(response)
            val status = json.optString("status")
            if (status != "ok") return ActivityProfileResult(status)
            val dayRows = json.getJSONArray("days")
            val days = (0 until dayRows.length()).map { index ->
                val row = dayRows.getJSONObject(index)
                ActivityDay(
                    date = row.getString("date"),
                    totalCalories = row.getInt("total_calories"),
                    entryCount = row.getInt("entry_count")
                )
            }
            return ActivityProfileResult(
                status,
                ActivityProfile(
                    userId = json.getString("user_id"),
                    nickname = json.getString("nickname"),
                    avatarEmoji = json.optString("avatar_emoji", ""),
                    publicId = json.optString("public_id"),
                    visibility = json.optString("visibility", "private"),
                    isSelf = json.optBoolean("is_self", false),
                    calorieGoal = json.optInt("calorie_goal", 1800),
                    activeDays = json.optInt("active_days", 0),
                    streakDays = calculateActivityStreak(days),
                    totalEntries = json.optInt("total_entries", 0),
                    totalCalories = json.optInt("total_calories", 0),
                    days = days
                )
            )
        } catch (e: JSONException) {
            throw NetworkException(NetworkErrorKind.BAD_RESPONSE, null, e)
        }
    }

    suspend fun getSocialSummary(session: SupabaseSession, userId: String): SocialSummary {
        val response = post(
            "/rest/v1/rpc/get_social_summary",
            JSONObject().put("p_user_id", userId),
            session.accessToken
        )
        try {
            val rows = JSONArray(response)
            if (rows.length() == 0) return SocialSummary()
            val row = rows.getJSONObject(0)
            return SocialSummary(
                following = row.optInt("following_count", 0),
                followers = row.optInt("follower_count", 0),
                mutual = row.optInt("mutual_count", 0)
            )
        } catch (e: JSONException) {
            throw NetworkException(NetworkErrorKind.BAD_RESPONSE, null, e)
        }
    }

    suspend fun listFollowing(session: SupabaseSession, userId: String): List<SocialUserEntry> =
        parseSocialUsers(
            post(
                "/rest/v1/rpc/list_following",
                JSONObject().put("p_user_id", userId),
                session.accessToken
            )
        )

    suspend fun listFollowers(session: SupabaseSession, userId: String): List<SocialUserEntry> =
        parseSocialUsers(
            post(
                "/rest/v1/rpc/list_followers",
                JSONObject().put("p_user_id", userId),
                session.accessToken
            )
        )

    /** 资料不存在时区分“已注销”与“从未存在”。 */
    suspend fun isProfileDeleted(session: SupabaseSession, userId: String): Boolean {
        val response = post(
            "/rest/v1/rpc/is_profile_deleted",
            JSONObject().put("p_user_id", userId),
            session.accessToken
        )
        return response.trim().toBooleanStrictOrNull()
            ?: throw NetworkException(NetworkErrorKind.BAD_RESPONSE, "注销状态校验响应异常")
    }

    suspend fun getPublicProfile(session: SupabaseSession, userId: String): PublicProfile? {
        val response = post(
            "/rest/v1/rpc/get_public_profile",
            JSONObject().put("p_user_id", userId),
            session.accessToken
        )
        try {
            val rows = JSONArray(response)
            if (rows.length() == 0) return null
            val row = rows.getJSONObject(0)
            return PublicProfile(
                userId = row.getString("user_id"),
                nickname = row.getString("nickname"),
                avatarEmoji = row.optTextOrNull("avatar_emoji") ?: "",
                publicId = row.optTextOrNull("public_id") ?: "",
                isSelf = row.optBoolean("is_self", false),
                isFriend = row.optBoolean("is_friend", false),
                isFollowing = row.optBoolean("is_following", false),
                isFollowedBy = row.optBoolean("is_followed_by", false),
                isMutual = row.optBoolean("is_mutual", false),
                hasSentFriendRequest = row.optBoolean("has_sent_request", false),
                hasIncomingFriendRequest = row.optBoolean("has_incoming_request", false),
                level = row.optInt("level", 1)
            )
        } catch (e: JSONException) {
            throw NetworkException(NetworkErrorKind.BAD_RESPONSE, null, e)
        }
    }

    private fun parseSocialUsers(response: String): List<SocialUserEntry> {
        try {
            val rows = JSONArray(response)
            return (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                SocialUserEntry(
                    userId = row.getString("user_id"),
                    nickname = row.getString("nickname"),
                    avatarEmoji = row.optTextOrNull("avatar_emoji") ?: "",
                    mutual = row.optBoolean("mutual", false),
                    followedAt = row.optString("followed_at")
                )
            }
        } catch (e: JSONException) {
            throw NetworkException(NetworkErrorKind.BAD_RESPONSE, null, e)
        }
    }

    suspend fun getGameState(session: SupabaseSession): CloudGameState {
        val response = post("/rest/v1/rpc/get_game_state", JSONObject(), session.accessToken)
        try {
            val json = parseObject(response)
            val itemRows = json.getJSONArray("items")
            val newbieRows = json.optJSONArray("newbie_tasks") ?: JSONArray()
            val level = json.optInt("level", 1).coerceAtLeast(1)
            return CloudGameState(
                userId = json.getString("user_id"),
                coins = json.optInt("coins", 0),
                exp = json.optInt("exp", 0),
                level = level,
                levelExp = json.optInt("level_exp", 0),
                levelNext = json.optInt("level_next", GameRules.levelSpan(level))
                    .coerceAtLeast(1),
                dailyTaskDone = json.optBoolean("daily_task_done", false),
                lastCheckInDate = json.optString("last_check_in_date")
                    .takeUnless { it == "null" }.orEmpty(),
                checkInStreak = json.optInt("check_in_streak", 0).coerceAtLeast(0),
                checkInCoinReward = json.optInt("checkin_coin_reward", 5).coerceAtLeast(0),
                checkInExpReward = json.optInt("checkin_exp_reward", 10).coerceAtLeast(0),
                checkInStreakDays = json.optInt("checkin_streak_days", 7).coerceAtLeast(0),
                checkInMultiplier = json.optInt("checkin_streak_multiplier", 2).coerceAtLeast(1),
                serverDate = json.getString("server_date"),
                inventory = (0 until itemRows.length()).map { index ->
                    val row = itemRows.getJSONObject(index)
                    GameInventoryItem(row.getString("item_id"), row.getInt("quantity"))
                },
                newbieTasks = (0 until newbieRows.length()).map { index ->
                    val row = newbieRows.getJSONObject(index)
                    NewbieTaskState(
                        taskId = row.getString("task_id"),
                        done = row.optBoolean("done", false),
                        coinReward = row.optInt("coin_reward", 0).coerceAtLeast(0),
                        expReward = row.optInt("exp_reward", 0).coerceAtLeast(0)
                    )
                }
            )
        } catch (e: JSONException) {
            throw NetworkException(NetworkErrorKind.BAD_RESPONSE, null, e)
        }
    }

    /** 每日任务：记录一次饮食即完成，服务端按天幂等发放经验。 */
    suspend fun completePhotoTask(
        session: SupabaseSession,
        clientRef: String,
        date: String
    ): PhotoTaskResult {
        val json = parseObject(
            post(
                "/rest/v1/rpc/complete_photo_task",
                JSONObject().put("p_client_ref", clientRef).put("p_entry_date", date),
                session.accessToken
            )
        )
        return PhotoTaskResult(
            status = json.optString("status"),
            exp = json.optInt("exp", 0),
            level = json.optInt("level", 1),
            leveledUp = json.optBoolean("leveled_up", false)
        )
    }

    /** 新手任务：服务端按真实行为判定并幂等发奖，返回本次实际完成的任务。 */
    suspend fun syncNewbieTasks(session: SupabaseSession): NewbieTaskSyncResult {
        val json = parseObject(
            post("/rest/v1/rpc/sync_newbie_tasks", JSONObject(), session.accessToken)
        )
        val awardedRows = json.optJSONArray("awarded") ?: JSONArray()
        return NewbieTaskSyncResult(
            status = json.optString("status"),
            awarded = (0 until awardedRows.length()).map { index ->
                val row = awardedRows.getJSONObject(index)
                NewbieTaskReward(
                    taskId = row.getString("task_id"),
                    coins = row.optInt("coin_reward", 0),
                    exp = row.optInt("exp_reward", 0)
                )
            },
            coinsAwarded = json.optInt("coins_awarded", 0),
            expAwarded = json.optInt("exp_awarded", 0),
            level = json.optInt("level", 1),
            leveledUp = json.optBoolean("leveled_up", false)
        )
    }

    /** 自动签到：服务端按配置表发放金币与经验，连续签到每满 N 天翻倍。 */
    suspend fun claimGameCheckIn(session: SupabaseSession): CheckInReward {
        val json = parseObject(
            post("/rest/v1/rpc/claim_game_check_in", JSONObject(), session.accessToken)
        )
        return CheckInReward(
            status = json.optString("status"),
            coins = json.optInt("coins_awarded", 0),
            exp = json.optInt("exp_awarded", 0),
            streak = json.optInt("streak", 0),
            doubled = json.optBoolean("doubled", false),
            level = json.optInt("level", 1),
            leveledUp = json.optBoolean("leveled_up", false)
        )
    }

    suspend fun purchaseGameItem(session: SupabaseSession, itemId: String): String = parseObject(
        post(
            "/rest/v1/rpc/purchase_game_item",
            JSONObject().put("p_item_id", itemId),
            session.accessToken
        )
    ).optString("status")

    private fun parseGroups(response: String): List<GroupConversation> {
        try {
            val rows = JSONArray(response)
            return (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                GroupConversation(
                    groupId = row.getString("group_id"),
                    name = row.getString("name"),
                    ownerId = row.getString("owner_id"),
                    calorieGoal = row.getInt("calorie_goal"),
                    createdAt = row.optString("created_at"),
                    lastMessage = row.optTextOrNull("last_message"),
                    lastMessageAt = row.optTextOrNull("last_message_at"),
                    sparkDays = row.optInt("spark_days", 0)
                )
            }
        } catch (e: JSONException) {
            throw NetworkException(NetworkErrorKind.BAD_RESPONSE, null, e)
        }
    }

    private fun parseGroupInvitations(response: String): List<GroupInvitation> {
        try {
            val rows = JSONArray(response)
            return (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                GroupInvitation(
                    id = row.getLong("invitation_id"),
                    groupId = row.getString("group_id"),
                    groupName = row.getString("group_name"),
                    inviterId = row.getString("inviter_id"),
                    inviterNickname = row.optString("inviter_nickname"),
                    inviterAvatarEmoji = row.optString("inviter_avatar_emoji"),
                    memberCount = row.optInt("member_count", 0),
                    createdAt = row.optString("created_at")
                )
            }
        } catch (e: JSONException) {
            throw NetworkException(NetworkErrorKind.BAD_RESPONSE, null, e)
        }
    }

    private fun parseGroupActionResult(response: String): String =
        runCatching {
            JSONArray(response).getJSONObject(0).optString("result_status")
        }.getOrDefault("")

    private fun parseGroupInfo(response: String): GroupInfo {
        try {
            val row = JSONArray(response).getJSONObject(0)
            return GroupInfo(
                groupId = row.getString("info_group_id"),
                name = row.getString("info_name"),
                ownerId = row.getString("info_owner_id"),
                announcement = row.optString("info_announcement"),
                memberCount = row.optInt("info_member_count", 0),
                searchable = row.optBoolean("info_searchable", true)
            )
        } catch (e: JSONException) {
            throw NetworkException(NetworkErrorKind.BAD_RESPONSE, null, e)
        }
    }

    private fun parseGroupMembers(response: String): List<GroupInfoMember> {
        try {
            val rows = JSONArray(response)
            return (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                GroupInfoMember(
                    userId = row.getString("member_id"),
                    nickname = row.optString("member_nickname"),
                    avatarEmoji = row.optString("member_avatar_emoji"),
                    isOwner = row.optBoolean("is_owner", false)
                )
            }
        } catch (e: JSONException) {
            throw NetworkException(NetworkErrorKind.BAD_RESPONSE, null, e)
        }
    }

    private fun parseGroupMessages(response: String): List<GroupMessage> {
        try {
            val rows = JSONArray(response)
            return (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                GroupMessage(
                    id = row.getLong("message_id"),
                    senderId = row.getString("sender_id"),
                    content = row.getString("content"),
                    messageType = row.getString("message_type"),
                    calories = if (row.isNull("calories")) null else row.getInt("calories"),
                    entryDate = row.optString("entry_date").takeIf { it.isNotBlank() },
                    createdAt = row.optString("created_at"),
                    items = parseSharedFoods(row)
                )
            }
        } catch (e: JSONException) {
            throw NetworkException(NetworkErrorKind.BAD_RESPONSE, null, e)
        }
    }

    private fun parseFriendAddResult(response: String): FriendAddResult {
        try {
            val rows = JSONArray(response)
            if (rows.length() == 0) return FriendAddResult(FriendAddStatus.NOT_FOUND)
            val row = rows.getJSONObject(0)
            val friendId = row.optTextOrNull("friend_id")
            return FriendAddResult(
                status = row.optString("status"),
                friend = friendId?.let { id ->
                    FriendEntry(
                        userId = id,
                        nickname = row.optString("nickname"),
                        avatarEmoji = row.optString("avatar_emoji"),
                        friendsSince = row.optString("friends_since")
                    )
                }
            )
        } catch (e: JSONException) {
            throw NetworkException(NetworkErrorKind.BAD_RESPONSE, null, e)
        }
    }

    private fun parseFriendRequests(response: String): List<FriendRequestEntry> {
        try {
            val rows = JSONArray(response)
            return (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                FriendRequestEntry(
                    userId = row.getString("user_id"),
                    nickname = row.getString("nickname"),
                    avatarEmoji = row.optTextOrNull("avatar_emoji") ?: "",
                    requestedAt = row.optString("created_at")
                )
            }
        } catch (e: JSONException) {
            throw NetworkException(NetworkErrorKind.BAD_RESPONSE, null, e)
        }
    }

    private fun JSONObject.optTextOrNull(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

    private fun parseFriends(response: String): List<FriendEntry> {
        try {
            val rows = JSONArray(response)
            return (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                FriendEntry(
                    userId = row.getString("friend_id"),
                    nickname = row.getString("nickname"),
                    avatarEmoji = row.optTextOrNull("avatar_emoji") ?: "",
                    friendsSince = row.optString("friends_since"),
                    lastMessage = row.optTextOrNull("last_message"),
                    lastMessageAt = row.optTextOrNull("last_message_at"),
                    publicId = row.optTextOrNull("public_id") ?: "",
                    sparkCount = row.optInt("spark_count", 0)
                )
            }
        } catch (e: JSONException) {
            throw NetworkException(NetworkErrorKind.BAD_RESPONSE, null, e)
        }
    }

    private fun parseSession(json: JSONObject): SupabaseSession {
        try {
            val user = json.getJSONObject("user")
            val now = System.currentTimeMillis()
            val expiresAt = if (json.has("expires_at")) {
                json.getLong("expires_at") * 1000L
            } else {
                now + json.optLong("expires_in", 3600L) * 1000L
            }
            return SupabaseSession(
                accessToken = json.getString("access_token"),
                refreshToken = json.getString("refresh_token"),
                expiresAtMillis = expiresAt,
                userId = user.getString("id"),
                email = user.optString("email")
            )
        } catch (e: JSONException) {
            throw NetworkException(NetworkErrorKind.BAD_RESPONSE, null, e)
        }
    }

    private fun parseObject(response: String): JSONObject =
        try {
            JSONObject(response)
        } catch (e: JSONException) {
            throw NetworkException(NetworkErrorKind.BAD_RESPONSE, null, e)
        }

    private suspend fun get(path: String, accessToken: String): String =
        execute(request(path, accessToken).get().build(), retryable = true)

    private suspend fun post(
        path: String,
        body: JSONObject,
        accessToken: String? = null
    ): String = execute(
        request(path, accessToken).post(body.toString().toRequestBody(jsonType)).build()
    )

    private suspend fun put(
        path: String,
        body: JSONObject,
        accessToken: String
    ): String = execute(
        request(path, accessToken).put(body.toString().toRequestBody(jsonType)).build()
    )

    private suspend fun patch(
        path: String,
        body: JSONObject,
        accessToken: String
    ): String = execute(
        request(path, accessToken)
            .header("Prefer", "return=minimal")
            .patch(body.toString().toRequestBody(jsonType))
            .build(),
        retryable = true
    )

    private suspend fun postArray(
        path: String,
        body: JSONArray,
        accessToken: String,
        prefer: String
    ): String = execute(
        request(path, accessToken)
            .header("Prefer", prefer)
            .post(body.toString().toRequestBody(jsonType))
            .build(),
        retryable = true
    )

    private suspend fun delete(path: String, accessToken: String): String = execute(
        request(path, accessToken)
            .header("Prefer", "return=minimal")
            .delete()
            .build(),
        retryable = true
    )

    private fun request(path: String, accessToken: String?): Request.Builder {
        check(isConfigured) { "Supabase is not configured" }
        return Request.Builder()
            .url(baseUrl + path)
            .header("apikey", publishableKey)
            .header("Accept", "application/json")
            .apply {
                if (accessToken != null) header("Authorization", "Bearer $accessToken")
            }
    }

    private suspend fun execute(request: Request, retryable: Boolean = false): String {
        val call: suspend () -> String = {
            withContext(Dispatchers.IO) {
                val response = try {
                    client.newCall(request).execute()
                } catch (e: IOException) {
                    throw NetworkException(e.toNetworkErrorKind(), null, e)
                }
                response.use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw apiError(resp.code, text)
                    text
                }
            }
        }
        return if (retryable) retryOnTransient(attempts = 2) { call() } else call()
    }

    private fun apiError(statusCode: Int, body: String): SupabaseApiException {
        val json = runCatching { JSONObject(body) }.getOrNull()
        val errorCode = json?.optString("error_code")?.takeIf { it.isNotBlank() }
            ?: json?.optString("error")?.takeIf { it.isNotBlank() }
            ?: json?.optString("code")?.takeIf { it.isNotBlank() }
        // 不记录或透传响应正文，避免认证响应中的敏感信息进入日志。
        val serverMessage = json?.optString("msg")?.takeIf { it.isNotBlank() }
            ?: json?.optString("message")?.takeIf { it.isNotBlank() }
            ?: json?.optString("error_description")?.takeIf { it.isNotBlank() }
        return SupabaseApiException(statusCode, errorCode, serverMessage)
    }
}

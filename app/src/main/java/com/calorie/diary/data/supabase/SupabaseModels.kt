package com.calorie.diary.data.supabase

import com.calorie.diary.data.DateUtils
import com.calorie.diary.data.game.GameInventoryItem
import com.calorie.diary.data.game.NewbieTaskState
import java.util.UUID
import java.util.regex.Pattern

/** 仅供数据层使用，界面状态不持有访问令牌。 */
data class SupabaseSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
    val userId: String,
    val email: String
) {
    fun needsRefresh(nowMillis: Long = System.currentTimeMillis()): Boolean =
        expiresAtMillis - nowMillis <= 60_000L

    fun toAccount() = AuthAccount(userId = userId, email = email)
}

data class AuthAccount(
    val userId: String,
    val email: String
)

object DefaultAvatarCatalog {
    const val RABBIT = "rabbit"
    const val FOX = "fox"
    private const val PREFIX = "default:"

    val ids = listOf(RABBIT, FOX)

    fun marker(avatarId: String): String? =
        if (avatarId in ids) PREFIX + avatarId else null

    fun idFrom(value: String?): String? {
        if (value?.startsWith(PREFIX) != true) return null
        return value.substring(PREFIX.length).takeIf { it in ids }
    }

    /** 兼容本地保存的纯 id（rabbit）和云端保存的标记（default:rabbit），各页面统一用它解析头像。 */
    fun resolve(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val raw = if (value.startsWith(PREFIX)) value.substring(PREFIX.length) else value
        return raw.takeIf { it in ids }
    }
}

data class CloudProfile(
    val id: String,
    val nickname: String,
    val avatarEmoji: String,
    val calorieGoal: Int,
    val waterGoal: Int
)

data class ActivityDay(
    val date: String,
    val totalCalories: Int,
    val entryCount: Int
)

data class ActivityProfile(
    val userId: String,
    val nickname: String,
    val avatarEmoji: String,
    val publicId: String,
    val visibility: String,
    val isSelf: Boolean,
    val calorieGoal: Int,
    val activeDays: Int,
    val streakDays: Int,
    val totalEntries: Int,
    val totalCalories: Int,
    val days: List<ActivityDay>
)

fun calculateActivityStreak(days: List<ActivityDay>): Int {
    val recorded = days.mapTo(mutableSetOf()) { it.date }
    var anchor = DateUtils.todayString()
    if (anchor !in recorded) anchor = DateUtils.addDays(anchor, -1)
    var streak = 0
    while (anchor in recorded) {
        streak++
        anchor = DateUtils.addDays(anchor, -1)
    }
    return streak
}

data class ActivityProfileResult(
    val status: String,
    val profile: ActivityProfile? = null
)

data class CloudGameState(
    val userId: String,
    val coins: Int,
    val exp: Int = 0,
    val level: Int = 1,
    val levelExp: Int = 0,
    val levelNext: Int = 100,
    val dailyTaskDone: Boolean = false,
    val lastCheckInDate: String,
    val checkInStreak: Int = 0,
    val checkInCoinReward: Int = 5,
    val checkInExpReward: Int = 10,
    val checkInStreakDays: Int = 7,
    val checkInMultiplier: Int = 2,
    val serverDate: String,
    val inventory: List<GameInventoryItem>,
    val newbieTasks: List<NewbieTaskState> = emptyList()
)

data class FriendEntry(
    val userId: String,
    val nickname: String,
    val avatarEmoji: String,
    val friendsSince: String,
    val lastMessage: String? = null,
    val lastMessageAt: String? = null,
    val publicId: String = "",
    val sparkCount: Int = 0
)

data class UserSearchResult(
    val userId: String,
    val nickname: String,
    val avatarEmoji: String,
    val publicId: String = "",
    val isFollowing: Boolean = false,
    val isFollowedBy: Boolean = false,
    val isFriend: Boolean = false,
    val hasSentFriendRequest: Boolean = false,
    val hasIncomingFriendRequest: Boolean = false
) {
    val isMutual: Boolean get() = isFollowing && isFollowedBy
}

data class FriendRequestEntry(
    val userId: String,
    val nickname: String,
    val avatarEmoji: String,
    val requestedAt: String
)

/** 粉丝关注通知：单向关注时可回关，互关且尚未成为好友时可申请好友。 */
data class FollowNoticeEntry(
    val userId: String,
    val nickname: String,
    val avatarEmoji: String,
    val followedAt: String,
    val mutual: Boolean = false
)

/** add_friend RPC 的结果状态：follow_required 表示尚未互相关注，request_sent 表示好友申请已发出。 */
object FriendAddStatus {
    const val NOT_FOUND = "not_found"
    const val ALREADY_FRIENDS = "already_friends"
    const val FOLLOW_REQUIRED = "follow_required"
    const val REQUEST_SENT = "request_sent"
    const val FRIENDS = "friends"
}

data class FriendAddResult(
    val status: String,
    val friend: FriendEntry? = null
)

/** 社交图谱统计：关注 = 互关好友 + 我发出的请求；粉丝 = 互关好友 + 待我回关的请求。 */
data class SocialSummary(
    val following: Int = 0,
    val followers: Int = 0,
    val mutual: Int = 0
)

data class SocialUserEntry(
    val userId: String,
    val nickname: String,
    val avatarEmoji: String,
    val mutual: Boolean,
    val followedAt: String = ""
)

/** 他人主页只暴露公开资料，不包含任何饮食数据。 */
data class PublicProfile(
    val userId: String,
    val nickname: String,
    val avatarEmoji: String,
    val publicId: String,
    val isSelf: Boolean,
    val isFriend: Boolean,
    val isFollowing: Boolean = false,
    val isFollowedBy: Boolean = false,
    val isMutual: Boolean = false,
    val hasSentFriendRequest: Boolean = false,
    val hasIncomingFriendRequest: Boolean = false,
    val level: Int = 1
)

data class SharedFood(
    val name: String,
    val emoji: String = "",
    val grams: Int = 0,
    val calories: Int = 0,
    val meal: Int = 0
)

data class FriendMessage(
    val id: Long,
    val senderId: String,
    val content: String,
    val createdAt: String,
    val messageType: String = "text",
    val calories: Int? = null,
    val entryDate: String? = null,
    val items: List<SharedFood> = emptyList()
)

data class ChatData(
    val friend: FriendEntry,
    val messages: List<FriendMessage>,
    val currentUserId: String,
    val relationType: String? = null,
    val relationSince: String? = null
)

/** 与某位好友的亲密关系绑定，since 为绑定时间（ISO），用于 30 天冷却计算。 */
data class RelationBinding(
    val type: String,
    val since: String? = null
)

data class GroupConversation(
    val groupId: String,
    val name: String,
    val ownerId: String,
    val calorieGoal: Int,
    val createdAt: String,
    val lastMessage: String? = null,
    val lastMessageAt: String? = null,
    /** 群火花：连续有群消息的天数，服务端按 Asia/Shanghai 计算。 */
    val sparkDays: Int = 0
)

data class GroupMessage(
    val id: Long,
    val senderId: String,
    val content: String,
    val messageType: String,
    val calories: Int?,
    val entryDate: String?,
    val createdAt: String,
    val items: List<SharedFood> = emptyList()
)

data class GroupMemberTotal(
    val userId: String,
    val nickname: String,
    val avatarEmoji: String,
    val totalCalories: Int
)

/** 待处理的群聊邀请：被邀请人在消息页自行决定是否入群。 */
data class GroupInvitation(
    val id: Long,
    val groupId: String,
    val groupName: String,
    val inviterId: String,
    val inviterNickname: String,
    val inviterAvatarEmoji: String,
    val memberCount: Int,
    val createdAt: String
)

/** 群聊主页的群基础信息：名称、群主、公告与成员数。 */
data class GroupInfo(
    val groupId: String,
    val name: String,
    val ownerId: String,
    val announcement: String,
    val memberCount: Int,
    /** 是否允许他人通过群号搜索并加入；关闭后仅能被邀请。 */
    val searchable: Boolean = true
)

/** 群聊主页成员条目，群主会被排在最前。 */
data class GroupInfoMember(
    val userId: String,
    val nickname: String,
    val avatarEmoji: String,
    val isOwner: Boolean
)

/** 云端饮食记录，拉取后按 clientRef 合并回本地数据库。 */
data class RemoteMealEntry(
    val clientRef: String,
    val date: String,
    val mealType: Int,
    val foodName: String,
    val emoji: String,
    val grams: Int,
    val calories: Int,
    val recordedAtMillis: Long
)

data class GroupChatData(
    val group: GroupConversation,
    val messages: List<GroupMessage>,
    val members: List<GroupMemberTotal>,
    val currentUserId: String
)

data class ActivityPrivacyInfo(
    val publicId: String,
    val activityVisibility: String = "friends"
)

data class FriendsOverview(
    val myUserId: String,
    val friends: List<FriendEntry>,
    val followNotices: List<FollowNoticeEntry> = emptyList(),
    val requests: List<FriendRequestEntry> = emptyList()
)

data class NicknameChangeStatus(
    val used: Int = 0,
    val remaining: Int = 3,
    val nextAvailableAt: String? = null
)

data class NicknameUpdateResult(
    val nickname: String,
    val remaining: Int
)

object AuthInputValidator {
    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_NICKNAME_LENGTH = 20
    private val emailPattern = Pattern.compile(
        "^[A-Z0-9_%+-]+(?:\\.[A-Z0-9_%+-]+)*@" +
            "(?:[A-Z0-9](?:[A-Z0-9-]*[A-Z0-9])?\\.)+[A-Z]{2,}$",
        Pattern.CASE_INSENSITIVE
    )

    fun isValid(email: String, password: String): Boolean =
        emailPattern.matcher(email.trim()).matches() &&
            password.length >= MIN_PASSWORD_LENGTH

    fun isValidEmail(email: String): Boolean =
        emailPattern.matcher(email.trim()).matches()

    /** Supabase 控制台可配置 OTP 长度为 6–10 位，这里按范围校验。 */
    fun isValidOtpCode(code: String): Boolean =
        code.trim().matches(Regex("^[0-9]{6,10}$"))

    /** 邀请码为选填；填写时必须为 8 位数字。 */
    fun isValidInvitationCode(code: String): Boolean =
        code.trim().matches(Regex("^[0-9]{8}$"))

    /** 与服务端 update_nickname 保持一致：去空白后 1-20 个字符，且不含控制字符。 */
    fun isValidNickname(nickname: String): Boolean {
        val cleaned = nickname.trim()
        val length = cleaned.codePointCount(0, cleaned.length)
        return length in 1..MAX_NICKNAME_LENGTH && cleaned.none { it.isISOControl() }
    }
}

object UserIdValidator {
    fun isValid(value: String): Boolean = runCatching {
        UUID.fromString(value.trim())
    }.isSuccess
}

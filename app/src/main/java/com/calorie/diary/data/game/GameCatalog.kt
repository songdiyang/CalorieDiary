package com.calorie.diary.data.game

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.calorie.diary.R

const val DAILY_PHOTO_TASK_EXP = 30

data class GameInventoryItem(
    val itemId: String,
    val quantity: Int
)

data class ShopItem(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val descriptionRes: Int,
    @DrawableRes val iconRes: Int,
    val price: Int
)

/** 新手任务完成状态与奖励，全部来自服务端配置。 */
data class NewbieTaskState(
    val taskId: String,
    val done: Boolean = false,
    val coinReward: Int = 0,
    val expReward: Int = 0
)

data class NewbieTaskReward(
    val taskId: String,
    val coins: Int,
    val exp: Int
)

/** sync_newbie_tasks 的发放结果：一次调用可能同时完成多个任务。 */
data class NewbieTaskSyncResult(
    val status: String = "nothing",
    val awarded: List<NewbieTaskReward> = emptyList(),
    val coinsAwarded: Int = 0,
    val expAwarded: Int = 0,
    val level: Int = 1,
    val leveledUp: Boolean = false
)

/** 新手任务完成后要引导去的地方。 */
enum class NewbieTaskAction { EDIT_NICKNAME, ADD_FRIEND, RECORD_FOOD }

data class NewbieTaskDefinition(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val descriptionRes: Int,
    @DrawableRes val iconRes: Int,
    val action: NewbieTaskAction
)

object GameCatalog {
    const val NEWBIE_NICKNAME = "newbie_nickname"
    const val NEWBIE_FIRST_FRIEND = "newbie_first_friend"
    const val NEWBIE_FIRST_FOOD = "newbie_first_food"

    /** 新手任务展示顺序与入口；完成判定由服务端负责。 */
    val newbieTasks = listOf(
        NewbieTaskDefinition(
            NEWBIE_NICKNAME,
            R.string.newbie_task_nickname,
            R.string.newbie_task_nickname_desc,
            R.drawable.icon_pencil,
            NewbieTaskAction.EDIT_NICKNAME
        ),
        NewbieTaskDefinition(
            NEWBIE_FIRST_FRIEND,
            R.string.newbie_task_friend,
            R.string.newbie_task_friend_desc,
            R.drawable.ic_friends,
            NewbieTaskAction.ADD_FRIEND
        ),
        NewbieTaskDefinition(
            NEWBIE_FIRST_FOOD,
            R.string.newbie_task_food,
            R.string.newbie_task_food_desc,
            R.drawable.ic_food_apple,
            NewbieTaskAction.RECORD_FOOD
        )
    )

    val items = listOf(
        ShopItem(
            "star_sticker", R.string.item_star_name, R.string.item_star_description,
            R.drawable.deco_star, 40
        ),
        ShopItem(
            "goal_badge", R.string.item_goal_badge_name, R.string.item_goal_badge_description,
            R.drawable.badge_goal, 80
        ),
        ShopItem(
            "heart_decoration", R.string.item_heart_name, R.string.item_heart_description,
            R.drawable.deco_heart, 60
        ),
        ShopItem(
            "celebration_mascot", R.string.item_celebration_name, R.string.item_celebration_description,
            R.drawable.mascot_celebrate, 100
        )
    )

    fun item(id: String): ShopItem? = items.firstOrNull { it.id == id }
}

enum class CheckInResult { SUCCESS, ALREADY_CLAIMED }
enum class PurchaseResult { SUCCESS, INSUFFICIENT_COINS, UNKNOWN_ITEM }

/** 每日签到发放结果：金额来自服务端配置表，连续签到每满 N 天翻倍。 */
data class CheckInReward(
    val status: String,
    val coins: Int = 0,
    val exp: Int = 0,
    val streak: Int = 0,
    val doubled: Boolean = false,
    val level: Int = 1,
    val leveledUp: Boolean = false
) {
    val success: Boolean get() = status == "success"
}

/** 每日拍照记录任务的发放结果（服务端 complete_photo_task）。 */
data class PhotoTaskResult(
    val status: String,
    val exp: Int = 0,
    val level: Int = 1,
    val leveledUp: Boolean = false
) {
    val success: Boolean get() = status == "success"
}

object GameRules {
    /** 签到不再依赖饮食记录：只要当天未签过即可。 */
    fun checkInEligibility(lastCheckInDate: String, today: String): CheckInResult =
        if (lastCheckInDate == today) CheckInResult.ALREADY_CLAIMED else CheckInResult.SUCCESS

    /** 累计升到 [level] 级所需经验，与服务端 game_level_floor 保持一致。 */
    fun levelFloor(level: Int): Int {
        val safe = level.coerceAtLeast(1)
        return 50 * safe * (safe - 1)
    }

    /** 当前等级内的经验进度（0 起步）。 */
    fun levelProgress(exp: Int, level: Int): Int = (exp - levelFloor(level)).coerceAtLeast(0)

    /** 升到下一级需要的经验总量。 */
    fun levelSpan(level: Int): Int = levelFloor(level + 1) - levelFloor(level)
}

package com.calorie.diary.ui.navigation

object Routes {
    const val HOME = "home"
    const val CALENDAR = "calendar"
    const val TOOLS = "tools"
    const val FRIENDS = "friends"
    const val PROFILE = "profile"
    const val TASKS = "tasks"
    const val BACKPACK = "backpack"
    const val SHOP = "shop"
    const val PERSONAL_PAGE = "personal_page?userId={userId}"
    fun personalPage(userId: String? = null): String =
        "personal_page?userId=${userId.orEmpty()}"
    const val SOCIAL_LIST = "social_list?userId={userId}&tab={tab}"
    fun socialList(userId: String?, tab: String): String =
        "social_list?userId=${userId.orEmpty()}&tab=$tab"
    const val WATER = "water"
    const val FASTING = "fasting"
    const val BMI = "bmi"
    const val RECOGNITION = "recognition?captureUri={captureUri}"
    fun recognition(captureUri: String? = null): String =
        "recognition?captureUri=${captureUri ?: ""}"
    const val SETTINGS = "settings"
    const val EXERCISE = "exercise"
    const val TEXTLOG = "textlog"
    const val GROUP = "group"
    const val ACCOUNT = "account?addAccount={addAccount}"
    fun account(addAccount: Boolean = false): String = "account?addAccount=$addAccount"
    const val REGISTER = "register"
    const val FRIEND_CHAT = "friend_chat/{friendId}"
    fun friendChat(friendId: String): String = "friend_chat/$friendId"
    const val FRIEND_SEARCH = "friend_search"
    const val GROUP_CHAT = "group_chat/{groupId}"
    fun groupChat(groupId: String): String = "group_chat/$groupId"
    const val GROUP_INFO = "group_info/{groupId}"
    fun groupInfo(groupId: String): String = "group_info/$groupId"
    const val GROUP_ANNOUNCEMENT = "group_announcement/{groupId}"
    fun groupAnnouncement(groupId: String): String = "group_announcement/$groupId"

    const val FOOD_SEARCH = "food_search/{mealType}/{date}"
    fun foodSearch(mealType: Int, date: String): String = "food_search/$mealType/$date"

    fun isAuthenticationRoute(route: String?): Boolean = route == ACCOUNT || route == REGISTER
}

enum class BottomTab(val route: String, val labelRes: Int, val iconRes: Int) {
    HOME(Routes.HOME, com.calorie.diary.R.string.tab_home, com.calorie.diary.R.drawable.nav_home),
    CALENDAR(Routes.CALENDAR, com.calorie.diary.R.string.tab_calendar, com.calorie.diary.R.drawable.nav_calendar),
    FRIENDS(Routes.FRIENDS, com.calorie.diary.R.string.tab_friends, com.calorie.diary.R.drawable.ic_friends),
    PROFILE(Routes.PROFILE, com.calorie.diary.R.string.tab_profile, com.calorie.diary.R.drawable.nav_profile)
}

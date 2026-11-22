package com.calorie.diary.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calorie.diary.R
import com.calorie.diary.data.AppRefreshBus
import com.calorie.diary.data.DateUtils
import com.calorie.diary.data.game.GameInventoryItem
import com.calorie.diary.data.supabase.DefaultAvatarCatalog
import com.calorie.diary.data.supabase.SocialSummary
import com.calorie.diary.data.supabase.SupabaseAuthRepository
import com.calorie.diary.ui.components.DefaultAvatar
import com.calorie.diary.ui.components.SoftCard
import com.calorie.diary.ui.home.appVm
import com.calorie.diary.ui.navigation.Routes
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Hairline
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.LavenderSoft
import com.calorie.diary.ui.theme.NumberFont
import com.calorie.diary.ui.theme.OrangeDeep
import com.calorie.diary.ui.theme.ScreenBg
import com.calorie.diary.ui.theme.YellowSoft
import com.calorie.diary.viewmodel.AuthViewModel
import com.calorie.diary.viewmodel.GameUiState
import com.calorie.diary.viewmodel.ActivityPrivacyViewModel
import com.calorie.diary.viewmodel.SocialSummaryViewModel
import com.calorie.diary.viewmodel.UserStats
import java.util.Locale

internal data class ProfileActions(
    val onSettings: () -> Unit = {},
    val onAccount: () -> Unit = {},
    val onOpenTasks: () -> Unit = {}
)

@Composable
fun ProfileScreen(
    onAvatarClick: () -> Unit,
    onSettings: () -> Unit,
    onAccount: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenSocialList: (String) -> Unit
) {
    val vm = appVm()
    val context = LocalContext.current
    val authRepo = remember { SupabaseAuthRepository.get(context) }
    val account by authRepo.account.collectAsState(initial = null)
    val privacyVm: ActivityPrivacyViewModel = viewModel()
    val privacyState by privacyVm.uiState.collectAsState()
    val authVm: AuthViewModel = viewModel()
    val authState by authVm.uiState.collectAsState()
    val socialVm: SocialSummaryViewModel = viewModel()
    val socialSummary by socialVm.summary.collectAsState()
    LaunchedEffect(account?.userId) {
        privacyVm.refresh()
        authVm.refreshProfile()
        socialVm.load(account?.userId)
    }
    LaunchedEffect(Unit) {
        vm.refreshGameState()
        AppRefreshBus.events.collect { route ->
            if (route == Routes.PROFILE || route == Routes.FRIENDS) {
                vm.refreshGameState()
                authVm.refreshProfile()
                socialVm.refresh(account?.userId)
            }
        }
    }
    val profile by vm.profile.collectAsState()
    val stats by vm.userStats.collectAsState()
    val identity by vm.identity.collectAsState()
    val game by vm.gameState.collectAsState()
    val fallbackName = stringResource(R.string.profile_greeting)
    val avatarId = DefaultAvatarCatalog.resolve(identity?.avatarEmoji)
        ?: DefaultAvatarCatalog.resolve(authState.cloudProfile?.avatarEmoji)
    val displayNickname = if (account == null) {
        stringResource(R.string.profile_not_logged_in)
    } else {
        authState.cloudProfile?.nickname?.takeIf { it.isNotBlank() }
            ?: identity?.nickname?.takeIf { it.isNotBlank() && it != "我" && it != "你好呀" }
            ?: account?.email?.substringBefore('@')?.takeIf { it.isNotBlank() }
            ?: fallbackName
    }

    ProfileContent(
        onAvatarClick = onAvatarClick,
        nickname = displayNickname,
        loggedIn = account != null,
        userId = privacyState.publicId,
        avatarUserId = account?.userId.orEmpty(),
        avatarId = avatarId,
        weightKg = profile?.weightKg ?: 60f,
        stats = stats,
        game = game,
        socialSummary = socialSummary,
        onOpenSocialList = onOpenSocialList,
        actions = ProfileActions(
            onSettings = onSettings,
            onAccount = onAccount,
            onOpenTasks = onOpenTasks
        )
    )
}

@Composable
private fun ProfileContent(
    onAvatarClick: () -> Unit,
    nickname: String,
    loggedIn: Boolean,
    userId: String,
    avatarUserId: String,
    weightKg: Float,
    stats: UserStats,
    game: GameUiState,
    socialSummary: SocialSummary?,
    onOpenSocialList: (String) -> Unit,
    actions: ProfileActions,
    avatarId: String? = null
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        ProfileHeaderCard(
            nickname = nickname,
            loggedIn = loggedIn,
            userId = userId,
            avatarUserId = avatarUserId,
            level = game.level,
            weightKg = weightKg,
            stats = stats,
            socialSummary = socialSummary,
            onOpenSocialList = onOpenSocialList,
            onSettings = actions.onSettings,
            onAccount = actions.onAccount,
            onAvatarClick = onAvatarClick,
            avatarId = avatarId
        )
        Spacer(Modifier.height(10.dp))
        TasksEntryCard(
            checkedIn = game.lastCheckInDate == game.serverDate.ifBlank { DateUtils.todayString() },
            coinReward = game.checkInCoinReward,
            expReward = game.checkInExpReward,
            onClick = actions.onOpenTasks
        )
        Spacer(Modifier.height(20.dp))
    }
}

/** 任务入口：签到等任务收进独立任务页，副标题带服务端下发的奖励数量。 */
@Composable
private fun TasksEntryCard(
    checkedIn: Boolean,
    coinReward: Int,
    expReward: Int,
    onClick: () -> Unit
) {
    SoftCard(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(LavenderSoft),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.nav_calendar),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.tasks_title),
                    fontFamily = CuteFont, fontSize = 15.sp, color = Ink
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(
                        R.string.tasks_entry_reward,
                        stringResource(
                            if (checkedIn) R.string.tasks_entry_done
                            else R.string.tasks_entry_pending
                        ),
                        coinReward,
                        expReward
                    ),
                    fontFamily = CuteFont, fontSize = 11.sp, color = InkLight
                )
            }
            Image(
                painter = painterResource(R.drawable.icon_arrow),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * 头部、关注统计、身体数据合为一张卡片，减少留白提高信息密度。
 */
@Composable
private fun ProfileHeaderCard(
    nickname: String,
    loggedIn: Boolean,
    userId: String,
    avatarUserId: String,
    level: Int,
    weightKg: Float,
    stats: UserStats,
    socialSummary: SocialSummary?,
    onOpenSocialList: (String) -> Unit,
    onSettings: () -> Unit,
    onAccount: () -> Unit,
    onAvatarClick: () -> Unit,
    avatarId: String?
) {
    val clipboard = LocalClipboardManager.current
    SoftCard(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !loggedIn,
                onClick = onAccount
            ),
        contentPadding = PaddingValues(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(58.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onAvatarClick
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    DefaultAvatar(
                        userId = avatarUserId,
                        contentDescription = stringResource(R.string.profile_choose_avatar),
                        modifier = Modifier.fillMaxSize(),
                        avatarId = avatarId
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            nickname,
                            fontFamily = CuteFont, fontSize = 20.sp, color = Ink,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (loggedIn) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.level_badge, level),
                                fontFamily = NumberFont, fontSize = 10.sp, color = Ink,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(LavenderSoft)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (loggedIn) userId.ifBlank { "--------" }
                        else stringResource(R.string.profile_login_action),
                        fontFamily = if (loggedIn) NumberFont else CuteFont,
                        fontSize = 11.sp,
                        color = if (loggedIn) InkLight else OrangeDeep,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(CardWhite)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = !loggedIn || userId.isNotBlank(),
                                onClick = {
                                    if (loggedIn) {
                                        clipboard.setText(AnnotatedString(userId))
                                    } else {
                                        onAccount()
                                    }
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                SettingsButton(onSettings)
            }
            if (loggedIn) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MiniSocialStat(
                        stringResource(R.string.social_following), socialSummary?.following
                    ) { onOpenSocialList("following") }
                    Spacer(Modifier.width(16.dp))
                    MiniSocialStat(
                        stringResource(R.string.social_followers), socialSummary?.followers
                    ) { onOpenSocialList("followers") }
                    Spacer(Modifier.width(16.dp))
                    MiniSocialStat(
                        stringResource(R.string.social_mutual), socialSummary?.mutual
                    ) { onOpenSocialList("following") }
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                CompactStat(
                    String.format(Locale.getDefault(), "%.1f", weightKg), "kg",
                    R.string.profile_current_weight, Modifier.weight(1f)
                )
                CompactStat(
                    stats.activeDays.toString(), stringResource(R.string.profile_unit_days),
                    R.string.profile_stat_days, Modifier.weight(1f)
                )
                CompactStat(
                    stats.streak.toString(), stringResource(R.string.profile_unit_days),
                    R.string.profile_stat_streak, Modifier.weight(1f)
                )
                CompactStat(
                    stats.entryCount.toString(), "",
                    R.string.profile_stat_entries, Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MiniSocialStat(label: String, value: Int?, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(value?.toString() ?: "-", fontFamily = NumberFont, fontSize = 14.sp, color = Ink)
        Spacer(Modifier.width(3.dp))
        Text(label, fontFamily = CuteFont, fontSize = 10.sp, color = InkLight, maxLines = 1)
    }
}

@Composable
private fun CompactStat(value: String, unit: String, labelRes: Int, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontFamily = NumberFont, fontSize = 18.sp, color = Ink)
            if (unit.isNotEmpty()) {
                Text(
                    " $unit",
                    fontFamily = CuteFont, fontSize = 9.sp, color = Ink,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(labelRes),
            fontFamily = CuteFont, fontSize = 10.sp, color = InkLight,
            maxLines = 1, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SettingsButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(CardWhite)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_settings),
            contentDescription = stringResource(R.string.profile_settings),
            modifier = Modifier.size(20.dp),
            colorFilter = ColorFilter.tint(Ink)
        )
    }
}

@Composable
internal fun ProfilePreviewContent() {
    ProfileContent(
        onAvatarClick = {},
        nickname = "不吃胡萝卜",
        loggedIn = true,
        userId = "04462317",
        avatarUserId = "04462317",
        weightKg = 62.5f,
        stats = UserStats(activeDays = 28, entryCount = 48, totalCalories = 23600, streak = 5),
        game = GameUiState(
            coins = 120,
            exp = 130,
            level = 2,
            levelExp = 30,
            levelNext = 200,
            dailyTaskDone = true,
            inventory = listOf(GameInventoryItem("star_sticker", quantity = 2)),
            todayFoodCount = 1,
            loading = false
        ),
        socialSummary = SocialSummary(following = 12, followers = 9, mutual = 7),
        onOpenSocialList = {},
        actions = ProfileActions()
    )
}

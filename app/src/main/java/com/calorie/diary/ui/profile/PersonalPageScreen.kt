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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calorie.diary.R
import com.calorie.diary.data.AppRefreshBus
import com.calorie.diary.data.DateUtils
import com.calorie.diary.data.supabase.ActivityDay
import com.calorie.diary.data.supabase.ActivityProfile
import com.calorie.diary.data.supabase.DefaultAvatarCatalog
import com.calorie.diary.data.supabase.SocialSummary
import com.calorie.diary.data.supabase.SupabaseAuthRepository
import com.calorie.diary.ui.components.AvatarPickerDialog
import com.calorie.diary.ui.components.CuteButton
import com.calorie.diary.ui.components.DefaultAvatar
import com.calorie.diary.ui.components.NicknameEditDialog
import com.calorie.diary.ui.components.SoftCard
import com.calorie.diary.ui.home.appVm
import com.calorie.diary.ui.navigation.Routes
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Hairline
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.LavenderSoft
import com.calorie.diary.ui.theme.MintSoft
import com.calorie.diary.ui.theme.NumberFont
import com.calorie.diary.ui.theme.Orange
import com.calorie.diary.ui.theme.OrangeDeep
import com.calorie.diary.ui.theme.ScreenBg
import com.calorie.diary.ui.theme.YellowSoft
import com.calorie.diary.viewmodel.AuthViewModel
import com.calorie.diary.viewmodel.PersonalPageUiState
import com.calorie.diary.viewmodel.PersonalPageViewModel

@Composable
fun PersonalPageScreen(
    userId: String?,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onOpenSocialList: (String) -> Unit
) {
    val target = userId?.takeIf { it.isNotBlank() }
    val vm: PersonalPageViewModel = viewModel(key = "personal-${target ?: "self"}")
    val state by vm.uiState.collectAsState()
    val localDays by vm.localDays.collectAsState()
    val context = LocalContext.current
    val authRepo = remember { SupabaseAuthRepository.get(context) }
    val account by authRepo.account.collectAsState(initial = null)
    val appVm = appVm()
    val identity by appVm.identity.collectAsState()
    var showAvatarPicker by remember { mutableStateOf(false) }
    val authVm: AuthViewModel = viewModel()
    val nicknameState by authVm.nicknameState.collectAsState()
    var showNicknameEditor by remember { mutableStateOf(false) }
    // 自己的头像与“我的”页取同一来源，未选择时也按同一账号做默认头像兜底。
    val selfAvatarUserId = account?.userId.orEmpty()
    val selfAvatarId = DefaultAvatarCatalog.resolve(identity?.avatarEmoji)

    LaunchedEffect(target, localDays) { vm.load(target) }
    LaunchedEffect(Unit) {
        AppRefreshBus.events.collect { route ->
            if (route == Routes.PERSONAL_PAGE) vm.load(target)
        }
    }
    PersonalPageContent(
        state = state,
        onBack = onBack,
        onLogin = onLogin,
        onOpenSocialList = onOpenSocialList,
        selfAvatarUserId = selfAvatarUserId,
        selfAvatarId = selfAvatarId,
        onAvatarClick = { showAvatarPicker = true },
        onNicknameClick = {
            authVm.loadNicknameStatus()
            showNicknameEditor = true
        }
    )
    if (showAvatarPicker) {
        AvatarPickerDialog(
            userId = selfAvatarUserId,
            selectedId = selfAvatarId,
            onDismiss = { showAvatarPicker = false },
            onSelect = {
                appVm.selectAvatar(it)
                showAvatarPicker = false
            }
        )
    }

    if (showNicknameEditor) {
        NicknameEditDialog(
            currentNickname = state.profile?.nickname.orEmpty(),
            status = nicknameState.status,
            busy = nicknameState.busy,
            errorRes = nicknameState.errorRes,
            onDismiss = {
                showNicknameEditor = false
                authVm.clearNicknameError()
            },
            onSubmit = {
                authVm.changeNickname(it) {
                    showNicknameEditor = false
                    // 个人主页的昵称来自服务端活动资料，改名后立即重拉。
                    vm.load(target)
                }
            },
            onClearError = authVm::clearNicknameError
        )
    }
}

@Composable
internal fun PersonalPageContent(
    state: PersonalPageUiState,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onOpenSocialList: (String) -> Unit = {},
    selfAvatarUserId: String = "",
    selfAvatarId: String? = null,
    onAvatarClick: () -> Unit = {},
    onNicknameClick: () -> Unit = {}
) {
    Column(Modifier.fillMaxSize().background(ScreenBg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.icon_back),
                contentDescription = stringResource(R.string.common_back),
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(CardWhite)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBack
                    )
                    .padding(7.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.personal_page_title),
                fontFamily = CuteFont, fontSize = 22.sp, color = Ink
            )
        }
        when {
            state.loading && state.profile == null && state.publicProfile == null -> Box(
                Modifier.fillMaxSize(), contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Orange)
            }
            state.requiresLogin -> CenterMessage(stringResource(R.string.activity_login_required), onLogin)
            !state.isOwnPage -> when {
                state.status == "deleted" ->
                    CenterMessage(stringResource(R.string.social_user_deleted))
                state.status == "not_found" ->
                    CenterMessage(stringResource(R.string.social_user_not_found))
                state.publicProfile != null ->
                    PublicProfileContent(state, onOpenSocialList)
                state.status != "ok" -> CenterMessage(stringResource(R.string.activity_error))
            }
            state.status == "private" -> CenterMessage(stringResource(R.string.activity_private_title))
            state.status != "ok" -> CenterMessage(stringResource(R.string.activity_error))
            state.profile != null -> ActivityContent(
                state, onOpenSocialList, selfAvatarUserId, selfAvatarId, onAvatarClick,
                onNicknameClick
            )
        }
    }
}

@Composable
private fun ActivityContent(
    state: PersonalPageUiState,
    onOpenSocialList: (String) -> Unit,
    selfAvatarUserId: String,
    selfAvatarId: String?,
    onAvatarClick: () -> Unit,
    onNicknameClick: () -> Unit
) {
    val profile = state.profile ?: return
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        ActivityHero(profile, state.localOnly, selfAvatarUserId, selfAvatarId, onAvatarClick, onNicknameClick)
        Spacer(Modifier.height(8.dp))
        OverviewCard(state.summary, profile, onOpenSocialList)
        if (state.failed && state.localOnly) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.activity_sync_failed),
                fontFamily = CuteFont, fontSize = 11.sp, color = OrangeDeep,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(8.dp))
        TrendCard(profile)
        Spacer(Modifier.height(18.dp))
    }
}

/** 他人主页：只展示公开资料与关注/粉丝/互关，不展示任何饮食数据。 */
@Composable
private fun PublicProfileContent(state: PersonalPageUiState, onOpenSocialList: (String) -> Unit) {
    val profile = state.publicProfile ?: return
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        SoftCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    DefaultAvatar(
                        userId = profile.userId,
                        modifier = Modifier.fillMaxSize(),
                        avatarId = DefaultAvatarCatalog.resolve(profile.avatarEmoji)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            profile.nickname,
                            fontFamily = CuteFont, fontSize = 19.sp, color = Ink,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.level_badge, profile.level),
                            fontFamily = NumberFont, fontSize = 10.sp, color = Ink,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(LavenderSoft)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (profile.publicId.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            stringResource(R.string.profile_user_id, profile.publicId),
                            fontFamily = CuteFont, fontSize = 11.sp, color = InkLight
                        )
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        stringResource(
                            if (profile.isFriend) R.string.social_list_mutual
                            else R.string.social_not_friend
                        ),
                        fontFamily = CuteFont, fontSize = 10.sp, color = InkLight,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (profile.isFriend) MintSoft else LavenderSoft)
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        SoftCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                OverviewItem(
                    label = stringResource(R.string.social_following),
                    value = state.summary?.following?.toString() ?: "-",
                    modifier = Modifier.weight(1f)
                ) { onOpenSocialList("following") }
                OverviewItem(
                    label = stringResource(R.string.social_followers),
                    value = state.summary?.followers?.toString() ?: "-",
                    modifier = Modifier.weight(1f)
                ) { onOpenSocialList("followers") }
                OverviewItem(
                    label = stringResource(R.string.social_mutual),
                    value = state.summary?.mutual?.toString() ?: "-",
                    modifier = Modifier.weight(1f)
                ) { onOpenSocialList("following") }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ActivityHero(
    profile: ActivityProfile,
    localOnly: Boolean,
    avatarUserId: String,
    avatarId: String?,
    onAvatarClick: () -> Unit,
    onNicknameClick: () -> Unit
) {
    SoftCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(72.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onAvatarClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                DefaultAvatar(
                    // 与“我的”页保持同一兜底规则（都按账号 id 取默认头像），未选择头像时两边也一致。
                    userId = avatarUserId,
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = stringResource(R.string.profile_choose_avatar),
                    avatarId = avatarId ?: DefaultAvatarCatalog.resolve(profile.avatarEmoji)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        profile.nickname,
                        fontFamily = CuteFont, fontSize = 19.sp, color = Ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(50))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onNicknameClick
                            )
                            .padding(vertical = 2.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.settings_nickname_change),
                        fontFamily = CuteFont, fontSize = 10.sp, color = OrangeDeep,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onNicknameClick
                            )
                            .background(YellowSoft)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (profile.publicId.isNotBlank()) {
                        Text(
                            stringResource(R.string.profile_user_id, profile.publicId),
                            fontFamily = CuteFont, fontSize = 11.sp, color = InkLight
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        if (localOnly) {
                            stringResource(R.string.activity_local_only)
                        } else {
                            stringResource(visibilityRes(profile.visibility))
                        },
                        fontFamily = CuteFont, fontSize = 10.sp,
                        color = if (localOnly) OrangeDeep else InkLight,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (localOnly) YellowSoft else MintSoft)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.activity_total_calories, profile.totalCalories),
                    fontFamily = CuteFont, fontSize = 12.sp, color = OrangeDeep
                )
            }
        }
    }
}

/** 社交与饮食数据合并为一张双行数据卡，上排可点进关注/粉丝列表。 */
@Composable
private fun OverviewCard(
    summary: SocialSummary?,
    profile: ActivityProfile,
    onOpenSocialList: (String) -> Unit
) {
    SoftCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 12.dp)) {
        Column {
            Row(Modifier.fillMaxWidth()) {
                OverviewItem(
                    label = stringResource(R.string.social_following),
                    value = summary?.following?.toString() ?: "-",
                    modifier = Modifier.weight(1f)
                ) { onOpenSocialList("following") }
                OverviewItem(
                    label = stringResource(R.string.social_followers),
                    value = summary?.followers?.toString() ?: "-",
                    modifier = Modifier.weight(1f)
                ) { onOpenSocialList("followers") }
                OverviewItem(
                    label = stringResource(R.string.social_mutual),
                    value = summary?.mutual?.toString() ?: "-",
                    modifier = Modifier.weight(1f)
                ) { onOpenSocialList("following") }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 8.dp)
                    .height(1.dp)
                    .background(Hairline)
            )
            Row(Modifier.fillMaxWidth()) {
                OverviewItem(
                    label = stringResource(R.string.profile_stat_streak),
                    value = profile.streakDays.toString(),
                    modifier = Modifier.weight(1f)
                )
                OverviewItem(
                    label = stringResource(R.string.profile_stat_days),
                    value = profile.activeDays.toString(),
                    modifier = Modifier.weight(1f)
                )
                OverviewItem(
                    label = stringResource(R.string.profile_stat_entries),
                    value = profile.totalEntries.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun OverviewItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = if (onClick != null) {
            modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
        } else {
            modifier.padding(vertical = 2.dp)
        },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontFamily = NumberFont, fontSize = 21.sp, color = Ink)
        Spacer(Modifier.height(2.dp))
        Text(label, fontFamily = CuteFont, fontSize = 10.sp, color = InkLight)
    }
}

/** 今日进度、近 7 天柱状图与最近动态合并为一张卡，减少卡片切换产生的空白。 */
@Composable
private fun TrendCard(profile: ActivityProfile) {
    val today = DateUtils.todayString()
    val calories = profile.days.firstOrNull { it.date == today }?.totalCalories ?: 0
    val progress = (calories.toFloat() / profile.calorieGoal.coerceAtLeast(1)).coerceIn(0f, 1f)
    val dates = (6 downTo 0).map { DateUtils.addDays(today, -it) }
    val byDate = profile.days.associateBy { it.date }
    val values = dates.map { byDate[it]?.totalCalories ?: 0 }
    // 柱高按窗口内最大值缩放，避免目标值远大于实际摄入时整排柱子被压成短横线。
    val maxValue = values.maxOrNull()?.coerceAtLeast(1) ?: 1

    SoftCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.activity_today),
                    fontFamily = CuteFont, fontSize = 14.sp, color = Ink
                )
                if (calories >= profile.calorieGoal && profile.calorieGoal > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.activity_goal_reached),
                        fontFamily = CuteFont, fontSize = 10.sp, color = OrangeDeep,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(YellowSoft)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.activity_today_value, calories, profile.calorieGoal),
                    fontFamily = NumberFont, fontSize = 12.sp, color = OrangeDeep
                )
            }
            Spacer(Modifier.height(7.dp))
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                color = OrangeDeep,
                trackColor = YellowSoft
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.activity_week),
                fontFamily = CuteFont, fontSize = 13.sp, color = Ink
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                dates.forEachIndexed { index, date ->
                    val value = values[index]
                    val barHeight = if (value == 0) 4 else (62f * value / maxValue).toInt().coerceAtLeast(6)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.height(62.dp), contentAlignment = Alignment.BottomCenter) {
                            Box(
                                Modifier
                                    .width(20.dp)
                                    .height(barHeight.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(if (value > 0) OrangeDeep else YellowSoft)
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            date.substring(5),
                            fontFamily = NumberFont, fontSize = 9.sp, color = InkLight,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
            Spacer(Modifier.height(9.dp))
            Text(
                stringResource(R.string.activity_recent),
                fontFamily = CuteFont, fontSize = 13.sp, color = Ink
            )
            if (profile.days.isEmpty()) {
                Text(
                    stringResource(R.string.activity_no_records),
                    fontFamily = CuteFont, fontSize = 12.sp, color = InkLight,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                profile.days.take(5).forEach { day ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            day.date.substring(5),
                            fontFamily = NumberFont, fontSize = 11.sp, color = Ink
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            stringResource(R.string.activity_day_entries, day.entryCount),
                            fontFamily = CuteFont, fontSize = 10.sp, color = InkLight
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            stringResource(R.string.profile_details_calorie_value, day.totalCalories),
                            fontFamily = NumberFont, fontSize = 12.sp, color = OrangeDeep
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterMessage(message: String, onLogin: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Text(
            message,
            fontFamily = CuteFont, fontSize = 17.sp, color = InkLight,
            textAlign = TextAlign.Center
        )
        if (onLogin != null) {
            Spacer(Modifier.height(18.dp))
            CuteButton(text = stringResource(R.string.game_login_action), onClick = onLogin)
        }
    }
}

private fun visibilityRes(value: String): Int = when (value) {
    "public" -> R.string.activity_visibility_public
    "friends" -> R.string.activity_visibility_friends
    else -> R.string.activity_visibility_private
}

@Composable
internal fun PersonalPagePreviewContent() {
    val today = DateUtils.todayString()
    val days = listOf(
        ActivityDay(today, 1680, 4),
        ActivityDay(DateUtils.addDays(today, -1), 1920, 5),
        ActivityDay(DateUtils.addDays(today, -2), 1450, 3),
        ActivityDay(DateUtils.addDays(today, -3), 2010, 6),
        ActivityDay(DateUtils.addDays(today, -4), 1760, 4),
        ActivityDay(DateUtils.addDays(today, -5), 1300, 3),
        ActivityDay(DateUtils.addDays(today, -6), 1880, 5)
    )
    PersonalPageContent(
        state = PersonalPageUiState(
            loading = false,
            profile = ActivityProfile(
                userId = "self",
                nickname = "不吃胡萝卜",
                avatarEmoji = DefaultAvatarCatalog.marker(DefaultAvatarCatalog.FOX) ?: "",
                publicId = "04462317",
                visibility = "friends",
                isSelf = true,
                calorieGoal = 1800,
                activeDays = 28,
                streakDays = 7,
                totalEntries = 48,
                totalCalories = 23600,
                days = days
            )
        ),
        onBack = {},
        onLogin = {}
    )
}

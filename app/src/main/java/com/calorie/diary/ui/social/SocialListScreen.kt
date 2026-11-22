package com.calorie.diary.ui.social

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calorie.diary.R
import com.calorie.diary.data.supabase.SocialUserEntry
import com.calorie.diary.ui.components.CuteButton
import com.calorie.diary.ui.components.UserAvatar
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.LavenderSoft
import com.calorie.diary.ui.theme.MintSoft
import com.calorie.diary.ui.theme.Orange
import com.calorie.diary.ui.theme.OrangeDeep
import com.calorie.diary.ui.theme.PinkSoft
import com.calorie.diary.ui.theme.ScreenBg
import com.calorie.diary.viewmodel.SocialListTab
import com.calorie.diary.viewmodel.SocialListUiState
import com.calorie.diary.viewmodel.SocialListViewModel

@Composable
fun SocialListScreen(
    userId: String,
    tabValue: String,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onLogin: () -> Unit
) {
    val tab = SocialListTab.from(tabValue)
    val vm: SocialListViewModel = viewModel(key = "social-list-${userId}-${tab.routeValue}")
    val state by vm.uiState.collectAsState()

    LaunchedEffect(userId, tab) { vm.load(userId, tab) }
    SocialListContent(
        state = state,
        onBack = onBack,
        onOpenProfile = onOpenProfile,
        onLogin = onLogin,
        onFollow = vm::follow,
        onUnfollow = vm::unfollow
    )
}

@Composable
internal fun SocialListContent(
    state: SocialListUiState,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onLogin: () -> Unit,
    onFollow: (String) -> Unit = {},
    onUnfollow: (String) -> Unit = {}
) {
    var pendingUnfollow by remember { mutableStateOf<SocialUserEntry?>(null) }
    Column(Modifier.fillMaxSize().background(ScreenBg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.icon_back),
                contentDescription = stringResource(R.string.common_back),
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CardWhite)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBack
                    )
                    .padding(8.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(titleFor(state.tab), fontFamily = CuteFont, fontSize = 24.sp, color = Ink)
        }
        state.messageRes?.let { messageRes ->
            Box(Modifier.padding(horizontal = 16.dp)) {
                MessageBar(stringResource(messageRes), state.messageIsError)
            }
            Spacer(Modifier.height(8.dp))
        }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Orange)
            }
            state.requiresLogin -> CenterMessage(
                message = stringResource(R.string.activity_login_required),
                action = stringResource(R.string.game_login_action),
                onAction = onLogin
            )
            state.failed -> CenterMessage(stringResource(R.string.activity_error))
            state.users.isEmpty() -> CenterMessage(
                if (state.tab == SocialListTab.FOLLOWING) {
                    stringResource(R.string.social_list_empty_following)
                } else {
                    stringResource(R.string.social_list_empty_followers)
                }
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.users, key = { it.userId }) { user ->
                    SocialUserRow(
                        user = user,
                        tab = state.tab,
                        showAction = state.isOwnList,
                        actionEnabled = state.actionUserId == null,
                        actionBusy = state.actionUserId == user.userId,
                        onClick = { onOpenProfile(user.userId) },
                        onFollow = { onFollow(user.userId) },
                        onUnfollowClick = { pendingUnfollow = user }
                    )
                }
            }
        }
    }
    pendingUnfollow?.let { user ->
        UnfollowDialog(
            nickname = user.nickname,
            onConfirm = {
                pendingUnfollow = null
                onUnfollow(user.userId)
            },
            onDismiss = { pendingUnfollow = null }
        )
    }
}

@Composable
private fun SocialUserRow(
    user: SocialUserEntry,
    tab: SocialListTab,
    showAction: Boolean,
    actionEnabled: Boolean,
    actionBusy: Boolean,
    onClick: () -> Unit,
    onFollow: () -> Unit,
    onUnfollowClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            UserAvatar(
                userId = user.userId,
                avatarEmoji = user.avatarEmoji,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            user.nickname,
            fontFamily = CuteFont, fontSize = 16.sp, color = Ink,
            maxLines = 1, modifier = Modifier.weight(1f)
        )
        if (showAction) {
            val needsFollowBack = !user.mutual && tab == SocialListTab.FOLLOWERS
            SocialActionButton(
                label = stringResource(
                    when {
                        user.mutual -> R.string.social_list_mutual
                        needsFollowBack -> R.string.social_list_follow_back
                        else -> R.string.social_list_following_state
                    }
                ),
                emphasized = needsFollowBack,
                mutual = user.mutual,
                busy = actionBusy,
                enabled = actionEnabled,
                onClick = { if (needsFollowBack) onFollow() else onUnfollowClick() }
            )
        } else {
            StatusChip(user.mutual)
        }
    }
}

@Composable
private fun SocialActionButton(
    label: String,
    emphasized: Boolean,
    mutual: Boolean,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    emphasized -> Orange
                    mutual -> MintSoft
                    else -> CardWhite
                }
            )
            .clickable(
                enabled = enabled && !busy,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = if (emphasized) CardWhite else Orange,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                label,
                fontFamily = CuteFont, fontSize = 12.sp,
                color = if (emphasized) CardWhite else Ink
            )
        }
    }
}

@Composable
private fun StatusChip(mutual: Boolean) {
    Text(
        stringResource(
            if (mutual) R.string.social_list_mutual else R.string.social_list_one_way
        ),
        fontFamily = CuteFont, fontSize = 11.sp,
        color = InkLight,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (mutual) MintSoft else LavenderSoft)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
private fun UnfollowDialog(nickname: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardWhite,
        title = {
            Text(
                stringResource(R.string.social_list_unfollow_title),
                fontFamily = CuteFont, fontSize = 18.sp, color = Ink
            )
        },
        text = {
            Text(
                stringResource(R.string.social_list_unfollow_message, nickname),
                fontFamily = CuteFont, fontSize = 13.sp, color = InkLight
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.social_list_unfollow_title),
                    fontFamily = CuteFont, fontSize = 14.sp, color = OrangeDeep
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(R.string.common_cancel),
                    fontFamily = CuteFont, fontSize = 14.sp, color = InkLight
                )
            }
        }
    )
}

@Composable
private fun MessageBar(text: String, isError: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isError) PinkSoft else MintSoft)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(text, fontFamily = CuteFont, fontSize = 12.sp, color = Ink)
    }
}

@Composable
private fun CenterMessage(message: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            message,
            fontFamily = CuteFont, fontSize = 16.sp, color = InkLight,
            textAlign = TextAlign.Center
        )
        if (action != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            CuteButton(text = action, onClick = onAction)
        }
    }
}

@Composable
private fun titleFor(tab: SocialListTab): String = stringResource(
    if (tab == SocialListTab.FOLLOWING) {
        R.string.social_list_title_following
    } else {
        R.string.social_list_title_followers
    }
)

package com.calorie.diary.ui.friends

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calorie.diary.R
import com.calorie.diary.data.supabase.UserSearchResult
import com.calorie.diary.ui.components.EmptyState
import com.calorie.diary.ui.components.UserAvatar
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Hairline
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.MintSoft
import com.calorie.diary.ui.theme.OrangeDeep
import com.calorie.diary.ui.theme.PinkSoft
import com.calorie.diary.ui.theme.ScreenBg
import com.calorie.diary.viewmodel.FriendsUiState
import com.calorie.diary.viewmodel.FriendsViewModel

/** 独立的找人页面：按用户名模糊或公开 UID 搜索；先关注，互关后才能申请好友。 */
@Composable
fun FriendSearchScreen(onBack: () -> Unit, onOpenChat: (String) -> Unit) {
    val vm: FriendsViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    // 从聊天返回后保留搜索词。
    var query by rememberSaveable { mutableStateOf("") }

    FriendSearchContent(
        state = state,
        query = query,
        onQueryChange = {
            query = it
            vm.searchUsers(it)
        },
        onFollow = { vm.follow(it) },
        onApplyFriend = { vm.applyFriend(it) },
        onOpenChat = onOpenChat,
        onBack = onBack
    )
}

@Composable
internal fun FriendSearchContent(
    state: FriendsUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onFollow: (String) -> Unit,
    onApplyFriend: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    onBack: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Column(Modifier.fillMaxSize().background(ScreenBg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.icon_back),
                contentDescription = stringResource(R.string.common_back),
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBack
                    )
                    .padding(8.dp)
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                SearchField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = stringResource(R.string.friends_search_global),
                    focusRequester = focusRequester
                )
            }
        }

        state.messageRes?.let {
            Box(Modifier.padding(horizontal = 16.dp)) {
                SearchMessageBar(stringResource(it), state.messageIsError)
            }
            Spacer(Modifier.height(10.dp))
        }

        when {
            query.isBlank() -> Column(
                Modifier.fillMaxSize().padding(bottom = 80.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🔍", fontSize = 34.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.friends_search_hint),
                    fontFamily = CuteFont,
                    fontSize = 13.sp,
                    color = InkLight
                )
            }

            state.searching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp, color = OrangeDeep)
            }

            state.searchResults.isEmpty() -> EmptyState(stringResource(R.string.friends_no_results))

            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.searchResults, key = { it.userId }) { user ->
                    SearchResultRow(
                        user = user,
                        enabled = !state.busy,
                        onFollow = { onFollow(user.userId) },
                        onApplyFriend = { onApplyFriend(user.userId) },
                        onOpenChat = { onOpenChat(user.userId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    user: UserSearchResult,
    enabled: Boolean,
    onFollow: () -> Unit,
    onApplyFriend: () -> Unit,
    onOpenChat: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .border(1.dp, Hairline, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            userId = user.userId,
            avatarEmoji = user.avatarEmoji,
            modifier = Modifier.size(42.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                user.nickname,
                fontFamily = CuteFont,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = Ink
            )
            Text(
                stringResource(R.string.friends_id_value, user.publicId),
                fontFamily = CuteFont,
                fontSize = 10.sp,
                color = InkLight
            )
        }
        SearchResultAction(
            user = user,
            enabled = enabled,
            onFollow = onFollow,
            onApplyFriend = onApplyFriend,
            onOpenChat = onOpenChat
        )
    }
}

/** 按关注/互关/好友申请状态决定按钮：只有互关后才出现“申请好友”。 */
@Composable
private fun SearchResultAction(
    user: UserSearchResult,
    enabled: Boolean,
    onFollow: () -> Unit,
    onApplyFriend: () -> Unit,
    onOpenChat: () -> Unit
) {
    when {
        user.isFriend -> SearchRowAction(
            text = stringResource(R.string.friends_send_message),
            color = OrangeDeep,
            enabled = enabled,
            onClick = onOpenChat
        )
        user.hasSentFriendRequest -> SearchRowAction(
            text = stringResource(R.string.friends_request_pending),
            color = InkLight,
            enabled = false,
            onClick = {}
        )
        user.hasIncomingFriendRequest -> SearchRowAction(
            text = stringResource(R.string.friends_request_incoming),
            color = InkLight,
            enabled = false,
            onClick = {}
        )
        user.isMutual -> SearchRowAction(
            text = stringResource(R.string.friends_apply_action),
            color = OrangeDeep,
            enabled = enabled,
            onClick = onApplyFriend
        )
        user.isFollowing -> SearchRowAction(
            text = stringResource(R.string.friends_following_state),
            color = InkLight,
            enabled = false,
            onClick = {}
        )
        user.isFollowedBy -> SearchRowAction(
            text = stringResource(R.string.friends_follow_back),
            color = OrangeDeep,
            enabled = enabled,
            onClick = onFollow
        )
        else -> SearchRowAction(
            text = stringResource(R.string.friends_follow_action),
            color = OrangeDeep,
            enabled = enabled,
            onClick = onFollow
        )
    }
}

@Composable
private fun SearchRowAction(
    text: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Text(
        text,
        fontFamily = CuteFont,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        color = color,
        modifier = Modifier
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(8.dp)
    )
}

@Composable
private fun SearchMessageBar(text: String, isError: Boolean) {
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
internal fun FriendSearchPreviewContent() {
    FriendSearchContent(
        state = FriendsUiState(
            loading = false,
            friends = emptyList(),
            searching = false,
            searchResults = listOf(
                UserSearchResult("u1", "小兔", "", "100234"),
                UserSearchResult(
                    "u2", "妍少 7.25", "", "100235",
                    isFollowing = true, isFollowedBy = true
                )
            )
        ),
        query = "小",
        onQueryChange = {},
        onFollow = {},
        onApplyFriend = {},
        onOpenChat = {},
        onBack = {}
    )
}

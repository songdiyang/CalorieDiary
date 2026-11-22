package com.calorie.diary.ui.friends

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calorie.diary.R
import com.calorie.diary.data.AppRefreshBus
import com.calorie.diary.data.supabase.ConversationSettings
import com.calorie.diary.data.supabase.FriendEntry
import com.calorie.diary.data.supabase.FriendRequestEntry
import com.calorie.diary.data.supabase.GroupInvitation
import com.calorie.diary.data.supabase.GroupConversation
import com.calorie.diary.ui.components.CuteButton
import com.calorie.diary.ui.components.EmptyState
import com.calorie.diary.ui.components.MenuLineIcons
import com.calorie.diary.ui.components.SoftCard
import com.calorie.diary.ui.components.SparkFlame
import com.calorie.diary.ui.components.UserAvatar
import com.calorie.diary.ui.navigation.Routes
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Danger
import com.calorie.diary.ui.theme.Hairline
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.LavenderSoft
import com.calorie.diary.ui.theme.MintSoft
import com.calorie.diary.ui.theme.Orange
import com.calorie.diary.ui.theme.OrangeDeep
import com.calorie.diary.ui.theme.PinkSoft
import com.calorie.diary.ui.theme.ScreenBg
import com.calorie.diary.viewmodel.FriendsUiState
import com.calorie.diary.viewmodel.FriendsViewModel
import com.calorie.diary.viewmodel.GroupsUiState
import com.calorie.diary.viewmodel.GroupsViewModel
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class ConversationEntry(
    val id: String,
    /** 对方的真实 userId，仅用于默认头像兜底；群聊为空。 */
    val avatarUserId: String,
    val title: String,
    val subtitle: String,
    val avatarEmoji: String?,
    val iconRes: Int?,
    val sparkCount: Int,
    val lastActivity: String,
    val onOpen: () -> Unit
)

/** 长按的会话条目及其在窗口中的位置，用于把菜单贴在该行下方弹出。 */
private data class ConversationMenuTarget(
    val entry: ConversationEntry,
    val rowBounds: Rect
)

@Composable
fun FriendsScreen(
    onOpenChat: (String) -> Unit,
    onOpenGroupChat: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onLogin: () -> Unit
) {
    val vm: FriendsViewModel = viewModel()
    val groupsVm: GroupsViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    val groupsState by groupsVm.uiState.collectAsState()
    val settings by vm.conversationSettings.collectAsState()

    // 从聊天页返回或重新进入消息页时刷新一次，会话摘要/火花不能停在旧数据上。
    LaunchedEffect(Unit) {
        vm.refresh()
        groupsVm.refresh()
    }

    FriendsContent(
        state = state,
        groupsState = groupsState,
        settings = settings,
        onRefresh = {
            vm.refresh()
            groupsVm.refresh()
        },
        onOpenChat = onOpenChat,
        onOpenGroupChat = onOpenGroupChat,
        onOpenProfile = onOpenProfile,
        onOpenSearch = onOpenSearch,
        onLogin = onLogin,
        onAcceptRequest = vm::acceptRequest,
        onDeclineRequest = vm::declineRequest,
        onCreateGroup = groupsVm::createGroup,
        onRespondInvitation = groupsVm::respondInvitation,
        onTogglePinned = vm::togglePinned,
        onToggleMuted = vm::toggleMuted,
        onDelete = vm::deleteConversation
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FriendsContent(
    state: FriendsUiState,
    groupsState: GroupsUiState,
    settings: ConversationSettings,
    onRefresh: () -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenGroupChat: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onLogin: () -> Unit,
    onAcceptRequest: (String) -> Unit,
    onDeclineRequest: (String) -> Unit,
    onCreateGroup: (String, List<String>) -> Unit,
    onRespondInvitation: (Long, Boolean) -> Unit,
    onTogglePinned: (String) -> Unit,
    onToggleMuted: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    var showCreateGroup by remember { mutableStateOf(false) }
    var showActionMenu by remember { mutableStateOf(false) }
    var actionMenuAnchor by remember { mutableStateOf(Rect.Zero) }
    var actionTarget by remember { mutableStateOf<ConversationMenuTarget?>(null) }
    val recentFriendsScroll = rememberLazyListState()

    LaunchedEffect(Unit) {
        AppRefreshBus.events.collect { route ->
            if (route == Routes.FRIENDS) onRefresh()
        }
    }

    val entries = buildList {
        state.friends.forEach { friend ->
            add(
                ConversationEntry(
                    id = "friend_${friend.userId}",
                    avatarUserId = friend.userId,
                    title = friend.nickname,
                    subtitle = friend.lastMessage ?: stringResource(R.string.friends_start_chat),
                    avatarEmoji = friend.avatarEmoji,
                    iconRes = null,
                    sparkCount = friend.sparkCount,
                    lastActivity = friend.lastMessageAt ?: friend.friendsSince,
                    onOpen = { onOpenChat(friend.userId) }
                )
            )
        }
        groupsState.groups.forEach { group ->
            add(
                ConversationEntry(
                    id = "group_${group.groupId}",
                    avatarUserId = "",
                    title = group.name,
                    subtitle = group.lastMessage ?: stringResource(R.string.groups_start_chat),
                    avatarEmoji = null,
                    iconRes = R.drawable.ic_friends,
                    sparkCount = group.sparkDays,
                    lastActivity = group.lastMessageAt ?: group.createdAt,
                    onOpen = { onOpenGroupChat(group.groupId) }
                )
            )
        }
    }
    val visibleEntries = entries
        .filterNot { it.id in settings.deletedIds }
        .sortedWith(
            compareByDescending<ConversationEntry> { it.id in settings.pinnedIds }
                .thenByDescending { it.lastActivity }
        )

    val requiresLogin = state.requiresLogin || groupsState.requiresLogin
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(ScreenBg)) {
            MessagesTopBar(
                showActions = !requiresLogin,
                onSearchClick = onOpenSearch,
                onAddClick = { bounds ->
                    actionMenuAnchor = bounds
                    showActionMenu = true
                }
            )
            val messageRes = state.messageRes ?: groupsState.messageRes
            messageRes?.let {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    MessageBar(
                        stringResource(it),
                        if (state.messageRes != null) state.messageIsError else groupsState.messageIsError
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            if (requiresLogin) {
                Spacer(Modifier.weight(1f))
                Box(Modifier.padding(24.dp)) { LoginRequiredCard(onLogin) }
                Spacer(Modifier.weight(1f))
            } else {
                LazyColumn(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(CardWhite)
                ) {
                    item(key = "recent_friends") {
                        RecentFriendsRow(
                            friends = state.friends
                                .filterNot { "friend_${it.userId}" in settings.deletedIds }
                                .sortedByDescending { it.lastMessageAt ?: it.friendsSince },
                            onAddFriend = onOpenSearch,
                            onOpenChat = onOpenChat,
                            state = recentFriendsScroll,
                            modifier = Modifier.background(ScreenBg)
                        )
                    }
                    if (state.requests.isNotEmpty()) {
                        item(key = "friend_requests_header") {
                            FriendRequestsHeader(state.requests.size)
                        }
                        items(state.requests, key = { "request_${it.userId}" }) { request ->
                            FriendRequestRow(
                                request = request,
                                enabled = !state.busy,
                                onOpenProfile = { onOpenProfile(request.userId) },
                                onAccept = { onAcceptRequest(request.userId) },
                                onDecline = { onDeclineRequest(request.userId) }
                            )
                        }
                    }
                    if (groupsState.invitations.isNotEmpty()) {
                        item(key = "group_invitations_header") {
                            GroupInvitationsHeader(groupsState.invitations.size)
                        }
                        items(
                            groupsState.invitations,
                            key = { "group_invite_${it.id}" }
                        ) { invitation ->
                            GroupInvitationRow(
                                invitation = invitation,
                                enabled = !groupsState.busy,
                                onAccept = { onRespondInvitation(invitation.id, true) },
                                onDecline = { onRespondInvitation(invitation.id, false) }
                            )
                        }
                    }
                    item(key = "interaction_gap") {
                        Spacer(
                            Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .background(ScreenBg)
                        )
                    }
                    when {
                        (state.loading || groupsState.loading) && entries.isEmpty() ->
                            item(key = "conversations_loading") { LoadingState() }
                        visibleEntries.isEmpty() ->
                            item(key = "conversations_empty") {
                                EmptyState(stringResource(R.string.chats_empty))
                            }
                        else -> itemsIndexed(
                            visibleEntries,
                            key = { _, entry -> entry.id }
                        ) { index, entry ->
                            ConversationRow(entry, settings) { target, bounds ->
                                actionTarget = ConversationMenuTarget(target, bounds)
                            }
                            if (index < visibleEntries.lastIndex) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = 84.dp)
                                        .height(1.dp)
                                        .background(Hairline)
                                )
                            }
                        }
                    }
                }
            }
        }

        actionTarget?.let { target ->
            ConversationContextMenu(
                entry = target.entry,
                rowBounds = target.rowBounds,
                settings = settings,
                onOpenProfile = { userId ->
                    onOpenProfile(userId)
                    actionTarget = null
                },
                onTogglePinned = {
                    onTogglePinned(target.entry.id)
                    actionTarget = null
                },
                onToggleMuted = {
                    onToggleMuted(target.entry.id)
                    actionTarget = null
                },
                onDelete = {
                    onDelete(target.entry.id)
                    actionTarget = null
                },
                onDismiss = { actionTarget = null }
            )
        }
    }
    if (showActionMenu) {
        SocialActionMenu(
            anchor = actionMenuAnchor,
            items = listOf(
                ActionMenuItem(MenuLineIcons.Group, stringResource(R.string.groups_create_action)) {
                    showActionMenu = false
                    showCreateGroup = true
                },
                ActionMenuItem(MenuLineIcons.Person, stringResource(R.string.friends_add_title)) {
                    showActionMenu = false
                    onOpenSearch()
                }
            ),
            onDismiss = { showActionMenu = false }
        )
    }
    if (showCreateGroup) {
        CreateGroupSheet(
            friends = state.friends,
            onCreate = { name, friendIds ->
                showCreateGroup = false
                onCreateGroup(name, friendIds)
            },
            onDismiss = { showCreateGroup = false }
        )
    }
}

@Composable
private fun MessagesTopBar(
    showActions: Boolean,
    onSearchClick: () -> Unit,
    onAddClick: (Rect) -> Unit
) {
    var addBounds by remember { mutableStateOf(Rect.Zero) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 10.dp)
    ) {
        Text(
            stringResource(R.string.chats_title),
            modifier = Modifier.align(Alignment.Center),
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 23.sp,
            color = Ink
        )
        if (showActions) {
            Row(
                Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onSearchClick
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.friends_search_title),
                        modifier = Modifier.size(27.dp),
                        tint = Ink
                    )
                }
                Box(
                    Modifier
                        .size(38.dp)
                        .onGloballyPositioned { addBounds = it.boundsInWindow() }
                        .border(1.dp, Ink, CircleShape)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onAddClick(addBounds) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.friends_add_action),
                        modifier = Modifier.size(25.dp),
                        tint = Ink
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentFriendsRow(
    friends: List<FriendEntry>,
    onAddFriend: () -> Unit,
    onOpenChat: (String) -> Unit,
    state: LazyListState,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth().padding(vertical = 10.dp),
        state = state,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "add_friend") {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(52.dp)
                        .border(1.dp, Hairline, CircleShape)
                        .clip(CircleShape)
                        .background(CardWhite)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onAddFriend
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.friends_add_title),
                        modifier = Modifier.size(29.dp),
                        tint = OrangeDeep
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.friends_add_short),
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 12.sp,
                    color = Ink
                )
            }
        }
        items(friends, key = { "recent_${it.userId}" }) { friend ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onOpenChat(friend.userId) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        UserAvatar(
                            userId = friend.userId,
                            avatarEmoji = friend.avatarEmoji,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (friend.sparkCount > 0) {
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .size(20.dp)
                                .border(2.dp, ScreenBg, CircleShape)
                                .clip(CircleShape)
                                .background(OrangeDeep),
                            contentAlignment = Alignment.Center
                        ) {
                            SparkFlame(Modifier.size(10.dp), Color.White)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    friend.nickname,
                    modifier = Modifier.widthIn(max = 72.dp),
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 12.sp,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FriendRequestsHeader(count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(CardWhite)
            .padding(start = 16.dp, end = 16.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.friends_requests_title),
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = InkLight
        )
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .clip(CircleShape)
                .background(OrangeDeep)
                .padding(horizontal = 7.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                count.toString(),
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
                color = Color.White
            )
        }
    }
}

@Composable
private fun FriendRequestRow(
    request: FriendRequestEntry,
    enabled: Boolean,
    onOpenProfile: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(CardWhite)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenProfile
                ),
            contentAlignment = Alignment.Center
        ) {
            UserAvatar(
                userId = request.userId,
                avatarEmoji = request.avatarEmoji,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier
                .weight(1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenProfile
                )
        ) {
            Text(
                request.nickname,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                stringResource(R.string.friends_request_new),
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
                color = InkLight
            )
        }
        Spacer(Modifier.width(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RequestActionButton(
                text = stringResource(R.string.friends_request_decline),
                primary = false,
                enabled = enabled,
                onClick = onDecline
            )
            RequestActionButton(
                text = stringResource(R.string.friends_request_accept),
                primary = true,
                enabled = enabled,
                onClick = onAccept
            )
        }
    }
}

@Composable
private fun RequestActionButton(
    text: String,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if (primary) OrangeDeep else CardWhite)
            .border(1.dp, if (primary) OrangeDeep else Hairline, shape)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 13.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontFamily = FontFamily.SansSerif,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (primary) Color.White else Ink
        )
    }
}

@Composable
private fun GroupInvitationsHeader(count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(CardWhite)
            .padding(start = 16.dp, end = 16.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.group_invitation_section),
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = InkLight
        )
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .clip(CircleShape)
                .background(OrangeDeep)
                .padding(horizontal = 7.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                count.toString(),
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
                color = Color.White
            )
        }
    }
}

@Composable
private fun GroupInvitationRow(
    invitation: GroupInvitation,
    enabled: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(CardWhite)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MintSoft),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_friends),
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                colorFilter = ColorFilter.tint(Ink)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                invitation.groupName,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                stringResource(
                    R.string.group_invitation_desc,
                    invitation.inviterNickname.ifBlank { invitation.groupId },
                    invitation.memberCount
                ),
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
                color = InkLight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RequestActionButton(
                text = stringResource(R.string.group_invitation_decline),
                primary = false,
                enabled = enabled,
                onClick = onDecline
            )
            RequestActionButton(
                text = stringResource(R.string.group_invitation_accept),
                primary = true,
                enabled = enabled,
                onClick = onAccept
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    entry: ConversationEntry,
    settings: ConversationSettings,
    onLongClick: (ConversationEntry, Rect) -> Unit
) {
    var rowBounds by remember { mutableStateOf(Rect.Zero) }
    Row(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { rowBounds = it.boundsInWindow() }
            .background(CardWhite)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = entry.onOpen,
                onLongClick = { onLongClick(entry, rowBounds) }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(52.dp)
                .then(
                    if (entry.iconRes == null) Modifier
                    else Modifier.clip(CircleShape).background(MintSoft)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (entry.iconRes == null) {
                UserAvatar(
                    // 兜底头像按用户 id 取，不能用 "friend_xxx" 这种会话 id，否则会和别处生成不同的默认头像。
                    userId = entry.avatarUserId.ifBlank { entry.id },
                    avatarEmoji = entry.avatarEmoji,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    painter = painterResource(entry.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(27.dp),
                    colorFilter = ColorFilter.tint(Ink)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.title,
                    modifier = Modifier.weight(1f, fill = false),
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 17.sp,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.sparkCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    val sparkDescription = stringResource(R.string.friends_spark_days, entry.sparkCount)
                    Row(
                        modifier = Modifier.clearAndSetSemantics {
                            contentDescription = sparkDescription
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SparkFlame(Modifier.size(16.dp), OrangeDeep)
                        Spacer(Modifier.width(3.dp))
                        Text(
                            entry.sparkCount.toString(),
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = OrangeDeep,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                entry.subtitle,
                fontFamily = FontFamily.SansSerif,
                fontSize = 13.sp,
                color = InkLight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                conversationTimeLabel(entry.lastActivity),
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
                color = InkLight,
                maxLines = 1
            )
            if (entry.id in settings.pinnedIds || entry.id in settings.mutedIds) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (entry.id in settings.pinnedIds) {
                        Image(
                            painter = painterResource(R.drawable.deco_star),
                            contentDescription = stringResource(R.string.chat_pin),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    if (entry.id in settings.mutedIds) {
                        Spacer(Modifier.width(5.dp))
                        Image(
                            painter = painterResource(R.drawable.icon_bell),
                            contentDescription = stringResource(R.string.chat_mute),
                            modifier = Modifier.size(15.dp),
                            alpha = 0.45f
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun conversationTimeLabel(raw: String): String {
    val instant = remember(raw) {
        runCatching { Instant.parse(raw) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
    } ?: return ""
    val zone = ZoneId.systemDefault()
    val dateTime = instant.atZone(zone)
    val now = ZonedDateTime.now(zone)
    val locale = Locale.getDefault()
    val minutes = Duration.between(dateTime, now).toMinutes()
    val time = DateTimeFormatter.ofPattern("HH:mm", locale).format(dateTime)
    return when {
        minutes == 0L -> stringResource(R.string.chat_time_now)
        minutes in 1L..59L -> stringResource(R.string.chat_time_minutes_ago, minutes)
        dateTime.toLocalDate() == now.toLocalDate() -> time
        dateTime.toLocalDate() == now.toLocalDate().minusDays(1) ->
            stringResource(R.string.date_yesterday) + " " + time
        dateTime.year == now.year -> DateTimeFormatter.ofPattern(
            if (locale.language == "zh") "M月d日" else "d MMM",
            locale
        ).format(dateTime)
        else -> DateTimeFormatter.ofPattern(
            if (locale.language == "zh") "yyyy年M月d日" else "d MMM yyyy",
            locale
        ).format(dateTime)
    }
}

/**
 * 长按会话后贴着该行弹出的菜单卡片：纯圆角白色卡片，不做对话气泡样式，
 * 每项为「图标 + 文案」，最后一项删除用红色。
 */
@Composable
private fun ConversationContextMenu(
    entry: ConversationEntry,
    rowBounds: Rect,
    settings: ConversationSettings,
    onOpenProfile: (String) -> Unit,
    onTogglePinned: () -> Unit,
    onToggleMuted: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val profileUserId = entry.avatarUserId.takeIf { it.isNotBlank() }
    val itemCount = if (profileUserId != null) 4 else 3
    // 用估算高度判断放在行下方还是上方，避免靠近屏幕底部时菜单被裁掉。
    val estimatedHeight = 12.dp + 50.dp * itemCount + 1.dp * (itemCount - 1)
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }
    val showBelow = rowBounds.bottom + with(density) { (estimatedHeight + 16.dp).toPx() } < screenHeight

    val positionProvider = remember(rowBounds, showBelow) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val margin = with(density) { 12.dp.roundToPx() }
                val gap = with(density) { 8.dp.roundToPx() }
                val x = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
                val y = if (showBelow) {
                    (rowBounds.bottom.toInt() + gap)
                        .coerceAtMost(windowSize.height - popupContentSize.height - margin)
                } else {
                    (rowBounds.top.toInt() - popupContentSize.height - gap)
                        .coerceAtLeast(margin)
                }
                return IntOffset(x, y)
            }
        }
    }

    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(durationMillis = 140, easing = FastOutSlowInEasing))
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Column(
            Modifier
                .width(214.dp)
                .graphicsLayer {
                    val progress = appear.value
                    scaleX = 0.88f + 0.12f * progress
                    scaleY = 0.88f + 0.12f * progress
                    alpha = progress
                    transformOrigin = TransformOrigin(1f, if (showBelow) 0f else 1f)
                }
                .shadow(14.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(CardWhite)
                .padding(vertical = 5.dp)
        ) {
            if (profileUserId != null) {
                ContextMenuItem(
                    icon = Icons.Default.Person,
                    label = stringResource(R.string.chat_profile),
                    onClick = { onOpenProfile(profileUserId) }
                )
                ContextMenuDivider()
            }
            ContextMenuItem(
                icon = Icons.Default.Star,
                label = stringResource(
                    if (entry.id in settings.pinnedIds) R.string.chat_unpin else R.string.chat_pin
                ),
                onClick = onTogglePinned
            )
            ContextMenuDivider()
            ContextMenuItem(
                icon = Icons.Default.Notifications,
                label = stringResource(
                    if (entry.id in settings.mutedIds) R.string.chat_unmute else R.string.chat_mute
                ),
                onClick = onToggleMuted
            )
            ContextMenuDivider()
            ContextMenuItem(
                icon = Icons.Default.Delete,
                label = stringResource(R.string.chat_delete),
                danger = true,
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun ContextMenuItem(
    icon: ImageVector,
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val tint = if (danger) Danger else Ink
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = tint
        )
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            fontFamily = FontFamily.SansSerif,
            fontSize = 15.sp,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ContextMenuDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Hairline)
    )
}

/** 「+」按钮下方弹出的下拉菜单项。 */
private data class ActionMenuItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit
)

/**
 * 消息页「+」的下拉菜单：白色圆角卡片，贴着按钮下沿右对齐弹出，
 * 包含发起群聊、添加好友、添加群聊三个入口。
 */
@Composable
private fun SocialActionMenu(
    anchor: Rect,
    items: List<ActionMenuItem>,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val positionProvider = remember(anchor) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val margin = with(density) { 12.dp.roundToPx() }
                val gap = with(density) { 6.dp.roundToPx() }
                val x = (anchor.right.toInt() - popupContentSize.width)
                    .coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
                val y = (anchor.bottom.toInt() + gap)
                    .coerceAtMost(windowSize.height - popupContentSize.height - margin)
                return IntOffset(x, y)
            }
        }
    }

    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(durationMillis = 140, easing = FastOutSlowInEasing))
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Column(
            Modifier
                .width(190.dp)
                .graphicsLayer {
                    val progress = appear.value
                    scaleX = 0.9f + 0.1f * progress
                    scaleY = 0.9f + 0.1f * progress
                    alpha = progress
                    transformOrigin = TransformOrigin(1f, 0f)
                }
                .shadow(14.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(CardWhite)
                .padding(vertical = 5.dp)
        ) {
            items.forEachIndexed { index, item ->
                SocialActionMenuItem(item.icon, item.label, item.onClick)
                if (index < items.lastIndex) ContextMenuDivider()
            }
        }
    }
}

@Composable
private fun SocialActionMenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 黑色线条图标直接落在白色菜单卡上，不填充、不加底色块。
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Ink,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            fontFamily = FontFamily.SansSerif,
            fontSize = 15.sp,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateGroupSheet(
    friends: List<FriendEntry>,
    onCreate: (String, List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardWhite,
        contentColor = Ink,
        scrimColor = Ink.copy(alpha = 0.32f),
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = { SheetDragHandle() }
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(
                stringResource(R.string.groups_create_action),
                fontFamily = CuteFont, fontWeight = FontWeight.Bold,
                fontSize = 19.sp, color = Ink
            )
            Spacer(Modifier.height(14.dp))
            SearchField(
                name,
                { name = it.take(30) },
                stringResource(R.string.group_name_label)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.group_invite_friends_label),
                fontFamily = CuteFont, fontWeight = FontWeight.Medium,
                fontSize = 13.sp, color = InkLight
            )
            Spacer(Modifier.height(4.dp))
            if (friends.isEmpty()) {
                Text(
                    stringResource(R.string.group_invite_none),
                    fontFamily = CuteFont, fontSize = 12.sp, color = InkLight,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                    items(friends, key = { it.userId }) { friend ->
                        FriendSelectRow(
                            friend = friend,
                            selected = friend.userId in selected
                        ) {
                            if (friend.userId in selected) {
                                selected.remove(friend.userId)
                            } else {
                                selected.add(friend.userId)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, Hairline, RoundedCornerShape(20.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss
                        )
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.common_cancel),
                        fontFamily = CuteFont, fontSize = 15.sp, color = Ink
                    )
                }
                CuteButton(
                    text = stringResource(R.string.group_create_chat_btn),
                    onClick = { onCreate(name.trim(), selected.toList()) },
                    modifier = Modifier.weight(1.4f),
                    enabled = name.isNotBlank(),
                    contentPadding = PaddingValues(vertical = 13.dp)
                )
            }
        }
    }
}

/** 上拉页顶部的小把手，保持与系统 bottom sheet 一致的拖拽暗示。 */
@Composable
private fun SheetDragHandle() {
    Box(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .width(38.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Hairline)
        )
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Orange)
    }
}

@Composable
private fun LoginRequiredCard(onLogin: () -> Unit) {
    SoftCard(Modifier.fillMaxWidth(), fill = CardWhite) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.friends_login_required),
                fontFamily = CuteFont, fontSize = 16.sp, color = Ink
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.friends_login_hint),
                fontFamily = CuteFont, fontSize = 13.sp, color = InkLight
            )
            Spacer(Modifier.height(16.dp))
            CuteButton(
                text = stringResource(R.string.friends_go_login),
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
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
internal fun FriendsPreviewContent() {
    FriendsContent(
        state = FriendsUiState(
            loading = false,
            friends = listOf(
                FriendEntry(
                    "f1", "小兔", "", "2026-11-01T08:00:00Z",
                    "今天也好好吃饭", "2026-11-15T00:58:00Z", sparkCount = 194
                ),
                FriendEntry(
                    "f2", "妍少 7.25", "", "2026-11-02T08:00:00Z",
                    "和你一起续火花", "2026-11-15T00:30:00Z", sparkCount = 177
                ),
                FriendEntry(
                    "f3", "小鹿", "", "2026-11-03T08:00:00Z",
                    "今天也要记得喝水", "2026-11-15T00:18:00Z", sparkCount = 63
                ),
                FriendEntry(
                    "f4", "阿橙", "", "2026-11-04T08:00:00Z",
                    "晚餐一起清淡一点", "2026-11-14T13:45:00Z", sparkCount = 24
                ),
                FriendEntry(
                    "f5", "桃桃", "", "2026-11-05T08:00:00Z",
                    "明天一起打卡", "2026-11-14T11:12:00Z", sparkCount = 8
                )
            ),
            requests = listOf(
                FriendRequestEntry("r1", "豆豆", "", "2026-11-15T09:20:00Z"),
                FriendRequestEntry("r2", "柚子", "", "2026-11-15T07:05:00Z")
            )
        ),
        groupsState = GroupsUiState(
            loading = false,
            groups = listOf(
                GroupConversation(
                    "123456", "减脂搭子群", "owner", 1800, "2026-11-10T08:00:00Z",
                    "今天一起打卡", "2026-11-14T12:30:00Z"
                )
            )
        ),
        settings = ConversationSettings(pinnedIds = setOf("friend_f1"), mutedIds = setOf("group_123456")),
        onRefresh = {},
        onOpenChat = {},
        onOpenGroupChat = {},
        onOpenProfile = {},
        onOpenSearch = {},
        onLogin = {},
        onAcceptRequest = {},
        onDeclineRequest = {},
        onCreateGroup = { _, _ -> },
        onRespondInvitation = { _, _ -> },
        onTogglePinned = {},
        onToggleMuted = {},
        onDelete = {}
    )
}

@Composable
internal fun GroupsPreviewContent() = FriendsPreviewContent()

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calorie.diary.R
import com.calorie.diary.data.AppRefreshBus
import com.calorie.diary.data.ChatEmojis
import com.calorie.diary.data.supabase.GroupConversation
import com.calorie.diary.data.supabase.GroupMemberTotal
import com.calorie.diary.data.supabase.GroupMessage
import com.calorie.diary.data.supabase.SharedFood
import com.calorie.diary.ui.components.CalorieShareCard
import com.calorie.diary.ui.components.ChatEmojiPanel
import com.calorie.diary.ui.components.ChatFunctionTile
import com.calorie.diary.ui.components.EmojiInputVisualTransformation
import com.calorie.diary.ui.components.ChatMessageContent
import com.calorie.diary.ui.components.CuteButton
import com.calorie.diary.ui.components.RiceRainEffect
import com.calorie.diary.ui.components.SoftCard
import com.calorie.diary.ui.components.SparkFlame
import com.calorie.diary.ui.components.UserAvatar
import com.calorie.diary.ui.home.appVm
import com.calorie.diary.ui.navigation.Routes
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Hairline
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.MintSoft
import com.calorie.diary.ui.theme.NumberFont
import com.calorie.diary.ui.theme.Orange
import com.calorie.diary.ui.theme.OrangeDeep
import com.calorie.diary.ui.theme.ScreenBg
import com.calorie.diary.ui.theme.Yellow
import com.calorie.diary.ui.theme.YellowSoft
import com.calorie.diary.viewmodel.GroupChatUiState
import com.calorie.diary.viewmodel.GroupChatViewModel
import kotlinx.coroutines.delay

@Composable
fun GroupChatScreen(groupId: String, onBack: () -> Unit, onOpenInfo: () -> Unit = {}) {
    val vm: GroupChatViewModel = viewModel(key = "group-chat-$groupId")
    val appViewModel = appVm()
    val selectedDate by appViewModel.selectedDate.collectAsState()
    val dayTotal by appViewModel.dayTotalCalories.collectAsState()
    val dayEntries by appViewModel.dayEntries.collectAsState()
    val identity by appViewModel.identity.collectAsState()
    val state by vm.uiState.collectAsState()

    LaunchedEffect(groupId, selectedDate) { vm.load(groupId, selectedDate) }
    // 从群聊主页返回时组合会重建，这里静默拉一次最新数据，让改过的群名立即生效。
    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(Unit) {
        AppRefreshBus.events.collect { route ->
            if (route == Routes.GROUP_CHAT) vm.refresh()
        }
    }
    LaunchedEffect(groupId) {
        while (true) {
            delay(5000)
            vm.refresh()
        }
    }

    var showRiceEffect by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        vm.riceEffects.collect { showRiceEffect = true }
    }

    Box(Modifier.fillMaxSize()) {
        GroupChatContent(
            state, dayTotal, onBack, vm::sendText, identity?.avatarEmoji.orEmpty(),
            onInviteMembers = vm::inviteMembers,
            onOpenInfo = onOpenInfo,
            onShare = {
                vm.shareCalories(
                    calories = dayTotal,
                    entryDate = selectedDate,
                    items = dayEntries.map {
                        SharedFood(
                            name = it.foodName,
                            emoji = it.emoji,
                            grams = it.grams,
                            calories = it.calories,
                            meal = it.mealType
                        )
                    }
                )
            }
        )
        if (showRiceEffect) {
            RiceRainEffect(onFinished = { showRiceEffect = false })
        }
    }
}

@Composable
private fun GroupChatContent(
    state: GroupChatUiState,
    dayTotal: Int,
    onBack: () -> Unit,
    onSendText: (String) -> Unit,
    myAvatarEmoji: String = "",
    onInviteMembers: (List<String>) -> Unit = {},
    onOpenInfo: () -> Unit = {},
    onShare: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var panelOpen by remember { mutableStateOf(false) }
    var emojiPanelOpen by remember { mutableStateOf(false) }
    var showInvite by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(state.sentCount) {
        if (state.sentCount > 0) input = ""
    }
    // 列表第 0 项是热量汇总卡，消息从索引 1 开始，最后一项索引等于消息条数。
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size)
    }

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
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        state.group?.name ?: stringResource(R.string.groups_tab),
                        fontFamily = CuteFont, fontSize = 20.sp, color = Ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    val sparkDays = state.group?.sparkDays ?: 0
                    if (sparkDays > 0) {
                        Spacer(Modifier.width(6.dp))
                        val sparkDescription = stringResource(R.string.friends_spark_days, sparkDays)
                        Row(
                            modifier = Modifier.clearAndSetSemantics {
                                contentDescription = sparkDescription
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SparkFlame(Modifier.size(15.dp), OrangeDeep)
                            Spacer(Modifier.width(3.dp))
                            Text(
                                sparkDays.toString(),
                                fontFamily = NumberFont, fontSize = 12.sp, color = OrangeDeep
                            )
                        }
                    }
                }
                state.group?.let {
                    Text(
                        stringResource(R.string.group_invite_code) + " " + it.groupId,
                        fontFamily = NumberFont, fontSize = 11.sp, color = InkLight
                    )
                }
            }
            if (state.group != null) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(CardWhite)
                        .border(1.dp, Hairline, RoundedCornerShape(50))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = !state.inviting,
                            onClick = { showInvite = true }
                        )
                        .padding(horizontal = 13.dp, vertical = 7.dp)
                ) {
                    Text(
                        stringResource(R.string.group_invite_action),
                        fontFamily = CuteFont, fontSize = 12.sp, color = OrangeDeep
                    )
                }
                Spacer(Modifier.width(4.dp))
                // 群聊主页入口：查看成员、群名称与群公告。
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOpenInfo
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⋯", fontFamily = CuteFont, fontSize = 20.sp, color = Ink)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))

        state.errorRes?.let {
            Text(
                stringResource(it),
                fontFamily = CuteFont, fontSize = 12.sp, color = OrangeDeep,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        state.notice?.let {
            Text(
                it,
                fontFamily = CuteFont, fontSize = 12.sp, color = Ink,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
        if (state.loading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Orange)
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(12.dp)
            ) {
                item {
                    DailyCaloriesCard(state.members, state.currentUserId, dayTotal, state.sending, onShare)
                }
                if (state.messages.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.chat_empty),
                            fontFamily = CuteFont, fontSize = 13.sp, color = InkLight,
                            modifier = Modifier.fillMaxWidth().padding(24.dp)
                        )
                    }
                } else {
                    items(state.messages, key = { it.id }) { message ->
                        GroupMessageBubble(message, state.currentUserId, state.members, myAvatarEmoji)
                        Spacer(Modifier.height(9.dp))
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().background(CardWhite).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = input,
                onValueChange = {
                    input = it.take(1000)
                    if (it.isNotEmpty()) {
                        panelOpen = false
                        emojiPanelOpen = false
                    }
                },
                enabled = !state.sending,
                textStyle = TextStyle(fontFamily = CuteFont, fontSize = 15.sp, color = Ink),
                cursorBrush = SolidColor(Orange),
                visualTransformation = EmojiInputVisualTransformation,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ScreenBg)
                    .border(1.dp, Hairline, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                decorationBox = { inner ->
                    if (input.isEmpty()) {
                        Text(
                            stringResource(R.string.chat_input_hint),
                            fontFamily = CuteFont, fontSize = 14.sp, color = InkLight
                        )
                    }
                    inner()
                }
            )
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (emojiPanelOpen) Orange.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            emojiPanelOpen = !emojiPanelOpen
                            if (emojiPanelOpen) panelOpen = false
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.emoji_smile),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            if (input.isNotBlank()) {
                CuteButton(
                    text = stringResource(R.string.chat_send),
                    onClick = { onSendText(input) },
                    enabled = !state.sending,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                )
            } else {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(ScreenBg)
                        .border(1.dp, Hairline, CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                panelOpen = !panelOpen
                                if (panelOpen) emojiPanelOpen = false
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (panelOpen) "×" else "+",
                        fontFamily = CuteFont, fontSize = 26.sp, color = OrangeDeep
                    )
                }
            }
        }
        // 面板放在输入框下方，展开时输入框保持在上方。
        if (panelOpen) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(ScreenBg)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                ChatFunctionTile(
                    iconRes = R.drawable.meal_lunch,
                    label = stringResource(R.string.chat_share_today),
                    onClick = {
                        panelOpen = false
                        onShare()
                    }
                )
            }
        }
        if (emojiPanelOpen) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
            ChatEmojiPanel(
                onPick = { emoji ->
                    if (ChatEmojis.isRice(emoji)) {
                        emojiPanelOpen = false
                        onSendText(emoji)
                    } else {
                        input = (input + emoji).take(1000)
                    }
                }
            )
        }
    }

    if (showInvite) {
        InviteFriendsDialog(
            friends = state.friends,
            memberIds = state.members.map { it.userId }.toSet(),
            inviting = state.inviting,
            onInvite = { ids ->
                showInvite = false
                onInviteMembers(ids)
            },
            onDismiss = { showInvite = false }
        )
    }
}

@Composable
private fun DailyCaloriesCard(
    members: List<GroupMemberTotal>,
    currentUserId: String,
    dayTotal: Int,
    sending: Boolean,
    onShare: () -> Unit
) {
    SoftCard(Modifier.fillMaxWidth().padding(bottom = 12.dp), fill = YellowSoft) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.group_daily_title),
                    fontFamily = CuteFont, fontSize = 16.sp, color = Ink
                )
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.group_share_calories),
                    fontFamily = CuteFont, fontSize = 12.sp, color = OrangeDeep,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !sending,
                        onClick = onShare
                    )
                )
            }
            Spacer(Modifier.height(10.dp))
            members.forEach { member ->
                val total = if (member.userId == currentUserId) dayTotal else member.totalCalories
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(
                        member.nickname,
                        fontFamily = CuteFont, fontSize = 12.sp, color = Ink,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        stringResource(R.string.group_calories_value, total),
                        fontFamily = NumberFont, fontSize = 12.sp, color = Ink
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupMessageBubble(
    message: GroupMessage,
    currentUserId: String,
    members: List<GroupMemberTotal>,
    myAvatarEmoji: String
) {
    val mine = message.senderId == currentUserId
    val sender = members.firstOrNull { it.userId == message.senderId }
    val senderName = sender?.nickname.orEmpty()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        if (!mine) {
            UserAvatar(
                userId = message.senderId,
                avatarEmoji = sender?.avatarEmoji.orEmpty(),
                modifier = Modifier.size(34.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Box(
            Modifier.weight(1f),
            contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                if (!mine && senderName.isNotEmpty()) {
                    Text(senderName, fontFamily = CuteFont, fontSize = 10.sp, color = InkLight)
                }
                // 四个角统一圆角，不做对话气泡的尾巴样式
                val bubbleShape = RoundedCornerShape(18.dp)
                Box(
                    Modifier
                        .widthIn(max = 260.dp)
                        .shadow(2.dp, bubbleShape)
                        .clip(bubbleShape)
                        .then(
                            when {
                                message.messageType == "calorie_share" -> Modifier.background(YellowSoft)
                                mine -> Modifier.background(
                                    Brush.verticalGradient(listOf(Orange, OrangeDeep))
                                )
                                else -> Modifier.background(CardWhite)
                            }
                        )
                        .border(
                            1.dp,
                            when {
                                message.messageType == "calorie_share" -> Yellow.copy(alpha = 0.55f)
                                mine -> Color.White.copy(alpha = 0.35f)
                                else -> Hairline
                            },
                            bubbleShape
                        )
                        .padding(horizontal = 13.dp, vertical = 10.dp)
                ) {
                    if (message.messageType == "calorie_share") {
                        CalorieShareCard(message.id, message.calories, message.items)
                    } else {
                        ChatMessageContent(
                            content = message.content,
                            fontFamily = CuteFont,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = if (mine) Color.White else Ink
                        )
                    }
                }
            }
        }
        if (mine) {
            Spacer(Modifier.width(8.dp))
            UserAvatar(
                userId = currentUserId.ifBlank { "me" },
                avatarEmoji = myAvatarEmoji,
                modifier = Modifier.size(34.dp)
            )
        }
    }
}

@Composable
internal fun GroupChatPreviewContent() {
    GroupChatContent(
        state = GroupChatUiState(
            loading = false,
            group = GroupConversation("123456", "减脂搭子群", "owner", 1800, ""),
            currentUserId = "me",
            members = listOf(
                GroupMemberTotal("me", "我", "", 0),
                GroupMemberTotal("friend", "小兔", "", 680)
            ),
            messages = listOf(
                GroupMessage(1, "friend", "今天一起打卡", "text", null, null, ""),
                GroupMessage(
                    2, "me", "今日热量分享", "calorie_share", 1116, "2026-11-14", "",
                    items = listOf(
                        SharedFood("全麦吐司", "🍞", 80, 200, 0),
                        SharedFood("煎蛋", "🍳", 60, 130, 0),
                        SharedFood("鸡胸肉饭", "🍚", 300, 390, 1)
                    )
                ),
                GroupMessage(3, "friend", "🍚", "text", null, null, "")
            )
        ),
        dayTotal = 1116,
        onBack = {},
        onSendText = {},
        onInviteMembers = {},
        onShare = {}
    )
}

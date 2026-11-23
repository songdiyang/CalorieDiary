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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calorie.diary.R
import com.calorie.diary.data.AppRefreshBus
import com.calorie.diary.ui.navigation.Routes
import com.calorie.diary.data.supabase.FriendEntry
import com.calorie.diary.data.supabase.FriendMessage
import com.calorie.diary.data.supabase.SharedFood
import com.calorie.diary.ui.components.CalorieShareCard
import com.calorie.diary.ui.components.ChatFunctionTile
import com.calorie.diary.ui.components.CuteButton
import com.calorie.diary.ui.components.RiceMountainCard
import com.calorie.diary.ui.components.riceBowlsOf
import com.calorie.diary.ui.components.riceCaloriesOf
import com.calorie.diary.ui.components.UserAvatar
import com.calorie.diary.ui.home.appVm
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Hairline
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkFaint
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.LavenderSoft
import com.calorie.diary.ui.theme.NumberFont
import com.calorie.diary.ui.theme.Orange
import com.calorie.diary.ui.theme.OrangeDeep
import com.calorie.diary.ui.theme.ScreenBg
import com.calorie.diary.ui.theme.Yellow
import com.calorie.diary.ui.theme.YellowSoft
import com.calorie.diary.viewmodel.ChatUiState
import com.calorie.diary.viewmodel.ChatViewModel
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun FriendChatScreen(friendId: String, onBack: () -> Unit, onOpenPersonal: (String) -> Unit) {
    val vm: ChatViewModel = viewModel(key = "chat-$friendId")
    val appViewModel = appVm()
    val identity by appViewModel.identity.collectAsState()
    val dayTotal by appViewModel.dayTotalCalories.collectAsState()
    val dayEntries by appViewModel.dayEntries.collectAsState()
    val selectedDate by appViewModel.selectedDate.collectAsState()
    val state by vm.uiState.collectAsState()
    LaunchedEffect(friendId) { vm.load(friendId) }
    LaunchedEffect(Unit) {
        AppRefreshBus.events.collect { route ->
            if (route == Routes.FRIEND_CHAT) vm.refresh()
        }
    }
    LaunchedEffect(friendId) {
        while (true) {
            delay(5000)
            vm.refresh()
        }
    }
    ChatContent(
        state = state,
        friendId = friendId,
        onBack = onBack,
        onSend = vm::send,
        onShareCalories = {
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
        },
        onShareRice = {
            val riceItems = dayEntries.filter {
                it.foodName.contains("饭") || it.emoji.contains("🍚") || it.emoji.contains("🍙")
            }.map {
                SharedFood(
                    name = it.foodName,
                    emoji = it.emoji,
                    grams = it.grams,
                    calories = it.calories,
                    meal = it.mealType
                )
            }
            val fallback = listOf(SharedFood("米饭", "🍚", 150, 232, 1))
            val items = riceItems.ifEmpty { fallback }
            vm.shareCalories(
                calories = riceCaloriesOf(items) ?: fallback.first().calories,
                entryDate = selectedDate,
                items = items,
                messageType = "rice_mountain"
            )
        },
        onBindRelation = vm::bindRelation,
        myAvatarEmoji = identity?.avatarEmoji.orEmpty(),
        onOpenPersonal = { onOpenPersonal(friendId) }
    )
}

@Composable
private fun ChatContent(
    state: ChatUiState,
    friendId: String = "",
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onShareCalories: () -> Unit = {},
    onShareRice: () -> Unit = {},
    onBindRelation: (String) -> Unit = {},
    myAvatarEmoji: String = "",
    onOpenPersonal: () -> Unit = {}
) {
    var input by remember { mutableStateOf("") }
    var panelOpen by remember { mutableStateOf(false) }
    var relationDialog by remember { mutableStateOf(false) }
    LaunchedEffect(state.sentCount) {
        if (state.sentCount > 0) input = ""
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
            Box(
                Modifier
                    .size(38.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenPersonal
                    ),
                contentAlignment = Alignment.Center
            ) {
                UserAvatar(
                    // 资料未加载或加载失败时用路由里的 friendId 兜底，默认头像才能和会话列表一致。
                    userId = state.friend?.userId?.takeIf { it.isNotBlank() } ?: friendId,
                    avatarEmoji = state.friend?.avatarEmoji,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        state.friend?.nickname ?: stringResource(R.string.chat_title),
                        fontFamily = CuteFont, fontSize = 19.sp, color = Ink
                    )
                    state.relationType?.let { relation ->
                        relationLabelRes(relation)?.let { labelRes ->
                            Spacer(Modifier.width(6.dp))
                            RelationBadge(labelRes)
                        }
                    }
                }
                Text(
                    state.friend?.let {
                        if (it.sparkCount > 0) {
                            stringResource(R.string.friends_spark_days, it.sparkCount)
                        } else {
                            stringResource(R.string.chat_spark_hint)
                        }
                    }.orEmpty(),
                    fontFamily = CuteFont, fontSize = 10.sp, color = OrangeDeep,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClickLabel = stringResource(R.string.chat_relation)
                        ) { relationDialog = true }
                        .padding(vertical = 2.dp, horizontal = 2.dp)
                )
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
        when {
            state.loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Orange)
            }
            state.messages.isEmpty() -> Box(
                Modifier.weight(1f).fillMaxWidth().padding(30.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.chat_empty),
                    fontFamily = CuteFont, fontSize = 14.sp, color = InkLight
                )
            }
            else -> MessageList(
                messages = state.messages,
                currentUserId = state.currentUserId,
                friendUserId = state.friend?.userId?.takeIf { it.isNotBlank() } ?: friendId,
                friendAvatarEmoji = state.friend?.avatarEmoji.orEmpty(),
                myAvatarEmoji = myAvatarEmoji,
                modifier = Modifier.weight(1f)
            )
        }

        ChatComposer(
            input = input,
            onInputChange = {
                input = it.take(1000)
                if (it.isNotEmpty()) panelOpen = false
            },
            sending = state.sending,
            panelOpen = panelOpen,
            onTogglePanel = { panelOpen = !panelOpen },
            onSend = { onSend(input) },
            onShareCalories = {
                panelOpen = false
                onShareCalories()
            },
            onShareRice = {
                panelOpen = false
                onShareRice()
            }
        )
    }

    if (relationDialog) {
        RelationDialog(
            current = state.relationType,
            currentSince = state.relationSince,
            onDismiss = { relationDialog = false },
            onBind = { type ->
                relationDialog = false
                onBindRelation(type)
            }
        )
    }
}

@Composable
private fun ChatComposer(
    input: String,
    onInputChange: (String) -> Unit,
    sending: Boolean,
    panelOpen: Boolean,
    onTogglePanel: () -> Unit,
    onSend: () -> Unit,
    onShareCalories: () -> Unit,
    onShareRice: () -> Unit
) {
    Column(Modifier.fillMaxWidth().background(CardWhite)) {
        if (panelOpen) {
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
                    onClick = onShareCalories
                )
                ChatFunctionTile(
                    iconRes = R.drawable.ic_food_rice,
                    label = stringResource(R.string.chat_share_rice),
                    onClick = onShareRice
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
        }
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                enabled = !sending,
                textStyle = TextStyle(fontFamily = CuteFont, fontSize = 15.sp, color = Ink),
                cursorBrush = SolidColor(Orange),
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
            Spacer(Modifier.width(10.dp))
            if (input.isNotBlank()) {
                CuteButton(
                    text = stringResource(R.string.chat_send),
                    onClick = onSend,
                    enabled = !sending,
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
                            onClick = onTogglePanel
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
    }
}

@Composable
private fun RelationBadge(labelRes: Int) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Orange.copy(alpha = 0.16f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.deco_heart),
            contentDescription = null,
            modifier = Modifier.size(11.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            stringResource(labelRes),
            fontFamily = CuteFont, fontSize = 10.sp, color = OrangeDeep
        )
    }
}

@Composable
private fun RelationDialog(
    current: String?,
    currentSince: String?,
    onDismiss: () -> Unit,
    onBind: (String) -> Unit
) {
    val options = listOf(
        "best_friend" to R.string.relation_best_friend,
        "lover" to R.string.relation_lover,
        "brother" to R.string.relation_brother,
        "bestie" to R.string.relation_bestie
    )
    var selected by remember(current) { mutableStateOf(current ?: "best_friend") }
    // 更换冷却：绑定后 30 天内只能保持不变，重复确认同一种关系不受限。
    val cooldownDays = remember(current, currentSince) { relationCooldownDays(current, currentSince) }
    val changeLocked = current != null && cooldownDays > 0 && selected != current

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(CardWhite)
                .padding(20.dp)
        ) {
            Text(
                stringResource(R.string.chat_relation_title),
                fontFamily = CuteFont, fontWeight = FontWeight.Bold,
                fontSize = 20.sp, color = Ink
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.chat_relation_hint),
                fontFamily = CuteFont, fontSize = 12.sp, lineHeight = 18.sp, color = InkLight
            )
            Spacer(Modifier.height(14.dp))
            options.chunked(2).forEach { rowOptions ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowOptions.forEach { (code, labelRes) ->
                        RelationOption(
                            labelRes = labelRes,
                            selected = selected == code,
                            modifier = Modifier.weight(1f),
                            onClick = { selected = code }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            current?.let { relation ->
                relationLabelRes(relation)?.let { labelRes ->
                    Text(
                        stringResource(R.string.chat_relation_current, stringResource(labelRes)),
                        fontFamily = CuteFont, fontSize = 12.sp, color = OrangeDeep
                    )
                }
            }
            if (cooldownDays > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.chat_relation_cooldown_hint, cooldownDays),
                    fontFamily = CuteFont, fontSize = 11.sp,
                    color = if (changeLocked) OrangeDeep else InkLight
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.common_cancel),
                    fontFamily = CuteFont, fontSize = 14.sp, color = InkLight,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                )
                Spacer(Modifier.width(8.dp))
                CuteButton(
                    text = stringResource(R.string.chat_relation_bind),
                    onClick = { onBind(selected) },
                    enabled = !changeLocked,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun RelationOption(
    labelRes: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Orange.copy(alpha = 0.16f) else ScreenBg)
            .border(1.5.dp, if (selected) Orange else Hairline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.deco_heart),
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(labelRes),
            fontFamily = CuteFont, fontSize = 13.sp,
            color = if (selected) OrangeDeep else Ink
        )
    }
}

private fun relationLabelRes(type: String?): Int? = when (type) {
    "best_friend" -> R.string.relation_best_friend
    "lover" -> R.string.relation_lover
    "brother" -> R.string.relation_brother
    "bestie" -> R.string.relation_bestie
    else -> null
}

private const val RELATION_COOLDOWN_DAYS = 30

/** 距可更换剩余天数（向上取整），无绑定或已过冷却返回 0。 */
private fun relationCooldownDays(current: String?, since: String?): Int {
    if (current.isNullOrBlank() || since.isNullOrBlank()) return 0
    val bound = parseChatInstant(since) ?: return 0
    val unlock = bound.plus(Duration.ofDays(RELATION_COOLDOWN_DAYS.toLong()))
    val seconds = Duration.between(Instant.now(), unlock).seconds
    if (seconds <= 0) return 0
    return ((seconds + 86_399) / 86_400).toInt()
}

private fun parseChatInstant(raw: String): Instant? =
    runCatching { Instant.parse(raw) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()

/**
 * 相邻两条消息间隔超过 5 分钟时返回居中时间分隔文案，否则返回 null。
 */
@Composable
private fun chatTimeDivider(previousRaw: String?, currentRaw: String): String? {
    val current = parseChatInstant(currentRaw) ?: return null
    if (previousRaw != null) {
        val previous = parseChatInstant(previousRaw) ?: return null
        if (Duration.between(previous, current).toMinutes() < 5) return null
    }
    val zone = ZoneId.systemDefault()
    val dateTime = current.atZone(zone)
    val now = ZonedDateTime.now(zone)
    val locale = Locale.getDefault()
    val time = DateTimeFormatter.ofPattern("HH:mm", locale).format(dateTime)
    return when {
        dateTime.toLocalDate() == now.toLocalDate() -> time
        dateTime.toLocalDate() == now.toLocalDate().minusDays(1) ->
            stringResource(R.string.date_yesterday) + " " + time
        dateTime.year == now.year -> DateTimeFormatter.ofPattern(
            if (locale.language == "zh") "M月d日 HH:mm" else "d MMM HH:mm",
            locale
        ).format(dateTime)
        else -> DateTimeFormatter.ofPattern(
            if (locale.language == "zh") "yyyy年M月d日 HH:mm" else "d MMM yyyy HH:mm",
            locale
        ).format(dateTime)
    }
}

@Composable
private fun MessageList(
    messages: List<FriendMessage>,
    currentUserId: String,
    friendUserId: String,
    friendAvatarEmoji: String,
    myAvatarEmoji: String,
    modifier: Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
            val mine = message.senderId == currentUserId
            val previous = messages.getOrNull(index - 1)
            val divider = chatTimeDivider(previous?.createdAt, message.createdAt)
            val sameSender = previous?.senderId == message.senderId
            val topPadding = when {
                index == 0 -> 0.dp
                divider != null -> 14.dp
                sameSender -> 6.dp
                else -> 12.dp
            }
            Column(Modifier.fillMaxWidth().padding(top = topPadding)) {
                divider?.let { label ->
                    Text(
                        label,
                        fontFamily = CuteFont, fontSize = 10.sp, color = InkFaint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    if (!mine) {
                        UserAvatar(
                            userId = friendUserId.ifBlank { message.senderId },
                            avatarEmoji = friendAvatarEmoji,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Box(
                        Modifier.weight(1f),
                        contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        val calorie = message.messageType == "calorie_share" ||
                                message.messageType == "rice_mountain"
                        // 四个角统一圆角，不做对话气泡的尾巴样式
                        val bubbleShape = RoundedCornerShape(18.dp)
                        Box(
                            Modifier
                                .widthIn(max = 260.dp)
                                .shadow(2.dp, bubbleShape)
                                .clip(bubbleShape)
                                .then(
                                    when {
                                        calorie -> Modifier.background(YellowSoft)
                                        mine -> Modifier.background(
                                            Brush.verticalGradient(listOf(Orange, OrangeDeep))
                                        )
                                        else -> Modifier.background(CardWhite)
                                    }
                                )
                                .border(
                                    1.dp,
                                    when {
                                        calorie -> Yellow.copy(alpha = 0.55f)
                                        mine -> Color.White.copy(alpha = 0.35f)
                                        else -> Hairline
                                    },
                                    bubbleShape
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            if (message.messageType == "rice_mountain") {
                                RiceMountainCard(
                                    bowls = riceBowlsOf(message.items),
                                    calories = message.calories
                                )
                            } else if (calorie) {
                                CalorieShareCard(message.id, message.calories, message.items)
                            } else {
                                Text(
                                    message.content,
                                    fontFamily = CuteFont, fontSize = 14.sp, lineHeight = 20.sp,
                                    color = if (mine) Color.White else Ink
                                )
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
        }
    }
}

@Composable
internal fun ChatPreviewContent() {
    ChatContent(
        state = ChatUiState(
            loading = false,
            friend = FriendEntry(
                            userId = "f1", nickname = "小兔", avatarEmoji = "",
                            friendsSince = "", sparkCount = 4
                        ),
            currentUserId = "me",
            messages = listOf(
                FriendMessage(1, "f1", "今天也好好吃饭", ""),
                FriendMessage(2, "me", "嗯嗯，一起打卡！", ""),
                FriendMessage(
                    3, "f1", "今日热量分享", "",
                    messageType = "calorie_share", calories = 720, entryDate = "2026-11-17",
                    items = listOf(
                        SharedFood("全麦吐司", "🍞", 80, 200, 0),
                        SharedFood("煎蛋", "🍳", 60, 130, 0),
                        SharedFood("鸡胸肉饭", "🍚", 300, 390, 1)
                    )
                ),
                FriendMessage(
                    4, "me", "今日米饭山", "",
                    messageType = "rice_mountain", calories = 348, entryDate = "2026-11-17",
                    items = listOf(SharedFood("米饭", "🍚", 300, 348, 1))
                )
            )
        ),
        friendId = "f1",
        onBack = {},
        onSend = {}
    )
}

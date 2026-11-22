package com.calorie.diary.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import com.calorie.diary.R
import com.calorie.diary.data.db.GroupMemberEntity
import com.calorie.diary.data.db.MemberTotal
import com.calorie.diary.data.network.NetworkMonitor
import com.calorie.diary.ui.components.CuteButton
import com.calorie.diary.ui.components.SoftCard
import com.calorie.diary.ui.components.SectionHeader
import com.calorie.diary.ui.components.UserAvatar
import com.calorie.diary.ui.components.openNetworkSettings
import com.calorie.diary.ui.home.appVm
import com.calorie.diary.ui.theme.BlueSoft
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.LavenderSoft
import com.calorie.diary.ui.theme.MintSoft
import com.calorie.diary.ui.theme.NumberFont
import com.calorie.diary.ui.theme.Orange
import com.calorie.diary.ui.theme.OrangeDeep
import com.calorie.diary.ui.theme.PinkSoft
import com.calorie.diary.ui.theme.YellowSoft

@Composable
fun GroupScreen(onBack: () -> Unit) {
    val vm = appVm()
    val activeId by vm.activeGroupId.collectAsState()
    val message by vm.groupMessage.collectAsState()
    val online by NetworkMonitor.online.collectAsState()

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackArrow(onBack)
            Spacer(Modifier.width(4.dp))
            GroupTitle(stringResource(R.string.group_title))
        }

        message?.let { msg ->
            Spacer(Modifier.height(10.dp))
            MessageBar(
                text = if (msg.startsWith("ok:"))
                    stringResource(R.string.group_success)
                else msg,
                isError = !msg.startsWith("ok:")
            )
            Spacer(Modifier.height(4.dp))
            LaunchedEffectOnce { vm.clearGroupMessage() }
        }

        // 离线时提前说明原因，避免用户点按钮后才看到失败提示
        if (!online) {
            Spacer(Modifier.height(10.dp))
            GroupOfflineHint()
        }

        Spacer(Modifier.height(12.dp))

        if (activeId == null) {
            NoRoomContent()
        } else {
            GroupDetailContent()
        }
    }
}

@Composable
private fun NoRoomContent() {
    val vm = appVm()
    var name by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("1800") }
    var code by remember { mutableStateOf("") }

    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        fill = CardWhite,
        contentPadding = PaddingValues(16.dp)
    ) {
        Column {
            SectionHeader(stringResource(R.string.group_create_title), emoji = "🏠")
            Spacer(Modifier.height(12.dp))
            CuteTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.group_name_label),
                placeholder = stringResource(R.string.group_name_placeholder)
            )
            Spacer(Modifier.height(10.dp))
            CuteTextField(
                value = goal,
                onValueChange = { goal = it.filter { c -> c.isDigit() }.take(5) },
                label = stringResource(R.string.group_goal_label),
                keyboardType = KeyboardType.Number
            )
            Spacer(Modifier.height(14.dp))
            CuteButton(
                stringResource(R.string.group_create_btn),
                { vm.createGroup(name, goal.toIntOrNull() ?: 1800) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        fill = CardWhite,
        contentPadding = PaddingValues(16.dp)
    ) {
        Column {
            SectionHeader(stringResource(R.string.group_join_title), emoji = "🔑")
            Spacer(Modifier.height(12.dp))
            CuteTextField(
                value = code,
                onValueChange = { code = it.filter { c -> c.isDigit() }.take(6) },
                label = stringResource(R.string.group_code_label),
                placeholder = stringResource(R.string.group_code_placeholder),
                keyboardType = KeyboardType.Number
            )
            Spacer(Modifier.height(14.dp))
            CuteButton(
                stringResource(R.string.group_join_btn),
                { vm.joinGroup(code) },
                modifier = Modifier.fillMaxWidth(),
                enabled = code.length == 6
            )
        }
    }
}

@Composable
private fun GroupDetailContent() {
    val vm = appVm()
    val activeId = vm.activeGroupId.collectAsState().value ?: return
    val group by vm.groups.collectAsState()
    val members by vm.groupMembers.collectAsState()
    val totals by vm.memberTotals.collectAsState()
    val entries by vm.groupEntries.collectAsState()
    val me by vm.identity.collectAsState()
    val current = group.firstOrNull { it.groupId == activeId }

    // 房间信息卡
    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        fill = YellowSoft,
        contentPadding = PaddingValues(16.dp)
    ) {
        Column {
            Text(
                current?.name ?: stringResource(R.string.group_title),
                fontFamily = CuteFont, fontSize = 19.sp, color = Ink
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.group_invite_code),
                    fontFamily = CuteFont, fontSize = 13.sp, color = InkLight
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(CardWhite)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        activeId,
                        fontFamily = NumberFont, fontSize = 16.sp, color = OrangeDeep
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    // 今日热量对比
    SectionHeader(stringResource(R.string.group_today_rank), emoji = "📊")
    Spacer(Modifier.height(10.dp))
    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        fill = CardWhite,
        contentPadding = PaddingValues(16.dp)
    ) {
        Column {
            val rows = members.map { m ->
                m to (totals.firstOrNull { it.ownerId == m.userId }?.memberTotal ?: 0)
            }.sortedByDescending { it.second }
            val goal = current?.calorieGoal ?: 1800
            rows.forEachIndexed { i, (m, total) ->
                MemberRankRow(
                    member = m,
                    total = total,
                    goal = goal,
                    isMe = m.userId == me?.userId,
                    rank = i + 1
                )
                if (i < rows.lastIndex) Spacer(Modifier.height(12.dp))
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    // 成员列表
    SectionHeader(
        stringResource(R.string.group_members),
        emoji = "👥",
        trailing = {
            Text(
                "${members.size}",
                fontFamily = NumberFont, fontSize = 14.sp, color = InkLight
            )
        }
    )
    Spacer(Modifier.height(10.dp))
    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        fill = CardWhite,
        contentPadding = PaddingValues(16.dp)
    ) {
        Column {
            members.forEachIndexed { i, m ->
                MemberRow(m, m.userId == me?.userId)
                if (i < members.lastIndex) Spacer(Modifier.height(10.dp))
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    // 共同时间线
    SectionHeader(stringResource(R.string.group_timeline), emoji = "🍽️")
    Spacer(Modifier.height(10.dp))
    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        fill = CardWhite,
        contentPadding = PaddingValues(16.dp)
    ) {
        Column {
            if (entries.isEmpty()) {
                Text(
                    stringResource(R.string.group_timeline_empty),
                    fontFamily = CuteFont, fontSize = 13.sp, color = InkLight,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            } else {
                entries.forEachIndexed { i, e ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(e.emoji, fontSize = 22.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                e.foodName,
                                fontFamily = CuteFont, fontSize = 14.sp, color = Ink
                            )
                            Text(
                                e.ownerName.ifBlank { stringResource(R.string.group_me) },
                                fontFamily = CuteFont, fontSize = 11.sp, color = InkLight
                            )
                        }
                        Text(
                            "${e.calories} ${stringResource(R.string.unit_kcal)}",
                            fontFamily = NumberFont, fontSize = 13.sp, color = Ink
                        )
                    }
                    if (i < entries.lastIndex) Spacer(Modifier.height(10.dp))
                }
            }
        }
    }

    Spacer(Modifier.height(18.dp))
    CuteButton(
        stringResource(R.string.group_leave),
        { vm.leaveActiveGroup() },
        modifier = Modifier.fillMaxWidth(),
        containerColor = PinkSoft,
        contentColor = OrangeDeep
    )
}

@Composable
private fun MemberRankRow(
    member: GroupMemberEntity,
    total: Int,
    goal: Int,
    isMe: Boolean,
    rank: Int
) {
    val barColors = listOf(MintSoft, BlueSoft, LavenderSoft, YellowSoft, PinkSoft)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (rank) {
                    1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "$rank"
                },
                fontSize = 16.sp
            )
            Spacer(Modifier.width(8.dp))
            UserAvatar(
                userId = member.userId,
                avatarEmoji = member.avatarEmoji,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                member.nickname + if (isMe) "（${stringResource(R.string.group_me)}）" else "",
                fontFamily = CuteFont, fontSize = 14.sp, color = Ink,
                modifier = Modifier.weight(1f)
            )
            Text(
                "$total",
                fontFamily = NumberFont, fontSize = 15.sp, color = Ink
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(InkLight.copy(alpha = 0.18f))
        ) {
            val fraction = (total.toFloat() / goal).coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(barColors[(rank - 1).coerceAtMost(barColors.lastIndex)])
            )
        }
    }
}

@Composable
private fun MemberRow(member: GroupMemberEntity, isMe: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            UserAvatar(
                userId = member.userId,
                avatarEmoji = member.avatarEmoji,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            member.nickname + if (isMe) "（${stringResource(R.string.group_me)}）" else "",
            fontFamily = CuteFont, fontSize = 14.sp, color = Ink,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CuteTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(modifier) {
        Text(label, fontFamily = CuteFont, fontSize = 13.sp, color = InkLight)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardWhite)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(fontFamily = CuteFont, color = Ink, fontSize = 16.sp),
                cursorBrush = SolidColor(Orange),
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            placeholder,
                            fontFamily = CuteFont, fontSize = 15.sp, color = InkLight
                        )
                    }
                    inner()
                }
            )
        }
    }
}

@Composable
private fun MessageBar(text: String, isError: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isError) PinkSoft else MintSoft)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text,
            fontFamily = CuteFont, fontSize = 13.sp,
            color = if (isError) OrangeDeep else Ink
        )
    }
}

/** 离线提示：说明建群/加入需要联网，并提供直达系统设置的入口。 */
@Composable
private fun GroupOfflineHint() {
    val context = LocalContext.current
    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        fill = PinkSoft,
        contentPadding = PaddingValues(14.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                "📶 " + stringResource(R.string.net_offline_title),
                fontFamily = CuteFont, fontSize = 14.sp, color = OrangeDeep
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.group_offline_hint),
                fontFamily = CuteFont, fontSize = 12.sp, color = InkLight
            )
            Spacer(Modifier.height(10.dp))
            CuteButton(
                text = stringResource(R.string.net_open_settings),
                onClick = { openNetworkSettings(context) },
                containerColor = CardWhite,
                contentColor = OrangeDeep,
                shadowColor = Ink.copy(alpha = 0.35f),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    androidx.compose.material3.Text(
        text, fontFamily = CuteFont, fontSize = 22.sp, color = Ink
    )
}

@Composable
private fun BackArrow(onBack: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onBack
            )
            .padding(8.dp)
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.icon_back),
            contentDescription = null,
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
private fun LaunchedEffectOnce(block: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2200)
        block()
    }
}

@Composable
internal fun GroupPreviewContent() {
    val code = "482910"
    val members = listOf(
        GroupMemberEntity(code, "u1", "小橙", "", 0, 0),
        GroupMemberEntity(code, "u2", "小栗", "", 0, 0),
        GroupMemberEntity(code, "u3", "小桃", "", 0, 0)
    )
    val totals = listOf(
        MemberTotal("u1", "小橙", 1240),
        MemberTotal("u2", "小栗", 1560),
        MemberTotal("u3", "小桃", 980)
    )

    Column {
        // 房间信息卡
        SoftCard(
            modifier = Modifier.fillMaxWidth(),
            fill = YellowSoft,
            contentPadding = PaddingValues(16.dp)
        ) {
            Column {
                Text("减脂搭子群", fontFamily = CuteFont, fontSize = 19.sp, color = Ink)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.group_invite_code),
                        fontFamily = CuteFont, fontSize = 13.sp, color = InkLight
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(CardWhite)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(code, fontFamily = NumberFont, fontSize = 16.sp, color = OrangeDeep)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader(stringResource(R.string.group_today_rank), emoji = "📊")
        Spacer(Modifier.height(10.dp))
        SoftCard(
            modifier = Modifier.fillMaxWidth(),
            fill = CardWhite,
            contentPadding = PaddingValues(16.dp)
        ) {
            Column {
                members.map { m ->
                    m to (totals.firstOrNull { it.ownerId == m.userId }?.memberTotal ?: 0)
                }.sortedByDescending { it.second }.forEachIndexed { i, (m, total) ->
                    MemberRankRow(m, total, 1800, m.userId == "u1", i + 1)
                    if (i < members.lastIndex) Spacer(Modifier.height(12.dp))
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader(stringResource(R.string.group_members), emoji = "👥")
        Spacer(Modifier.height(10.dp))
        SoftCard(
            modifier = Modifier.fillMaxWidth(),
            fill = CardWhite,
            contentPadding = PaddingValues(16.dp)
        ) {
            Column {
                members.forEachIndexed { i, m ->
                    MemberRow(m, m.userId == "u1")
                    if (i < members.lastIndex) Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

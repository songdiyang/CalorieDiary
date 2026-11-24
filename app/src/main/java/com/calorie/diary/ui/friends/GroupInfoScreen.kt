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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calorie.diary.R
import com.calorie.diary.data.supabase.GroupInfo
import com.calorie.diary.data.supabase.GroupInfoMember
import com.calorie.diary.ui.components.SoftCard
import com.calorie.diary.ui.components.UserAvatar
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
import com.calorie.diary.viewmodel.GroupInfoUiState
import com.calorie.diary.viewmodel.GroupInfoViewModel
import kotlinx.coroutines.delay

/** 群聊主页：群成员、群名称、群号、群公告与群管理操作。 */
@Composable
fun GroupInfoScreen(
    groupId: String,
    onBack: () -> Unit,
    onGroupClosed: () -> Unit = onBack,
    onOpenAnnouncement: () -> Unit = {}
) {
    val vm: GroupInfoViewModel = viewModel(key = "group-info-$groupId")
    val state by vm.uiState.collectAsState()

    LaunchedEffect(groupId) { vm.load(groupId) }
    LaunchedEffect(state.notice) {
        if (state.notice != null) {
            delay(2200)
            vm.clearNotice()
        }
    }
    // 解散或退出成功后群已不可见，直接回到消息页。
    LaunchedEffect(state.closed) {
        if (state.closed) onGroupClosed()
    }

    GroupInfoContent(
        state = state,
        onBack = onBack,
        onInviteMembers = vm::inviteMembers,
        onUpdateName = vm::updateName,
        onSetSearchable = vm::setSearchable,
        onOpenAnnouncement = onOpenAnnouncement,
        onDissolveGroup = vm::dissolveGroup,
        onLeaveGroup = vm::leaveGroup,
        onTransferOwnership = vm::transferOwnership
    )
}

@Composable
private fun GroupInfoContent(
    state: GroupInfoUiState,
    onBack: () -> Unit,
    onInviteMembers: (List<String>) -> Unit,
    onUpdateName: (String) -> Unit,
    onSetSearchable: (Boolean) -> Unit,
    onOpenAnnouncement: () -> Unit,
    onDissolveGroup: () -> Unit,
    onLeaveGroup: () -> Unit,
    onTransferOwnership: (String) -> Unit
) {
    var showInvite by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(false) }
    var showTransfer by remember { mutableStateOf(false) }
    var transferTarget by remember { mutableStateOf<GroupInfoMember?>(null) }
    var showDissolve by remember { mutableStateOf(false) }
    var showLeave by remember { mutableStateOf(false) }
    val info = state.info

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
            Text(
                stringResource(R.string.group_info_title),
                fontFamily = CuteFont, fontSize = 20.sp, color = Ink
            )
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
                contentPadding = PaddingValues(12.dp)
            ) {
                item(key = "header") { GroupHeaderCard(info) }
                item(key = "members") {
                    MembersCard(
                        members = state.members,
                        inviting = state.inviting,
                        onInvite = { showInvite = true }
                    )
                }
                item(key = "name") {
                    GroupNameCard(
                        info = info,
                        editable = state.isOwner && !state.busy,
                        onEdit = { editName = true }
                    )
                }
                item(key = "announcement") {
                    GroupAnnouncementCard(
                        info = info,
                        enabled = !state.busy,
                        onClick = onOpenAnnouncement
                    )
                }
                item(key = "searchable") {
                    GroupSearchableCard(
                        info = info,
                        canEdit = state.isOwner,
                        enabled = !state.busy,
                        onToggle = onSetSearchable
                    )
                }
                item(key = "manage") {
                    ManageCard(
                        isOwner = state.isOwner,
                        enabled = !state.busy,
                        onTransfer = { showTransfer = true },
                        onDissolve = { showDissolve = true },
                        onLeave = { showLeave = true }
                    )
                }
            }
        }
    }

    if (showInvite) {
        InviteFriendsDialog(
            friends = state.friends,
            memberIds = state.memberIds,
            inviting = state.inviting,
            onInvite = { ids ->
                showInvite = false
                onInviteMembers(ids)
            },
            onDismiss = { showInvite = false }
        )
    }
    if (editName && info != null) {
        EditTextDialog(
            title = stringResource(R.string.group_info_name_section),
            initial = info.name,
            hint = stringResource(R.string.group_info_name_hint),
            maxLength = 30,
            singleLine = true,
            busy = state.busy,
            onSave = {
                editName = false
                onUpdateName(it)
            },
            onDismiss = { editName = false }
        )
    }
    if (showTransfer) {
        TransferOwnershipDialog(
            members = state.members,
            currentUserId = state.currentUserId,
            target = transferTarget,
            busy = state.busy,
            onPick = { transferTarget = it },
            onConfirm = {
                transferTarget?.let { onTransferOwnership(it.userId) }
                showTransfer = false
                transferTarget = null
            },
            onDismiss = {
                showTransfer = false
                transferTarget = null
            }
        )
    }
    if (showDissolve) {
        ManageConfirmDialog(
            title = stringResource(R.string.group_info_dissolve_group),
            message = stringResource(R.string.group_info_dissolve_confirm),
            confirmLabel = stringResource(R.string.group_info_dissolve_group),
            busy = state.busy,
            onConfirm = {
                showDissolve = false
                onDissolveGroup()
            },
            onDismiss = { showDissolve = false }
        )
    }
    if (showLeave) {
        ManageConfirmDialog(
            title = stringResource(R.string.group_info_leave_group),
            message = stringResource(R.string.group_info_leave_confirm),
            confirmLabel = stringResource(R.string.group_info_leave_group),
            busy = state.busy,
            onConfirm = {
                showLeave = false
                onLeaveGroup()
            },
            onDismiss = { showLeave = false }
        )
    }
}

@Composable
private fun GroupHeaderCard(info: GroupInfo?) {
    info ?: return
    SoftCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(MintSoft),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_friends),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    info.name,
                    fontFamily = CuteFont, fontSize = 18.sp, color = Ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.group_info_number, info.groupId),
                    fontFamily = NumberFont, fontSize = 11.sp, color = InkLight
                )
            }
        }
    }
}

@Composable
private fun MembersCard(
    members: List<GroupInfoMember>,
    inviting: Boolean,
    onInvite: () -> Unit
) {
    SoftCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.group_info_members_section),
                    fontFamily = CuteFont, fontSize = 16.sp, color = Ink
                )
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.group_info_member_count, members.size),
                    fontFamily = CuteFont, fontSize = 12.sp, color = InkLight
                )
            }
            Spacer(Modifier.height(12.dp))
            // 成员格子按每行 5 个排布，最后一格固定是邀请入口。
            val cells = members.size + 1
            (0 until cells).chunked(5).forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    row.forEach { index ->
                        Box(Modifier.weight(1f)) {
                            if (index < members.size) {
                                MemberCell(members[index])
                            } else {
                                InviteCell(enabled = !inviting, onInvite = onInvite)
                            }
                        }
                    }
                    repeat(5 - row.size) { Box(Modifier.weight(1f)) {} }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun MemberCell(member: GroupInfoMember) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            UserAvatar(
                userId = member.userId,
                avatarEmoji = member.avatarEmoji,
                modifier = Modifier.fillMaxSize()
            )
            if (member.isOwner) {
                Image(
                    painter = painterResource(R.drawable.deco_crown),
                    contentDescription = stringResource(R.string.group_owner_badge),
                    modifier = Modifier.align(Alignment.TopEnd).size(14.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            member.nickname,
            fontFamily = CuteFont, fontSize = 11.sp, color = Ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun InviteCell(enabled: Boolean, onInvite: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onInvite
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(ScreenBg)
                .border(1.dp, Hairline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("+", fontFamily = CuteFont, fontSize = 22.sp, color = OrangeDeep)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.group_invite_action),
            fontFamily = CuteFont, fontSize = 11.sp, color = InkLight,
            maxLines = 1
        )
    }
}

/** 群号搜索开关：仅群主可切换；关闭后他人无法凭群号加入，只能被邀请。 */
@Composable
private fun GroupSearchableCard(
    info: GroupInfo?,
    canEdit: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    info ?: return
    SoftCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.group_info_searchable_title),
                    fontFamily = CuteFont, fontSize = 16.sp, color = Ink
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(
                        if (info.searchable) R.string.group_info_searchable_hint_on
                        else R.string.group_info_searchable_hint_off
                    ),
                    fontFamily = CuteFont,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = InkLight
                )
                if (!canEdit) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.group_info_searchable_owner_only),
                        fontFamily = CuteFont, fontSize = 10.sp, color = InkLight
                    )
                }
            }
            Switch(
                checked = info.searchable,
                onCheckedChange = onToggle,
                enabled = canEdit && enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CardWhite,
                    checkedTrackColor = Orange,
                    uncheckedThumbColor = CardWhite,
                    uncheckedTrackColor = Hairline
                )
            )
        }
    }
}

@Composable
private fun GroupNameCard(info: GroupInfo?, editable: Boolean, onEdit: () -> Unit) {
    info ?: return
    SoftCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = editable,
                    onClick = onEdit
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.group_info_name_section),
                fontFamily = CuteFont, fontSize = 16.sp, color = Ink,
                modifier = Modifier.weight(1f)
            )
            Text(
                info.name,
                fontFamily = CuteFont, fontSize = 13.sp, color = InkLight,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 170.dp)
            )
            if (editable) {
                Spacer(Modifier.width(6.dp))
                Text("›", fontFamily = CuteFont, fontSize = 18.sp, color = InkLight)
            }
        }
    }
}

/** 群公告入口行：所有成员可进入公告页查看，群主在公告页内编辑。 */
@Composable
private fun GroupAnnouncementCard(info: GroupInfo?, enabled: Boolean, onClick: () -> Unit) {
    info ?: return
    SoftCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                    onClick = onClick
                )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.group_info_announcement_section),
                    fontFamily = CuteFont, fontSize = 16.sp, color = Ink,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "›", fontFamily = CuteFont, fontSize = 18.sp, color = InkLight
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                info.announcement.ifBlank {
                    stringResource(R.string.group_info_announcement_empty)
                },
                fontFamily = CuteFont, fontSize = 13.sp,
                color = if (info.announcement.isBlank()) InkLight else Ink,
                lineHeight = 20.sp
            )
        }
    }
}

/** 群名称编辑弹窗。 */
@Composable
private fun EditTextDialog(
    title: String,
    initial: String,
    hint: String,
    maxLength: Int,
    singleLine: Boolean,
    busy: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(maxLength) },
                textStyle = TextStyle(fontFamily = CuteFont, fontSize = 15.sp, color = Ink),
                cursorBrush = SolidColor(Orange),
                singleLine = singleLine,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (singleLine) 0.dp else 120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ScreenBg)
                    .border(1.dp, Hairline, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                decorationBox = { inner ->
                    if (text.isEmpty()) {
                        Text(hint, fontFamily = CuteFont, fontSize = 14.sp, color = InkLight)
                    }
                    inner()
                }
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = !busy && text.isNotBlank()) {
                Text(stringResource(R.string.group_info_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

/** 群管理操作区：群主可转让/解散，普通成员可退出。 */
@Composable
private fun ManageCard(
    isOwner: Boolean,
    enabled: Boolean,
    onTransfer: () -> Unit,
    onDissolve: () -> Unit,
    onLeave: () -> Unit
) {
    SoftCard(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        if (isOwner) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    ManageActionButton(
                        stringResource(R.string.group_info_transfer_owner),
                        enabled,
                        onTransfer
                    )
                }
                Box(Modifier.weight(1f)) {
                    ManageActionButton(
                        stringResource(R.string.group_info_dissolve_group),
                        enabled,
                        onDissolve
                    )
                }
            }
        } else {
            ManageActionButton(
                stringResource(R.string.group_info_leave_group),
                enabled,
                onLeave
            )
        }
    }
}

@Composable
private fun ManageActionButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(CardWhite)
            .border(1.dp, Hairline, RoundedCornerShape(50))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontFamily = CuteFont, fontSize = 14.sp, color = OrangeDeep)
    }
}

/** 转让群主：先选成员，再二次确认。 */
@Composable
private fun TransferOwnershipDialog(
    members: List<GroupInfoMember>,
    currentUserId: String,
    target: GroupInfoMember?,
    busy: Boolean,
    onPick: (GroupInfoMember) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    // 群主自己不能被选为新群主。
    val candidates = members.filter { it.userId != currentUserId }
    if (target == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.group_info_transfer_title)) },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    if (candidates.isEmpty()) {
                        Text(
                            stringResource(R.string.group_transfer_no_members),
                            fontFamily = CuteFont, fontSize = 12.sp, color = InkLight,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        LazyColumn(Modifier.fillMaxWidth()) {
                            items(candidates, key = { it.userId }) { member ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            enabled = !busy,
                                            onClick = { onPick(member) }
                                        )
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UserAvatar(
                                        userId = member.userId,
                                        avatarEmoji = member.avatarEmoji,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        member.nickname,
                                        fontFamily = CuteFont, fontSize = 14.sp, color = Ink,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.group_info_transfer_title)) },
            text = {
                Text(stringResource(R.string.group_info_transfer_confirm, target.nickname))
            },
            confirmButton = {
                TextButton(onClick = onConfirm, enabled = !busy) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

/** 解散群聊 / 退出群聊共用的确认弹窗。 */
@Composable
private fun ManageConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

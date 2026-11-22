package com.calorie.diary.ui.friends

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calorie.diary.R
import com.calorie.diary.ui.components.SoftCard
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Hairline
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.NumberFont
import com.calorie.diary.ui.theme.Orange
import com.calorie.diary.ui.theme.OrangeDeep
import com.calorie.diary.ui.theme.ScreenBg
import com.calorie.diary.viewmodel.GroupAnnouncementUiState
import com.calorie.diary.viewmodel.GroupAnnouncementViewModel
import kotlinx.coroutines.delay

/** 群公告页：展示公告全文，群主可编辑并发布。 */
@Composable
fun GroupAnnouncementScreen(groupId: String, onBack: () -> Unit) {
    val vm: GroupAnnouncementViewModel = viewModel(key = "group-announcement-$groupId")
    val state by vm.uiState.collectAsState()
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    LaunchedEffect(groupId) { vm.load(groupId) }
    // 保存成功后自动退出编辑态，并展示一次「已保存」提示。
    LaunchedEffect(state.notice) {
        if (state.notice != null) {
            editing = false
            delay(2200)
            vm.clearNotice()
        }
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
                        // 编辑态先退出编辑，避免误触丢草稿后直接离开。
                        onClick = { if (editing) editing = false else onBack() }
                    )
                    .padding(8.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.group_announcement_title),
                fontFamily = CuteFont, fontSize = 20.sp, color = Ink,
                modifier = Modifier.weight(1f)
            )
            if (state.isOwner) {
                if (editing) {
                    TextButton(
                        onClick = { vm.saveAnnouncement(draft) },
                        enabled = !state.busy && draft.isNotBlank()
                    ) {
                        Text(
                            stringResource(R.string.group_announcement_publish),
                            fontFamily = CuteFont, fontSize = 14.sp, color = OrangeDeep
                        )
                    }
                } else {
                    TextButton(
                        onClick = {
                            draft = state.info?.announcement.orEmpty()
                            editing = true
                        },
                        enabled = !state.busy
                    ) {
                        Text(
                            stringResource(R.string.group_announcement_edit),
                            fontFamily = CuteFont, fontSize = 14.sp, color = OrangeDeep
                        )
                    }
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
        } else if (editing) {
            AnnouncementEditor(
                draft = draft,
                onDraftChange = { draft = it },
                modifier = Modifier.weight(1f)
            )
        } else {
            AnnouncementViewer(
                state = state,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 查看态：完整展示公告内容，群主空态时给出发布引导。 */
@Composable
private fun AnnouncementViewer(state: GroupAnnouncementUiState, modifier: Modifier = Modifier) {
    val announcement = state.info?.announcement.orEmpty()
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        SoftCard(Modifier.fillMaxWidth()) {
            Text(
                announcement.ifBlank { stringResource(R.string.group_info_announcement_empty) },
                fontFamily = CuteFont, fontSize = 15.sp,
                color = if (announcement.isBlank()) InkLight else Ink,
                lineHeight = 24.sp
            )
        }
        if (announcement.isBlank() && state.isOwner) {
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.group_announcement_owner_hint),
                fontFamily = CuteFont, fontSize = 12.sp, color = InkLight,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

/** 编辑态：整页文本输入 + 字数统计。 */
@Composable
private fun AnnouncementEditor(
    draft: String,
    onDraftChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().padding(12.dp)) {
        SoftCard(Modifier.fillMaxWidth()) {
            BasicTextField(
                value = draft,
                onValueChange = { onDraftChange(it.take(MAX_ANNOUNCEMENT_LENGTH)) },
                textStyle = TextStyle(fontFamily = CuteFont, fontSize = 15.sp, color = Ink),
                cursorBrush = SolidColor(Orange),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 280.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ScreenBg)
                    .border(1.dp, Hairline, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                decorationBox = { inner ->
                    if (draft.isEmpty()) {
                        Text(
                            stringResource(R.string.group_info_announcement_hint),
                            fontFamily = CuteFont, fontSize = 14.sp, color = InkLight
                        )
                    }
                    inner()
                }
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "${draft.length}/$MAX_ANNOUNCEMENT_LENGTH",
            fontFamily = NumberFont, fontSize = 11.sp, color = InkLight,
            modifier = Modifier.align(Alignment.End)
        )
    }
}

/** 与 update_group_info RPC 的校验上限保持一致。 */
private const val MAX_ANNOUNCEMENT_LENGTH = 500

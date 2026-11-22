package com.calorie.diary.ui.friends

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorie.diary.R
import com.calorie.diary.data.supabase.FriendEntry
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.InkLight

/** 邀请好友进群的勾选弹窗，群聊页与群聊主页共用。 */
@Composable
internal fun InviteFriendsDialog(
    friends: List<FriendEntry>,
    memberIds: Set<String>,
    inviting: Boolean,
    onInvite: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    // 已在群里的好友不再出现在候选列表里，避免重复邀请。
    val candidates = friends.filterNot { it.userId in memberIds }
    val selected = remember { mutableStateListOf<String>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.group_invite_friends_label)) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                if (candidates.isEmpty()) {
                    Text(
                        stringResource(R.string.group_invite_none),
                        fontFamily = CuteFont, fontSize = 12.sp, color = InkLight,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyColumn(Modifier.fillMaxWidth()) {
                        items(candidates, key = { it.userId }) { friend ->
                            FriendSelectRow(
                                friend = friend,
                                selected = friend.userId in selected,
                                enabled = !inviting
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onInvite(selected.toList()) },
                enabled = !inviting && selected.isNotEmpty()
            ) {
                Text(stringResource(R.string.group_invite_action))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

package com.calorie.diary.ui.friends

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorie.diary.data.supabase.FriendEntry
import com.calorie.diary.ui.components.UserAvatar
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Hairline
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.OrangeDeep

/** 好友多选行：头像 + 昵称 + 右侧勾选圈，创建群聊和群内邀请共用。 */
@Composable
internal fun FriendSelectRow(
    friend: FriendEntry,
    selected: Boolean,
    enabled: Boolean = true,
    onToggle: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle
            )
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            UserAvatar(
                userId = friend.userId,
                avatarEmoji = friend.avatarEmoji,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                friend.nickname,
                fontFamily = CuteFont,
                fontSize = 14.sp,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                friend.publicId.ifBlank { friend.userId },
                fontFamily = CuteFont,
                fontSize = 10.sp,
                color = InkLight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) OrangeDeep else CardWhite)
                .border(1.5.dp, if (selected) OrangeDeep else Hairline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Text("✓", fontFamily = CuteFont, fontSize = 13.sp, color = CardWhite)
            }
        }
    }
}

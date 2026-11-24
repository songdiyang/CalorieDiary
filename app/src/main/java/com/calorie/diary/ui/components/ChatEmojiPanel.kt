package com.calorie.diary.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorie.diary.R
import com.calorie.diary.data.ChatEmojis
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Orange
import com.calorie.diary.ui.theme.OrangeDeep
import com.calorie.diary.ui.theme.ScreenBg

/** 聊天表情面板：普通表情插入输入框，米饭为特殊表情，点击直接发送并触发全屏特效。 */
@Composable
fun ChatEmojiPanel(
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(ScreenBg)
            .padding(horizontal = 10.dp, vertical = 12.dp)
    ) {
        ChatEmojis.all.chunked(7).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { emoji ->
                    EmojiCell(
                        emoji = emoji,
                        special = ChatEmojis.isRice(emoji),
                        modifier = Modifier.weight(1f),
                        onClick = { onPick(emoji) }
                    )
                }
                repeat(7 - row.size) {
                    Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun EmojiCell(
    emoji: String,
    special: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (special) Orange.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(4.dp))
        val artRes = ChatEmojiArt.resId(emoji)
        if (artRes != null) {
            Image(
                painter = painterResource(artRes),
                contentDescription = emoji,
                modifier = Modifier.size(26.dp)
            )
        } else {
            Text(emoji, fontSize = 22.sp)
        }
        if (special) {
            Text(
                stringResource(R.string.chat_emoji_special),
                fontFamily = CuteFont,
                fontSize = 8.sp,
                color = OrangeDeep
            )
        }
    }
}
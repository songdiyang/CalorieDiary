package com.calorie.diary.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorie.diary.R
import com.calorie.diary.data.supabase.DefaultAvatarCatalog
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Hairline
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.NumberFont
import com.calorie.diary.ui.theme.Orange
import com.calorie.diary.ui.theme.YellowSoft

@Composable
fun CuteSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardWhite)
            .border(1.dp, Hairline, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = Orange
        )
        Box(Modifier.padding(start = 10.dp)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                enabled = enabled,
                textStyle = TextStyle(fontFamily = CuteFont, color = Ink, fontSize = 16.sp),
                cursorBrush = SolidColor(Orange),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth()
            )
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    fontFamily = CuteFont,
                    color = InkLight,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
fun CuteChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = Orange
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) selectedColor else CardWhite)
            .then(
                if (selected) Modifier
                else Modifier.border(1.dp, Hairline, shape)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            fontFamily = CuteFont,
            fontSize = 14.sp,
            color = if (selected) Color.White else Ink
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (emoji != null) {
                Text(emoji, fontSize = 20.sp, modifier = Modifier.padding(end = 8.dp))
            }
            Text(title, fontFamily = CuteFont, fontSize = 19.sp, color = Ink)
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun EmptyState(
    hint: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            hint,
            fontFamily = CuteFont,
            color = InkLight,
            fontSize = 14.sp
        )
    }
}

@Composable
fun CuteNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String = "",
    label: String = "",
    keyboardType: KeyboardType = KeyboardType.Number
) {
    val shape = RoundedCornerShape(18.dp)
    Column(modifier) {
        if (label.isNotBlank()) {
            Text(
                label,
                fontFamily = CuteFont,
                color = Ink,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(CardWhite)
                .border(1.dp, Hairline, shape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = NumberFont,
                    color = Ink,
                    fontSize = 18.sp
                ),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                cursorBrush = SolidColor(Orange),
                modifier = Modifier.weight(1f)
            )
            if (suffix.isNotBlank()) {
                Text(suffix, fontFamily = CuteFont, color = InkLight, fontSize = 14.sp)
            }
        }
    }
}

private val defaultAvatarResources = listOf(
    R.drawable.avatar_default_rabbit,
    R.drawable.avatar_default_fox
)

internal fun defaultAvatarId(userId: String, selectedId: String? = null): String {
    selectedId?.takeIf { it in DefaultAvatarCatalog.ids }?.let { return it }
    if (userId.isBlank()) return DefaultAvatarCatalog.RABBIT
    val index = Math.floorMod(userId.hashCode(), DefaultAvatarCatalog.ids.size)
    return DefaultAvatarCatalog.ids[index]
}

internal fun defaultAvatarResource(userId: String, selectedId: String? = null): Int {
    val index = DefaultAvatarCatalog.ids.indexOf(defaultAvatarId(userId, selectedId))
    return defaultAvatarResources[index]
}

/** 头像框占容器的比例：圆盘比头像小，角色自然溢出框外。 */
private const val AvatarFrameRatio = 0.88f

@Composable
fun DefaultAvatar(
    userId: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    avatarId: String? = null
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize(AvatarFrameRatio)
                .clip(CircleShape)
                .background(YellowSoft)
                .border(1.5.dp, Orange.copy(alpha = 0.45f), CircleShape)
        )
        Image(
            painter = painterResource(defaultAvatarResource(userId, avatarId)),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

/**
 * 聊天消息内容：若整条消息就是一个已映射的表情贴纸，用贴纸渲染；
 * 否则按普通文本渲染。
 */
@Composable
fun ChatMessageContent(
    content: String,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    color: Color,
    modifier: Modifier = Modifier
) {
    val artRes = ChatEmojiArt.resId(content)
    if (artRes != null) {
        Image(
            painter = painterResource(artRes),
            contentDescription = content,
            modifier = modifier.size(34.dp)
        )
    } else {
        Text(
            text = content,
            fontFamily = fontFamily,
            fontSize = fontSize,
            lineHeight = lineHeight,
            color = color,
            modifier = modifier
        )
    }
}

@Composable
fun UserAvatar(
    userId: String,
    avatarEmoji: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    DefaultAvatar(
        userId = userId,
        modifier = modifier,
        contentDescription = contentDescription,
        avatarId = DefaultAvatarCatalog.resolve(avatarEmoji)
    )
}

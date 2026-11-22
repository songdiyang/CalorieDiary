package com.calorie.diary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorie.diary.R
import com.calorie.diary.data.supabase.DefaultAvatarCatalog
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.OrangeDeep
import com.calorie.diary.ui.theme.YellowSoft

/** 选头像弹窗：我的页头像、设置页头像共用，保证两处入口展示和选择逻辑一致。 */
@Composable
fun AvatarPickerDialog(
    userId: String,
    selectedId: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onOpenPersonal: (() -> Unit)? = null
) {
    val effectiveId = defaultAvatarId(userId, selectedId)
    val dismissButton: (@Composable () -> Unit)? = onOpenPersonal?.let { openPersonal ->
        {
            TextButton(onClick = openPersonal) {
                Text(stringResource(R.string.personal_page_title))
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.profile_choose_avatar),
                fontFamily = CuteFont, fontSize = 20.sp, color = Ink
            )
        },
        text = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
            ) {
                DefaultAvatarCatalog.ids.forEach { id ->
                    val labelRes = when (id) {
                        DefaultAvatarCatalog.RABBIT -> R.string.profile_avatar_rabbit
                        else -> R.string.profile_avatar_fox
                    }
                    val selected = id == effectiveId
                    Column(
                        Modifier
                            .width(104.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) YellowSoft else Color.Transparent)
                            .border(
                                2.dp,
                                if (selected) OrangeDeep else InkLight.copy(alpha = 0.18f),
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { onSelect(id) }
                            .padding(horizontal = 3.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        DefaultAvatar(
                            userId = userId,
                            avatarId = id,
                            contentDescription = stringResource(labelRes),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(labelRes),
                            fontFamily = CuteFont,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = Ink,
                            maxLines = 2,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
        dismissButton = dismissButton
    )
}

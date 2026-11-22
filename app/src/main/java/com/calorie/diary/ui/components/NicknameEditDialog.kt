package com.calorie.diary.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorie.diary.R
import com.calorie.diary.data.supabase.AuthInputValidator
import com.calorie.diary.data.supabase.NicknameChangeStatus
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.OrangeDeep
import com.calorie.diary.ui.theme.ScreenBg

/**
 * 修改用户名弹窗：我的页、个人主页、设置页共用。
 * 7 天 3 次的限制由服务端 update_nickname 强制执行，这里只负责输入校验与剩余次数展示。
 */
@Composable
fun NicknameEditDialog(
    currentNickname: String,
    status: NicknameChangeStatus?,
    busy: Boolean,
    @StringRes errorRes: Int?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
    onClearError: () -> Unit
) {
    var value by remember(currentNickname) { mutableStateOf(currentNickname) }
    val remaining = status?.remaining ?: 3
    val exhausted = remaining <= 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.nickname_edit_title),
                fontFamily = CuteFont, fontSize = 20.sp, color = Ink
            )
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                BasicTextField(
                    value = value,
                    onValueChange = {
                        // 与服务端一致按码点截断，避免 emoji 被截成半个字。
                        value = clampCodePoints(it, AuthInputValidator.MAX_NICKNAME_LENGTH)
                        if (errorRes != null) onClearError()
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = CuteFont, fontSize = 16.sp, color = Ink
                    ),
                    cursorBrush = SolidColor(OrangeDeep),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(ScreenBg)
                                .border(
                                    1.dp, InkLight.copy(alpha = 0.22f),
                                    RoundedCornerShape(14.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            if (value.isEmpty()) {
                                Text(
                                    stringResource(R.string.nickname_edit_hint),
                                    fontFamily = CuteFont, fontSize = 16.sp,
                                    color = InkLight.copy(alpha = 0.7f)
                                )
                            } else {
                                inner()
                            }
                        }
                    }
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.nickname_change_rule),
                    fontFamily = CuteFont, fontSize = 12.sp, color = InkLight
                )
                Spacer(Modifier.height(4.dp))
                if (status != null) {
                    Text(
                        if (exhausted) {
                            stringResource(R.string.nickname_change_limit)
                        } else {
                            stringResource(R.string.nickname_changes_left, remaining)
                        },
                        fontFamily = CuteFont, fontSize = 12.sp,
                        color = if (exhausted) OrangeDeep else InkLight
                    )
                }
                if (errorRes != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(errorRes),
                        fontFamily = CuteFont, fontSize = 12.sp, color = OrangeDeep,
                        textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(value) },
                enabled = !busy && !exhausted && value.trim().isNotEmpty()
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

private fun clampCodePoints(text: String, max: Int): String {
    if (text.codePointCount(0, text.length) <= max) return text
    val end = text.offsetByCodePoints(0, max)
    return text.substring(0, end)
}

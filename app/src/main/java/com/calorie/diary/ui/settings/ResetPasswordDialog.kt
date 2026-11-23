package com.calorie.diary.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.calorie.diary.R
import com.calorie.diary.ui.components.CuteButton
import com.calorie.diary.ui.profile.AccountTextField
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Danger
import com.calorie.diary.ui.theme.Hairline
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.OrangeDeep
import com.calorie.diary.viewmodel.PasswordResetUiState
import kotlinx.coroutines.delay

/** 设置页的重置密码弹窗：邮箱固定为当前账号，验证码验证后可设置新密码。 */
@Composable
fun ResetPasswordDialog(
    email: String,
    state: PasswordResetUiState,
    onSendCode: () -> Unit,
    onConfirm: (code: String, newPassword: String) -> Unit,
    onDismiss: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }
    var resendSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.codeSentCount) {
        if (state.codeSentCount > 0) resendSeconds = 60
    }
    LaunchedEffect(resendSeconds) {
        if (resendSeconds > 0) {
            delay(1000L)
            resendSeconds -= 1
        }
    }

    Dialog(onDismissRequest = { if (!state.busy) onDismiss() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(CardWhite)
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Text(
                stringResource(R.string.auth_reset_title),
                fontFamily = CuteFont,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                color = Ink
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_reset_password_hint, email),
                fontFamily = CuteFont,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = InkLight
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Top) {
                AccountTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(10) },
                    placeholder = stringResource(R.string.auth_otp_placeholder),
                    supportingText = stringResource(R.string.auth_otp_hint),
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = when {
                        state.sendingCode -> stringResource(R.string.auth_otp_sending)
                        resendSeconds > 0 ->
                            stringResource(R.string.auth_otp_resend, resendSeconds)
                        else -> stringResource(R.string.auth_otp_send)
                    },
                    fontFamily = CuteFont,
                    fontSize = 13.sp,
                    color = if (state.busy || state.sendingCode || resendSeconds > 0) {
                        InkLight.copy(alpha = 0.55f)
                    } else {
                        OrangeDeep
                    },
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = !state.busy && !state.sendingCode && resendSeconds == 0,
                            onClick = onSendCode
                        )
                        .padding(horizontal = 6.dp, vertical = 16.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            AccountTextField(
                value = password,
                onValueChange = {
                    password = it
                    mismatch = false
                },
                placeholder = stringResource(R.string.auth_password_label),
                supportingText = stringResource(R.string.auth_password_hint),
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next,
                visualTransformation = PasswordVisualTransformation(),
                enabled = !state.busy
            )
            Spacer(Modifier.height(14.dp))
            AccountTextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    mismatch = false
                },
                placeholder = stringResource(R.string.auth_confirm_password_label),
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                visualTransformation = PasswordVisualTransformation(),
                enabled = !state.busy
            )
            if (mismatch) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.auth_password_mismatch),
                    fontFamily = CuteFont,
                    fontSize = 12.sp,
                    color = Danger
                )
            }
            state.errorRes?.let { errorRes ->
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(errorRes),
                    fontFamily = CuteFont,
                    fontSize = 12.sp,
                    color = Danger
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth()) {
                val cancelShape = RoundedCornerShape(20.dp)
                Column(
                    Modifier
                        .weight(1f)
                        .clip(cancelShape)
                        .border(1.dp, Hairline, cancelShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = !state.busy,
                            onClick = onDismiss
                        )
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.common_cancel),
                        fontFamily = CuteFont,
                        fontSize = 15.sp,
                        color = Ink
                    )
                }
                Spacer(Modifier.width(12.dp))
                CuteButton(
                    text = stringResource(R.string.auth_reset_action),
                    onClick = {
                        if (password != confirmPassword) {
                            mismatch = true
                        } else {
                            onConfirm(code, password)
                        }
                    },
                    modifier = Modifier.weight(1.4f),
                    enabled = !state.busy && !state.sendingCode,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
                )
            }
        }
    }
}
package com.calorie.diary.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.calorie.diary.R
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.Danger
import com.calorie.diary.ui.theme.Hairline
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.OrangeDeep
import com.calorie.diary.ui.theme.ScreenBg
import com.calorie.diary.viewmodel.AccountDeletionUiState
import kotlinx.coroutines.delay

private val DialogFont = FontFamily.SansSerif

@Composable
fun AccountDeletionDialog(
    state: AccountDeletionUiState,
    email: String,
    onSendCode: () -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var resendSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(resendSeconds) {
        if (resendSeconds > 0) {
            delay(1000L)
            resendSeconds -= 1
        }
    }
    LaunchedEffect(state.codeSentCount) {
        if (state.codeSentCount > 0) resendSeconds = 60
    }

    Dialog(onDismissRequest = { if (!state.busy && !state.sendingCode) onDismiss() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(CardWhite)
                .padding(20.dp)
        ) {
            Text(
                stringResource(R.string.delete_dialog_title),
                fontFamily = DialogFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = Ink
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.delete_dialog_message),
                fontFamily = DialogFont,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = InkLight
            )
            Spacer(Modifier.height(6.dp))
            Text(
                email,
                fontFamily = DialogFont,
                fontSize = 12.sp,
                color = InkLight
            )
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Hairline, RoundedCornerShape(12.dp))
                        .background(ScreenBg)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = code,
                        onValueChange = { code = it.filter(Char::isDigit).take(10) },
                        singleLine = true,
                        enabled = !state.busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = TextStyle(
                            fontFamily = DialogFont, fontSize = 15.sp, color = Ink
                        ),
                        cursorBrush = SolidColor(OrangeDeep),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                if (code.isEmpty()) {
                                    Text(
                                        stringResource(R.string.auth_otp_placeholder),
                                        fontFamily = DialogFont,
                                        fontSize = 14.sp,
                                        color = InkLight
                                    )
                                }
                                inner()
                            }
                        }
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = when {
                        state.sendingCode -> stringResource(R.string.auth_otp_sending)
                        resendSeconds > 0 -> stringResource(R.string.auth_otp_resend, resendSeconds)
                        else -> stringResource(R.string.auth_otp_send)
                    },
                    fontFamily = DialogFont,
                    fontSize = 13.sp,
                    color = if (state.sendingCode || resendSeconds > 0) {
                        InkLight.copy(alpha = 0.55f)
                    } else {
                        OrangeDeep
                    },
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = !state.sendingCode && resendSeconds == 0 && !state.busy,
                            onClick = onSendCode
                        )
                        .padding(vertical = 12.dp, horizontal = 4.dp)
                )
            }

            if (state.codeSentCount > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.auth_otp_sent),
                    fontFamily = DialogFont,
                    fontSize = 12.sp,
                    color = InkLight
                )
            }
            state.errorRes?.let { errorRes ->
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(errorRes),
                    fontFamily = DialogFont,
                    fontSize = 12.sp,
                    color = Danger
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.delete_dialog_cancel),
                    fontFamily = DialogFont,
                    fontSize = 14.sp,
                    color = InkLight,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = !state.busy && !state.sendingCode,
                            onClick = onDismiss
                        )
                        .padding(vertical = 10.dp, horizontal = 8.dp)
                )
                Spacer(Modifier.weight(1f))
                val canConfirm = code.length in 6..10 && !state.busy && !state.sendingCode
                Text(
                    if (state.busy) {
                        stringResource(R.string.delete_dialog_deleting)
                    } else {
                        stringResource(R.string.delete_dialog_confirm)
                    },
                    fontFamily = DialogFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = if (canConfirm) Danger else Danger.copy(alpha = 0.4f),
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = canConfirm,
                            onClick = { onConfirm(code) }
                        )
                        .padding(vertical = 10.dp, horizontal = 8.dp)
                )
            }
        }
    }
}
package com.calorie.diary.ui.privacy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.calorie.diary.R
import com.calorie.diary.ui.AppLinks
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Hairline
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.Orange
import com.calorie.diary.ui.theme.OrangeDeep

private const val PrivacyLinkTag = "privacy_policy"

/**
 * 登录/注册等涉及个人信息的操作前的隐私政策确认弹窗。
 * 点击“不同意”只中止当前操作，不退出应用。
 */
@Composable
fun PrivacyConsentDialog(onAgree: () -> Unit, onDecline: () -> Unit) {
    Dialog(
        onDismissRequest = onDecline,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            contentAlignment = Alignment.Center
        ) {
            PrivacyConsentCard(
                onAgree = onAgree,
                onDecline = onDecline,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
            )
        }
    }
}

@Composable
internal fun PrivacyConsentCard(
    onAgree: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    var checked by remember { mutableStateOf(false) }
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(CardWhite)
            .border(1.dp, Hairline, RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Text(
            stringResource(R.string.privacy_consent_title),
            fontFamily = CuteFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            color = Ink
        )
        Spacer(Modifier.height(10.dp))
        PrivacyConsentBody()
        Spacer(Modifier.height(10.dp))
        PrivacyConsentCheckRow(checked = checked, onToggle = { checked = it })
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onAgree,
            enabled = checked,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Orange,
                contentColor = Color.White,
                disabledContainerColor = Orange.copy(alpha = 0.4f),
                disabledContentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp
            )
        ) {
            Text(
                stringResource(R.string.privacy_consent_agree),
                fontFamily = CuteFont,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.privacy_consent_disagree),
            fontFamily = CuteFont,
            fontSize = 13.sp,
            color = InkLight,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDecline
                )
                .padding(vertical = 10.dp)
        )
    }
}

/** 需用户主动勾选“我已阅读并同意《隐私政策》”；政策标题可点击查看。 */
@Composable
private fun PrivacyConsentCheckRow(checked: Boolean, onToggle: (Boolean) -> Unit) {
    val uriHandler = LocalUriHandler.current
    val prefix = stringResource(R.string.privacy_consent_check_prefix)
    val linkText = stringResource(R.string.privacy_policy_title)
    val suffix = stringResource(R.string.privacy_consent_check_suffix)
    val text = buildAnnotatedString {
        append(prefix)
        pushStringAnnotation(PrivacyLinkTag, AppLinks.PRIVACY_POLICY)
        withStyle(SpanStyle(color = OrangeDeep)) { append(linkText) }
        pop()
        append(suffix)
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onToggle,
            colors = CheckboxDefaults.colors(
                checkedColor = OrangeDeep,
                uncheckedColor = InkLight
            )
        )
        Spacer(Modifier.width(4.dp))
        ClickableText(
            text = text,
            style = TextStyle(
                fontFamily = CuteFont,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = InkLight
            ),
            onClick = { offset ->
                val annotations = text.getStringAnnotations(PrivacyLinkTag, offset, offset)
                if (annotations.isNotEmpty()) {
                    uriHandler.openUri(annotations.first().item)
                } else {
                    onToggle(!checked)
                }
            }
        )
    }
}

@Composable
private fun PrivacyConsentBody() {
    val uriHandler = LocalUriHandler.current
    val prefix = stringResource(R.string.privacy_consent_prefix)
    val linkText = stringResource(R.string.privacy_policy_title)
    val suffix = stringResource(R.string.privacy_consent_suffix)
    val body = buildAnnotatedString {
        append(prefix)
        pushStringAnnotation(PrivacyLinkTag, AppLinks.PRIVACY_POLICY)
        withStyle(SpanStyle(color = OrangeDeep)) { append(linkText) }
        pop()
        append(suffix)
    }
    ClickableText(
        text = body,
        style = TextStyle(
            fontFamily = CuteFont,
            fontSize = 13.sp,
            lineHeight = 21.sp,
            color = InkLight
        ),
        onClick = { offset ->
            body.getStringAnnotations(PrivacyLinkTag, offset, offset)
                .firstOrNull()
                ?.let { annotation -> uriHandler.openUri(annotation.item) }
        }
    )
}
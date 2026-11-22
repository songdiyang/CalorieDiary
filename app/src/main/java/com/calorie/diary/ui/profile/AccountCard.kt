package com.calorie.diary.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calorie.diary.R
import com.calorie.diary.data.PrivacyConsentStore
import com.calorie.diary.ui.AppLinks
import com.calorie.diary.ui.components.CuteButton
import com.calorie.diary.ui.privacy.PrivacyConsentDialog
import com.calorie.diary.ui.components.SoftCard
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Danger
import com.calorie.diary.ui.theme.Hairline
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.MintSoft
import com.calorie.diary.ui.theme.Orange
import com.calorie.diary.ui.theme.OrangeDeep
import com.calorie.diary.ui.theme.PinkSoft
import com.calorie.diary.ui.theme.ScreenBg
import com.calorie.diary.viewmodel.AuthUiState
import com.calorie.diary.viewmodel.AuthViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AuthPageMode { LOGIN, REGISTER }

@Composable
fun AccountCard(
    authVm: AuthViewModel = viewModel(),
    showHeader: Boolean = true,
    forceAddAccount: Boolean = false,
    mode: AuthPageMode = AuthPageMode.LOGIN,
    onRegister: () -> Unit = {},
    onLogin: () -> Unit = {}
) {
    val state by authVm.uiState.collectAsState()
    val accentColor = Orange
    val accentDarkColor = OrangeDeep

    // 登录、注册、发送验证码前必须点击确认隐私政策；每次进入账号页至少确认一次。
    val context = LocalContext.current
    val consentStore = remember { PrivacyConsentStore(context) }
    val scope = rememberCoroutineScope()
    var showPrivacyConsent by remember { mutableStateOf(false) }
    var consentConfirmed by remember { mutableStateOf(false) }
    var pendingConsentAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun withPrivacyConsent(action: () -> Unit) {
        if (consentConfirmed) {
            action()
        } else {
            pendingConsentAction = action
            showPrivacyConsent = true
        }
    }

    Column(Modifier.fillMaxWidth()) {
        if (showHeader) {
            Text(
                stringResource(R.string.auth_section_title),
                fontFamily = CuteFont, fontSize = 17.sp, color = Ink
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.auth_section_subtitle),
                fontFamily = CuteFont, fontSize = 12.sp, color = InkLight
            )
            Spacer(Modifier.height(14.dp))
        }

        when {
            !state.configured -> Text(
                stringResource(R.string.auth_not_configured),
                fontFamily = CuteFont,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = InkLight,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            state.restoring -> Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = accentColor
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.auth_restoring),
                    fontFamily = CuteFont, fontSize = 13.sp, color = InkLight
                )
            }
            mode == AuthPageMode.REGISTER -> RegisterForm(
                busy = state.busy,
                onSendCode = { email, invite, onSent ->
                    withPrivacyConsent { authVm.sendRegisterCode(email, invite, onSent) }
                },
                onRegister = { email, password, code, invite ->
                    withPrivacyConsent { authVm.register(email, password, code, invite) }
                },
                onLogin = onLogin
            )
            state.account != null && !forceAddAccount -> SoftCard(
                modifier = Modifier.fillMaxWidth(),
                fill = CardWhite
            ) {
                LoggedInAccount(state, authVm::refreshProfile, authVm::signOut)
            }
            else -> LoginForm(
                busy = state.busy,
                onSignIn = { email, password ->
                    withPrivacyConsent { authVm.signIn(email, password) }
                },
                onRegister = onRegister
            )
        }

        state.messageRes?.let { messageRes ->
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (state.messageIsError) PinkSoft
                        else accentColor.copy(alpha = 0.18f)
                    )
                    .padding(horizontal = 14.dp, vertical = 11.dp)
            ) {
                Text(
                    stringResource(messageRes),
                    fontFamily = CuteFont,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = if (state.messageIsError) Danger else accentDarkColor
                )
            }
        }
    }

    if (showPrivacyConsent) {
        PrivacyConsentDialog(
            onAgree = {
                scope.launch {
                    consentStore.agree()
                    consentConfirmed = true
                    showPrivacyConsent = false
                    pendingConsentAction?.invoke()
                    pendingConsentAction = null
                }
            },
            onDecline = {
                showPrivacyConsent = false
                pendingConsentAction = null
            }
        )
    }
}

@Composable
private fun LoginForm(
    busy: Boolean,
    onSignIn: (String, String) -> Unit,
    onRegister: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth()) {
        AccountTextField(
            value = email,
            onValueChange = { email = it },
            placeholder = stringResource(R.string.auth_email_label),
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            enabled = !busy
        )
        Spacer(Modifier.height(14.dp))
        AccountTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = stringResource(R.string.auth_password_label),
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            visualTransformation = PasswordVisualTransformation(),
            enabled = !busy
        )
        Spacer(Modifier.height(26.dp))
        AuthPrimaryButton(
            text = stringResource(R.string.auth_sign_in),
            onClick = { onSignIn(email, password) },
            enabled = !busy,
            loading = busy
        )
        AuthPageLink(stringResource(R.string.auth_no_account), onRegister, !busy)
        PrivacyConsentLine()
    }
}

private const val PrivacyLinkTag = "privacy_policy"

@Composable
private fun PrivacyConsentLine() {
    val uriHandler = LocalUriHandler.current
    val prefix = stringResource(R.string.auth_privacy_prefix)
    val linkText = stringResource(R.string.privacy_policy_title)
    val suffix = stringResource(R.string.auth_privacy_suffix)
    val consent = buildAnnotatedString {
        append(prefix)
        pushStringAnnotation(PrivacyLinkTag, AppLinks.PRIVACY_POLICY)
        withStyle(SpanStyle(color = OrangeDeep)) { append(linkText) }
        pop()
        append(suffix)
    }
    ClickableText(
        text = consent,
        style = TextStyle(
            fontFamily = CuteFont,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = InkLight,
            textAlign = TextAlign.Center
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        onClick = { offset ->
            consent.getStringAnnotations(PrivacyLinkTag, offset, offset)
                .firstOrNull()
                ?.let { annotation -> uriHandler.openUri(annotation.item) }
        }
    )
}

@Composable
private fun RegisterForm(
    busy: Boolean,
    onSendCode: (String, String, () -> Unit) -> Unit,
    onRegister: (String, String, String, String) -> Unit,
    onLogin: () -> Unit,
    accentColor: Color = Orange,
    accentDarkColor: Color = OrangeDeep
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var invitationCode by remember { mutableStateOf("") }
    var resendSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(resendSeconds) {
        if (resendSeconds > 0) {
            delay(1000L)
            resendSeconds -= 1
        }
    }

    Column(Modifier.fillMaxWidth()) {
        AccountTextField(
            value = email,
            onValueChange = { email = it },
            placeholder = stringResource(R.string.auth_email_label),
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            accentColor = accentColor,
            enabled = !busy
        )
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
            accentColor = accentColor,
            enabled = !busy
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
            imeAction = ImeAction.Next,
            visualTransformation = PasswordVisualTransformation(),
            accentColor = accentColor,
            enabled = !busy
        )
        if (mismatch) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.auth_password_mismatch),
                fontFamily = CuteFont,
                fontSize = 12.sp,
                color = Danger,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Top) {
            AccountTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isDigit).take(10) },
                placeholder = stringResource(R.string.auth_otp_placeholder),
                supportingText = stringResource(R.string.auth_otp_hint),
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
                accentColor = accentColor,
                enabled = !busy,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (resendSeconds > 0) {
                    stringResource(R.string.auth_otp_resend, resendSeconds)
                } else {
                    stringResource(R.string.auth_otp_send)
                },
                fontFamily = CuteFont,
                fontSize = 13.sp,
                color = if (busy || resendSeconds > 0) InkLight.copy(alpha = 0.55f)
                else accentDarkColor,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !busy && resendSeconds == 0,
                        onClick = { onSendCode(email, invitationCode) { resendSeconds = 60 } }
                    )
                    .padding(horizontal = 6.dp, vertical = 16.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        AccountTextField(
            value = invitationCode,
            onValueChange = { invitationCode = it.filter(Char::isDigit).take(8) },
            placeholder = stringResource(R.string.auth_invitation_optional_label),
            supportingText = stringResource(R.string.auth_invitation_optional_hint),
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
            accentColor = accentColor,
            enabled = !busy
        )
        Spacer(Modifier.height(26.dp))
        AuthPrimaryButton(
            text = stringResource(R.string.auth_sign_up),
            onClick = {
                if (password != confirmPassword) {
                    mismatch = true
                } else {
                    onRegister(email, password, code, invitationCode)
                }
            },
            enabled = !busy,
            loading = busy,
            containerColor = accentColor
        )
        AuthPageLink(
            stringResource(R.string.auth_have_account),
            onLogin,
            !busy,
            color = accentDarkColor
        )
    }
}

@Composable
private fun AuthPrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    loading: Boolean = false,
    containerColor: Color = Orange
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = Color.White,
            disabledContainerColor = containerColor.copy(alpha = 0.45f),
            disabledContentColor = Color.White
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp
        ),
        contentPadding = PaddingValues(horizontal = 20.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(9.dp))
        }
        Text(
            text = text,
            color = Color.White,
            fontFamily = CuteFont,
            fontWeight = FontWeight.Medium,
            fontSize = 17.sp
        )
    }
}

@Composable
private fun AuthPageLink(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    color: Color = OrangeDeep
) {
    Spacer(Modifier.height(8.dp))
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(12.dp),
        fontFamily = CuteFont,
        fontSize = 14.sp,
        color = color.copy(alpha = if (enabled) 1f else 0.45f),
        textAlign = TextAlign.Center
    )
}

@Composable
internal fun AuthPagesPreviewContent() {
    Column(
        Modifier
            .fillMaxWidth()
            .background(ScreenBg)
            .padding(24.dp)
    ) {
        LoginForm(busy = false, onSignIn = { _, _ -> }, onRegister = {})
        Spacer(Modifier.height(32.dp))
        RegisterForm(
            busy = false,
            onSendCode = { _, _, _ -> },
            onRegister = { _, _, _, _ -> },
            onLogin = {}
        )
    }
}

@Composable
internal fun LoggedInAccount(
    state: AuthUiState,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit
) {
    val profile = state.cloudProfile
    // SoftCard 内容容器是 Box，多子元素必须用 Column 串起来，否则全部堆叠在左上角。
    Column(Modifier.fillMaxWidth()) {
        Text(
            profile?.nickname ?: stringResource(R.string.auth_logged_in),
            fontFamily = CuteFont, fontSize = 18.sp, color = Ink
        )
        Spacer(Modifier.height(4.dp))
        Text(
            state.account?.email.orEmpty(),
            fontFamily = CuteFont, fontSize = 13.sp, color = InkLight
        )
        if (profile != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.auth_cloud_goal, profile.calorieGoal, profile.waterGoal),
                fontFamily = CuteFont, fontSize = 12.sp, color = InkLight
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            CuteButton(
                text = stringResource(R.string.auth_refresh_profile),
                onClick = onRefresh,
                modifier = Modifier.weight(1f),
                containerColor = MintSoft,
                contentColor = Ink,
                enabled = !state.busy,
                contentPadding = PaddingValues(vertical = 10.dp)
            )
            Spacer(Modifier.width(12.dp))
            CuteButton(
                text = stringResource(R.string.auth_sign_out),
                onClick = onSignOut,
                modifier = Modifier.weight(1f),
                containerColor = PinkSoft,
                contentColor = Ink,
                enabled = !state.busy,
                contentPadding = PaddingValues(vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun AccountTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    supportingText: String? = null,
    accentColor: Color = Orange,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(14.dp)

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(shape)
                .background(CardWhite.copy(alpha = if (enabled) 1f else 0.6f))
                .border(1.dp, if (focused) accentColor else Hairline, shape)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                enabled = enabled,
                interactionSource = interactionSource,
                textStyle = TextStyle(
                    fontFamily = CuteFont,
                    color = Ink.copy(alpha = if (enabled) 1f else 0.5f),
                    fontSize = 16.sp
                ),
                cursorBrush = SolidColor(accentColor),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                visualTransformation = visualTransformation,
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                placeholder,
                                fontFamily = CuteFont,
                                fontSize = 15.sp,
                                color = InkLight
                            )
                        }
                        inner()
                    }
                }
            )
        }
        if (supportingText != null) {
            Spacer(Modifier.height(7.dp))
            Text(
                text = supportingText,
                modifier = Modifier.padding(horizontal = 4.dp),
                fontFamily = CuteFont,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = InkLight
            )
        }
    }
}

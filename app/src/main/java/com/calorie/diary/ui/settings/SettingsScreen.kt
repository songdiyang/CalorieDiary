package com.calorie.diary.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calorie.diary.BuildConfig
import com.calorie.diary.R
import com.calorie.diary.data.AppRefreshBus
import com.calorie.diary.data.UiMessenger
import com.calorie.diary.data.supabase.AuthAccount
import com.calorie.diary.data.supabase.DefaultAvatarCatalog
import com.calorie.diary.data.update.AppUpdater
import com.calorie.diary.data.update.UpdateState
import com.calorie.diary.viewmodel.AuthUiState
import com.calorie.diary.viewmodel.AuthViewModel
import com.calorie.diary.ui.AppLinks
import com.calorie.diary.ui.navigation.Routes
import com.calorie.diary.ui.home.appVm
import com.calorie.diary.ui.components.AvatarPickerDialog
import com.calorie.diary.ui.components.DefaultAvatar
import com.calorie.diary.ui.components.NicknameEditDialog
import com.calorie.diary.viewmodel.ActivityPrivacyUiState
import com.calorie.diary.viewmodel.ActivityPrivacyViewModel
import com.calorie.diary.ui.theme.AppTheme
import com.calorie.diary.ui.theme.AppThemePalettes
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.Danger
import com.calorie.diary.ui.theme.Hairline
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.OrangeDeep
import com.calorie.diary.ui.theme.ScreenBg
import com.calorie.diary.ui.theme.ThemeController
import com.calorie.diary.ui.theme.YellowSoft
import kotlin.math.roundToInt

private val SettingsFont = FontFamily.SansSerif

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAccount: () -> Unit,
    onAddAccount: () -> Unit,
    onCheckUpdate: () -> Unit
) {
    val vm = appVm()
    val privacyVm: ActivityPrivacyViewModel = viewModel()
    val privacyState by privacyVm.uiState.collectAsState()
    val authVm: AuthViewModel = viewModel()
    val authState by authVm.uiState.collectAsState()
    val savedAccounts by authVm.savedAccounts.collectAsState()
    LaunchedEffect(Unit) { privacyVm.refresh() }
    LaunchedEffect(Unit) {
        AppRefreshBus.events.collect { route ->
            if (route == Routes.SETTINGS) privacyVm.refresh()
        }
    }
    val uriHandler = LocalUriHandler.current
    val profileState by vm.profile.collectAsState()
    val identity by vm.identity.collectAsState()
    val profile = profileState ?: return
    var showAvatarPicker by remember { mutableStateOf(false) }
    var showNicknameEditor by remember { mutableStateOf(false) }
    var showDeletionDialog by remember { mutableStateOf(false) }
    var showResetPasswordDialog by remember { mutableStateOf(false) }
    val updateState by AppUpdater.state.collectAsState()
    val deletionState by authVm.deletionState.collectAsState()
    val resetPasswordState by authVm.passwordResetState.collectAsState()
    val nicknameState by authVm.nicknameState.collectAsState()
    val context = LocalContext.current
    val currentNickname = authState.cloudProfile?.nickname?.takeIf { it.isNotBlank() }
        ?: identity?.nickname?.takeIf { it.isNotBlank() }
        ?: ""
    val avatarId = DefaultAvatarCatalog.resolve(identity?.avatarEmoji)
        ?: DefaultAvatarCatalog.resolve(authState.cloudProfile?.avatarEmoji)
    val updateActionText = when (val state = updateState) {
        is UpdateState.Checking -> stringResource(R.string.update_checking)
        is UpdateState.UpToDate -> stringResource(R.string.update_up_to_date)
        is UpdateState.Available ->
            stringResource(R.string.update_new_version_row, state.info.versionName)
        is UpdateState.Downloading -> state.percent?.let {
            stringResource(R.string.update_downloading_row, it)
        } ?: stringResource(R.string.update_downloading)
        is UpdateState.Ready -> stringResource(R.string.update_ready_row)
        is UpdateState.Failed -> stringResource(R.string.update_retry)
        UpdateState.Idle -> stringResource(R.string.update_check_now)
    }

    var age by remember { mutableStateOf(profile.age.toString()) }
    var height by remember { mutableStateOf(profile.heightCm.toString()) }
    var weight by remember {
        mutableStateOf(
            profile.weightKg.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }
        )
    }
    var calorieGoal by remember { mutableStateOf(profile.calorieGoal.toString()) }
    var waterGoal by remember { mutableStateOf(profile.waterGoal.toString()) }

    val suggested = suggestCalorie(profile.gender, profile.weightKg, profile.heightCm, profile.age)

    Column(
        Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        SettingsHeader(onBack)
        Spacer(Modifier.height(24.dp))

        SettingsSection(stringResource(R.string.settings_personal)) {
            SettingAvatarRow(
                userId = authState.account?.userId.orEmpty(),
                avatarId = avatarId,
                onClick = { showAvatarPicker = true }
            )
            SettingsDivider()
            SettingNicknameRow(
                nickname = currentNickname.ifBlank { "——" },
                enabled = authState.account != null,
                onClick = {
                    authVm.loadNicknameStatus()
                    showNicknameEditor = true
                }
            )
            SettingsDivider()
            SettingLabel(stringResource(R.string.settings_gender))
            GenderSelector(
                selected = profile.gender,
                onFemale = { vm.setGender("女") },
                onMale = { vm.setGender("男") }
            )
            SettingsDivider()
            SettingNumberRow(
                label = stringResource(R.string.settings_age),
                value = age,
                onValueChange = {
                    age = it.filter(Char::isDigit)
                    age.toIntOrNull()?.let(vm::setAge)
                },
                suffix = stringResource(R.string.unit_age)
            )
            SettingsDivider()
            SettingNumberRow(
                label = stringResource(R.string.settings_height),
                value = height,
                onValueChange = {
                    height = it.filter(Char::isDigit)
                    height.toIntOrNull()?.let(vm::setHeight)
                },
                suffix = stringResource(R.string.unit_cm)
            )
            SettingsDivider()
            SettingNumberRow(
                label = stringResource(R.string.settings_weight),
                value = weight,
                onValueChange = {
                    weight = it.filter { c -> c.isDigit() || c == '.' }
                    weight.toFloatOrNull()?.let(vm::setWeight)
                },
                suffix = stringResource(R.string.unit_kg),
                keyboardType = KeyboardType.Decimal
            )
        }

        Spacer(Modifier.height(20.dp))
        SettingsSection(stringResource(R.string.settings_goal)) {
            SettingNumberRow(
                label = stringResource(R.string.settings_daily_calorie),
                value = calorieGoal,
                onValueChange = {
                    calorieGoal = it.filter(Char::isDigit)
                    calorieGoal.toIntOrNull()?.let(vm::setCalorieGoal)
                },
                suffix = stringResource(R.string.unit_kcal)
            )
            SettingsDivider()
            SettingNumberRow(
                label = stringResource(R.string.settings_daily_water),
                value = waterGoal,
                onValueChange = {
                    waterGoal = it.filter(Char::isDigit)
                    waterGoal.toIntOrNull()?.let(vm::setWaterGoal)
                },
                suffix = stringResource(R.string.unit_ml)
            )
            RecommendationPanel(suggested) {
                calorieGoal = suggested.toString()
                vm.setCalorieGoal(suggested)
            }
        }

        Spacer(Modifier.height(20.dp))
        SettingsSection(stringResource(R.string.settings_appearance_title)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    stringResource(R.string.settings_appearance_hint),
                    fontFamily = SettingsFont, fontSize = 12.sp, color = InkLight
                )
                Spacer(Modifier.height(12.dp))
                ThemeSelector()
            }
        }

        Spacer(Modifier.height(20.dp))
        SettingsSection(stringResource(R.string.activity_privacy_title)) {
            ActivityPrivacySettings(
                state = privacyState,
                onAccount = onAccount,
                onChange = privacyVm::updateActivityVisibility
            )
        }

        Spacer(Modifier.height(20.dp))
        SettingsSection(stringResource(R.string.account_manager_title)) {
            AccountManagementSettings(
                state = authState,
                accounts = savedAccounts,
                onAddAccount = onAddAccount,
                onSwitch = authVm::switchAccount,
                onSignOut = authVm::signOut,
                onResetPassword = {
                    authVm.clearPasswordResetState()
                    showResetPasswordDialog = true
                },
                onDeleteAccount = {
                    authVm.resetDeletionState()
                    showDeletionDialog = true
                }
            )
        }

        Spacer(Modifier.height(20.dp))
        SettingsSection(stringResource(R.string.settings_about_title)) {
            SettingActionRow(
                label = stringResource(R.string.privacy_policy_title),
                action = stringResource(R.string.settings_view),
                onClick = { uriHandler.openUri(AppLinks.PRIVACY_POLICY) }
            )
            SettingsDivider()
            SettingActionRow(
                label = stringResource(R.string.update_check),
                action = updateActionText,
                onClick = onCheckUpdate
            )
            SettingsDivider()
            SettingInfoRow(
                label = stringResource(R.string.settings_version),
                value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
            )
        }
        Spacer(Modifier.height(28.dp))
    }

    if (showAvatarPicker) {
        AvatarPickerDialog(
            userId = authState.account?.userId.orEmpty(),
            selectedId = avatarId,
            onDismiss = { showAvatarPicker = false },
            onSelect = {
                vm.selectAvatar(it)
                showAvatarPicker = false
            }
        )
    }

    if (showNicknameEditor) {
        NicknameEditDialog(
            currentNickname = currentNickname,
            status = nicknameState.status,
            busy = nicknameState.busy,
            errorRes = nicknameState.errorRes,
            onDismiss = {
                showNicknameEditor = false
                authVm.clearNicknameError()
            },
            onSubmit = { authVm.changeNickname(it) { showNicknameEditor = false } },
            onClearError = authVm::clearNicknameError
        )
    }

    if (showDeletionDialog) {
        AccountDeletionDialog(
            state = deletionState,
            email = authState.account?.email.orEmpty(),
            onSendCode = authVm::sendDeletionCode,
            onConfirm = { code ->
                authVm.deleteAccount(code) { showDeletionDialog = false }
            },
            onDismiss = {
                showDeletionDialog = false
                authVm.resetDeletionState()
            }
        )
    }

    if (showResetPasswordDialog) {
        val email = authState.account?.email.orEmpty()
        ResetPasswordDialog(
            email = email,
            state = resetPasswordState,
            onSendCode = { authVm.sendPasswordResetCode(email) },
            onConfirm = { code, password ->
                authVm.resetPassword(email, code, password) {
                    showResetPasswordDialog = false
                    UiMessenger.show(
                        context.getString(R.string.auth_reset_success),
                        isError = false
                    )
                }
            },
            onDismiss = {
                showResetPasswordDialog = false
                authVm.clearPasswordResetState()
            }
        )
    }
}

@Composable
private fun SettingNicknameRow(nickname: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.settings_nickname),
            fontFamily = SettingsFont, fontSize = 14.sp, color = Ink,
            modifier = Modifier.weight(1f)
        )
        Text(
            nickname,
            fontFamily = SettingsFont, fontSize = 14.sp, color = InkLight,
            maxLines = 1
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.settings_nickname_change),
            fontFamily = SettingsFont, fontSize = 13.sp,
            color = if (enabled) OrangeDeep else InkLight.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun SettingAvatarRow(userId: String, avatarId: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.settings_avatar),
            fontFamily = SettingsFont, fontSize = 14.sp, color = Ink,
            modifier = Modifier.weight(1f)
        )
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            DefaultAvatar(
                userId = userId,
                avatarId = avatarId,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.settings_avatar_change),
            fontFamily = SettingsFont, fontSize = 13.sp, color = OrangeDeep
        )
    }
}

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BackArrow(onBack)
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.settings_title),
            fontFamily = SettingsFont, fontSize = 25.sp,
            fontWeight = FontWeight.SemiBold, color = Ink
        )
    }
    Spacer(Modifier.height(6.dp))
    Text(
        stringResource(R.string.settings_subtitle),
        fontFamily = SettingsFont, fontSize = 13.sp, color = InkLight,
        modifier = Modifier.padding(start = 48.dp)
    )
}

@Composable
private fun ActivityPrivacySettings(
    state: ActivityPrivacyUiState,
    onAccount: () -> Unit,
    onChange: (String) -> Unit
) {
    if (state.requiresLogin) {
        Text(
            stringResource(R.string.activity_privacy_login),
            fontFamily = SettingsFont, fontSize = 13.sp, color = InkLight,
            modifier = Modifier.padding(16.dp)
        )
        Text(
            stringResource(R.string.game_login_action),
            fontFamily = SettingsFont, fontSize = 13.sp, color = OrangeDeep,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 0.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onAccount
                )
                .padding(bottom = 16.dp)
        )
        return
    }

    val options = listOf(
        Triple("public", R.string.activity_visibility_public, R.string.activity_privacy_public_hint),
        Triple("friends", R.string.activity_visibility_friends, R.string.activity_privacy_friends_hint),
        Triple("private", R.string.activity_visibility_private, R.string.activity_privacy_private_hint)
    )
    options.forEachIndexed { index, option ->
        if (index > 0) SettingsDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !state.savingVisibility,
                    onClick = { onChange(option.first) }
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = state.activityVisibility == option.first,
                onClick = { onChange(option.first) },
                enabled = !state.savingVisibility,
                colors = RadioButtonDefaults.colors(selectedColor = OrangeDeep)
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    stringResource(option.second),
                    fontFamily = SettingsFont, fontSize = 14.sp, color = Ink
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    stringResource(option.third),
                    fontFamily = SettingsFont, fontSize = 11.sp, color = InkLight
                )
            }
        }
    }
    if (state.savingVisibility) {
        Text(
            stringResource(R.string.activity_privacy_saving),
            fontFamily = SettingsFont, fontSize = 11.sp, color = InkLight,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    } else if (state.failed) {
        Text(
            stringResource(R.string.activity_privacy_failed),
            fontFamily = SettingsFont, fontSize = 11.sp, color = OrangeDeep,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun AccountManagementSettings(
    state: AuthUiState,
    accounts: List<AuthAccount>,
    onAddAccount: () -> Unit,
    onSwitch: (String) -> Unit,
    onSignOut: () -> Unit,
    onResetPassword: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    if (accounts.isEmpty()) {
        Text(
            stringResource(R.string.account_manager_empty),
            fontFamily = SettingsFont, fontSize = 13.sp, color = InkLight,
            modifier = Modifier.padding(16.dp)
        )
    }
    accounts.forEach { account ->
        val current = state.account?.userId == account.userId
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    account.email.ifBlank { account.userId.take(8) },
                    fontFamily = SettingsFont, fontSize = 14.sp, color = Ink,
                    maxLines = 1
                )
                if (current) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        stringResource(R.string.account_current),
                        fontFamily = SettingsFont, fontSize = 11.sp, color = OrangeDeep
                    )
                }
            }
            if (!current) {
                Text(
                    stringResource(R.string.account_switch),
                    fontFamily = SettingsFont, fontSize = 13.sp, color = OrangeDeep,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !state.busy,
                        onClick = { onSwitch(account.userId) }
                    ).padding(6.dp)
                )
            }
        }
        SettingsDivider()
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.account_add),
            fontFamily = SettingsFont, fontSize = 13.sp, color = OrangeDeep,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !state.busy,
                onClick = onAddAccount
            ).padding(vertical = 4.dp)
        )
        Spacer(Modifier.weight(1f))
        if (state.account != null) {
            Text(
                stringResource(R.string.auth_sign_out),
                fontFamily = SettingsFont, fontSize = 13.sp, color = OrangeDeep,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !state.busy,
                    onClick = onSignOut
                ).padding(vertical = 4.dp)
            )
        }
    }
    if (state.account != null) {
        SettingsDivider()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.settings_reset_password),
                fontFamily = SettingsFont, fontSize = 13.sp, color = OrangeDeep,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !state.busy,
                        onClick = onResetPassword
                    )
                    .padding(vertical = 4.dp)
            )
        }
        SettingsDivider()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.settings_delete_account),
                fontFamily = SettingsFont, fontSize = 13.sp, color = Danger,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !state.busy,
                        onClick = onDeleteAccount
                    )
                    .padding(vertical = 4.dp)
            )
            Spacer(Modifier.weight(1f))
        }
    }
    state.messageRes?.let {
        Text(
            stringResource(it),
            fontFamily = SettingsFont, fontSize = 11.sp,
            color = if (state.messageIsError) OrangeDeep else InkLight,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            fontFamily = SettingsFont, fontSize = 13.sp,
            fontWeight = FontWeight.Medium, color = InkLight,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardWhite)
                .border(1.dp, Hairline, RoundedCornerShape(16.dp))
        ) {
            content()
        }
    }
}

@Composable
private fun ThemeSelector() {
    val selectedTheme = ThemeController.theme
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AppTheme.values().forEach { theme ->
            ThemeOption(
                theme = theme,
                selected = theme == selectedTheme,
                modifier = Modifier.weight(1f),
                onClick = { ThemeController.select(theme) }
            )
        }
    }
}

@Composable
private fun ThemeOption(
    theme: AppTheme,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val palette = AppThemePalettes.of(theme)
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(
                if (selected) palette.yellowSoft.copy(alpha = 0.6f)
                else ScreenBg.copy(alpha = 0.55f)
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) palette.primaryDeep.copy(alpha = 0.6f) else Hairline,
                shape = shape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(palette.primary, palette.primaryDeep))
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .border(2.dp, palette.cardWhite.copy(alpha = 0.9f), CircleShape)
                )
                Text(
                    "✓",
                    fontFamily = SettingsFont, fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold, color = palette.cardWhite
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            stringResource(
                when (theme) {
                    AppTheme.MINT -> R.string.settings_theme_mint
                    AppTheme.SKY -> R.string.settings_theme_sky
                    AppTheme.SUNSET -> R.string.settings_theme_sunset
                }
            ),
            fontFamily = SettingsFont, fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) palette.primaryDeep else Ink
        )
    }
}

@Composable
private fun SettingInfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontFamily = SettingsFont, fontSize = 14.sp, color = Ink,
            modifier = Modifier.weight(1f)
        )
        Text(value, fontFamily = SettingsFont, fontSize = 13.sp, color = InkLight)
    }
}

@Composable
private fun SettingActionRow(label: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontFamily = SettingsFont, fontSize = 14.sp, color = Ink,
            modifier = Modifier.weight(1f)
        )
        Text(action, fontFamily = SettingsFont, fontSize = 13.sp, color = OrangeDeep)
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text,
        fontFamily = SettingsFont, fontSize = 14.sp, color = Ink,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp)
    )
}

@Composable
private fun GenderSelector(selected: String, onFemale: () -> Unit, onMale: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ScreenBg)
            .padding(4.dp)
    ) {
        GenderOption(
            text = stringResource(R.string.gender_female),
            selected = selected == "女",
            onClick = onFemale,
            modifier = Modifier.weight(1f)
        )
        GenderOption(
            text = stringResource(R.string.gender_male),
            selected = selected == "男",
            onClick = onMale,
            modifier = Modifier.weight(1f)
        )
    }
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun GenderOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Text(
        text,
        fontFamily = SettingsFont, fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        color = if (selected) OrangeDeep else InkLight,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) CardWhite else ScreenBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 9.dp)
    )
}

@Composable
private fun SettingNumberRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    suffix: String,
    keyboardType: KeyboardType = KeyboardType.Number
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontFamily = SettingsFont, fontSize = 14.sp, color = Ink,
            modifier = Modifier.weight(1f)
        )
        Row(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(ScreenBg)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                textStyle = TextStyle(
                    fontFamily = SettingsFont,
                    fontSize = 15.sp,
                    color = Ink,
                    textAlign = TextAlign.End
                ),
                cursorBrush = SolidColor(OrangeDeep),
                modifier = Modifier.width(72.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(suffix, fontFamily = SettingsFont, fontSize = 12.sp, color = InkLight)
        }
    }
}

@Composable
private fun RecommendationPanel(suggested: Int, onApply: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(YellowSoft.copy(alpha = 0.42f))
            .padding(14.dp)
    ) {
        Text(
            stringResource(R.string.settings_recommend_hint, suggested),
            fontFamily = SettingsFont, fontSize = 12.sp, color = InkLight,
            lineHeight = 19.sp
        )
        Text(
            stringResource(R.string.settings_smart_recommend),
            fontFamily = SettingsFont, fontSize = 13.sp,
            fontWeight = FontWeight.Medium, color = OrangeDeep,
            modifier = Modifier
                .align(Alignment.End)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onApply
                )
                .padding(top = 8.dp)
        )
    }
}

@Composable
private fun SettingsDivider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(Hairline))
}

@Composable
private fun BackArrow(onBack: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardWhite)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onBack
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.icon_back),
            contentDescription = stringResource(R.string.common_back),
            modifier = Modifier.size(23.dp)
        )
    }
}

@Composable
internal fun SettingsPreviewContent() {
    Column(
        Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        SettingsHeader({})
        Spacer(Modifier.height(24.dp))
        SettingsSection("个人资料") {
            SettingLabel("生理性别")
            GenderSelector("女", {}, {})
            SettingsDivider()
            SettingNumberRow("年龄", "25", {}, "岁")
            SettingsDivider()
            SettingNumberRow("身高", "165", {}, "厘米")
            SettingsDivider()
            SettingNumberRow("体重", "62.5", {}, "公斤", KeyboardType.Decimal)
        }
        Spacer(Modifier.height(20.dp))
        SettingsSection("我的目标") {
            SettingNumberRow("每日热量目标", "1800", {}, "千卡")
            SettingsDivider()
            SettingNumberRow("每日饮水目标", "1500", {}, "毫升")
            RecommendationPanel(1634) {}
        }
        Spacer(Modifier.height(20.dp))
        SettingsSection("关于") {
            SettingActionRow("隐私政策", "查看") {}
            SettingsDivider()
            SettingInfoRow("版本", "0.21 (17)")
        }
        Spacer(Modifier.height(28.dp))
    }
}

// Mifflin-St Jeor 公式估算基础代谢，再按轻活动量并留出热量缺口
private fun suggestCalorie(gender: String, weight: Float, height: Int, age: Int): Int {
    val bmr = 10 * weight + 6.25 * height - 5 * age + if (gender == "男") 5 else -161
    return (bmr * 1.2 - 300).roundToInt().coerceAtLeast(1200)
}

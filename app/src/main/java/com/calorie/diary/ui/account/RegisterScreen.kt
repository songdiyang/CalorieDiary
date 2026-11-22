package com.calorie.diary.ui.account

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calorie.diary.R
import com.calorie.diary.ui.profile.AccountCard
import com.calorie.diary.ui.profile.AuthPageMode
import com.calorie.diary.ui.theme.Orange
import com.calorie.diary.ui.theme.ScreenBg
import com.calorie.diary.viewmodel.AuthViewModel

@Composable
fun RegisterScreen(onBack: () -> Unit, onLogin: () -> Unit) {
    val authVm: AuthViewModel = viewModel()

    AuthPageLayout(
        title = stringResource(R.string.auth_register_title),
        subtitle = stringResource(R.string.auth_register_subtitle),
        onBack = onBack,
        accentColor = Orange,
        backgroundColor = ScreenBg
    ) {
        AccountCard(
            authVm = authVm,
            showHeader = false,
            mode = AuthPageMode.REGISTER,
            onLogin = onLogin
        )
    }
}

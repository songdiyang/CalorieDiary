package com.calorie.diary.ui.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calorie.diary.R
import com.calorie.diary.data.AppRefreshBus
import com.calorie.diary.ui.navigation.Routes
import com.calorie.diary.ui.profile.AccountCard
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.Cream
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.OrangeDeep
import com.calorie.diary.ui.theme.ScreenBg
import com.calorie.diary.viewmodel.AuthViewModel

@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onRegister: () -> Unit,
    addAccount: Boolean = false
) {
    val authVm: AuthViewModel = viewModel()
    val state by authVm.uiState.collectAsState()
    LaunchedEffect(Unit) {
        AppRefreshBus.events.collect { route ->
            if (route == Routes.ACCOUNT) authVm.refreshProfile()
        }
    }

    val showingLogin = state.account == null || addAccount
    AuthPageLayout(
        title = stringResource(if (showingLogin) R.string.app_name else R.string.auth_page_title),
        subtitle = stringResource(
            if (showingLogin) R.string.auth_login_subtitle else R.string.auth_page_intro
        ),
        onBack = onBack
    ) {
        AccountCard(
            authVm = authVm,
            showHeader = false,
            forceAddAccount = addAccount,
            onRegister = onRegister
        )
    }
}

@Composable
internal fun AuthPageLayout(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    accentColor: Color = OrangeDeep,
    backgroundColor: Color = ScreenBg,
    content: @Composable () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Cream, backgroundColor)))
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = Ink,
                    modifier = Modifier.size(25.dp)
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .size(98.dp)
                    .clip(CircleShape)
                    .background(CardWhite)
                    .border(1.dp, accentColor.copy(alpha = 0.28f), CircleShape)
                    .padding(5.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_fox),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = title,
                fontFamily = CuteFont,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = Ink,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = subtitle,
                fontFamily = CuteFont,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = InkLight,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(34.dp))
            content()
            Spacer(Modifier.height(36.dp))
        }
    }
}

package com.calorie.diary.ui.components

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorie.diary.R
import com.calorie.diary.data.UiMessage
import com.calorie.diary.data.network.NetworkMonitor
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Danger
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.Orange
import com.calorie.diary.ui.theme.OrangeDeep
import com.calorie.diary.ui.theme.Radius
import kotlinx.coroutines.delay

/**
 * 全局离线横幅：无网络时常驻顶部，点击直达系统网络设置。
 * 网络恢复后自动淡出，不打断原有页面布局。
 */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    val online by NetworkMonitor.online.collectAsState()
    val context = LocalContext.current

    val alpha by animateFloatAsState(
        targetValue = if (online) 0f else 1f,
        animationSpec = tween(220),
        label = "offlineBannerAlpha"
    )
    // 在线且淡出完成时不再占位，避免影响页面布局
    if (online && alpha <= 0.01f) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .background(Danger.copy(alpha = 0.14f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { openNetworkSettings(context) }
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("📶", fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.net_offline_title),
                fontFamily = CuteFont, fontSize = 13.sp, color = Ink
            )
            Text(
                stringResource(R.string.net_offline_hint),
                fontFamily = CuteFont, fontSize = 11.sp, color = InkLight
            )
        }
        Text(
            stringResource(R.string.net_open_settings),
            fontFamily = CuteFont, fontSize = 12.sp, color = OrangeDeep
        )
    }
}

/**
 * 全局轻提示：用于保存失败、网络异常等不应阻断操作的反馈，2.6 秒后自动消失。
 */
@Composable
fun AppToastHost(
    message: UiMessage?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var lastMessage by remember { mutableStateOf<UiMessage?>(null) }

    LaunchedEffect(message) {
        if (message != null) {
            lastMessage = message
            delay(2600)
            onDismiss()
        }
    }

    val shown = message ?: lastMessage ?: return
    val visible = message != null
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(200),
        label = "toastAlpha"
    )
    val offsetY by animateDpAsState(
        targetValue = if (visible) 0.dp else (-10).dp,
        animationSpec = tween(200),
        label = "toastOffset"
    )
    if (alpha <= 0.01f) return

    val shape = RoundedCornerShape(Radius.lg)
    val tint = if (shown.isError) Danger else Orange
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .offset(y = offsetY)
            .graphicsLayer { this.alpha = alpha }
            .shadow(
                elevation = 12.dp,
                shape = shape,
                clip = false,
                ambientColor = Ink.copy(alpha = 0.16f),
                spotColor = Ink.copy(alpha = 0.2f)
            )
            .clip(shape)
            .background(tint.copy(alpha = 0.16f))
            .background(
                Brush.verticalGradient(
                    listOf(
                        CardWhite.copy(alpha = 0.82f),
                        CardWhite.copy(alpha = 0.46f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        CardWhite.copy(alpha = 0.95f),
                        CardWhite.copy(alpha = 0.25f)
                    )
                ),
                shape = shape
            )
            .padding(start = 12.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.22f))
                .border(1.dp, CardWhite.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(if (shown.isError) "⚠️" else "✅", fontSize = 13.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            shown.text,
            fontFamily = CuteFont,
            fontSize = 13.sp,
            color = Ink,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 打开系统网络设置；优先无线设置，失败回退到设置首页。 */
fun openNetworkSettings(context: Context) {
    val newTask = Intent.FLAG_ACTIVITY_NEW_TASK
    val opened = runCatching {
        context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(newTask))
    }.isSuccess
    if (!opened) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(newTask))
        }
    }
}

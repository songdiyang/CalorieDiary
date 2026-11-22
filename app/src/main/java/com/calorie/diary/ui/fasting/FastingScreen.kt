package com.calorie.diary.ui.fasting

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorie.diary.R
import com.calorie.diary.ui.components.CuteButton
import com.calorie.diary.ui.components.CuteChip
import com.calorie.diary.ui.components.CuteProgressRing
import com.calorie.diary.ui.components.SoftCard
import com.calorie.diary.ui.home.appVm
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.NumberFont
import com.calorie.diary.ui.theme.Orange
import com.calorie.diary.ui.theme.Pink
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val plans = listOf(16, 14, 18)

@Composable
fun FastingScreen(onBack: () -> Unit) {
    val vm = appVm()
    val profile by vm.profile.collectAsState()
    val fastingStart = profile?.fastingStart ?: 0L
    val fastingHours = profile?.fastingHours ?: 16

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(fastingStart) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val running = fastingStart > 0
    val endAt = if (running) fastingStart + fastingHours * 3600_000L else 0L
    val isFasting = running && now < endAt
    val remaining = if (isFasting) endAt - now else 0L
    val elapsed = if (running) (now - fastingStart).coerceAtMost(fastingHours * 3600_000L) else 0L
    val progress = if (running) elapsed.toFloat() / (fastingHours * 3600_000L) else 0f

    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.US) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackArrow(onBack)
            Spacer(Modifier.size(4.dp))
            Text(stringResource(R.string.fasting_title), fontFamily = CuteFont, fontSize = 22.sp, color = Ink)
        }

        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(R.drawable.ill_fasting_banner),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        Spacer(Modifier.height(14.dp))

        // 方案选择
        SoftCard(
            modifier = Modifier.fillMaxWidth(),
            fill = CardWhite,
            contentPadding = PaddingValues(16.dp)
        ) {
            Column {
                Text(
                    stringResource(R.string.fasting_choose_plan),
                    fontFamily = CuteFont, fontSize = 15.sp, color = Ink
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    plans.forEach { h ->
                        CuteChip(
                            "$h-${24 - h}",
                            fastingHours == h,
                            { if (!running) vm.setFastingHours(h) },
                            selectedColor = Pink
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.fasting_plan_desc, fastingHours, 24 - fastingHours),
                    fontFamily = CuteFont, fontSize = 12.sp, color = InkLight
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        SoftCard(
            modifier = Modifier.fillMaxWidth(),
            fill = CardWhite,
            contentPadding = PaddingValues(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CuteProgressRing(
                    progress = if (isFasting) progress else if (running) 1f else 0f,
                    size = 220.dp,
                    ringColor = if (isFasting) Orange else Color(0xFF7FC4E8),
                    dotColor = if (isFasting) Orange else Color(0xFF7FC4E8)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(
                                when {
                                    isFasting -> R.string.fasting_state_fasting
                                    running -> R.string.fasting_state_eating
                                    else -> R.string.fasting_state_idle
                                }
                            ),
                            fontFamily = CuteFont, fontSize = 16.sp, color = InkLight
                        )
                        Text(
                            if (isFasting) formatDuration(remaining)
                            else if (running) "00:00:00"
                            else "--:--:--",
                            fontFamily = NumberFont, fontSize = 36.sp, color = Ink
                        )
                        Text(
                            stringResource(
                                when {
                                    isFasting -> R.string.fasting_remaining
                                    running -> R.string.fasting_done
                                    else -> R.string.fasting_start_hint
                                }
                            ),
                            fontFamily = CuteFont, fontSize = 12.sp, color = InkLight
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                if (running) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TimeInfo(stringResource(R.string.fasting_start_time), timeFmt.format(Date(fastingStart)))
                        TimeInfo(stringResource(R.string.fasting_end_time), timeFmt.format(Date(endAt)))
                    }
                    Spacer(Modifier.height(14.dp))
                    CuteButton(
                        stringResource(
                            if (isFasting) R.string.fasting_stop_early else R.string.fasting_stop_round
                        ),
                        { vm.setFastingStart(0L) },
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = Color(0xFFEF6C6C)
                    )
                } else {
                    CuteButton(
                        stringResource(R.string.fasting_start),
                        { vm.setFastingStart(System.currentTimeMillis()) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(R.drawable.mascot_sleep),
                contentDescription = null,
                modifier = Modifier.size(86.dp)
            )
        }
        Text(
            stringResource(R.string.fasting_tip),
            fontFamily = CuteFont, fontSize = 12.sp, color = InkLight
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun TimeInfo(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontFamily = CuteFont, fontSize = 12.sp, color = InkLight)
        Text(value, fontFamily = NumberFont, fontSize = 18.sp, color = Ink)
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

@Composable
private fun BackArrow(onBack: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null, onClick = onBack
        ).padding(8.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.icon_back),
            contentDescription = null,
            modifier = Modifier.size(26.dp)
        )
    }
}

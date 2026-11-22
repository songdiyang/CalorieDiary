package com.calorie.diary.ui.water

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorie.diary.R
import com.calorie.diary.ui.components.CuteChip
import com.calorie.diary.ui.components.SoftCard
import com.calorie.diary.ui.home.appVm
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.Cream
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.NumberFont
import com.calorie.diary.ui.theme.Orange

private val WaterBlue = Color(0xFF7EC8E3)
private val WaterBlueDeep = Color(0xFF5AA9D6)

@Composable
fun WaterScreen(onBack: () -> Unit) {
    val vm = appVm()
    val water by vm.water.collectAsState()
    val profile by vm.profile.collectAsState()

    val currentMl = water?.ml ?: 0
    val goal = profile?.waterGoal ?: 1500
    val reminder = profile?.waterReminder ?: false
    val progress = if (goal == 0) 0f else (currentMl.toFloat() / goal).coerceIn(0f, 1f)
    val remaining = (goal - currentMl).coerceAtLeast(0)

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackArrow(onBack)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.water_title), fontFamily = CuteFont, fontSize = 22.sp, color = Ink)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.water_status, (progress * 100).toInt(), remaining),
            fontFamily = CuteFont, fontSize = 14.sp, color = InkLight
        )

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Image(
                painter = painterResource(R.drawable.ill_water_banner),
                contentDescription = null,
                modifier = Modifier.height(150.dp)
            )
        }

        Spacer(Modifier.height(12.dp))
        SoftCard(
            modifier = Modifier.fillMaxWidth(),
            fill = CardWhite,
            contentPadding = PaddingValues(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$currentMl",
                    fontFamily = NumberFont, fontSize = 40.sp, color = WaterBlueDeep
                )
                Text(
                    stringResource(R.string.unit_ml),
                    fontFamily = CuteFont, fontSize = 14.sp, color = InkLight
                )
                Spacer(Modifier.height(12.dp))
                WaterCup(progress, Modifier.size(width = 170.dp, height = 210.dp))
            }
        }

        Spacer(Modifier.height(14.dp))

        // 目标与提醒
        SoftCard(
            modifier = Modifier.fillMaxWidth(),
            fill = CardWhite,
            contentPadding = PaddingValues(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.water_daily_goal),
                        fontFamily = CuteFont, fontSize = 15.sp, color = Ink
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "$goal ${stringResource(R.string.unit_ml)}",
                        fontFamily = NumberFont, fontSize = 16.sp, color = Orange
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.water_reminder),
                        fontFamily = CuteFont, fontSize = 15.sp, color = Ink
                    )
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = reminder,
                        onCheckedChange = { vm.setWaterReminder(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Orange,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = InkLight
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // 快捷加减
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AdjustChip("-100") { vm.adjustWater(-100) }
            AdjustChip("+100") { vm.adjustWater(100) }
            AdjustChip("+250") { vm.adjustWater(250) }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.water_tip),
            fontFamily = CuteFont, fontSize = 12.sp, color = InkLight
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun WaterCup(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height

        // 圆润胖胖杯几何
        val top = h * 0.07f
        val bottom = h * 0.94f
        val left = w * 0.20f
        val right = w * 0.80f
        val lip = w * 0.07f           // 杯口外扩
        val corner = w * 0.20f         // 底部圆角
        val strokeW = w * 0.05f

        // 水位（留出杯口内边距）
        val innerTop = top + h * 0.03f
        val fillTop = bottom - (bottom - innerTop) * progress.coerceIn(0f, 1f)

        val cupPath = Path().apply {
            moveTo(left - lip, top)
            lineTo(right + lip, top)
            lineTo(right, top + h * 0.015f)
            lineTo(right, bottom - corner)
            quadraticBezierTo(right, bottom, right - corner, bottom)
            lineTo(left + corner, bottom)
            quadraticBezierTo(left, bottom, left, bottom - corner)
            lineTo(left, top + h * 0.015f)
            close()
        }

        clipPath(cupPath) {
            // 杯内淡淡的玻璃底色
            drawRect(
                color = WaterBlue.copy(alpha = 0.10f),
                topLeft = Offset(0f, top),
                size = Size(w, bottom - top)
            )

            if (progress > 0.001f) {
                val waveAmp = w * 0.035f
                val waveY = fillTop
                // 带波浪水面的水体路径
                val waterPath = Path().apply {
                    moveTo(0f, waveY)
                    cubicTo(
                        w * 0.25f, waveY - waveAmp,
                        w * 0.35f, waveY + waveAmp,
                        w * 0.5f, waveY
                    )
                    cubicTo(
                        w * 0.65f, waveY - waveAmp,
                        w * 0.75f, waveY + waveAmp,
                        w, waveY
                    )
                    lineTo(w, bottom + 1f)
                    lineTo(0f, bottom + 1f)
                    close()
                }
                drawPath(
                    waterPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF9ED8EF),
                            WaterBlue,
                            WaterBlueDeep
                        ),
                        startY = fillTop,
                        endY = bottom
                    )
                )

                // 水面高光线
                drawLine(
                    Color.White.copy(alpha = 0.55f),
                    Offset(left + w * 0.05f, fillTop + w * 0.02f),
                    Offset(right - w * 0.12f, fillTop + w * 0.02f),
                    strokeWidth = w * 0.018f
                )

                // 气泡
                val bubbles = listOf(
                    Triple(w * 0.34f, fillTop + (bottom - fillTop) * 0.35f, w * 0.030f),
                    Triple(w * 0.60f, fillTop + (bottom - fillTop) * 0.55f, w * 0.022f),
                    Triple(w * 0.46f, fillTop + (bottom - fillTop) * 0.78f, w * 0.026f),
                    Triple(w * 0.68f, fillTop + (bottom - fillTop) * 0.82f, w * 0.016f)
                )
                bubbles.forEach { (bx, by, br) ->
                    drawCircle(Color.White.copy(alpha = 0.5f), br, Offset(bx, by))
                }
            }

            // 杯身左侧玻璃高光
            drawLine(
                Color.White.copy(alpha = 0.55f),
                Offset(left + w * 0.05f, top + h * 0.10f),
                Offset(left + w * 0.05f, bottom - corner),
                strokeWidth = w * 0.028f
            )
        }

        // 杯身描边
        drawPath(cupPath, color = Ink, style = Stroke(width = strokeW))

        // 杯口厚边
        drawLine(
            Ink,
            Offset(left - lip, top),
            Offset(right + lip, top),
            strokeWidth = strokeW * 1.15f
        )
    }
}

@Composable
private fun AdjustChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Orange)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, onClick = onClick
            )
            .padding(horizontal = 22.dp, vertical = 12.dp)
    ) {
        Text(text, fontFamily = NumberFont, fontSize = 16.sp, color = Color.White)
    }
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

@Composable
internal fun WaterPreviewContent() {
    Column(
        Modifier.fillMaxWidth().background(CardWhite).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        listOf(0f, 0.35f, 0.69f, 1f).forEach { p ->
            WaterCup(progress = p, modifier = Modifier.size(width = 150.dp, height = 185.dp))
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
internal fun WaterPagePreviewContent() {
    val currentMl = 1040
    val goal = 1500
    val p = currentMl.toFloat() / goal
    Column(
        Modifier.fillMaxWidth().background(Cream).padding(18.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackArrow { }
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.water_title),
                fontFamily = CuteFont, fontSize = 22.sp, color = Ink
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.water_status, (p * 100).toInt(), goal - currentMl),
            fontFamily = CuteFont, fontSize = 14.sp, color = InkLight
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Image(
                painter = painterResource(R.drawable.ill_water_banner),
                contentDescription = null,
                modifier = Modifier.height(150.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        SoftCard(
            modifier = Modifier.fillMaxWidth(),
            fill = CardWhite,
            contentPadding = PaddingValues(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$currentMl", fontFamily = NumberFont, fontSize = 40.sp, color = WaterBlueDeep)
                Text(
                    stringResource(R.string.unit_ml),
                    fontFamily = CuteFont, fontSize = 14.sp, color = InkLight
                )
                Spacer(Modifier.height(12.dp))
                WaterCup(p, Modifier.size(width = 170.dp, height = 210.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            AdjustChip("-100") { }
            AdjustChip("+100") { }
            AdjustChip("+250") { }
        }
    }
}

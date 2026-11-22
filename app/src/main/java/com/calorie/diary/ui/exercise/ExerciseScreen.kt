package com.calorie.diary.ui.exercise

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorie.diary.R
import com.calorie.diary.ui.components.CuteChip
import com.calorie.diary.ui.components.CuteNumberField
import com.calorie.diary.ui.components.SoftCard
import com.calorie.diary.ui.home.appVm
import com.calorie.diary.ui.theme.Blue
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.Mint
import com.calorie.diary.ui.theme.NumberFont
import com.calorie.diary.ui.theme.Orange
import com.calorie.diary.ui.theme.OrangeDeep
import com.calorie.diary.ui.theme.Pink
import kotlin.math.roundToInt

@Composable
fun ExerciseScreen(onBack: () -> Unit) {
    val vm = appVm()
    val profile by vm.profile.collectAsState()
    val todayIntake by vm.dayTotalCalories.collectAsState()

    fun fmtWeight(v: Float) = if (v % 1f == 0f) v.toInt().toString() else v.toString()

    var weightStr by remember { mutableStateOf(fmtWeight(profile?.weightKg ?: 60f)) }
    var heightStr by remember { mutableStateOf((profile?.heightCm ?: 165).toString()) }
    var durationStr by remember { mutableStateOf("30") }
    var loadWeightStr by remember { mutableStateOf("") }

    var tab by remember { mutableIntStateOf(0) }
    var cardioId by remember { mutableStateOf("brisk") }
    var loadIndex by remember { mutableIntStateOf(2) }

    val weight = weightStr.toFloatOrNull()
    val height = heightStr.toIntOrNull()
    val duration = durationStr.toIntOrNull()

    val bmi = if (weight != null && height != null && height > 0) {
        weight / ((height / 100f) * (height / 100f))
    } else null

    val selectedCardio = cardioExercises.firstOrNull { it.id == cardioId } ?: cardioExercises.first()
    val met = if (tab == 0) selectedCardio.met else loadLevels[loadIndex].met

    val gross = if (weight != null && duration != null) grossCalories(met, weight, duration) else 0.0
    val net = if (weight != null && duration != null) netCalories(met, weight, duration) else 0.0
    val netInt = net.roundToInt()
    val gap = computeGap(profile?.calorieGoal ?: 1800, todayIntake, netInt)

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
            Text(stringResource(R.string.exercise_title), fontFamily = CuteFont, fontSize = 22.sp, color = Ink)
        }

        // —— 身材卡片 ——
        Spacer(Modifier.height(14.dp))
        SoftCard(
            modifier = Modifier.fillMaxWidth(),
            fill = CardWhite,
            contentPadding = PaddingValues(16.dp)
        ) {
            Column {
                Text(stringResource(R.string.exercise_body), fontFamily = CuteFont, fontSize = 16.sp, color = Ink)
                Spacer(Modifier.height(12.dp))
                Row {
                    Box(Modifier.weight(1f)) {
                        CuteNumberField(
                            weightStr,
                            { weightStr = it.filter { c -> c.isDigit() || c == '.' } },
                            suffix = stringResource(R.string.unit_kg),
                            keyboardType = KeyboardType.Decimal
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Box(Modifier.weight(1f)) {
                        CuteNumberField(
                            heightStr,
                            { heightStr = it.filter { c -> c.isDigit() } },
                            suffix = stringResource(R.string.unit_cm)
                        )
                    }
                }
                if (bmi != null) {
                    val shape = bodyShape(bmi)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "%.1f".format(bmi),
                            fontFamily = NumberFont, fontSize = 22.sp, color = shape.color
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            stringResource(shape.labelRes),
                            fontFamily = CuteFont, fontSize = 14.sp, color = shape.color
                        )
                    }
                }
            }
        }

        // —— 运动选择 ——
        Spacer(Modifier.height(14.dp))
        SoftCard(
            modifier = Modifier.fillMaxWidth(),
            fill = CardWhite,
            contentPadding = PaddingValues(16.dp)
        ) {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CuteChip(stringResource(R.string.exercise_tab_cardio), tab == 0, { tab = 0 })
                    CuteChip(stringResource(R.string.exercise_tab_strength), tab == 1, { tab = 1 })
                }
                Spacer(Modifier.height(14.dp))
                if (tab == 0) {
                    FlowChips(
                        labels = cardioExercises.map { stringResource(it.nameRes) },
                        selectedIndex = cardioExercises.indexOfFirst { it.id == cardioId },
                        onSelect = { cardioId = cardioExercises[it].id }
                    )
                } else {
                    Text(
                        stringResource(R.string.exercise_load),
                        fontFamily = CuteFont, fontSize = 14.sp, color = InkLight
                    )
                    Spacer(Modifier.height(10.dp))
                    FlowChips(
                        labels = loadLevels.map { stringResource(it.nameRes) },
                        selectedIndex = loadIndex,
                        onSelect = { loadIndex = it }
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(loadLevels[loadIndex].descRes),
                        fontFamily = CuteFont, fontSize = 12.sp, color = InkLight
                    )
                    Spacer(Modifier.height(14.dp))
                    CuteNumberField(
                        loadWeightStr,
                        {
                            loadWeightStr = it.filter { c -> c.isDigit() || c == '.' }
                            val lw = loadWeightStr.toFloatOrNull()
                            if (weight != null && lw != null) {
                                loadIndex = recommendLoad(lw, weight)
                            }
                        },
                        suffix = stringResource(R.string.exercise_load_weight_unit),
                        label = stringResource(R.string.exercise_load_weight),
                        keyboardType = KeyboardType.Decimal
                    )
                }
                Spacer(Modifier.height(14.dp))
                CuteNumberField(
                    durationStr,
                    { durationStr = it.filter { c -> c.isDigit() } },
                    suffix = stringResource(R.string.exercise_minutes),
                    label = stringResource(R.string.exercise_duration)
                )
            }
        }

        // —— 结果卡片 ——
        Spacer(Modifier.height(14.dp))
        if (weight != null && duration != null && duration > 0) {
            SoftCard(
                modifier = Modifier.fillMaxWidth(),
                fill = CardWhite,
                contentPadding = PaddingValues(20.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.exercise_net), fontFamily = CuteFont, fontSize = 14.sp, color = InkLight)
                    Text(
                        netInt.toString(),
                        fontFamily = NumberFont, fontSize = 54.sp, color = OrangeDeep
                    )
                    Text(stringResource(R.string.unit_kcal), fontFamily = CuteFont, fontSize = 14.sp, color = InkLight)
                    Spacer(Modifier.height(10.dp))
                    Row {
                        Text(
                            stringResource(R.string.exercise_gross),
                            fontFamily = CuteFont, fontSize = 13.sp, color = InkLight
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            gross.roundToInt().toString(),
                            fontFamily = NumberFont, fontSize = 15.sp, color = Ink
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(stringResource(R.string.unit_kcal), fontFamily = CuteFont, fontSize = 12.sp, color = InkLight)
                    }
                    Spacer(Modifier.height(16.dp))
                    GapPanel(gap)
                }
            }

            // —— 不同身材对比 ——
            Spacer(Modifier.height(14.dp))
            SoftCard(
                modifier = Modifier.fillMaxWidth(),
                fill = CardWhite,
                contentPadding = PaddingValues(16.dp)
            ) {
                Column {
                    Text(stringResource(R.string.exercise_compare_body), fontFamily = CuteFont, fontSize = 16.sp, color = Ink)
                    Spacer(Modifier.height(16.dp))
                    val bars = weightCompareKg.map { kw ->
                        BarData(
                            label = kw.toString(),
                            value = netCalories(met, kw.toFloat(), duration).roundToInt(),
                            highlight = weight.roundToInt() == kw
                        )
                    }
                    HandBars(bars, Orange)
                }
            }

            // —— 不同负荷对比（力量） ——
            Spacer(Modifier.height(14.dp))
            SoftCard(
                modifier = Modifier.fillMaxWidth(),
                fill = CardWhite,
                contentPadding = PaddingValues(16.dp)
            ) {
                Column {
                    Text(stringResource(R.string.exercise_compare_load), fontFamily = CuteFont, fontSize = 16.sp, color = Ink)
                    Spacer(Modifier.height(16.dp))
                    val bars = loadLevels.mapIndexed { i, lv ->
                        BarData(
                            label = stringResource(lv.nameRes),
                            value = netCalories(lv.met, weight, duration).roundToInt(),
                            highlight = tab == 1 && i == loadIndex
                        )
                    }
                    HandBars(bars, Mint)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.exercise_load_note),
                        fontFamily = CuteFont, fontSize = 11.sp, color = InkLight
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.exercise_formula_note),
                fontFamily = CuteFont, fontSize = 11.sp, color = InkLight
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun GapPanel(gap: GapResult) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(com.calorie.diary.ui.theme.Cream)
            .padding(14.dp)
    ) {
        GapRow(stringResource(R.string.exercise_gap_goal), gap.goal.toString(), Ink)
        GapRow(stringResource(R.string.exercise_gap_intake), gap.intake.toString(), Ink)
        GapRow(
            stringResource(R.string.exercise_gap_before),
            gap.beforeRemaining.toString(),
            if (gap.beforeRemaining >= 0) Mint else Pink
        )
        GapRow(
            stringResource(R.string.exercise_gap_after),
            gap.afterRemaining.toString(),
            if (gap.afterRemaining >= 0) OrangeDeep else Pink
        )
    }
}

@Composable
private fun GapRow(label: String, value: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontFamily = CuteFont, fontSize = 13.sp, color = InkLight)
        Text(value, fontFamily = NumberFont, fontSize = 15.sp, color = color)
    }
}

private data class BarData(val label: String, val value: Int, val highlight: Boolean)

@Composable
private fun HandBars(bars: List<BarData>, baseColor: Color, maxHeight: androidx.compose.ui.unit.Dp = 120.dp) {
    val maxVal = (bars.maxOfOrNull { it.value }?.coerceAtLeast(1)) ?: 1
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        bars.forEach { bar ->
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    bar.value.toString(),
                    fontFamily = NumberFont, fontSize = 12.sp,
                    color = if (bar.highlight) OrangeDeep else InkLight
                )
                Spacer(Modifier.height(6.dp))
                val h = (maxHeight * (bar.value.toFloat() / maxVal))
                Box(
                    Modifier
                        .height(h)
                        .width(26.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (bar.highlight) OrangeDeep else baseColor)
                        .border(2.dp, Ink, RoundedCornerShape(10.dp))
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    bar.label,
                    fontFamily = CuteFont, fontSize = 11.sp, color = Ink,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun FlowChips(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.chunked(3).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { label ->
                    val idx = labels.indexOf(label)
                    CuteChip(label, idx == selectedIndex, { onSelect(idx) })
                }
            }
        }
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

/** 静态预览（不依赖 ViewModel），供 Paparazzi 截图与 IDE 预览使用 */
@Composable
fun ExercisePreviewContent() {
    Column(
        Modifier
            .fillMaxWidth()
            .background(CardWhite)
            .padding(16.dp)
    ) {
        Text("净运动消耗", fontFamily = CuteFont, fontSize = 14.sp, color = InkLight)
        Text("168", fontFamily = NumberFont, fontSize = 44.sp, color = OrangeDeep)
        Spacer(Modifier.height(12.dp))
        GapPanel(computeGap(1800, 1200, 168))
        Spacer(Modifier.height(18.dp))
        Text("不同身材对比", fontFamily = CuteFont, fontSize = 16.sp, color = Ink)
        Spacer(Modifier.height(14.dp))
        HandBars(
            weightCompareKg.map { kw ->
                BarData(kw.toString(), netCalories(6.6, kw.toFloat(), 30).roundToInt(), kw == 60)
            },
            Orange
        )
        Spacer(Modifier.height(18.dp))
        Text("不同负荷对比", fontFamily = CuteFont, fontSize = 16.sp, color = Ink)
        Spacer(Modifier.height(14.dp))
        HandBars(
            loadLevels.mapIndexed { i, lv ->
                BarData(
                    (i + 1).toString(),
                    netCalories(lv.met, 60f, 30).roundToInt(),
                    i == 2
                )
            },
            Mint
        )
    }
}

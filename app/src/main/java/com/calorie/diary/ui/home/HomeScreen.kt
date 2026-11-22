package com.calorie.diary.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.calorie.diary.data.DateUtils
import com.calorie.diary.data.db.MealEntryEntity
import com.calorie.diary.data.vision.scaledCalories
import com.calorie.diary.ui.components.BarItem
import com.calorie.diary.ui.components.CuteButton
import com.calorie.diary.ui.components.CuteProgressRing
import com.calorie.diary.ui.components.FoodIcon
import com.calorie.diary.ui.components.SimpleBarChart
import com.calorie.diary.ui.components.SoftCard
import com.calorie.diary.ui.components.localizedFoodName
import com.calorie.diary.viewmodel.AppViewModel
import com.calorie.diary.R
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.Cream
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Danger
import com.calorie.diary.ui.theme.Hairline
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.NumberFont
import com.calorie.diary.ui.theme.Orange
import com.calorie.diary.ui.theme.Radius
import com.calorie.diary.ui.theme.Space
import com.calorie.diary.ui.theme.Success

private val mealTitleRes = listOf(
    R.string.meal_breakfast,
    R.string.meal_lunch,
    R.string.meal_dinner,
    R.string.meal_snack
)

@Composable
fun appVm(): AppViewModel {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext
            as com.calorie.diary.CalorieApp
    return viewModel(
        factory = viewModelFactory {
            initializer { AppViewModel(app) }
        }
    )
}

@Composable
fun HomeScreen(
    onAddFood: (mealType: Int, date: String) -> Unit,
    onOpenTools: () -> Unit
) {
    val vm = appVm()
    val date by vm.selectedDate.collectAsState()
    val entries by vm.dayEntries.collectAsState()
    val total by vm.dayTotalCalories.collectAsState()
    val profile by vm.profile.collectAsState()
    val water by vm.water.collectAsState()

    LaunchedEffect(Unit) { vm.selectDate(DateUtils.todayString()) }

    HomeDashboard(
        date = date,
        entries = entries,
        total = total,
        goal = profile?.calorieGoal ?: 1800,
        waterMl = water?.ml ?: 0,
        waterGoal = profile?.waterGoal ?: 1500,
        onAddFood = onAddFood,
        onAdjustWater = { vm.adjustWater(200) },
        onDeleteEntry = { vm.deleteEntry(it) },
        onUpdateGrams = { entry, grams -> vm.updateEntryGrams(entry, grams) }
    )
}

@Composable
fun HomeDashboard(
    date: String,
    entries: List<MealEntryEntity>,
    total: Int,
    goal: Int,
    waterMl: Int,
    waterGoal: Int,
    onAddFood: (mealType: Int, date: String) -> Unit,
    onAdjustWater: () -> Unit,
    onDeleteEntry: (Long) -> Unit,
    onUpdateGrams: (MealEntryEntity, Int) -> Unit
) {
    val remaining = goal - total
    val progress = if (goal == 0) 0f else total.toFloat() / goal
    var editingEntry by remember { mutableStateOf<MealEntryEntity?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.s18)
        ) {
            Spacer(Modifier.height(Space.s16))

            // 主热量卡：左大数字 + 右环形百分比
            SoftCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = Radius.xxl,
                contentPadding = PaddingValues(Space.s20)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "$total",
                            fontFamily = NumberFont,
                            fontSize = 44.sp,
                            color = Ink
                        )
                        Text(
                            "${stringResource(R.string.unit_kcal)} · " +
                                stringResource(R.string.home_goal_prefix, goal),
                            fontFamily = CuteFont,
                            fontSize = 13.sp,
                            color = InkLight
                        )
                        Spacer(Modifier.height(Space.s8))
                        Text(
                            "${stringResource(R.string.home_can_eat)} ${remaining.coerceAtLeast(0)}",
                            fontFamily = CuteFont,
                            fontSize = 13.sp,
                            color = if (remaining >= 0) Success else Danger
                        )
                    }
                    val ringColor = if (remaining >= 0) Orange else Danger
                    CuteProgressRing(
                        progress = progress,
                        size = 128.dp,
                        ringColor = ringColor,
                        dotColor = ringColor,
                        dots = 24
                    ) {
                        Text(
                            "${(progress.coerceIn(0f, 1f) * 100).toInt()}%",
                            fontFamily = NumberFont,
                            fontSize = 20.sp,
                            color = Ink
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.s14))

            // 并排数据卡：三餐柱状图 + 饮水进度（IntrinsicHeight 保证两卡真正等高）
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Space.s12)
            ) {
                SoftCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(Space.s14)
                ) {
                    // SoftCard 内容层是 Box：多段内容必须显式包 Column，否则会互相叠压
                    Column {
                        Text(
                            stringResource(R.string.home_meal_total),
                            fontFamily = CuteFont,
                            fontSize = 14.sp,
                            color = Ink
                        )
                        Spacer(Modifier.height(Space.s10))
                        val bars = (0..3).map { meal ->
                            BarItem(
                                label = stringResource(mealTitleRes[meal]),
                                value = entries.filter { it.mealType == meal }.sumOf { it.calories },
                                color = Orange.copy(alpha = 1f - meal * 0.16f)
                            )
                        }
                        SimpleBarChart(items = bars, height = 96.dp)
                    }
                }

                SoftCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(Space.s14)
                ) {
                    Column(Modifier.fillMaxHeight()) {
                        Text(
                            stringResource(R.string.water_title),
                            fontFamily = CuteFont,
                            fontSize = 14.sp,
                            color = Ink
                        )
                        Spacer(Modifier.height(Space.s10))
                        // 占满剩余高度，让进度条与相邻卡底部对齐，消除卡片下方空白
                        WaterMini(
                            ml = waterMl,
                            goal = waterGoal,
                            onAdd = onAdjustWater,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.s14))

            // 今日记录列表
            SoftCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(Space.s16)
            ) {
                Column {
                    Text(
                        stringResource(R.string.rec_result),
                        fontFamily = CuteFont,
                        fontSize = 16.sp,
                        color = Ink
                    )
                    Spacer(Modifier.height(Space.s10))
                    if (entries.isEmpty()) {
                        Text(
                            stringResource(R.string.home_meal_empty),
                            fontFamily = CuteFont,
                            fontSize = 13.sp,
                            color = InkLight,
                            modifier = Modifier.padding(vertical = Space.s8)
                        )
                    } else {
                        entries.forEach { e ->
                            RecordRow(
                                e = e,
                                onDelete = { onDeleteEntry(e.id) },
                                onEditGrams = { editingEntry = e }
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Hairline)
                            )
                        }
                    }
                    Spacer(Modifier.height(Space.s12))
                    // 餐次快速添加
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s8)) {
                        for (meal in 0..3) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(Radius.sm))
                                    .background(Orange.copy(alpha = 0.12f))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { onAddFood(meal, date) }
                                    .padding(vertical = Space.s10),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    stringResource(mealTitleRes[meal]),
                                    fontFamily = CuteFont,
                                    fontSize = 13.sp,
                                    color = Ink
                                )
                            }
                        }
                    }
                }
            }

            // 底部留白：让滚动到底时内容不被悬浮拍照按钮的托盘遮住
            Spacer(Modifier.height(Space.s32))
        }

        // 点击克数后从底部滑出的克数调整面板
        editingEntry?.let { entry ->
            AdjustGramsSheet(
                entry = entry,
                onDismiss = { editingEntry = null },
                onConfirm = { grams ->
                    editingEntry = null
                    onUpdateGrams(entry, grams)
                }
            )
        }
    }
}

@Composable
private fun WaterMini(
    ml: Int,
    goal: Int,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fraction = if (goal == 0) 0f else ml.toFloat() / goal
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "$ml",
                fontFamily = NumberFont,
                fontSize = 26.sp,
                color = Ink
            )
            Text(
                stringResource(R.string.unit_ml),
                fontFamily = CuteFont,
                fontSize = 12.sp,
                color = InkLight,
                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Orange)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onAdd
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("+", fontFamily = NumberFont, fontSize = 18.sp, color = Color.White)
            }
        }
        // 留白吸收卡片多余高度，把进度条压在卡片底部
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(Hairline)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(Orange)
            )
        }
        Spacer(Modifier.height(Space.s6))
        Text(
            stringResource(R.string.water_daily_goal) + " $goal",
            fontFamily = CuteFont,
            fontSize = 11.sp,
            color = InkLight
        )
    }
}

@Composable
private fun RecordRow(e: MealEntryEntity, onDelete: () -> Unit, onEditGrams: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.s10),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FoodIcon(name = e.foodName, fallbackEmoji = e.emoji, size = 40.dp)
        Spacer(Modifier.size(Space.s10))
        Column(Modifier.weight(1f)) {
            Text(
                localizedFoodName(e.foodName),
                fontFamily = CuteFont,
                fontSize = 15.sp,
                color = Ink
            )
            // 克数可点击调整；offset 抵消热区留白，让文字与名称保持左对齐
            Row(
                Modifier
                    .offset(x = (-4).dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onEditGrams
                    )
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${e.grams} ${stringResource(R.string.unit_gram)}",
                    fontFamily = CuteFont,
                    fontSize = 11.sp,
                    color = InkLight
                )
                Spacer(Modifier.width(3.dp))
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.icon_pencil),
                    contentDescription = stringResource(R.string.home_adjust_grams),
                    modifier = Modifier.size(10.dp)
                )
            }
        }
        Text(
            "${e.calories} ${stringResource(R.string.unit_kcal)}",
            fontFamily = NumberFont,
            fontSize = 14.sp,
            color = Ink
        )
        Spacer(Modifier.size(Space.s10))
        Box(
            Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDelete
            )
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(R.drawable.icon_close),
                contentDescription = stringResource(R.string.home_delete),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** 底部滑出的克数调整面板：直接输入新的克数，热量按原记录每克热量等比换算。 */
@Composable
private fun AdjustGramsSheet(
    entry: MealEntryEntity,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var gramsText by remember(entry.id) { mutableStateOf(entry.grams.toString()) }
    val grams = gramsText.toIntOrNull()?.takeIf { it in 1..99999 }
    val calories = grams?.let { scaledCalories(entry.grams, entry.calories, it) }
    val per100 = scaledCalories(entry.grams, entry.calories, 100)
    val inputFocus = remember { FocusRequester() }

    // 进入面板即聚焦输入框，让用户直接敲数字
    LaunchedEffect(Unit) { runCatching { inputFocus.requestFocus() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .background(Color(0x99000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        SoftCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { },
            fill = Cream,
            cornerRadius = 30.dp,
            contentPadding = PaddingValues(22.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FoodIcon(name = entry.foodName, fallbackEmoji = entry.emoji, size = 68.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            localizedFoodName(entry.foodName),
                            fontFamily = CuteFont, fontSize = 20.sp, color = Ink
                        )
                        Text(
                            stringResource(R.string.search_each100, per100),
                            fontFamily = CuteFont, fontSize = 13.sp, color = InkLight
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.search_weight), fontFamily = CuteFont, fontSize = 15.sp, color = Ink)
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardWhite)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = gramsText,
                            onValueChange = { input ->
                                gramsText = input.filter { it.isDigit() }.take(5)
                            },
                            modifier = Modifier
                                .width(86.dp)
                                .focusRequester(inputFocus),
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = NumberFont,
                                fontSize = 26.sp,
                                color = Orange,
                                textAlign = TextAlign.End
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            cursorBrush = SolidColor(Orange)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.unit_gram), fontFamily = CuteFont, fontSize = 14.sp, color = InkLight)
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            stringResource(R.string.search_this_cal),
                            fontFamily = CuteFont, fontSize = 13.sp, color = InkLight
                        )
                        Text(
                            calories?.let { "$it ${stringResource(R.string.unit_kcal)}" } ?: "--",
                            fontFamily = NumberFont, fontSize = 24.sp, color = Ink
                        )
                    }
                    CuteButton(
                        stringResource(R.string.common_save),
                        { grams?.let(onConfirm) },
                        enabled = grams != null
                    )
                }
            }
        }
    }
}

@Composable
internal fun HomePreviewContent() {
    val sample = listOf(
        MealEntryEntity(1, "2026-11-09", 0, "玉米", "🌽", 200, 224, 1L),
        MealEntryEntity(2, "2026-11-09", 1, "鸡胸肉", "🍗", 150, 195, 2L),
        MealEntryEntity(3, "2026-11-09", 1, "米饭", "🍚", 100, 116, 3L),
        MealEntryEntity(4, "2026-11-09", 3, "酸奶", "🥛", 100, 72, 4L)
    )
    HomeDashboard(
        date = "2026-11-09",
        entries = sample,
        total = 607,
        goal = 1800,
        waterMl = 1040,
        waterGoal = 1500,
        onAddFood = { _, _ -> },
        onAdjustWater = {},
        onDeleteEntry = {},
        onUpdateGrams = { _, _ -> }
    )
}

@Composable
internal fun HomeEmptyPreviewContent() {
    HomeDashboard(
        date = "2026-11-09",
        entries = emptyList(),
        total = 0,
        goal = 1784,
        waterMl = 0,
        waterGoal = 1500,
        onAddFood = { _, _ -> },
        onAdjustWater = {},
        onDeleteEntry = {},
        onUpdateGrams = { _, _ -> }
    )
}

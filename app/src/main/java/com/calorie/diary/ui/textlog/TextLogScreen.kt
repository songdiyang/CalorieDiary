package com.calorie.diary.ui.textlog

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorie.diary.R
import com.calorie.diary.data.DateUtils
import com.calorie.diary.data.db.FoodEntity
import com.calorie.diary.data.parser.ParseIntent
import com.calorie.diary.data.parser.ParsedFood
import com.calorie.diary.data.parser.TextFoodParser
import com.calorie.diary.data.parser.TextParseResult
import com.calorie.diary.ui.components.CuteChip
import com.calorie.diary.ui.components.FoodIcon
import com.calorie.diary.ui.components.SoftCard
import com.calorie.diary.ui.home.appVm
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.Cream
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.MintSoft
import com.calorie.diary.ui.theme.NumberFont
import com.calorie.diary.ui.theme.Orange
import com.calorie.diary.ui.theme.PinkSoft
import com.calorie.diary.ui.theme.YellowSoft
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private data class EditableLine(
    val food: FoodEntity,
    val grams: Int,
    val estimated: Boolean,
    val removed: Boolean = false,
    val fixedCalories: Int? = null
) {
    val calories: Int
        get() = fixedCalories
            ?: Math.round(food.caloriesPer100g * grams / 100.0).toInt()
}

@Composable
fun TextLogScreen(onBack: () -> Unit) {
    val vm = appVm()
    val foods by vm.foods.collectAsState()
    val profile by vm.profile.collectAsState()
    val context = LocalContext.current

    var text by remember { mutableStateOf("") }
    var mealType by remember { mutableIntStateOf(smartMealType()) }
    var dateOffset by remember { mutableIntStateOf(0) }
    var lines by remember { mutableStateOf<List<EditableLine>>(emptyList()) }
    var parsed by remember { mutableStateOf(TextParseResult()) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var existingCal by remember { mutableIntStateOf(0) }
    var aiLoading by remember { mutableStateOf(false) }
    var aiRequestId by remember { mutableIntStateOf(0) }

    fun applyParsedResult(r: TextParseResult) {
        parsed = r
        if (r.mealFromText != null) mealType = r.mealFromText
        dateOffset = r.dateOffset
        lines = buildList {
            r.addItems.forEach {
                add(EditableLine(it.food, it.totalGrams, it.estimated))
            }
            r.directCalories?.let { cal ->
                add(
                    EditableLine(
                        food = FoodEntity(0, "手动记录", 0, "其他", "🍴", false),
                        grams = 100,
                        estimated = false,
                        fixedCalories = cal
                    )
                )
            }
        }
        editingIndex = null
    }

    // 本地结果先反馈，输入停止后调用 AI；仅当前最新请求可以覆盖结果。
    LaunchedEffect(text, foods) {
        val requestId = aiRequestId + 1
        aiRequestId = requestId
        val requestText = text
        applyParsedResult(TextFoodParser.parse(requestText, foods))
        if (requestText.isBlank()) {
            aiLoading = false
            return@LaunchedEffect
        }
        delay(700)
        aiLoading = true
        try {
            val result = vm.parseTextWithAi(requestText, foods)
            if (aiRequestId == requestId) applyParsedResult(result)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            if (aiRequestId == requestId) {
                vm.showMessage(context.getString(R.string.textlog_ai_fallback))
            }
        } finally {
            if (aiRequestId == requestId) aiLoading = false
        }
    }

    val targetDate = DateUtils.addDays(DateUtils.todayString(), dateOffset)

    // 目标日期当日已有热量
    LaunchedEffect(targetDate) {
        existingCal = vm.entriesForDate(targetDate).sumOf { it.calories }
    }

    val activeLines = lines.filter { !it.removed }
    val addCalories = activeLines.sumOf { it.calories }
    val goal = profile?.calorieGoal ?: 1800
    val projected = existingCal + addCalories
    val ratio = (projected.toFloat() / goal).coerceIn(0f, 1.5f)
    val remaining = goal - projected
    val canSave = !aiLoading && when (parsed.intent) {
        ParseIntent.DELETE -> parsed.removeNames.isNotEmpty()
        else -> activeLines.isNotEmpty()
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackArrow(onBack)
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.textlog_title),
                fontFamily = CuteFont, fontSize = 22.sp, color = Ink
            )
        }

        Spacer(Modifier.height(16.dp))

        // 日期选择
        FieldLabel(stringResource(R.string.textlog_date_label))
        Spacer(Modifier.height(8.dp))
        Row {
            listOf(
                0 to R.string.date_today,
                -1 to R.string.date_yesterday,
                -2 to R.string.date_day_before
            ).forEach { (off, labelRes) ->
                CuteChip(stringResource(labelRes), dateOffset == off, { dateOffset = off })
                Spacer(Modifier.width(8.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            DateUtils.friendly(targetDate),
            fontFamily = CuteFont, fontSize = 12.sp, color = InkLight
        )

        Spacer(Modifier.height(16.dp))

        // 输入卡
        SoftCard(
            modifier = Modifier.fillMaxWidth(),
            fill = CardWhite,
            contentPadding = PaddingValues(16.dp)
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = false,
                textStyle = TextStyle(
                    fontFamily = CuteFont, color = Ink, fontSize = 17.sp, lineHeight = 24.sp
                ),
                cursorBrush = SolidColor(Orange),
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Default),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth()) {
                        if (text.isEmpty()) {
                            Text(
                                stringResource(R.string.textlog_placeholder),
                                fontFamily = CuteFont, color = InkLight, fontSize = 15.sp
                            )
                        }
                        inner()
                    }
                }
            )
        }

        Spacer(Modifier.height(12.dp))

        // 快捷示例（横向滚动）
        Text(
            stringResource(R.string.textlog_quick_label),
            fontFamily = CuteFont, fontSize = 12.sp, color = InkLight
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                R.string.textlog_sample_1,
                R.string.textlog_sample_2,
                R.string.textlog_sample_3,
                R.string.textlog_sample_4,
                R.string.textlog_sample_5
            ).forEach { res ->
                val sample = stringResource(res)
                CuteChip(sample, false, { text = sample })
            }
        }

        Spacer(Modifier.height(16.dp))

        // 意图提示
        if (text.isNotBlank()) {
            IntentBanner(parsed.intent, activeLines.size, parsed.removeNames.size)
            Spacer(Modifier.height(16.dp))
        }

        // 餐次选择
        FieldLabel(stringResource(R.string.textlog_meal_label))
        Spacer(Modifier.height(8.dp))
        Row {
            listOf(
                R.string.meal_breakfast to 0,
                R.string.meal_lunch to 1,
                R.string.meal_dinner to 2,
                R.string.meal_snack to 3
            ).forEach { (labelRes, type) ->
                CuteChip(stringResource(labelRes), mealType == type, { mealType = type })
                Spacer(Modifier.width(8.dp))
            }
        }

        Spacer(Modifier.height(16.dp))

        // 删除意图：展示将删除的目标
        if (parsed.intent == ParseIntent.DELETE) {
            if (parsed.removeNames.isNotEmpty()) {
                SoftCard(
                    modifier = Modifier.fillMaxWidth(),
                    fill = PinkSoft,
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Text(
                        stringResource(
                            R.string.textlog_will_remove,
                            parsed.removeNames.joinToString("、")
                        ),
                        fontFamily = CuteFont, fontSize = 13.sp, color = Ink
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        } else if (activeLines.isNotEmpty()) {
            // 结果卡
            SoftCard(
                modifier = Modifier.fillMaxWidth(),
                fill = CardWhite,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Column {
                    activeLines.forEachIndexed { i, line ->
                        val realIndex = lines.indexOf(line)
                        ResultRow(
                            line = line,
                            editing = editingIndex == realIndex,
                            onEdit = {
                                editingIndex = if (editingIndex == realIndex) null else realIndex
                            },
                            onGramsChange = { g ->
                                lines = lines.mapIndexed { idx, l ->
                                    if (idx == realIndex) l.copy(grams = g) else l
                                }
                            },
                            onRemove = {
                                lines = lines.mapIndexed { idx, l ->
                                    if (idx == realIndex) l.copy(removed = true) else l
                                }
                            }
                        )
                        if (i < activeLines.lastIndex) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(InkLight.copy(alpha = 0.15f))
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // 未匹配提示
        if (parsed.unmatched.isNotEmpty()) {
            SoftCard(
                modifier = Modifier.fillMaxWidth(),
                fill = YellowSoft,
                contentPadding = PaddingValues(14.dp)
            ) {
                Text(
                    stringResource(
                        R.string.textlog_unmatched,
                        parsed.unmatched.joinToString("、")
                    ),
                    fontFamily = CuteFont, fontSize = 12.sp, color = Ink
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        if (aiLoading) {
            Text(
                stringResource(R.string.textlog_ai_working),
                fontFamily = CuteFont, fontSize = 12.sp, color = Orange
            )
            Spacer(Modifier.height(12.dp))
        }

        if (text.isBlank()) {
            Text(
                stringResource(R.string.textlog_hint),
                fontFamily = CuteFont, fontSize = 12.sp, color = InkLight
            )
            Spacer(Modifier.height(12.dp))
        }

        // 汇总卡
        SoftCard(
            modifier = Modifier.fillMaxWidth(),
            fill = MintSoft,
            contentPadding = PaddingValues(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.textlog_total),
                            fontFamily = CuteFont, fontSize = 12.sp, color = InkLight
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                addCalories.toString(),
                                fontFamily = NumberFont, fontSize = 26.sp, color = Orange
                            )
                            Text(
                                stringResource(R.string.unit_kcal),
                                fontFamily = CuteFont, fontSize = 12.sp, color = InkLight
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            stringResource(R.string.textlog_remaining),
                            fontFamily = CuteFont, fontSize = 12.sp, color = InkLight
                        )
                        Text(
                            if (remaining >= 0) remaining.toString() else "0",
                            fontFamily = NumberFont, fontSize = 20.sp, color = Ink
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                // 目标进度条
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(CardWhite)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(ratio.coerceAtMost(1f))
                            .height(10.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (remaining >= 0) Orange else PinkSoft)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(
                        R.string.textlog_goal_percent,
                        (ratio * 100).roundToInt()
                    ),
                    fontFamily = CuteFont, fontSize = 11.sp, color = InkLight
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        com.calorie.diary.ui.components.CuteButton(
            text = stringResource(
                if (parsed.intent == ParseIntent.DELETE) R.string.textlog_confirm_delete
                else R.string.textlog_save
            ),
            onClick = {
                if (!canSave) return@CuteButton
                if (parsed.intent == ParseIntent.DELETE) {
                    val r = TextParseResult(
                        intent = ParseIntent.DELETE,
                        removeNames = parsed.removeNames
                    )
                    vm.applyParseResult(r, mealType, targetDate)
                } else {
                    val adds = activeLines.filter { it.fixedCalories == null }.map { l ->
                        ParsedFood(l.food, 1, l.grams, l.grams, l.calories, l.estimated)
                    }
                    val direct = activeLines.filter { it.fixedCalories != null }
                        .sumOf { it.fixedCalories ?: 0 }
                    vm.applyParseResult(
                        TextParseResult(
                            intent = ParseIntent.ADD,
                            addItems = adds,
                            directCalories = direct.takeIf { it > 0 }
                        ),
                        mealType,
                        targetDate
                    )
                }
                onBack()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = canSave
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, fontFamily = CuteFont, fontSize = 15.sp, color = Ink)
}

@Composable
private fun IntentBanner(intent: ParseIntent, addCount: Int, removeCount: Int) {
    val (fill, msg) = when (intent) {
        ParseIntent.DELETE -> PinkSoft to stringResource(R.string.intent_delete, removeCount)
        ParseIntent.QUERY -> YellowSoft to stringResource(R.string.intent_query)
        ParseIntent.STATS -> YellowSoft to stringResource(R.string.intent_stats)
        else -> MintSoft to stringResource(R.string.intent_add, addCount)
    }
    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        fill = fill,
        cornerRadius = 18.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(msg, fontFamily = CuteFont, fontSize = 13.sp, color = Ink)
    }
}

@Composable
private fun ResultRow(
    line: EditableLine,
    editing: Boolean,
    onEdit: () -> Unit,
    onGramsChange: (Int) -> Unit,
    onRemove: () -> Unit
) {
    var draft by remember { mutableStateOf("") }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FoodIcon(line.food.name, line.food.emoji, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(line.food.name, fontFamily = CuteFont, fontSize = 15.sp, color = Ink)
            if (editing && line.fixedCalories == null) {
                if (draft.isEmpty()) draft = line.grams.toString()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .width(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Cream)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        BasicTextField(
                            value = draft,
                            onValueChange = { v ->
                                draft = v.filter { it.isDigit() }.take(5)
                                draft.toIntOrNull()?.let(onGramsChange)
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = NumberFont, fontSize = 14.sp, color = Ink
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            cursorBrush = SolidColor(Orange)
                        )
                    }
                    Text(
                        stringResource(R.string.textlog_gram_unit),
                        fontFamily = CuteFont, fontSize = 11.sp, color = InkLight
                    )
                }
            } else {
                Row(
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = if (line.fixedCalories == null) onEdit else ({} )
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (line.fixedCalories != null)
                            stringResource(R.string.textlog_direct_cal, line.fixedCalories)
                        else "${line.grams} 克",
                        fontFamily = CuteFont, fontSize = 11.sp, color = InkLight
                    )
                    if (line.fixedCalories == null) {
                        Spacer(Modifier.width(4.dp))
                        Image(
                            painter = painterResource(R.drawable.icon_pencil),
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                Text(
                    stringResource(
                        if (line.estimated) R.string.tag_estimated else R.string.tag_exact
                    ),
                    fontFamily = CuteFont, fontSize = 10.sp, color = InkLight
                )
            }
        }
        Text(
            line.calories.toString(),
            fontFamily = NumberFont, fontSize = 16.sp, color = Ink
        )
        Text(
            stringResource(R.string.unit_kcal),
            fontFamily = CuteFont, fontSize = 11.sp, color = InkLight
        )
        Spacer(Modifier.width(10.dp))
        Image(
            painter = painterResource(R.drawable.icon_close),
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null, onClick = onRemove
                )
        )
    }
}

private fun smartMealType(): Int {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        h in 5..9 -> 0
        h in 10..13 -> 1
        h in 14..16 -> 3
        else -> 2
    }
}

@Composable
private fun BackArrow(onBack: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, onClick = onBack
            )
            .padding(8.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.icon_back),
            contentDescription = null,
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
internal fun TextLogPreviewContent() {
    val foods = com.calorie.diary.data.seed.FoodSeed.foods
    val result = remember {
        TextFoodParser.parse("吃了一根玉米，一杯豆浆", foods)
    }
    val previewLines = result.addItems.map {
        EditableLine(it.food, it.totalGrams, it.estimated)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Cream)
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackArrow { }
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.textlog_title),
                fontFamily = CuteFont, fontSize = 22.sp, color = Ink
            )
        }
        Spacer(Modifier.height(16.dp))
        FieldLabel(stringResource(R.string.textlog_date_label))
        Spacer(Modifier.height(8.dp))
        Row {
            listOf(
                R.string.date_today to true,
                R.string.date_yesterday to false,
                R.string.date_day_before to false
            ).forEachIndexed { i, (res, sel) ->
                CuteChip(stringResource(res), sel, { })
                Spacer(Modifier.width(8.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        SoftCard(
            modifier = Modifier.fillMaxWidth(),
            fill = CardWhite,
            contentPadding = PaddingValues(16.dp)
        ) {
            Text(
                "吃了一根玉米，一杯豆浆",
                fontFamily = CuteFont, fontSize = 17.sp, color = Ink
            )
        }
        Spacer(Modifier.height(16.dp))
        FieldLabel(stringResource(R.string.textlog_meal_label))
        Spacer(Modifier.height(8.dp))
        Row {
            listOf(
                R.string.meal_breakfast to false,
                R.string.meal_lunch to true,
                R.string.meal_dinner to false,
                R.string.meal_snack to false
            ).forEach { (res, sel) ->
                CuteChip(stringResource(res), sel, { })
                Spacer(Modifier.width(8.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        SoftCard(
            modifier = Modifier.fillMaxWidth(),
            fill = CardWhite,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Column {
                previewLines.forEachIndexed { i, line ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FoodIcon(line.food.name, line.food.emoji, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                line.food.name,
                                fontFamily = CuteFont, fontSize = 15.sp, color = Ink
                            )
                            Text(
                                "${line.grams} 克",
                                fontFamily = CuteFont, fontSize = 11.sp, color = InkLight
                            )
                        }
                        Text(
                            line.calories.toString(),
                            fontFamily = NumberFont, fontSize = 16.sp, color = Ink
                        )
                        Text(
                            stringResource(R.string.unit_kcal),
                            fontFamily = CuteFont, fontSize = 11.sp, color = InkLight
                        )
                    }
                    if (i < previewLines.lastIndex) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(InkLight.copy(alpha = 0.15f))
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SoftCard(
            modifier = Modifier.fillMaxWidth(),
            fill = MintSoft,
            contentPadding = PaddingValues(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.textlog_total),
                            fontFamily = CuteFont, fontSize = 12.sp, color = InkLight
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                result.totalCalories.toString(),
                                fontFamily = NumberFont, fontSize = 26.sp, color = Orange
                            )
                            Text(
                                stringResource(R.string.unit_kcal),
                                fontFamily = CuteFont, fontSize = 12.sp, color = InkLight
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(CardWhite)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.17f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Orange)
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        com.calorie.diary.ui.components.CuteButton(
            text = stringResource(R.string.textlog_save),
            onClick = { },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
    }
}

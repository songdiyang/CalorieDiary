package com.calorie.diary.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorie.diary.R
import com.calorie.diary.data.supabase.SharedFood
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Hairline
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.NumberFont
import com.calorie.diary.ui.theme.OrangeDeep
import com.calorie.diary.ui.theme.YellowSoft

/**
 * 聊天气泡里的热量分享卡片：总热量 + 可展开的食物明细。
 * 好友对话与群聊共用同一份实现，避免两处样式走偏。
 */
@Composable
fun CalorieShareCard(
    messageId: Long,
    calories: Int?,
    items: List<SharedFood>,
    modifier: Modifier = Modifier
) {
    var expanded by remember(messageId) { mutableStateOf(false) }
    val expandable = items.isNotEmpty()
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "calorieArrow"
    )
    Column(
        modifier
            .then(if (expandable) Modifier.width(232.dp) else Modifier)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = expandable) { expanded = !expanded }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardWhite.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.meal_lunch),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.chat_calorie_share_title),
                    fontFamily = CuteFont, fontSize = 10.sp, color = InkLight
                )
                Spacer(Modifier.height(1.dp))
                calories?.let { value ->
                    Text(
                        stringResource(R.string.group_calories_value, value),
                        fontFamily = NumberFont, fontSize = 20.sp, color = OrangeDeep
                    )
                }
            }
            if (expandable) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ChevronDown(
                        Modifier
                            .size(12.dp)
                            .rotate(arrowRotation)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(
                            if (expanded) R.string.chat_calorie_collapse_hint
                            else R.string.chat_calorie_expand_hint
                        ),
                        fontFamily = CuteFont, fontSize = 9.sp, color = InkLight
                    )
                }
            }
        }
        if (expandable) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.chat_calorie_items_count, items.size),
                    fontFamily = CuteFont, fontSize = 9.sp, color = InkLight,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(CardWhite.copy(alpha = 0.6f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardWhite.copy(alpha = 0.55f))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    items.groupBy { it.meal }.toSortedMap().forEach { (meal, foods) ->
                        Row(
                            Modifier.padding(top = 4.dp, bottom = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(mealLabelRes(meal)),
                                fontFamily = CuteFont, fontSize = 10.sp, color = OrangeDeep,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(YellowSoft)
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(1.dp)
                                    .background(Hairline.copy(alpha = 0.7f))
                            )
                        }
                        foods.forEach { food -> SharedFoodRow(food) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedFoodRow(food: SharedFood) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (food.emoji.isNotBlank()) {
            Text(food.emoji, fontSize = 12.sp)
            Spacer(Modifier.width(5.dp))
        }
        Text(
            food.name,
            fontFamily = CuteFont, fontSize = 12.sp, color = Ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(R.string.chat_food_grams, food.grams),
            fontFamily = CuteFont, fontSize = 10.sp, color = InkLight
        )
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.group_calories_value, food.calories),
            fontFamily = NumberFont, fontSize = 11.sp, color = OrangeDeep
        )
    }
}

@Composable
private fun ChevronDown(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = 1.6.dp.toPx()
        drawLine(
            color = InkLight,
            start = Offset(size.width * 0.18f, size.height * 0.34f),
            end = Offset(size.width * 0.5f, size.height * 0.66f),
            strokeWidth = stroke, cap = StrokeCap.Round
        )
        drawLine(
            color = InkLight,
            start = Offset(size.width * 0.5f, size.height * 0.66f),
            end = Offset(size.width * 0.82f, size.height * 0.34f),
            strokeWidth = stroke, cap = StrokeCap.Round
        )
    }
}

private fun mealLabelRes(meal: Int): Int = when (meal) {
    0 -> R.string.meal_breakfast
    1 -> R.string.meal_lunch
    2 -> R.string.meal_dinner
    else -> R.string.meal_snack
}
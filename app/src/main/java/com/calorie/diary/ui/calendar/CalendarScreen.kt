package com.calorie.diary.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorie.diary.R
import com.calorie.diary.data.DateUtils
import com.calorie.diary.data.db.DateTotal
import com.calorie.diary.ui.components.FoodIcon
import com.calorie.diary.ui.components.SoftCard
import com.calorie.diary.ui.components.localizedFoodName
import com.calorie.diary.ui.home.appVm
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.NumberFont
import com.calorie.diary.ui.theme.Orange
import java.util.Calendar

@Composable
fun CalendarScreen() {
    val vm = appVm()
    val selectedDate by vm.selectedDate.collectAsState()
    val monthTotals by vm.monthTotals.collectAsState()
    val dayEntries by vm.dayEntries.collectAsState()
    val locale = LocalConfiguration.current.locales[0]

    val totalMap = monthTotals.associateBy { it.date }
    val cal = DateUtils.calendarOf(selectedDate)

    val firstDay = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    val startOffset = firstDay.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        SoftCard(
            modifier = Modifier.fillMaxWidth(),
            fill = CardWhite,
            contentPadding = PaddingValues(16.dp)
        ) {
            Column {
                // 月份切换
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MonthArrow("◀") {
                        val c = DateUtils.calendarOf(selectedDate)
                        c.add(Calendar.MONTH, -1)
                        vm.selectDate(DateUtils.format(c))
                    }
                    Text(
                        DateUtils.monthLabel(selectedDate, locale),
                        fontFamily = CuteFont, fontSize = 18.sp, color = Ink
                    )
                    MonthArrow("▶") {
                        val c = DateUtils.calendarOf(selectedDate)
                        c.add(Calendar.MONTH, 1)
                        vm.selectDate(DateUtils.format(c))
                    }
                }
                Spacer(Modifier.height(12.dp))

                // 星期表头
                val weekRes = listOf(
                    R.string.calendar_week_sun, R.string.calendar_week_mon,
                    R.string.calendar_week_tue, R.string.calendar_week_wed,
                    R.string.calendar_week_thu, R.string.calendar_week_fri,
                    R.string.calendar_week_sat
                )
                Row(Modifier.fillMaxWidth()) {
                    weekRes.forEach {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(it),
                                fontFamily = CuteFont, fontSize = 12.sp, color = InkLight
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                // 日期格子
                val rows = ((startOffset + daysInMonth) + 6) / 7
                for (row in 0 until rows) {
                    Row(Modifier.fillMaxWidth()) {
                        for (col in 0..6) {
                            val dayIndex = row * 7 + col - startOffset + 1
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (dayIndex in 1..daysInMonth) {
                                    val cellCal = (cal.clone() as Calendar).apply {
                                        set(Calendar.DAY_OF_MONTH, dayIndex)
                                    }
                                    val dateStr = DateUtils.format(cellCal)
                                    val isSelected = dateStr == selectedDate
                                    val isToday = dateStr == DateUtils.todayString()
                                    val total = totalMap[dateStr]?.total ?: 0
                                    Column(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                when {
                                                    isSelected -> Orange
                                                    isToday -> Color(0xFFFFE8C2)
                                                    else -> Color.Transparent
                                                }
                                            )
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) { vm.selectDate(dateStr) }
                                            .padding(4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            "$dayIndex",
                                            fontFamily = NumberFont,
                                            fontSize = 14.sp,
                                            color = if (isSelected) Color.White else Ink
                                        )
                                        if (total > 0) {
                                            Box(
                                                Modifier
                                                    .size(5.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isSelected) Color.White else Orange
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // 当日详情
        SoftCard(
            modifier = Modifier.fillMaxWidth(),
            fill = CardWhite,
            contentPadding = PaddingValues(16.dp)
        ) {
            Column {
                Text(
                    DateUtils.friendly(selectedDate, locale),
                    fontFamily = CuteFont, fontSize = 16.sp, color = Ink
                )
                Spacer(Modifier.height(10.dp))
                if (dayEntries.isEmpty()) {
                    Text(
                        stringResource(R.string.calendar_no_record),
                        fontFamily = CuteFont, fontSize = 13.sp, color = InkLight
                    )
                } else {
                    val dayTotal = dayEntries.sumOf { it.calories }
                    dayEntries.forEach { e ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FoodIcon(e.foodName, e.emoji, size = 40.dp)
                            Spacer(Modifier.size(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(localizedFoodName(e.foodName), fontFamily = CuteFont, fontSize = 14.sp, color = Ink)
                                Text(
                                    "${e.grams} ${stringResource(R.string.unit_gram)}",
                                    fontFamily = CuteFont, fontSize = 11.sp, color = InkLight
                                )
                            }
                            Text(
                                "${e.calories} ${stringResource(R.string.unit_kcal)}",
                                fontFamily = NumberFont, fontSize = 14.sp, color = Ink
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(
                            "${stringResource(R.string.home_meal_total)}: $dayTotal ${stringResource(R.string.unit_kcal)}",
                            fontFamily = NumberFont, fontSize = 15.sp, color = Orange
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun MonthArrow(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) { Text(text, fontSize = 16.sp, color = Ink) }
}

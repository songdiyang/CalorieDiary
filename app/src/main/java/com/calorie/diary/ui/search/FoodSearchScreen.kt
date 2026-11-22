package com.calorie.diary.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorie.diary.data.db.FoodEntity
import com.calorie.diary.data.seed.FoodSeed
import com.calorie.diary.ui.components.CuteButton
import com.calorie.diary.ui.components.CuteChip
import com.calorie.diary.ui.components.CuteNumberField
import com.calorie.diary.ui.components.CuteSearchBar
import com.calorie.diary.ui.components.FoodIcon
import com.calorie.diary.ui.components.SoftCard
import com.calorie.diary.ui.components.localizedFoodName
import com.calorie.diary.ui.home.appVm
import com.calorie.diary.R
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.Cream
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.NumberFont
import com.calorie.diary.ui.theme.Orange

private val mealTitleRes = listOf(
    R.string.meal_breakfast, R.string.meal_lunch,
    R.string.meal_dinner, R.string.meal_snack
)
private val categoryLabelRes = listOf(
    R.string.cat_staple, R.string.cat_meat, R.string.cat_veg,
    R.string.cat_fruit, R.string.cat_dairy, R.string.cat_snack, R.string.cat_drink
)

@Composable
fun FoodSearchScreen(
    mealType: Int,
    date: String,
    onBack: () -> Unit
) {
    val vm = appVm()
    val keyword by vm.searchKeyword.collectAsState()
    val allFoods by vm.foods.collectAsState()

    var selectedCategory by remember { mutableStateOf("") }
    var selectedFood by remember { mutableStateOf<FoodEntity?>(null) }
    var showCustom by remember { mutableStateOf(false) }

    val mealTitle = stringResource(mealTitleRes[mealType])
    val displayed = if (selectedCategory.isBlank()) allFoods
    else allFoods.filter { it.category == selectedCategory }

    Box(modifier = Modifier.fillMaxSize().background(Cream)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackArrow(onBack)
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(
                        stringResource(R.string.search_add_meal, mealTitle),
                        fontFamily = CuteFont, fontSize = 21.sp, color = Ink
                    )
                    Text(date, fontFamily = CuteFont, fontSize = 12.sp, color = InkLight)
                }
            }

            Spacer(Modifier.height(12.dp))
            CuteSearchBar(
                value = keyword,
                onValueChange = { vm.updateKeyword(it) },
                placeholder = stringResource(R.string.search_placeholder)
            )

            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    CuteChip(
                        stringResource(R.string.common_all),
                        selectedCategory.isBlank(),
                        { selectedCategory = "" }
                    )
                }
                items(FoodSeed.categories.indices.toList()) { i ->
                    val cat = FoodSeed.categories[i]
                    CuteChip(
                        stringResource(categoryLabelRes[i]),
                        selectedCategory == cat,
                        { selectedCategory = cat }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                item {
                    SoftCard(
                        modifier = Modifier.fillMaxWidth(),
                        fill = CardWhite,
                        contentPadding = PaddingValues(14.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showCustom = true },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(R.drawable.icon_pencil),
                                contentDescription = null,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                stringResource(R.string.search_custom_entry),
                                fontFamily = CuteFont, fontSize = 15.sp, color = Ink
                            )
                        }
                    }
                }
                items(displayed, key = { it.id }) { food ->
                    FoodRow(food) { selectedFood = food }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }

        selectedFood?.let { food ->
            WeightSheet(
                food = food,
                mealTitle = mealTitle,
                onDismiss = { selectedFood = null },
                onConfirm = { grams ->
                    vm.addEntry(mealType, food, grams, date)
                    selectedFood = null
                    onBack()
                }
            )
        }

        AnimatedVisibility(visible = showCustom) {
            CustomFoodSheet(
                onDismiss = { showCustom = false },
                onSave = { name, cal, emoji, cat ->
                    vm.addCustomFood(name, cal, emoji, cat)
                    showCustom = false
                }
            )
        }
    }
}

@Composable
private fun FoodRow(food: FoodEntity, onClick: () -> Unit) {
    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        fill = CardWhite,
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, onClick = onClick
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FoodIcon(name = food.name, fallbackEmoji = food.emoji, size = 54.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(localizedFoodName(food.name), fontFamily = CuteFont, fontSize = 16.sp, color = Ink)
                Text(
                    stringResource(R.string.search_per100, food.caloriesPer100g),
                    fontFamily = CuteFont, fontSize = 12.sp, color = InkLight
                )
            }
            if (food.isCustom) {
                Text(
                    stringResource(R.string.common_custom),
                    fontFamily = CuteFont, fontSize = 11.sp, color = Orange
                )
                Spacer(Modifier.width(8.dp))
            }
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(R.drawable.icon_plus),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun WeightSheet(
    food: FoodEntity,
    mealTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var grams by remember { mutableIntStateOf(100) }
    val calories = Math.round(food.caloriesPer100g * grams / 100.0).toInt()

    Box(
        modifier = Modifier
            .fillMaxSize()
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
                    FoodIcon(name = food.name, fallbackEmoji = food.emoji, size = 68.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(localizedFoodName(food.name), fontFamily = CuteFont, fontSize = 20.sp, color = Ink)
                        Text(
                            stringResource(R.string.search_each100, food.caloriesPer100g),
                            fontFamily = CuteFont, fontSize = 13.sp, color = InkLight
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.search_weight), fontFamily = CuteFont, fontSize = 15.sp, color = Ink)
                    Spacer(Modifier.weight(1f))
                    Text("$grams", fontFamily = NumberFont, fontSize = 26.sp, color = Orange)
                    Text(" ${stringResource(R.string.unit_gram)}", fontFamily = CuteFont, fontSize = 14.sp, color = InkLight)
                }
                Slider(
                    value = grams.toFloat(),
                    onValueChange = { grams = it.toInt() },
                    valueRange = 10f..500f,
                    colors = SliderDefaults.colors(
                        thumbColor = Orange,
                        activeTrackColor = Orange,
                        inactiveTrackColor = InkLight.copy(alpha = 0.3f)
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(50, 100, 150, 200).forEach { g ->
                        CuteChip("$g", grams == g, { grams = g }, selectedColor = Orange)
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
                            "$calories ${stringResource(R.string.unit_kcal)}",
                            fontFamily = NumberFont, fontSize = 24.sp, color = Ink
                        )
                    }
                    CuteButton(
                        stringResource(R.string.search_add_to, mealTitle),
                        { onConfirm(grams) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomFoodSheet(
    onDismiss: () -> Unit,
    onSave: (String, Int, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var cal by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🍴") }
    var category by remember { mutableStateOf(FoodSeed.CAT_SNACK) }
    val emojiOptions = listOf("🍴", "🍚", "🍗", "🥦", "🍎", "🥛", "🍪", "🥤", "🍞", "🥗")

    Box(
        modifier = Modifier
            .fillMaxSize()
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
                Text(stringResource(R.string.search_custom_title), fontFamily = CuteFont, fontSize = 20.sp, color = Ink)
                Spacer(Modifier.height(14.dp))
                CuteNumberField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.search_food_name),
                    keyboardType = KeyboardType.Text
                )
                Spacer(Modifier.height(10.dp))
                CuteNumberField(
                    value = cal,
                    onValueChange = { v -> cal = v.filter { c -> c.isDigit() } },
                    label = stringResource(R.string.search_cal_per100),
                    keyboardType = KeyboardType.Number
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.search_pick_icon), fontFamily = CuteFont, fontSize = 14.sp, color = Ink)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(emojiOptions) { e ->
                        CuteChip(e, emoji == e, { emoji = e })
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.search_category), fontFamily = CuteFont, fontSize = 14.sp, color = Ink)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(FoodSeed.categories.indices.toList()) { i ->
                        val c = FoodSeed.categories[i]
                        CuteChip(
                            stringResource(categoryLabelRes[i]),
                            category == c,
                            { category = c }
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                CuteButton(
                    stringResource(R.string.common_save),
                    onClick = {
                        if (name.isNotBlank() && cal.isNotBlank()) {
                            onSave(name.trim(), cal.toInt(), emoji, category)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = name.isNotBlank() && cal.isNotBlank()
                )
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

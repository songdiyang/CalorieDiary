package com.calorie.diary

import com.calorie.diary.data.parser.ParseIntent
import com.calorie.diary.data.parser.TextFoodParser
import com.calorie.diary.data.seed.FoodSeed
import org.junit.Assert
import org.junit.Test

class TextFoodParserTest {

    private val foods = FoodSeed.foods

    @Test
    fun userExample_cornAndSoymilk() {
        val r = TextFoodParser.parse("吃了一根玉米，一杯豆浆", foods)
        Assert.assertEquals(ParseIntent.ADD, r.intent)
        Assert.assertEquals(2, r.addItems.size)
        Assert.assertEquals(0, r.unmatched.size)

        val corn = r.addItems[0]
        Assert.assertEquals("玉米", corn.food.name)
        Assert.assertEquals(200, corn.totalGrams)
        Assert.assertTrue(corn.estimated)
        Assert.assertEquals(224, corn.calories)

        val soy = r.addItems[1]
        Assert.assertEquals("无糖豆浆", soy.food.name)
        Assert.assertEquals(250, soy.totalGrams)
        Assert.assertEquals(78, soy.calories)

        Assert.assertEquals(302, r.totalCalories)
    }

    @Test
    fun explicitGrams_markedExact() {
        val r = TextFoodParser.parse("200克米饭", foods)
        val rice = r.addItems[0]
        Assert.assertEquals("米饭", rice.food.name)
        Assert.assertEquals(200, rice.totalGrams)
        Assert.assertFalse(rice.estimated)
        Assert.assertEquals(232, rice.calories)
    }

    @Test
    fun multipleWithChineseNumber() {
        val r = TextFoodParser.parse("两个鸡蛋", foods)
        val egg = r.addItems[0]
        Assert.assertEquals("鸡蛋", egg.food.name)
        Assert.assertEquals(100, egg.totalGrams)
        Assert.assertEquals(144, egg.calories)
    }

    @Test
    fun drinkMl() {
        val r = TextFoodParser.parse("喝了300毫升牛奶", foods)
        val milk = r.addItems[0]
        Assert.assertEquals("牛奶", milk.food.name)
        Assert.assertEquals(300, milk.totalGrams)
        Assert.assertFalse(milk.estimated)
        Assert.assertEquals(162, milk.calories)
    }

    @Test
    fun unmatchedReported() {
        val r = TextFoodParser.parse("一碗燕窝", foods)
        Assert.assertEquals(0, r.addItems.size)
        Assert.assertEquals(1, r.unmatched.size)
    }

    @Test
    fun blankInput() {
        val r = TextFoodParser.parse("   ", foods)
        Assert.assertTrue(r.isEmpty)
    }

    @Test
    fun severalItems() {
        val r = TextFoodParser.parse("一个苹果 一根香蕉 一块蛋糕", foods)
        Assert.assertEquals(3, r.addItems.size)
        Assert.assertEquals(0, r.unmatched.size)
    }

    @Test
    fun deleteIntent_collectsNames() {
        val r = TextFoodParser.parse("去掉米饭", foods)
        Assert.assertEquals(ParseIntent.DELETE, r.intent)
        Assert.assertEquals(1, r.removeNames.size)
        Assert.assertEquals("米饭", r.removeNames[0])
    }

    @Test
    fun dateAndMeal_parsed() {
        val r = TextFoodParser.parse("昨天午饭吃了一碗牛肉面", foods)
        Assert.assertEquals(-1, r.dateOffset)
        Assert.assertEquals(Integer.valueOf(1), r.mealFromText)
        Assert.assertEquals(1, r.addItems.size)
        Assert.assertEquals("面条", r.addItems[0].food.name)
        Assert.assertEquals(0, r.unmatched.size)
    }

    @Test
    fun halfPortion() {
        val r = TextFoodParser.parse("半碗米饭", foods)
        val rice = r.addItems[0]
        Assert.assertEquals(75, rice.totalGrams)
        Assert.assertEquals(87, rice.calories)
    }

    @Test
    fun largeSize() {
        val r = TextFoodParser.parse("大杯奶茶", foods)
        val tea = r.addItems[0]
        Assert.assertEquals(520, tea.totalGrams)
        Assert.assertEquals(494, tea.calories)
    }

    @Test
    fun directCalories() {
        val r = TextFoodParser.parse("300千卡", foods)
        Assert.assertEquals(0, r.addItems.size)
        Assert.assertEquals(Integer.valueOf(300), r.directCalories)
    }

    @Test
    fun englishInput() {
        val r = TextFoodParser.parse("one corn", foods)
        Assert.assertEquals(1, r.addItems.size)
        Assert.assertEquals("玉米", r.addItems[0].food.name)
    }

    @Test
    fun fuzzyAmount() {
        val r = TextFoodParser.parse("一点坚果", foods)
        val nuts = r.addItems[0]
        Assert.assertEquals(10, nuts.totalGrams)
        Assert.assertEquals(61, nuts.calories)
    }
}

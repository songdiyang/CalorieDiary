package com.calorie.diary

import com.calorie.diary.data.vision.RecognizedFood
import com.calorie.diary.data.vision.caloriesForGrams
import com.calorie.diary.data.vision.scaledCalories
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 验证与 AppRepository.addEntry 相同的热量计算公式。
 */
class CalorieCalcTest {

    private fun calories(calPer100: Int, grams: Int): Int =
        Math.round(calPer100 * grams / 100.0).toInt()

    @Test
    fun testStandardPortions() {
        // 米饭 116/100g，200g 应为 232
        assertEquals(232, calories(116, 200))
        // 鸡胸肉 133/100g，150g 应为 200（199.5 四舍五入）
        assertEquals(200, calories(133, 150))
        // 鸡蛋 144/100g，50g 应为 72
        assertEquals(72, calories(144, 50))
        // 薯片 536/100g，30g 应为 161
        assertEquals(161, calories(536, 30))
        // 矿泉水 0 热量
        assertEquals(0, calories(0, 500))
    }

    @Test
    fun testRounding() {
        // 131/100g * 100g = 131
        assertEquals(131, calories(131, 100))
        // 47/100g * 150g = 70.5 -> 71
        assertEquals(71, calories(47, 150))
    }

    @Test
    fun testRecognizedFoodCalorieScaling() {
        val rice = RecognizedFood("米饭", 200, 232)
        assertEquals(116, rice.caloriesForGrams(100))
        assertEquals(58, rice.caloriesForGrams(50))
        assertEquals(290, rice.caloriesForGrams(250))
        assertEquals(100, RecognizedFood("测试食物", 200, 199).caloriesForGrams(100))
    }

    @Test
    fun testRecognizedFoodCalorieScalingEdgeCases() {
        assertEquals(0, RecognizedFood("米饭", 200, 232).caloriesForGrams(0))
        assertEquals(0, RecognizedFood("矿泉水", 500, 0).caloriesForGrams(250))
        assertEquals(80, RecognizedFood("未知份量", 0, 80).caloriesForGrams(100))
    }

    @Test
    fun testScaledCaloriesForEntryGramsAdjustment() {
        // 首页“调整克数”按记录原每克热量等比换算：100克139千卡 → 200克278千卡
        assertEquals(278, scaledCalories(100, 139, 200))
        // 69.5 四舍五入为 70
        assertEquals(70, scaledCalories(100, 139, 50))
        // 原克数异常（0）时保持原热量，不做除法
        assertEquals(139, scaledCalories(0, 139, 100))
    }
}

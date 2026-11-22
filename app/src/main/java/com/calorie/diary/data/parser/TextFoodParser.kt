package com.calorie.diary.data.parser

import com.calorie.diary.data.db.FoodEntity

enum class ParseIntent { ADD, DELETE, QUERY, STATS, UNKNOWN }

/**
 * 一条解析出的待记录食物。
 * @param estimated true=按量词/标准份估算；false=用户给了明确克数或毫升
 */
data class ParsedFood(
    val food: FoodEntity,
    val quantity: Int,
    val gramsPerPortion: Int,
    val totalGrams: Int,
    val calories: Int,
    val estimated: Boolean
)

data class TextParseResult(
    val intent: ParseIntent = ParseIntent.ADD,
    val dateOffset: Int = 0,
    val mealFromText: Int? = null,
    val addItems: List<ParsedFood> = emptyList(),
    val removeNames: List<String> = emptyList(),
    val directCalories: Int? = null,
    val unmatched: List<String> = emptyList()
) {
    val totalCalories: Int get() = addItems.sumOf { it.calories } + (directCalories ?: 0)
    val isEmpty: Boolean
        get() = addItems.isEmpty() && unmatched.isEmpty() &&
            removeNames.isEmpty() && directCalories == null
}

/**
 * 本地自然语言饮食记录解析器（无网络、无 Android 依赖，可直接单测）。
 */
object TextFoodParser {

    // —— 口语/英文别名 → 库内食物名 ——
    private val aliases: Map<String, String> = mapOf(
        // 中文口语
        "豆浆" to "无糖豆浆", "白饭" to "米饭", "饭" to "米饭",
        "美式咖啡" to "黑咖啡", "美式" to "黑咖啡", "咖啡" to "黑咖啡",
        "白开水" to "矿泉水", "水" to "矿泉水", "坚果" to "混合坚果",
        "鸡胸" to "鸡胸肉", "面包" to "全麦面包", "果汁" to "橙汁",
        "薯条" to "薯条", "薯饼" to "薯条", "意面" to "意大利面",
        "地瓜" to "红薯", "蕃茄" to "番茄", "马铃薯" to "土豆", "洋芋" to "土豆",
        "起司" to "奶酪", "乳酪" to "奶酪", "芝士" to "奶酪",
        "西红柿" to "番茄",
        "牛肉面" to "面条", "拉面" to "面条", "炒面" to "面条",
        "汤面" to "面条", "米粉" to "米饭", "烤鸡" to "鸡腿",
        "鸡腿堡" to "汉堡",
        // 英文
        "corn" to "玉米", "rice" to "米饭", "milk" to "牛奶", "egg" to "鸡蛋",
        "eggs" to "鸡蛋", "coffee" to "黑咖啡", "bread" to "全麦面包",
        "chicken" to "鸡胸肉", "apple" to "苹果", "banana" to "香蕉",
        "salad" to "蔬菜沙拉", "water" to "矿泉水", "tea" to "柠檬红茶",
        "noodles" to "面条", "noodle" to "面条", "yogurt" to "酸奶",
        "soymilk" to "无糖豆浆", "fish" to "鲈鱼", "beef" to "牛肉",
        "pizza" to "披萨", "burger" to "汉堡", "hamburger" to "汉堡",
        "cola" to "可乐", "coke" to "可乐", "juice" to "橙汁"
    )

    private val cnDigits = mapOf(
        '零' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
        '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9
    )
    private val enDigits = mapOf(
        "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3,
        "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8,
        "nine" to 9, "ten" to 10
    )

    private val measureDefault: Map<String, Int> = mapOf(
        "杯" to 250, "碗" to 250, "盘" to 200, "份" to 150, "盒" to 200,
        "瓶" to 500, "袋" to 100, "个" to 80, "只" to 80, "枚" to 50,
        "根" to 120, "条" to 80, "片" to 30, "块" to 40, "勺" to 15,
        "口" to 20, "把" to 80, "颗" to 10, "粒" to 10, "顿" to 400,
        "cup" to 250, "bowl" to 250, "plate" to 200, "bottle" to 500,
        "piece" to 40, "can" to 330, "slice" to 30
    )

    private val gramUnits = setOf("克", "g", "gram", "grams")
    private val mlUnits = setOf("毫升", "ml", "milliliter", "milliliters")
    private val calorieUnits = setOf("千卡", "大卡", "kcal", "calorie", "calories")
    private val smallCalUnit = setOf("卡")

    private val dateWords: Map<String, Int> = mapOf(
        "大前天" to -3, "大前日" to -3, "前天" to -2, "前日" to -2,
        "昨天" to -1, "昨日" to -1, "今天" to 0, "今日" to 0,
        "明天" to 1, "明日" to 1
    )

    private val mealWords: Map<String, Int> = mapOf(
        "早餐" to 0, "早饭" to 0, "早点" to 0, "早上" to 0, "早餐" to 0,
        "午餐" to 1, "午饭" to 1, "中饭" to 1, "中午" to 1,
        "晚餐" to 2, "晚饭" to 2, "晚上" to 2,
        "加餐" to 3, "下午茶" to 3, "零食" to 3, "夜宵" to 3, "宵夜" to 3
    )

    private val deleteWords = listOf("去掉", "删除", "删掉", "移除", "不要", "减去", "减掉", "remove", "delete")
    private val queryWords = listOf("还能吃", "还可以吃", "还能喝", "剩多少", "查询", "多少", "how much", "left")
    private val statsWords = listOf("统计", "汇总", "一共吃", "总共", "total", "summary")
    private val fuzzyWords = listOf("一点", "一些", "少许", "少量", "一点点", "点儿", "a little", "some")

    private data class Portion(val default: Int, val byMeasure: Map<String, Int>)

    private val portionTable: Map<String, Portion> = mapOf(
        "玉米" to Portion(200, mapOf("根" to 200, "段" to 100, "杯" to 160)),
        "鸡蛋" to Portion(50, mapOf("个" to 50, "只" to 50, "枚" to 50)),
        "煎蛋" to Portion(60, mapOf("个" to 60)),
        "苹果" to Portion(200, mapOf("个" to 200, "只" to 200)),
        "香蕉" to Portion(120, mapOf("根" to 120, "只" to 120)),
        "橙子" to Portion(180, mapOf("个" to 180)),
        "梨" to Portion(180, mapOf("个" to 180)),
        "桃子" to Portion(150, mapOf("个" to 150)),
        "猕猴桃" to Portion(80, mapOf("个" to 80)),
        "米饭" to Portion(150, mapOf("碗" to 150, "盒" to 250, "份" to 150, "杯" to 150)),
        "白粥" to Portion(300, mapOf("碗" to 300, "杯" to 250)),
        "小米粥" to Portion(300, mapOf("碗" to 300)),
        "面条" to Portion(250, mapOf("碗" to 250, "份" to 250)),
        "馒头" to Portion(80, mapOf("个" to 80)),
        "豆腐包子" to Portion(100, mapOf("个" to 100)),
        "香菇油菜包" to Portion(100, mapOf("个" to 100)),
        "饺子" to Portion(20, mapOf("个" to 20, "只" to 20)),
        "馄饨" to Portion(15, mapOf("个" to 15, "只" to 15)),
        "全麦面包" to Portion(35, mapOf("片" to 35, "个" to 80)),
        "吐司" to Portion(30, mapOf("片" to 30)),
        "红薯" to Portion(150, mapOf("个" to 150, "根" to 150)),
        "土豆" to Portion(150, mapOf("个" to 150)),
        "鸡腿" to Portion(120, mapOf("个" to 120, "只" to 120)),
        "牛排" to Portion(180, mapOf("份" to 180, "块" to 180)),
        "三文鱼" to Portion(120, mapOf("份" to 120, "块" to 100)),
        "虾仁" to Portion(80, mapOf("份" to 80, "把" to 80)),
        "豆腐" to Portion(100, mapOf("块" to 100, "份" to 100)),
        "香肠" to Portion(40, mapOf("根" to 40)),
        "汉堡" to Portion(220, mapOf("个" to 220)),
        "披萨" to Portion(120, mapOf("块" to 100, "份" to 250)),
        "薯片" to Portion(40, mapOf("袋" to 40, "份" to 40)),
        "薯条" to Portion(100, mapOf("份" to 100, "盒" to 100)),
        "混合坚果" to Portion(25, mapOf("把" to 25, "袋" to 30, "份" to 30)),
        "牛奶" to Portion(250, mapOf("杯" to 250, "盒" to 250, "瓶" to 250)),
        "无糖豆浆" to Portion(250, mapOf("杯" to 250, "碗" to 250, "瓶" to 300)),
        "豆奶" to Portion(250, mapOf("杯" to 250, "盒" to 250)),
        "酸奶" to Portion(130, mapOf("杯" to 130, "盒" to 130)),
        "希腊酸奶" to Portion(150, mapOf("杯" to 150)),
        "拿铁" to Portion(350, mapOf("杯" to 350)),
        "奶茶" to Portion(400, mapOf("杯" to 400)),
        "可乐" to Portion(330, mapOf("杯" to 330, "瓶" to 500, "can" to 330, "罐" to 330)),
        "橙汁" to Portion(250, mapOf("杯" to 250, "瓶" to 300)),
        "啤酒" to Portion(330, mapOf("杯" to 330, "瓶" to 500, "罐" to 330)),
        "黑咖啡" to Portion(250, mapOf("杯" to 250)),
        "柠檬红茶" to Portion(350, mapOf("杯" to 350)),
        "西兰花" to Portion(150, mapOf("份" to 150, "颗" to 150)),
        "蔬菜沙拉" to Portion(200, mapOf("份" to 200, "碗" to 200)),
        "黄瓜" to Portion(130, mapOf("根" to 130)),
        "胡萝卜" to Portion(100, mapOf("根" to 100)),
        "西瓜" to Portion(300, mapOf("块" to 300, "份" to 300)),
        "葡萄" to Portion(100, mapOf("串" to 100, "份" to 100, "颗" to 8)),
        "草莓" to Portion(15, mapOf("颗" to 15, "个" to 15)),
        "蓝莓" to Portion(80, mapOf("盒" to 80, "份" to 80)),
        "牛油果" to Portion(150, mapOf("个" to 150)),
        "奶酪" to Portion(20, mapOf("片" to 20, "块" to 20)),
        "冰淇淋" to Portion(80, mapOf("个" to 80, "份" to 100, "球" to 80)),
        "蛋糕" to Portion(100, mapOf("块" to 100, "份" to 100)),
        "饼干" to Portion(10, mapOf("块" to 10, "片" to 10)),
        "巧克力" to Portion(40, mapOf("块" to 40)),
        "爆米花" to Portion(60, mapOf("份" to 60, "桶" to 100))
    )

    fun parse(input: String, foods: List<FoodEntity>): TextParseResult {
        val normalized = normalize(input)
        if (normalized.isBlank()) return TextParseResult()

        val intent = detectIntent(normalized)
        val dateOffset = detectDate(normalized)
        val mealFromText = detectMeal(normalized)

        val segments = splitSegments(normalized)
        val addItems = mutableListOf<ParsedFood>()
        val removeNames = mutableListOf<String>()
        val unmatched = mutableListOf<String>()
        var directCalories: Int? = null

        for (seg in segments) {
            if (seg.isBlank() || isModifierOnly(seg)) continue

            // 直接热量：数字 + 千卡/大卡/卡（无食物名）
            val cal = extractDirectCalories(seg)
            if (cal != null && matchFood(seg, foods) == null) {
                directCalories = cal
                continue
            }

            val food = matchFood(seg, foods)
            if (food == null) {
                // 删除意图下，把无法匹配食物库但含明确食物词的片段也收集
                if (intent == ParseIntent.DELETE) removeNames.add(seg)
                else unmatched.add(seg)
                continue
            }

            if (intent == ParseIntent.DELETE) {
                removeNames.add(food.name)
            } else {
                addItems.add(buildItem(seg, food))
            }
        }

        return TextParseResult(
            intent = intent,
            dateOffset = dateOffset,
            mealFromText = mealFromText,
            addItems = addItems,
            removeNames = removeNames,
            directCalories = directCalories,
            unmatched = unmatched
        )
    }

    // —— 预处理 ——
    private fun normalize(s: String): String {
        var t = s.trim().lowercase()
        t = t.replace('　', ' ')
        listOf("，", "、", "；", "。", "！", "!", "：", ":", "?", "？").forEach {
            t = t.replace(it, ",")
        }
        return t.trim()
    }

    private fun detectIntent(s: String): ParseIntent = when {
        deleteWords.any { s.contains(it) } -> ParseIntent.DELETE
        statsWords.any { s.contains(it) } -> ParseIntent.STATS
        queryWords.any { s.contains(it) } -> ParseIntent.QUERY
        else -> ParseIntent.ADD
    }

    private fun detectDate(s: String): Int {
        // 多字优先（大前天 > 前天）
        dateWords.entries
            .sortedByDescending { it.key.length }
            .firstOrNull { s.contains(it.key) }
            ?.let { return it.value }
        return 0
    }

    private fun detectMeal(s: String): Int? {
        mealWords.entries
            .sortedByDescending { it.key.length }
            .firstOrNull { s.contains(it.key) }
            ?.let { return it.value }
        return null
    }

    private fun splitSegments(s: String): List<String> {
        var t = s
        listOf("还有", "然后", "以及", "和", "跟", "再", "加", "吃了", "喝了", "来", "记录").forEach {
            t = t.replace(it, ",")
        }
        return t.split(",", " ", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun isModifierOnly(seg: String): Boolean {
        var t = seg
        dateWords.keys.forEach { t = t.replace(it, "") }
        mealWords.keys.forEach { t = t.replace(it, "") }
        enDigits.keys.forEach { t = t.replace(it, "") }
        listOf("吃", "喝", "了", "的", "在", "记录", "然后", "再", "一", "顿", "of").forEach {
            t = t.replace(it, "")
        }
        return t.isBlank()
    }

    private fun matchFood(seg: String, foods: List<FoodEntity>): FoodEntity? {
        data class Hit(val food: FoodEntity, val keyLen: Int)
        val hits = mutableListOf<Hit>()

        // 库内名字直接命中
        foods.filter { seg.contains(it.name) }.forEach { hits.add(Hit(it, it.name.length)) }

        // 别名命中（取最长别名键，如“牛肉面”优先于“牛肉”）
        aliases.entries
            .filter { seg.contains(it.key) }
            .maxByOrNull { it.key.length }
            ?.let { a ->
                foods.firstOrNull { it.name == a.value }?.let { f -> hits.add(Hit(f, a.key.length)) }
            }

        if (hits.isEmpty()) return null
        return hits.maxByOrNull { it.keyLen }!!.food
    }

    private fun buildItem(seg: String, food: FoodEntity): ParsedFood {
        // 明确重量
        val explicitGrams = extractExplicit(seg, gramUnits)
        val explicitMl = extractExplicit(seg, mlUnits)
        if (explicitGrams != null) {
            return makeItem(food, 1, explicitGrams, estimated = false)
        }
        if (explicitMl != null) {
            return makeItem(food, 1, explicitMl, estimated = false)
        }

        val measure = findMeasure(seg)
        val portion = portionTable[food.name]
        val basePortion = when {
            portion != null && measure != null -> portion.byMeasure[measure] ?: portion.default
            portion != null -> portion.default
            measure != null -> measureDefault[measure] ?: 100
            else -> 100
        }

        // 数量与大小系数
        val countMult = extractCountMultiplier(seg)
        val sizeMult = extractSizeMultiplier(seg)
        val totalGrams = Math.round(basePortion * countMult * sizeMult)
        return makeItem(food, 1, totalGrams, estimated = true)
    }

    private fun makeItem(food: FoodEntity, quantity: Int, totalGrams: Int, estimated: Boolean): ParsedFood {
        val grams = totalGrams.coerceAtLeast(1)
        val calories = Math.round(food.caloriesPer100g * grams / 100.0).toInt()
        return ParsedFood(
            food = food,
            quantity = quantity,
            gramsPerPortion = grams,
            totalGrams = grams,
            calories = calories,
            estimated = estimated
        )
    }

    private fun extractCountMultiplier(seg: String): Float {
        if (seg.contains("半") && numberInSeg(seg) == null) return 0.5f
        if (fuzzyWords.any { seg.contains(it) }) return 0.4f
        val n = numberInSeg(seg)
        return n?.toFloat() ?: 1f
    }

    private fun extractSizeMultiplier(seg: String): Float = when {
        seg.contains("大杯") || seg.contains("大碗") || seg.contains("大份") || seg.contains("large") -> 1.3f
        seg.contains("小杯") || seg.contains("小碗") || seg.contains("小份") || seg.contains("small") -> 0.7f
        else -> 1f
    }

    private fun numberInSeg(seg: String): Int? {
        Regex("\\d+").find(seg)?.let { return it.value.toInt() }
        // 英文数字
        seg.split(" ", "-").firstOrNull { enDigits.containsKey(it) }?.let { return enDigits[it] }
        val numChars = seg.filter { cnDigits.containsKey(it) || it == '十' }
        if (numChars.isEmpty()) return null
        if ('十' in numChars) {
            val idx = numChars.indexOf('十')
            val tens = if (idx == 0) 1 else cnDigits[numChars[idx - 1]] ?: 1
            val ones = numChars.getOrNull(idx + 1)?.let { cnDigits[it] } ?: 0
            return tens * 10 + ones
        }
        return cnDigits[numChars.first()]
    }

    private val allMeasureChars: Set<String>
        get() = (measureDefault.keys + setOf("段", "串", "罐", "球", "桶", "顿")).toSet()

    private fun findMeasure(seg: String): String? {
        for (i in seg.indices) {
            val ch = seg[i].toString()
            if (ch in allMeasureChars) {
                val prev = if (i > 0) seg[i - 1] else null
                val prevIsNum = prev != null && (cnDigits.containsKey(prev) || prev.isDigit())
                if (prevIsNum) return ch
            }
        }
        return seg.firstOrNull { it.toString() in allMeasureChars }?.toString()
    }

    private fun extractExplicit(seg: String, units: Set<String>): Int? {
        for (u in units) {
            val idx = seg.indexOf(u)
            if (idx >= 0) {
                val before = seg.substring(0, idx)
                Regex("(\\d+)$").find(before)?.let { return it.value.toInt() }
                val cn = numberInSeg(before)
                if (cn != null && before.any { cnDigits.containsKey(it) || it == '十' }) return cn
            }
        }
        return null
    }

    private fun extractDirectCalories(seg: String): Int? {
        calorieUnits.forEach { u ->
            val idx = seg.indexOf(u)
            if (idx >= 0) {
                val before = seg.substring(0, idx)
                Regex("(\\d+)$").find(before)?.let { return it.value.toInt() }
                numberInSeg(before)?.let { return it }
            }
        }
        smallCalUnit.forEach { u ->
            // 仅“数字+卡”，避免误匹配“千卡”
            Regex("(\\d+)$u(?!路)").find(seg)?.let { m ->
                return m.groupValues[1].toInt()
            }
        }
        return null
    }
}

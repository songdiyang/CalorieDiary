package com.calorie.diary.data.seed

import com.calorie.diary.data.db.FoodEntity

object FoodSeed {

    const val CAT_STAPLE = "主食"
    const val CAT_MEAT = "肉蛋"
    const val CAT_VEG = "蔬菜"
    const val CAT_FRUIT = "水果"
    const val CAT_DAIRY = "奶类"
    const val CAT_SNACK = "零食"
    const val CAT_DRINK = "饮品"

    val categories = listOf(CAT_STAPLE, CAT_MEAT, CAT_VEG, CAT_FRUIT, CAT_DAIRY, CAT_SNACK, CAT_DRINK)

    // 每 100 克可食部热量（千卡），数值为常见食物成分表参考值
    val foods: List<FoodEntity> = listOf(
        // 主食
        FoodEntity(name = "米饭", caloriesPer100g = 116, category = CAT_STAPLE, emoji = "🍚"),
        FoodEntity(name = "五谷杂粮饭", caloriesPer100g = 120, category = CAT_STAPLE, emoji = "🍱"),
        FoodEntity(name = "白粥", caloriesPer100g = 46, category = CAT_STAPLE, emoji = "🥣"),
        FoodEntity(name = "小米粥", caloriesPer100g = 46, category = CAT_STAPLE, emoji = "🍲"),
        FoodEntity(name = "面条", caloriesPer100g = 110, category = CAT_STAPLE, emoji = "🍜"),
        FoodEntity(name = "方便面", caloriesPer100g = 473, category = CAT_STAPLE, emoji = "🍥"),
        FoodEntity(name = "意大利面", caloriesPer100g = 158, category = CAT_STAPLE, emoji = "🍝"),
        FoodEntity(name = "馒头", caloriesPer100g = 223, category = CAT_STAPLE, emoji = "🥟"),
        FoodEntity(name = "豆腐包子", caloriesPer100g = 205, category = CAT_STAPLE, emoji = "🥟"),
        FoodEntity(name = "香菇油菜包", caloriesPer100g = 198, category = CAT_STAPLE, emoji = "🥟"),
        FoodEntity(name = "饺子", caloriesPer100g = 240, category = CAT_STAPLE, emoji = "🥟"),
        FoodEntity(name = "馄饨", caloriesPer100g = 220, category = CAT_STAPLE, emoji = "🥣"),
        FoodEntity(name = "全麦面包", caloriesPer100g = 258, category = CAT_STAPLE, emoji = "🍞"),
        FoodEntity(name = "吐司", caloriesPer100g = 280, category = CAT_STAPLE, emoji = "🍞"),
        FoodEntity(name = "燕麦片", caloriesPer100g = 377, category = CAT_STAPLE, emoji = "🥣"),
        FoodEntity(name = "玉米", caloriesPer100g = 112, category = CAT_STAPLE, emoji = "🌽"),
        FoodEntity(name = "红薯", caloriesPer100g = 86, category = CAT_STAPLE, emoji = "🍠"),
        FoodEntity(name = "紫薯", caloriesPer100g = 106, category = CAT_STAPLE, emoji = "🍠"),
        FoodEntity(name = "土豆", caloriesPer100g = 77, category = CAT_STAPLE, emoji = "🥔"),
        FoodEntity(name = "炒饭", caloriesPer100g = 186, category = CAT_STAPLE, emoji = "🍳"),

        // 肉蛋
        FoodEntity(name = "鸡胸肉", caloriesPer100g = 133, category = CAT_MEAT, emoji = "🍗"),
        FoodEntity(name = "青椒炒鸡丁", caloriesPer100g = 151, category = CAT_MEAT, emoji = "🍗"),
        FoodEntity(name = "鸡腿", caloriesPer100g = 181, category = CAT_MEAT, emoji = "🍗"),
        FoodEntity(name = "牛肉", caloriesPer100g = 125, category = CAT_MEAT, emoji = "🥩"),
        FoodEntity(name = "牛排", caloriesPer100g = 220, category = CAT_MEAT, emoji = "🥩"),
        FoodEntity(name = "猪里脊", caloriesPer100g = 155, category = CAT_MEAT, emoji = "🥓"),
        FoodEntity(name = "鸭肉", caloriesPer100g = 240, category = CAT_MEAT, emoji = "🍖"),
        FoodEntity(name = "鸡蛋", caloriesPer100g = 144, category = CAT_MEAT, emoji = "🥚"),
        FoodEntity(name = "煎蛋", caloriesPer100g = 196, category = CAT_MEAT, emoji = "🍳"),
        FoodEntity(name = "三文鱼", caloriesPer100g = 139, category = CAT_MEAT, emoji = "🐟"),
        FoodEntity(name = "口蘑煎三文鱼", caloriesPer100g = 156, category = CAT_MEAT, emoji = "🍣"),
        FoodEntity(name = "金枪鱼", caloriesPer100g = 108, category = CAT_MEAT, emoji = "🐟"),
        FoodEntity(name = "鲈鱼", caloriesPer100g = 105, category = CAT_MEAT, emoji = "🐟"),
        FoodEntity(name = "虾仁", caloriesPer100g = 87, category = CAT_MEAT, emoji = "🦐"),
        FoodEntity(name = "虾仁炖豆腐", caloriesPer100g = 96, category = CAT_MEAT, emoji = "🍲"),
        FoodEntity(name = "豆腐", caloriesPer100g = 82, category = CAT_MEAT, emoji = "🍢"),
        FoodEntity(name = "香肠", caloriesPer100g = 508, category = CAT_MEAT, emoji = "🌭"),

        // 蔬菜
        FoodEntity(name = "西兰花", caloriesPer100g = 36, category = CAT_VEG, emoji = "🥦"),
        FoodEntity(name = "香菇", caloriesPer100g = 26, category = CAT_VEG, emoji = "🍄"),
        FoodEntity(name = "口蘑", caloriesPer100g = 28, category = CAT_VEG, emoji = "🍄"),
        FoodEntity(name = "油菜", caloriesPer100g = 20, category = CAT_VEG, emoji = "🥬"),
        FoodEntity(name = "香菇炒油菜", caloriesPer100g = 48, category = CAT_VEG, emoji = "🥬"),
        FoodEntity(name = "青椒", caloriesPer100g = 22, category = CAT_VEG, emoji = "🫑"),
        FoodEntity(name = "生菜", caloriesPer100g = 15, category = CAT_VEG, emoji = "🥗"),
        FoodEntity(name = "黄瓜", caloriesPer100g = 16, category = CAT_VEG, emoji = "🥒"),
        FoodEntity(name = "番茄", caloriesPer100g = 18, category = CAT_VEG, emoji = "🍅"),
        FoodEntity(name = "菠菜", caloriesPer100g = 28, category = CAT_VEG, emoji = "🥬"),
        FoodEntity(name = "胡萝卜", caloriesPer100g = 39, category = CAT_VEG, emoji = "🥕"),
        FoodEntity(name = "冬瓜", caloriesPer100g = 12, category = CAT_VEG, emoji = "🍈"),
        FoodEntity(name = "芹菜", caloriesPer100g = 17, category = CAT_VEG, emoji = "🥬"),
        FoodEntity(name = "白菜", caloriesPer100g = 17, category = CAT_VEG, emoji = "🥬"),
        FoodEntity(name = "茄子", caloriesPer100g = 23, category = CAT_VEG, emoji = "🍆"),
        FoodEntity(name = "蔬菜沙拉", caloriesPer100g = 65, category = CAT_VEG, emoji = "🥗"),

        // 水果
        FoodEntity(name = "苹果", caloriesPer100g = 53, category = CAT_FRUIT, emoji = "🍎"),
        FoodEntity(name = "香蕉", caloriesPer100g = 93, category = CAT_FRUIT, emoji = "🍌"),
        FoodEntity(name = "橙子", caloriesPer100g = 48, category = CAT_FRUIT, emoji = "🍊"),
        FoodEntity(name = "梨", caloriesPer100g = 51, category = CAT_FRUIT, emoji = "🍐"),
        FoodEntity(name = "草莓", caloriesPer100g = 32, category = CAT_FRUIT, emoji = "🍓"),
        FoodEntity(name = "蓝莓", caloriesPer100g = 57, category = CAT_FRUIT, emoji = "🫐"),
        FoodEntity(name = "西瓜", caloriesPer100g = 31, category = CAT_FRUIT, emoji = "🍉"),
        FoodEntity(name = "葡萄", caloriesPer100g = 45, category = CAT_FRUIT, emoji = "🍇"),
        FoodEntity(name = "桃子", caloriesPer100g = 42, category = CAT_FRUIT, emoji = "🍑"),
        FoodEntity(name = "猕猴桃", caloriesPer100g = 61, category = CAT_FRUIT, emoji = "🥝"),
        FoodEntity(name = "火龙果", caloriesPer100g = 55, category = CAT_FRUIT, emoji = "🐉"),
        FoodEntity(name = "牛油果", caloriesPer100g = 171, category = CAT_FRUIT, emoji = "🥑"),

        // 奶类
        FoodEntity(name = "牛奶", caloriesPer100g = 54, category = CAT_DAIRY, emoji = "🥛"),
        FoodEntity(name = "无糖豆浆", caloriesPer100g = 31, category = CAT_DAIRY, emoji = "🥛"),
        FoodEntity(name = "酸奶", caloriesPer100g = 72, category = CAT_DAIRY, emoji = "🍶"),
        FoodEntity(name = "希腊酸奶", caloriesPer100g = 97, category = CAT_DAIRY, emoji = "🍶"),
        FoodEntity(name = "豆奶", caloriesPer100g = 42, category = CAT_DAIRY, emoji = "🥛"),
        FoodEntity(name = "奶酪", caloriesPer100g = 328, category = CAT_DAIRY, emoji = "🧀"),

        // 零食
        FoodEntity(name = "薯片", caloriesPer100g = 536, category = CAT_SNACK, emoji = "🍟"),
        FoodEntity(name = "薯条", caloriesPer100g = 298, category = CAT_SNACK, emoji = "🍟"),
        FoodEntity(name = "巧克力", caloriesPer100g = 586, category = CAT_SNACK, emoji = "🍫"),
        FoodEntity(name = "蛋糕", caloriesPer100g = 347, category = CAT_SNACK, emoji = "🍰"),
        FoodEntity(name = "饼干", caloriesPer100g = 435, category = CAT_SNACK, emoji = "🍪"),
        FoodEntity(name = "冰淇淋", caloriesPer100g = 207, category = CAT_SNACK, emoji = "🍦"),
        FoodEntity(name = "混合坚果", caloriesPer100g = 607, category = CAT_SNACK, emoji = "🥜"),
        FoodEntity(name = "爆米花", caloriesPer100g = 387, category = CAT_SNACK, emoji = "🍿"),
        FoodEntity(name = "汉堡", caloriesPer100g = 266, category = CAT_SNACK, emoji = "🍔"),
        FoodEntity(name = "披萨", caloriesPer100g = 266, category = CAT_SNACK, emoji = "🍕"),

        // 饮品
        FoodEntity(name = "黑咖啡", caloriesPer100g = 2, category = CAT_DRINK, emoji = "☕"),
        FoodEntity(name = "拿铁", caloriesPer100g = 42, category = CAT_DRINK, emoji = "☕"),
        FoodEntity(name = "柠檬红茶", caloriesPer100g = 40, category = CAT_DRINK, emoji = "🧋"),
        FoodEntity(name = "奶茶", caloriesPer100g = 95, category = CAT_DRINK, emoji = "🧋"),
        FoodEntity(name = "可乐", caloriesPer100g = 43, category = CAT_DRINK, emoji = "🥤"),
        FoodEntity(name = "橙汁", caloriesPer100g = 46, category = CAT_DRINK, emoji = "🧃"),
        FoodEntity(name = "啤酒", caloriesPer100g = 32, category = CAT_DRINK, emoji = "🍺"),
        FoodEntity(name = "矿泉水", caloriesPer100g = 0, category = CAT_DRINK, emoji = "💧")
    )
}

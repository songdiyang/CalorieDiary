package com.calorie.diary.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.calorie.diary.R

/**
 * 中文食物名 -> 手绘贴纸资源映射。
 */
object FoodArt {
    private val map: Map<String, Int> = mapOf(
        // 主食
        "米饭" to R.drawable.ic_food_rice,
        "五谷杂粮饭" to R.drawable.ic_food_grain_rice,
        "白粥" to R.drawable.ic_food_congee,
        "小米粥" to R.drawable.ic_food_millet_congee,
        "面条" to R.drawable.ic_food_noodles,
        "方便面" to R.drawable.ic_food_instant_noodles,
        "意大利面" to R.drawable.ic_food_pasta,
        "馒头" to R.drawable.ic_food_steamed_bun,
        "豆腐包子" to R.drawable.ic_food_tofu_bun,
        "香菇油菜包" to R.drawable.ic_food_veggie_bun,
        "饺子" to R.drawable.ic_food_dumpling,
        "馄饨" to R.drawable.ic_food_wonton,
        "全麦面包" to R.drawable.ic_food_wholewheat_bread,
        "吐司" to R.drawable.ic_food_toast,
        "燕麦片" to R.drawable.ic_food_oatmeal,
        "玉米" to R.drawable.ic_food_corn,
        "红薯" to R.drawable.ic_food_sweet_potato,
        "紫薯" to R.drawable.ic_food_purple_potato,
        "土豆" to R.drawable.ic_food_potato,
        "炒饭" to R.drawable.ic_food_fried_rice,
        "蒸米饭" to R.drawable.ic_food_cooked_rice,
        "包子" to R.drawable.ic_food_steamed_bun_basket,
        "烙饼" to R.drawable.ic_food_flatbread,
        "蔬菜粥" to R.drawable.ic_food_veggie_congee,
        "油条" to R.drawable.ic_food_fried_dough,
        "葱油饼" to R.drawable.ic_food_pancake,
        "番茄炒蛋" to R.drawable.ic_food_tomato_egg,
        "牛肉面" to R.drawable.ic_food_beef_noodle,
        // 肉蛋
        "鸡胸肉" to R.drawable.ic_food_chicken_breast,
        "青椒炒鸡丁" to R.drawable.ic_food_chicken_pepper,
        "鸡腿" to R.drawable.ic_food_chicken_leg,
        "牛肉" to R.drawable.ic_food_beef,
        "牛排" to R.drawable.ic_food_steak,
        "猪里脊" to R.drawable.ic_food_pork,
        "鸭肉" to R.drawable.ic_food_duck,
        "鸡蛋" to R.drawable.ic_food_egg,
        "煎蛋" to R.drawable.ic_food_fried_egg,
        "三文鱼" to R.drawable.ic_food_salmon,
        "口蘑煎三文鱼" to R.drawable.ic_food_salmon_mushroom,
        "金枪鱼" to R.drawable.ic_food_tuna,
        "鲈鱼" to R.drawable.ic_food_bass_fish,
        "虾仁" to R.drawable.ic_food_shrimp,
        "虾仁炖豆腐" to R.drawable.ic_food_shrimp_tofu,
        "豆腐" to R.drawable.ic_food_tofu,
        "香肠" to R.drawable.ic_food_sausage,
        "五花肉" to R.drawable.ic_food_pork_belly,
        "排骨" to R.drawable.ic_food_pork_ribs,
        "鸡翅" to R.drawable.ic_food_chicken_wing,
        "卤蛋" to R.drawable.ic_food_marinated_egg,
        "带鱼" to R.drawable.ic_food_hairtail,
        "螃蟹" to R.drawable.ic_food_crab,
        "鱿鱼" to R.drawable.ic_food_squid,
        "蛤蜊" to R.drawable.ic_food_clam,
        // 蔬菜
        "西兰花" to R.drawable.ic_food_broccoli,
        "香菇" to R.drawable.ic_food_shiitake,
        "口蘑" to R.drawable.ic_food_mushroom,
        "油菜" to R.drawable.ic_food_bokchoy,
        "香菇炒油菜" to R.drawable.ic_food_mushroom_greens,
        "青椒" to R.drawable.ic_food_green_pepper,
        "生菜" to R.drawable.ic_food_lettuce,
        "黄瓜" to R.drawable.ic_food_cucumber,
        "番茄" to R.drawable.ic_food_tomato,
        "菠菜" to R.drawable.ic_food_spinach,
        "胡萝卜" to R.drawable.ic_food_carrot,
        "冬瓜" to R.drawable.ic_food_winter_melon,
        "芹菜" to R.drawable.ic_food_celery,
        "白菜" to R.drawable.ic_food_cabbage,
        "茄子" to R.drawable.ic_food_eggplant,
        "蔬菜沙拉" to R.drawable.ic_food_salad,
        "莲藕" to R.drawable.ic_food_lotus_root,
        "洋葱" to R.drawable.ic_food_onion,
        "大蒜" to R.drawable.ic_food_garlic,
        "豆角" to R.drawable.ic_food_green_beans,
        "南瓜" to R.drawable.ic_food_pumpkin,
        "苦瓜" to R.drawable.ic_food_bitter_melon,
        "豆芽" to R.drawable.ic_food_bean_sprout,
        "西葫芦" to R.drawable.ic_food_zucchini,
        // 水果
        "苹果" to R.drawable.ic_food_apple,
        "香蕉" to R.drawable.ic_food_banana,
        "橙子" to R.drawable.ic_food_orange,
        "梨" to R.drawable.ic_food_pear,
        "草莓" to R.drawable.ic_food_strawberry,
        "蓝莓" to R.drawable.ic_food_blueberry,
        "西瓜" to R.drawable.ic_food_watermelon,
        "葡萄" to R.drawable.ic_food_grape,
        "桃子" to R.drawable.ic_food_peach,
        "猕猴桃" to R.drawable.ic_food_kiwi,
        "火龙果" to R.drawable.ic_food_dragon_fruit,
        "牛油果" to R.drawable.ic_food_avocado,
        "芒果" to R.drawable.ic_food_mango,
        "菠萝" to R.drawable.ic_food_pineapple,
        "葡萄柚" to R.drawable.ic_food_grapefruit,
        "柠檬" to R.drawable.ic_food_lemon,
        "樱桃" to R.drawable.ic_food_cherry,
        "甜瓜" to R.drawable.ic_food_melon,
        "木瓜" to R.drawable.ic_food_papaya,
        "椰子" to R.drawable.ic_food_coconut,
        // 奶类
        "牛奶" to R.drawable.ic_food_milk,
        "无糖豆浆" to R.drawable.ic_food_soy_milk,
        "酸奶" to R.drawable.ic_food_yogurt,
        "希腊酸奶" to R.drawable.ic_food_greek_yogurt,
        "豆奶" to R.drawable.ic_food_soybean_milk,
        "奶酪" to R.drawable.ic_food_cheese,
        "脱脂牛奶" to R.drawable.ic_food_skim_milk,
        "炼乳" to R.drawable.ic_food_condensed_milk,
        "豆腐脑" to R.drawable.ic_food_tofu_pudding,
        "豆干" to R.drawable.ic_food_dried_tofu,
        "腐竹" to R.drawable.ic_food_dried_bean_curds,
        "豆腐皮" to R.drawable.ic_food_tofu_skin,
        // 零食
        "薯片" to R.drawable.ic_food_chips,
        "薯条" to R.drawable.ic_food_fries,
        "巧克力" to R.drawable.ic_food_chocolate,
        "蛋糕" to R.drawable.ic_food_cake,
        "饼干" to R.drawable.ic_food_cookie,
        "冰淇淋" to R.drawable.ic_food_icecream,
        "混合坚果" to R.drawable.ic_food_nuts,
        "爆米花" to R.drawable.ic_food_popcorn,
        "汉堡" to R.drawable.ic_food_burger,
        "披萨" to R.drawable.ic_food_pizza,
        "甜甜圈" to R.drawable.ic_food_donut,
        "布丁" to R.drawable.ic_food_pudding,
        "麻薯" to R.drawable.ic_food_mochi,
        "月饼" to R.drawable.ic_food_mooncake,
        "蛋黄酥" to R.drawable.ic_food_egg_yolk_pastry,
        "威化饼干" to R.drawable.ic_food_wafer,
        "麻花" to R.drawable.ic_food_mahua,
        "红枣" to R.drawable.ic_food_red_date,
        // 饮品
        "黑咖啡" to R.drawable.ic_food_black_coffee,
        "拿铁" to R.drawable.ic_food_latte,
        "柠檬红茶" to R.drawable.ic_food_lemon_tea,
        "奶茶" to R.drawable.ic_food_milk_tea,
        "可乐" to R.drawable.ic_food_cola,
        "橙汁" to R.drawable.ic_food_orange_juice,
        "啤酒" to R.drawable.ic_food_beer,
        "矿泉水" to R.drawable.ic_food_water_bottle,
        "热豆浆" to R.drawable.ic_food_soy_milk_drink,
        "瓶装酸奶" to R.drawable.ic_food_yogurt_drink,
        "柠檬气泡水" to R.drawable.ic_food_lemon_soda,
        "葡萄汁" to R.drawable.ic_food_grape_juice,
        "椰子水" to R.drawable.ic_food_coconut_water,
        "蜂蜜水" to R.drawable.ic_food_honey_water,
        "果味汽水" to R.drawable.ic_food_soda_can,
        "热可可" to R.drawable.ic_food_hot_cocoa
    )

    fun resId(name: String): Int? {
        val trimmed = name.trim()
        map[trimmed]?.let { return it }
        // 识别名常带前缀/后缀（如“白米饭”“清炒西兰花”），用最长 key 子串兜底
        return map.keys
            .filter { trimmed.contains(it) }
            .maxByOrNull { it.length }
            ?.let { map[it] }
    }
}

@Composable
fun FoodIcon(
    name: String,
    fallbackEmoji: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    val res = FoodArt.resId(name)
    if (res != null) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(res),
            contentDescription = name,
            modifier = modifier.size(size)
        )
    } else {
        Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Text(fallbackEmoji, fontSize = (size.value * 0.7).sp)
        }
    }
}

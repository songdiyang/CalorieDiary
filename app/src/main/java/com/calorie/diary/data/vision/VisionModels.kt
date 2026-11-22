package com.calorie.diary.data.vision

data class RecognizedFood(
    val name: String,
    val grams: Int,
    val calories: Int,
    /** 彩蛋物品（核弹、燃气罐、汽油等）仅用于趣味提示，不可加入饮食记录 */
    val easterEgg: Boolean = false
)

/** 按原始克数与热量等比换算新克重的热量，使用 Long 避免大数相乘溢出。 */
fun scaledCalories(grams: Int, calories: Int, newGrams: Int): Int {
    val targetGrams = newGrams.coerceAtLeast(0)
    if (grams <= 0) return calories.coerceAtLeast(0)
    val scaled = calories.toLong() * targetGrams.toLong()
    return ((scaled + grams / 2L) / grams.toLong())
        .coerceIn(0L, Int.MAX_VALUE.toLong())
        .toInt()
}

/** 按原始识别结果的每克热量换算新克重。 */
fun RecognizedFood.caloriesForGrams(newGrams: Int): Int =
    scaledCalories(grams, calories, newGrams)

enum class VisionErrorKind {
    NO_FOOD,    // 图里没有可识别食物
    OFFLINE,    // 设备无网络
    TIMEOUT,    // 请求超时
    NETWORK,    // 其它网络异常
    SERVER,     // 服务端繁忙或异常
    SERVICE,    // 云端识别服务不可用（代理未配置/appKey 不匹配/上游密钥失效）
    AUTH,       // 登录会话缺失或已过期
    RATE_LIMIT, // 请求过于频繁
    IMAGE,      // 图片读取失败
    UNKNOWN
}

sealed interface VisionState {
    data object Idle : VisionState
    data object Loading : VisionState
    data class Success(val foods: List<RecognizedFood>) : VisionState
    data class Error(val kind: VisionErrorKind) : VisionState
}

/** 云端识别服务侧问题（代理未配置、appKey 不匹配、上游密钥失效），用户重试无意义 */
class VisionServiceException(message: String) : RuntimeException(message)

/** 本地图片无法读取（文件损坏、权限缺失、解码失败等） */
class ImageLoadException(message: String = "图片读取失败") : RuntimeException(message)

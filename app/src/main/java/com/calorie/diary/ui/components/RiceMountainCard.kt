package com.calorie.diary.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorie.diary.R
import com.calorie.diary.data.supabase.SharedFood
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.NumberFont
import com.calorie.diary.ui.theme.OrangeDeep
import kotlin.math.sin
import kotlin.random.Random

/**
 * 米饭山分享动画卡片：米粒像小山一样从碗上方陆续落下堆积，
 * 并跟随手机重力感应左右轻微滑动。碗数决定米粒数量与堆积高度。
 *
 * @param bowls 今天吃的米饭碗数（用于决定米粒数量，至少 1 碗）
 * @param calories 总热量（千卡）
 */
@Composable
fun RiceMountainCard(
    bowls: Int,
    calories: Int?,
    modifier: Modifier = Modifier
) {
    val tilt = rememberDeviceTilt()
    // 倾角驱动的横向偏移：向右倾米粒偏右，向左倾偏左。
    val tiltShift by animateFloatAsState(
        targetValue = tilt.x * 18f,
        animationSpec = tween(220, easing = LinearEasing),
        label = "riceTiltShift"
    )

    val safeBowls = bowls.coerceAtLeast(1)
    val grainCount = remember(safeBowls) { grainCountForBowls(safeBowls) }
    val grains = remember(grainCount) { buildGrains(grainCount) }

    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.rice_share_title),
            fontFamily = CuteFont, fontSize = 11.sp, color = InkLight
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(200.dp)
                .height(150.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Image(
                painter = painterResource(R.drawable.ic_food_rice),
                contentDescription = null,
                modifier = Modifier.size(72.dp)
            )
            RiceMountainCanvas(
                grains = grains,
                tiltShift = tiltShift,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.rice_share_bowls, safeBowls),
            fontFamily = CuteFont, fontSize = 15.sp, color = Ink
        )
        calories?.let {
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.group_calories_value, it),
                fontFamily = NumberFont, fontSize = 18.sp, color = OrangeDeep
            )
        }
    }
}

/** 每碗对应的米粒数量上限，碗越多山越高。 */
private fun grainCountForBowls(bowls: Int): Int {
    val safe = bowls.coerceIn(1, 8)
    return 14 + safe * 8
}

private data class Grain(
    val xRatio: Float,      // 0..1 水平落点
    val delay: Float,      // 下落起始秒
    val fallDuration: Float,
    val size: Float,        // 米粒相对尺寸
    val phase: Float,       // 摆动相位
    val settledY: Float     // 落定后的高度比（0 碗口 .. 1 山顶）
)

private fun buildGrains(count: Int): List<Grain> {
    val rnd = Random(seed = 20261122)
    return List(count) { i ->
        // 中间密、两边疏，堆成小山轮廓。
        val rowIndex = i % 7
        val spread = 0.16f + 0.34f * (1 - kotlin.math.abs(0.5f - rowIndex / 6f))
        Grain(
            xRatio = (0.5f + (rnd.nextFloat() - 0.5f) * spread).coerceIn(0.08f, 0.92f),
            delay = i * 0.055f,
            fallDuration = 0.7f + rnd.nextFloat() * 0.35f,
            size = 0.7f + rnd.nextFloat() * 0.5f,
            phase = rnd.nextFloat() * 6.28f,
            settledY = (i.toFloat() / count).coerceIn(0f, 1f)
        )
    }
}

@Composable
private fun RiceMountainCanvas(
    grains: List<Grain>,
    tiltShift: Float,
    modifier: Modifier = Modifier
) {
    // 动画时间轴（秒），播放完后停在终点，米粒保持堆积状态。
    val animTime = remember { Animatable(0f) }
    LaunchedEffect(grains.size) {
        animTime.animateTo(
            targetValue = 2.4f,
            animationSpec = tween(durationMillis = 2400, easing = LinearEasing)
        )
    }
    val elapsed = animTime.value

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val bowlTop = h * 0.62f
        val mountainBase = bowlTop
        val mountainPeak = h * 0.16f

        // 碗口椭圆（浅米色底托）。
        drawOval(
            color = CardWhite,
            topLeft = Offset(w * 0.12f, bowlTop - h * 0.05f),
            size = Size(w * 0.76f, h * 0.12f)
        )

        grains.forEachIndexed { index, grain ->
            // 该米粒是否已进入落米窗口。
            val local = (elapsed - grain.delay)
            if (local <= 0f) return@forEachIndexed

            val fallT = (local / grain.fallDuration).coerceIn(0f, 1f)
            // 起点在碗口上方，落到山的对应高度。
            val startY = -h * 0.05f
            val settledY = mountainBase - (mountainBase - mountainPeak) * grain.settledY
            val y = startY + (settledY - startY) * fallT
            // 落定后随倾角和轻微呼吸摆动横向偏移；越高的米粒受倾斜影响越明显。
            val sway = sin(grain.phase + elapsed * 2.0).toFloat() * w * 0.012f
            val x = grain.xRatio * w + tiltShift * (0.5f + grain.settledY) + sway

            val r = w * 0.018f * grain.size
            drawCircle(
                color = RiceGrainColor,
                radius = r,
                center = Offset(x, y)
            )
        }
    }
}

private val RiceGrainColor = Color(0xFFFBF6E9)

/** 一份米饭默认 150 克，用于把米饭克数折算成碗数。 */
private const val RICE_GRAMS_PER_BOWL = 150f

/**
 * 从分享条目反推米饭碗数：优先取米饭类条目克数，按一碗约 150 克折算；
 * 没有条目时默认 1 碗。
 */
fun riceBowlsOf(items: List<SharedFood>): Int {
    val rice = riceItems(items)
    val grams = rice.sumOf { it.grams }
    return if (grams <= 0) 1 else Math.round(grams / RICE_GRAMS_PER_BOWL).coerceAtLeast(1)
}

/** 挑出条目里属于米饭/饭类的项。 */
private fun riceItems(items: List<SharedFood>): List<SharedFood> =
    items.filter {
        it.name.contains("饭") || it.emoji.contains("🍚") || it.emoji.contains("🍙")
    }

/** 米饭类条目总热量；没有米饭条目返回 null。 */
fun riceCaloriesOf(items: List<SharedFood>): Int? {
    val total = riceItems(items).sumOf { it.calories }
    return total.takeIf { it > 0 }
}

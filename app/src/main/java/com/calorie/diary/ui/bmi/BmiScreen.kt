package com.calorie.diary.ui.bmi

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorie.diary.R
import com.calorie.diary.ui.components.CuteButton
import com.calorie.diary.ui.components.CuteNumberField
import com.calorie.diary.ui.components.SoftCard
import com.calorie.diary.ui.home.appVm
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.NumberFont

@Composable
fun BmiScreen(onBack: () -> Unit) {
    val vm = appVm()
    val profile by vm.profile.collectAsState()

    var height by remember { mutableStateOf((profile?.heightCm ?: 165).toString()) }
    var weight by remember {
        mutableStateOf(
            (profile?.weightKg ?: 60f).let {
                if (it % 1f == 0f) it.toInt().toString() else it.toString()
            }
        )
    }

    val h = height.toIntOrNull()
    val w = weight.toFloatOrNull()
    val bmi = if (h != null && w != null && h > 0) w / ((h / 100f) * (h / 100f)) else null

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackArrow(onBack)
            Spacer(Modifier.size(4.dp))
            // 手写体字母 B 字形异常，英文 BMI 用数字字体、中文用手写体
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("BMI", fontFamily = NumberFont, fontSize = 22.sp, color = Ink)
                Text(" 测算", fontFamily = CuteFont, fontSize = 22.sp, color = Ink)
            }
        }

        Spacer(Modifier.height(14.dp))
        SoftCard(
            modifier = Modifier.fillMaxWidth(),
            fill = CardWhite,
            contentPadding = PaddingValues(16.dp)
        ) {
            Column {
                CuteNumberField(
                    height,
                    { height = it.filter { c -> c.isDigit() } },
                    suffix = stringResource(R.string.unit_cm)
                )
                Spacer(Modifier.height(12.dp))
                CuteNumberField(
                    weight,
                    { weight = it.filter { c -> c.isDigit() || c == '.' } },
                    suffix = stringResource(R.string.unit_kg),
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        if (bmi != null) {
            val category = bmiCategory(bmi)
            SoftCard(
                modifier = Modifier.fillMaxWidth(),
                fill = CardWhite,
                contentPadding = PaddingValues(20.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.mascot_celebrate),
                        contentDescription = null,
                        modifier = Modifier.size(80.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("你的 ", fontFamily = CuteFont, fontSize = 14.sp, color = InkLight)
                        Text("BMI", fontFamily = NumberFont, fontSize = 14.sp, color = InkLight)
                    }
                    Text(
                        "%.1f".format(bmi),
                        fontFamily = NumberFont, fontSize = 52.sp, color = category.color
                    )
                    Text(
                        stringResource(category.labelRes),
                        fontFamily = CuteFont, fontSize = 19.sp, color = category.color
                    )
                    Spacer(Modifier.height(16.dp))
                    BmiScale()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(category.adviceRes),
                        fontFamily = CuteFont, fontSize = 13.sp, color = InkLight
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            CuteButton(
                stringResource(R.string.bmi_save_profile),
                {
                    h?.let { vm.setHeight(it) }
                    w?.let { vm.setWeight(it) }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

private data class BmiCategory(val labelRes: Int, val adviceRes: Int, val color: Color)

private fun bmiCategory(bmi: Float): BmiCategory = when {
    bmi < 18.5f -> BmiCategory(
        R.string.bmi_thin, R.string.bmi_advice_thin, Color(0xFF5DADE2)
    )
    bmi < 24f -> BmiCategory(
        R.string.bmi_normal, R.string.bmi_advice_normal, Color(0xFF58B79B)
    )
    bmi < 28f -> BmiCategory(
        R.string.bmi_overweight, R.string.bmi_advice_overweight, Color(0xFFE8A33D)
    )
    else -> BmiCategory(
        R.string.bmi_obese, R.string.bmi_advice_obese, Color(0xFFEF6C6C)
    )
}

@Composable
private fun BmiScale() {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            val segments = listOf(
                Color(0xFF5DADE2),
                Color(0xFF58B79B),
                Color(0xFFE8A33D),
                Color(0xFFEF6C6C)
            )
            segments.forEach { c ->
                Box(
                    Modifier
                        .weight(1f)
                        .padding(2.dp)
                        .height(14.dp)
                        .background(c, RoundedCornerShape(6.dp))
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("<18.5", "18.5-24", "24-28", "≥28").forEach {
                Text(
                    it,
                    fontFamily = NumberFont,
                    fontSize = 10.sp,
                    color = InkLight,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
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

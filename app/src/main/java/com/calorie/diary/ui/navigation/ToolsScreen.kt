package com.calorie.diary.ui.navigation

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorie.diary.R
import com.calorie.diary.ui.components.SoftCard
import com.calorie.diary.ui.theme.BlueSoft
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.NumberFont
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.LavenderSoft
import com.calorie.diary.ui.theme.MintSoft
import com.calorie.diary.ui.theme.PinkSoft
import com.calorie.diary.ui.theme.YellowSoft

private data class ToolItem(
    val iconRes: Int,
    val titleRes: Int,
    val descRes: Int,
    val tint: Color,
    val onClick: () -> Unit
)

@Composable
fun ToolsScreen(
    onOpenWater: () -> Unit,
    onOpenFasting: () -> Unit,
    onOpenBmi: () -> Unit,
    onOpenRecognition: () -> Unit,
    onOpenExercise: () -> Unit,
    onOpenTextLog: () -> Unit,
    onOpenGroup: () -> Unit
) {
    val items = listOf(
        ToolItem(R.drawable.icon_pencil, R.string.tools_textlog, R.string.tools_textlog_desc, YellowSoft, onOpenTextLog),
        ToolItem(R.drawable.nav_toolbox, R.string.tools_group, R.string.tools_group_desc, LavenderSoft, onOpenGroup),
        ToolItem(R.drawable.icon_camera, R.string.tools_recognition, R.string.tools_recognition_desc, PinkSoft, onOpenRecognition),
        ToolItem(R.drawable.tool_water, R.string.tools_water, R.string.tools_water_desc, BlueSoft, onOpenWater),
        ToolItem(R.drawable.tool_fasting, R.string.tools_fasting, R.string.tools_fasting_desc, YellowSoft, onOpenFasting),
        ToolItem(R.drawable.tool_bmi, R.string.tools_bmi, R.string.tools_bmi_desc, MintSoft, onOpenBmi),
        ToolItem(R.drawable.tool_exercise, R.string.tools_exercise, R.string.tools_exercise_desc, PinkSoft, onOpenExercise)
    )

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.tools_title), fontFamily = CuteFont, fontSize = 22.sp, color = Ink)
        Spacer(Modifier.height(16.dp))

        items.forEach { item ->
            SoftCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null, onClick = item.onClick
                    ),
                fill = CardWhite,
                contentPadding = PaddingValues(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(item.tint),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(item.iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(Modifier.size(14.dp))
                    Column(Modifier.weight(1f)) {
                        if (item.titleRes == R.string.tools_bmi) {
                            // 手写体的字母 B 字形异常，英文 BMI 用数字字体、中文用手写体
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("BMI", fontFamily = NumberFont, fontSize = 16.sp, color = Ink)
                                Text(
                                    " 测算",
                                    fontFamily = CuteFont,
                                    fontSize = 16.sp,
                                    color = Ink
                                )
                            }
                        } else {
                            Text(
                                stringResource(item.titleRes),
                                fontFamily = CuteFont, fontSize = 16.sp, color = Ink
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            stringResource(item.descRes),
                            fontFamily = CuteFont, fontSize = 12.sp, color = InkLight
                        )
                    }
                    Image(
                        painter = painterResource(R.drawable.icon_arrow),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
internal fun ToolsPreviewContent() {
    val items = listOf(
        ToolItem(R.drawable.icon_pencil, R.string.tools_textlog, R.string.tools_textlog_desc, YellowSoft) { },
        ToolItem(R.drawable.nav_toolbox, R.string.tools_group, R.string.tools_group_desc, LavenderSoft) { },
        ToolItem(R.drawable.icon_camera, R.string.tools_recognition, R.string.tools_recognition_desc, PinkSoft) { },
        ToolItem(R.drawable.tool_water, R.string.tools_water, R.string.tools_water_desc, BlueSoft) { },
        ToolItem(R.drawable.tool_fasting, R.string.tools_fasting, R.string.tools_fasting_desc, YellowSoft) { },
        ToolItem(R.drawable.tool_bmi, R.string.tools_bmi, R.string.tools_bmi_desc, MintSoft) { },
        ToolItem(R.drawable.tool_exercise, R.string.tools_exercise, R.string.tools_exercise_desc, PinkSoft) { }
    )
    Column(
        Modifier
            .fillMaxWidth()
            .background(com.calorie.diary.ui.theme.Cream)
            .padding(18.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.tools_title), fontFamily = CuteFont, fontSize = 22.sp, color = Ink)
        Spacer(Modifier.height(16.dp))
        items.forEach { item ->
            SoftCard(
                modifier = Modifier.fillMaxWidth(),
                fill = CardWhite,
                contentPadding = PaddingValues(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(item.tint),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(item.iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(Modifier.size(14.dp))
                    Column(Modifier.weight(1f)) {
                        if (item.titleRes == R.string.tools_bmi) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("BMI", fontFamily = NumberFont, fontSize = 16.sp, color = Ink)
                                Text(" 测算", fontFamily = CuteFont, fontSize = 16.sp, color = Ink)
                            }
                        } else {
                            Text(stringResource(item.titleRes), fontFamily = CuteFont, fontSize = 16.sp, color = Ink)
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(stringResource(item.descRes), fontFamily = CuteFont, fontSize = 12.sp, color = InkLight)
                    }
                    Image(
                        painter = painterResource(R.drawable.icon_arrow),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

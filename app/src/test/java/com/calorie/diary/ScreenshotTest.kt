package com.calorie.diary

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorie.diary.ui.components.CuteButton
import com.calorie.diary.ui.components.CuteChip
import com.calorie.diary.ui.components.CuteProgressRing
import com.calorie.diary.ui.components.CuteSearchBar
import com.calorie.diary.ui.components.FoodIcon
import com.calorie.diary.ui.components.HandDrawnCard
import com.calorie.diary.ui.theme.CalorieDiaryTheme
import com.calorie.diary.ui.theme.Cream
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.NumberFont
import com.calorie.diary.ui.theme.mealColors
import androidx.compose.material3.Text
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class ScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        showSystemUi = false
    )

    @Test
    fun componentShowcase() {
        paparazzi.snapshot {
            CalorieDiaryTheme {
                Column(
                    Modifier
                        .background(Cream)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    Text("手绘组件展示", fontFamily = CuteFont, fontSize = 22.sp, color = Ink)
                    Spacer(Modifier.height(16.dp))
                    CuteProgressRing(progress = 0.62f) {
                        Column {
                            Text("已摄入", fontFamily = CuteFont, color = Ink)
                            Text("1116", fontFamily = NumberFont, fontSize = 40.sp, color = Ink)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    CuteSearchBar(value = "米饭", onValueChange = {})
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CuteChip("全部", true, {})
                        CuteChip("主食", false, {})
                        CuteChip("水果", false, {})
                    }
                    Spacer(Modifier.height(18.dp))
                    CuteButton("添加记录", {})
                }
            }
        }
    }

    @Test
    fun mealCardSample() {
        paparazzi.snapshot {
            CalorieDiaryTheme {
                Column(
                    Modifier
                        .background(Cream)
                        .padding(18.dp)
                ) {
                    HandDrawnCard(
                        modifier = Modifier.fillMaxWidth(),
                        fill = mealColors[0],
                        seed = 2.1f
                    ) {
                        Column {
                            Row {
                                Text("🌅", fontSize = 24.sp)
                                Spacer(Modifier.width(10.dp))
                                Text("早餐", fontFamily = CuteFont, fontSize = 18.sp, color = Ink)
                                Spacer(Modifier.weight(1f))
                                Text("425 千卡", fontFamily = NumberFont, color = Ink)
                            }
                            Spacer(Modifier.height(10.dp))
                            FakeRow("🍚", "米饭", "200 克", "232 千卡")
                            FakeRow("🥚", "鸡蛋", "50 克", "72 千卡")
                            Spacer(Modifier.height(10.dp))
                            Text("➕ 添加早餐", fontFamily = CuteFont, color = Ink)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun FakeRow(emoji: String, name: String, grams: String, cal: String) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 5.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            FoodIcon(name = name, fallbackEmoji = emoji, size = 40.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontFamily = CuteFont, fontSize = 15.sp, color = Ink)
                Text(grams, fontFamily = CuteFont, fontSize = 11.sp, color = Ink)
            }
            Text(cal, fontFamily = NumberFont, fontSize = 14.sp, color = Ink)
        }
    }

    @Test
    fun extraShowcase() {
        val res = listOf(
            com.calorie.diary.R.drawable.nav_home,
            com.calorie.diary.R.drawable.nav_calendar,
            com.calorie.diary.R.drawable.nav_toolbox,
            com.calorie.diary.R.drawable.nav_profile,
            com.calorie.diary.R.drawable.tool_water,
            com.calorie.diary.R.drawable.tool_fasting,
            com.calorie.diary.R.drawable.tool_bmi,
            com.calorie.diary.R.drawable.meal_breakfast,
            com.calorie.diary.R.drawable.meal_lunch,
            com.calorie.diary.R.drawable.meal_dinner,
            com.calorie.diary.R.drawable.meal_snack,
            com.calorie.diary.R.drawable.mascot_happy,
            com.calorie.diary.R.drawable.mascot_cheer,
            com.calorie.diary.R.drawable.mascot_sleep,
            com.calorie.diary.R.drawable.mascot_celebrate,
            com.calorie.diary.R.drawable.deco_star,
            com.calorie.diary.R.drawable.deco_heart,
            com.calorie.diary.R.drawable.deco_flower,
            com.calorie.diary.R.drawable.deco_sparkle,
            com.calorie.diary.R.drawable.deco_crown,
            com.calorie.diary.R.drawable.badge_streak,
            com.calorie.diary.R.drawable.badge_goal,
            com.calorie.diary.R.drawable.badge_champion,
            com.calorie.diary.R.drawable.icon_plus,
            com.calorie.diary.R.drawable.icon_back,
            com.calorie.diary.R.drawable.icon_bell,
            com.calorie.diary.R.drawable.icon_pencil,
            com.calorie.diary.R.drawable.icon_close,
            com.calorie.diary.R.drawable.icon_arrow,
            com.calorie.diary.R.drawable.icon_target,
            com.calorie.diary.R.drawable.icon_drop
        )
        paparazzi.snapshot {
            CalorieDiaryTheme {
                Column(
                    Modifier
                        .background(Cream)
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp)
                ) {
                    res.chunked(4).forEach { rowItems ->
                        Row(Modifier.fillMaxWidth()) {
                            rowItems.forEach {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(it),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(6.dp)
                                )
                            }
                            repeat(4 - rowItems.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun bannerShowcase() {
        paparazzi.snapshot {
            CalorieDiaryTheme {
                Column(
                    Modifier
                        .background(Cream)
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp)
                ) {
                    listOf(
                        com.calorie.diary.R.drawable.ill_welcome_banner,
                        com.calorie.diary.R.drawable.ill_water_banner,
                        com.calorie.diary.R.drawable.ill_fasting_banner,
                        com.calorie.diary.R.drawable.ill_empty_state
                    ).forEach {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(it),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }

    @Test
    fun waterShowcase() {
        paparazzi.snapshot {
            CalorieDiaryTheme {
                Column(
                    Modifier
                        .background(Cream)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    com.calorie.diary.ui.water.WaterPreviewContent()
                }
            }
        }
    }

    @Test
    fun waterPageShowcase() {
        paparazzi.snapshot {
            CalorieDiaryTheme {
                Column(
                    Modifier
                        .background(Cream)
                        .verticalScroll(rememberScrollState())
                ) {
                    com.calorie.diary.ui.water.WaterPagePreviewContent()
                }
            }
        }
    }

    @Test
    fun ringStates() {
        paparazzi.snapshot {
            CalorieDiaryTheme {
                Column(
                    Modifier.background(Cream).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CuteProgressRing(progress = 0.3f, size = 180.dp)
                    Spacer(Modifier.height(16.dp))
                    CuteProgressRing(progress = 1f, size = 180.dp)
                    Spacer(Modifier.height(16.dp))
                    CuteProgressRing(progress = 1.15f, size = 180.dp)
                }
            }
        }
    }

    @Test
    fun recognitionShowcase() {
        paparazzi.snapshot {
            CalorieDiaryTheme {
                Column(
                    Modifier
                        .background(Cream)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    com.calorie.diary.ui.vision.RecognitionPreviewContent()
                }
            }
        }
    }

    @Test
    fun recognitionErrorShowcase() {
        paparazzi.snapshot {
            CalorieDiaryTheme {
                Column(
                    Modifier
                        .background(Cream)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    com.calorie.diary.ui.vision.RecognitionErrorPreviewContent()
                }
            }
        }
    }

    @Test
    fun recognitionEasterEggShowcase() {
        paparazzi.snapshot {
            CalorieDiaryTheme {
                Column(
                    Modifier
                        .background(Cream)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    com.calorie.diary.ui.vision.RecognitionEasterEggPreviewContent()
                }
            }
        }
    }

    @Test
    fun authPagesShowcase() {
        paparazzi.snapshot {
            CalorieDiaryTheme {
                Column(
                    Modifier
                        .background(Cream)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    com.calorie.diary.ui.profile.AuthPagesPreviewContent()
                }
            }
        }
    }

    @Test
    fun languageChipShowcase() {
        paparazzi.snapshot {
            CalorieDiaryTheme {
                Column(
                    Modifier
                        .background(Cream)
                        .padding(16.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("中文", "English", "日本語", "Русский").forEachIndexed { i, name ->
                            CuteChip(name, i == 0, {})
                        }
                    }
                }
            }
        }
    }

    @Test
    fun exerciseShowcase() {
        paparazzi.snapshot {
            CalorieDiaryTheme {
                Column(
                    Modifier
                        .background(Cream)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    com.calorie.diary.ui.exercise.ExercisePreviewContent()
                }
            }
        }
    }

    @Test
    fun splashShowcase() {
        paparazzi.snapshot {
            CalorieDiaryTheme {
                com.calorie.diary.ui.splash.SplashContent(animate = false)
            }
        }
    }

    @Test
    fun launcherIconShowcase() {
        paparazzi.snapshot {
            CalorieDiaryTheme {
                Row(
                    Modifier
                        .background(androidx.compose.ui.graphics.Color(0xFFE7E2DA))
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconTile(CircleShape, "圆形")
                    IconTile(RoundedCornerShape(34.dp), "圆角方")
                    IconTile(RoundedCornerShape(0.dp), "满版")
                }
            }
        }
    }

    @Composable
    private fun IconTile(shape: androidx.compose.ui.graphics.Shape, label: String) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(150.dp)
                    .clip(shape)
            ) {
                Image(
                    painterResource(R.drawable.ic_launcher_background),
                    null,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Image(
                    painterResource(R.drawable.ic_launcher_foreground),
                    null,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(label, fontFamily = CuteFont, fontSize = 13.sp, color = Ink)
        }
    }

    @Test
    fun genderSwitchFixed() {
        val genderFlow = MutableStateFlow("女")

        paparazzi.snapshot("female") {
            CalorieDiaryTheme {
                val g by genderFlow.collectAsState()
                Row(
                    Modifier.background(Cream).padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CuteChip("女", g == "女", { genderFlow.value = "女" })
                    CuteChip("男", g == "男", { genderFlow.value = "男" })
                }
            }
        }

        // 模拟用户点击“男”：flow 发射新值，collectAsState 应驱动重组
        genderFlow.value = "男"
        paparazzi.snapshot("male") {
            CalorieDiaryTheme {
                val g by genderFlow.collectAsState()
                Row(
                    Modifier.background(Cream).padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CuteChip("女", g == "女", { genderFlow.value = "女" })
                    CuteChip("男", g == "男", { genderFlow.value = "男" })
                }
            }
        }
    }

    @Test
    fun friendChatShowcase() {
        paparazzi.snapshot {
            CalorieDiaryTheme {
                com.calorie.diary.ui.friends.ChatPreviewContent()
            }
        }
    }

    @Test
    fun groupChatShowcase() {
        paparazzi.snapshot {
            CalorieDiaryTheme {
                com.calorie.diary.ui.friends.GroupChatPreviewContent()
            }
        }
    }

    @Test
    fun groupsPageShowcase() {
        paparazzi.snapshot {
            CalorieDiaryTheme {
                com.calorie.diary.ui.friends.GroupsPreviewContent()
            }
        }
    }

    @Test
    fun friendsPageShowcase() {
        paparazzi.snapshot {
            CalorieDiaryTheme {
                com.calorie.diary.ui.friends.FriendsPreviewContent()
            }
        }
    }

    @Test
    fun friendSearchPageShowcase() {
        paparazzi.snapshot {
            CalorieDiaryTheme {
                com.calorie.diary.ui.friends.FriendSearchPreviewContent()
            }
        }
    }

    @Test
    fun profilePageShowcase() {
        paparazzi.snapshot {
            CalorieDiaryTheme {
                Column(Modifier.background(Cream)) {
                    com.calorie.diary.ui.profile.ProfilePreviewContent()
                }
            }
        }
    }

    @Test
    fun personalPageShowcase() {
        paparazzi.snapshot {
            CalorieDiaryTheme {
                Column(Modifier.background(Cream)) {
                    com.calorie.diary.ui.profile.PersonalPagePreviewContent()
                }
            }
        }
    }

    @Test
    fun toolsPageShowcase() {
        paparazzi.snapshot {
            CalorieDiaryTheme {
                Column(
                    Modifier
                        .background(Cream)
                        .verticalScroll(rememberScrollState())
                ) {
                    com.calorie.diary.ui.navigation.ToolsPreviewContent()
                }
            }
        }
    }

    @Test
    fun textLogPageShowcase() {
        paparazzi.snapshot {
            CalorieDiaryTheme {
                Column(
                    Modifier
                        .background(Cream)
                        .verticalScroll(rememberScrollState())
                ) {
                    com.calorie.diary.ui.textlog.TextLogPreviewContent()
                }
            }
        }
    }

    @Test
    fun groupPageShowcase() {
        paparazzi.snapshot {
            CalorieDiaryTheme {
                Column(
                    Modifier
                        .background(Cream)
                        .verticalScroll(rememberScrollState())
                ) {
                    com.calorie.diary.ui.group.GroupPreviewContent()
                }
            }
        }
    }

    @Test
    fun homePageShowcase() {
        paparazzi.snapshot {
            CalorieDiaryTheme {
                Column(
                    Modifier.background(com.calorie.diary.ui.theme.ScreenBg)
                ) {
                    com.calorie.diary.ui.home.HomePreviewContent()
                }
            }
        }
    }

    @Test
    fun bottomBarShowcase() {
        paparazzi.snapshot {
            CalorieDiaryTheme {
                Column(
                    Modifier.background(com.calorie.diary.ui.theme.ScreenBg)
                ) {
                    com.calorie.diary.ui.navigation.BottomBarPreviewContent()
                }
            }
        }
    }

    @Test
    fun deletedUserProfileShowcase() {
        paparazzi.snapshot {
            CalorieDiaryTheme {
                com.calorie.diary.ui.profile.PersonalPageContent(
                    state = com.calorie.diary.viewmodel.PersonalPageUiState(
                        loading = false,
                        isOwnPage = false,
                        status = "deleted"
                    ),
                    onBack = {},
                    onLogin = {}
                )
            }
        }
    }

    @Test
    fun privacyConsentShowcase() {
        paparazzi.snapshot {
            CalorieDiaryTheme {
                Column(Modifier.background(Cream).padding(28.dp)) {
                    com.calorie.diary.ui.privacy.PrivacyConsentCard(
                        onAgree = {},
                        onDecline = {}
                    )
                }
            }
        }
    }

    @Test
    fun settingsPageShowcase() {
        paparazzi.snapshot {
            CalorieDiaryTheme {
                Column(
                    Modifier.background(com.calorie.diary.ui.theme.ScreenBg)
                ) {
                    com.calorie.diary.ui.settings.SettingsPreviewContent()
                }
            }
        }
    }

    @Test
    fun homeEmptyShowcase() {
        paparazzi.snapshot {
            CalorieDiaryTheme {
                Column(
                    Modifier.background(com.calorie.diary.ui.theme.ScreenBg)
                ) {
                    com.calorie.diary.ui.home.HomeEmptyPreviewContent()
                }
            }
        }
    }
}

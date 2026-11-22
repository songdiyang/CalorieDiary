package com.calorie.diary.ui.profile

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorie.diary.R
import com.calorie.diary.data.AppRefreshBus
import com.calorie.diary.data.DateUtils
import com.calorie.diary.data.game.GameCatalog
import com.calorie.diary.data.game.NewbieTaskAction
import com.calorie.diary.data.game.NewbieTaskDefinition
import com.calorie.diary.data.game.NewbieTaskState
import com.calorie.diary.data.game.ShopItem
import com.calorie.diary.ui.components.CuteButton
import com.calorie.diary.ui.components.SoftCard
import com.calorie.diary.ui.home.appVm
import com.calorie.diary.ui.navigation.Routes
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Hairline
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.LavenderSoft
import com.calorie.diary.ui.theme.MintSoft
import com.calorie.diary.ui.theme.NumberFont
import com.calorie.diary.ui.theme.OrangeDeep
import com.calorie.diary.ui.theme.ScreenBg
import com.calorie.diary.ui.theme.YellowSoft
import com.calorie.diary.viewmodel.AppViewModel
import com.calorie.diary.viewmodel.GameUiState

@Composable
fun BackpackScreen(
    onBack: () -> Unit,
    onOpenShop: () -> Unit,
    onLogin: () -> Unit
) {
    val vm = appVm()
    val game by vm.gameState.collectAsState()
    SyncGamePage(Routes.BACKPACK, vm)

    Column(Modifier.fillMaxSize().background(ScreenBg)) {
        GamePageHeader(stringResource(R.string.profile_backpack), onBack)
        if (game.requiresLogin) {
            GameLoginContent(onLogin)
        } else {
            BackpackContent(game, onOpenShop)
        }
    }
}

@Composable
fun ShopScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit
) {
    val vm = appVm()
    val game by vm.gameState.collectAsState()
    SyncGamePage(Routes.SHOP, vm)

    Column(Modifier.fillMaxSize().background(ScreenBg)) {
        GamePageHeader(stringResource(R.string.profile_shop), onBack)
        if (game.requiresLogin) {
            GameLoginContent(onLogin)
        } else {
            ShopContent(game, vm::purchaseItem)
        }
    }
}

/** 任务页：新手任务 + 每日签到任务，签到登录后自动完成。 */
@Composable
fun TasksScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onNewbieAction: (NewbieTaskAction) -> Unit
) {
    val vm = appVm()
    val game by vm.gameState.collectAsState()
    SyncGamePage(Routes.TASKS, vm)

    Column(Modifier.fillMaxSize().background(ScreenBg)) {
        GamePageHeader(stringResource(R.string.tasks_title), onBack)
        if (game.requiresLogin) {
            GameLoginContent(onLogin)
        } else {
            TasksContent(game, onNewbieAction)
        }
    }
}

@Composable
private fun TasksContent(game: GameUiState, onNewbieAction: (NewbieTaskAction) -> Unit) {
    val today = game.serverDate.ifBlank { DateUtils.todayString() }
    val checkedIn = game.lastCheckInDate == today
    val streak = game.checkInStreak
    val streakDays = game.checkInStreakDays
    val doubledToday = checkedIn && streakDays > 0 && streak > 0 && streak % streakDays == 0
    // 翻倍日直接展示翻倍后的实际奖励。
    val multiplier = if (doubledToday) game.checkInMultiplier else 1
    val expShown = game.checkInExpReward * multiplier
    val coinsShown = game.checkInCoinReward * multiplier
    val footer = when {
        doubledToday -> stringResource(R.string.tasks_doubled_footer, expShown, coinsShown)
        streak > 0 -> stringResource(R.string.tasks_streak_line, streak, streakDays)
        else -> stringResource(R.string.tasks_streak_hint, streakDays)
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { LevelSummaryCard(game) }
        val newbieTasks = GameCatalog.newbieTasks.mapNotNull { definition ->
            game.newbieTasks.firstOrNull { it.taskId == definition.id }
                ?.let { definition to it }
        }
        // 新手任务常驻：全部完成后三项仍显示“已完成”。
        if (newbieTasks.isNotEmpty()) {
            item {
                Column {
                    Text(
                        stringResource(R.string.tasks_section_newbie),
                        fontFamily = CuteFont,
                        fontSize = 16.sp,
                        color = Ink,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )
                    SoftCard(
                        modifier = Modifier.fillMaxWidth(),
                        fill = CardWhite,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Column {
                            newbieTasks.forEachIndexed { index, (definition, state) ->
                                if (index > 0) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(Hairline)
                                    )
                                }
                                NewbieTaskRow(definition, state, onNewbieAction)
                            }
                        }
                    }
                }
            }
        }
        item {
            Column {
                Text(
                    stringResource(R.string.tasks_section_daily),
                    fontFamily = CuteFont,
                    fontSize = 16.sp,
                    color = Ink,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
                SoftCard(
                    modifier = Modifier.fillMaxWidth(),
                    fill = CardWhite,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Column {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(LavenderSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.nav_calendar),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        stringResource(R.string.checkin_title),
                                        fontFamily = CuteFont, fontSize = 16.sp, color = Ink
                                    )
                                    if (doubledToday) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            stringResource(R.string.tasks_doubled_today),
                                            fontFamily = CuteFont,
                                            fontSize = 10.sp,
                                            color = OrangeDeep,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(MintSoft)
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(5.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RewardTag(
                                        stringResource(R.string.tasks_reward_exp, expShown),
                                        LavenderSoft
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    RewardTag(
                                        stringResource(R.string.tasks_reward_coins, coinsShown),
                                        YellowSoft,
                                        iconRes = R.drawable.deco_star
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            if (checkedIn && streak > 0) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        stringResource(R.string.tasks_streak_label),
                                        fontFamily = CuteFont, fontSize = 10.sp, color = InkLight
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        stringResource(R.string.tasks_streak_value, streak),
                                        fontFamily = NumberFont,
                                        fontSize = 18.sp,
                                        color = OrangeDeep
                                    )
                                }
                            } else {
                                Text(
                                    stringResource(R.string.tasks_pending),
                                    fontFamily = CuteFont,
                                    fontSize = 12.sp,
                                    color = OrangeDeep,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(YellowSoft)
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                footer,
                                fontFamily = CuteFont,
                                fontSize = 11.sp,
                                color = InkLight,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
        if (game.loading) {
            item { GameSyncingText() }
        }
    }
}

/** 新手任务行：未完成显示“去完成”跳转，完成后只展示完成态与奖励。 */
@Composable
private fun NewbieTaskRow(
    definition: NewbieTaskDefinition,
    state: NewbieTaskState,
    onAction: (NewbieTaskAction) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(LavenderSoft),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(definition.iconRes),
                contentDescription = null,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(definition.nameRes),
                fontFamily = CuteFont,
                fontSize = 15.sp,
                color = Ink
            )
            Spacer(Modifier.height(3.dp))
            Text(
                stringResource(definition.descriptionRes),
                fontFamily = CuteFont,
                fontSize = 11.sp,
                color = InkLight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RewardTag(
                    stringResource(R.string.tasks_reward_coins, state.coinReward),
                    YellowSoft,
                    iconRes = R.drawable.deco_star
                )
                Spacer(Modifier.width(6.dp))
                RewardTag(
                    stringResource(R.string.tasks_reward_exp, state.expReward),
                    LavenderSoft
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (state.done) {
            Text(
                stringResource(R.string.newbie_task_done),
                fontFamily = CuteFont,
                fontSize = 12.sp,
                color = InkLight,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(ScreenBg)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        } else {
            Text(
                stringResource(R.string.newbie_task_go),
                fontFamily = CuteFont,
                fontSize = 12.sp,
                color = OrangeDeep,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MintSoft)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onAction(definition.action) }
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

/** 等级进度卡：展示当前等级、距升级所需经验与进度条。 */
@Composable
private fun LevelSummaryCard(game: GameUiState) {
    val span = game.levelNext.coerceAtLeast(1)
    val progress = (game.levelExp.toFloat() / span).coerceIn(0f, 1f)
    val remaining = (span - game.levelExp).coerceAtLeast(0)
    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        fill = CardWhite,
        contentPadding = PaddingValues(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.level_badge, game.level),
                    fontFamily = NumberFont,
                    fontSize = 12.sp,
                    color = Ink,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(LavenderSoft)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.tasks_level_remaining, remaining),
                    fontFamily = CuteFont,
                    fontSize = 12.sp,
                    color = InkLight,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${game.levelExp} / $span",
                    fontFamily = NumberFont,
                    fontSize = 11.sp,
                    color = InkLight
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50)),
                color = OrangeDeep,
                trackColor = ScreenBg
            )
        }
    }
}

/** 金币/经验数量胶囊：金额全部来自服务端配置。 */
@Composable
private fun RewardTag(text: String, fill: Color, @DrawableRes iconRes: Int? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(fill)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        if (iconRes != null) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(3.dp))
        }
        Text(text, fontFamily = CuteFont, fontSize = 11.sp, color = Ink)
    }
}

@Composable
private fun SyncGamePage(route: String, vm: AppViewModel) {
    LaunchedEffect(route) {
        vm.refreshGameState()
        AppRefreshBus.events.collect { refreshedRoute ->
            if (refreshedRoute == route) vm.refreshGameState()
        }
    }
}

@Composable
private fun GamePageHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CardWhite)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.icon_back),
                contentDescription = stringResource(R.string.common_back),
                modifier = Modifier.size(23.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            fontFamily = CuteFont,
            fontSize = 24.sp,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GameLoginContent(onLogin: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(18.dp)) {
        CloudLoginPanel(onLogin)
    }
}

@Composable
private fun BackpackContent(game: GameUiState, onOpenShop: () -> Unit) {
    val ownedItems = game.inventory.mapNotNull { inventory ->
        GameCatalog.item(inventory.itemId)?.let { it to inventory.quantity }
    }

    if (ownedItems.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.mascot_happy),
                contentDescription = null,
                modifier = Modifier.size(112.dp)
            )
            Spacer(Modifier.height(18.dp))
            if (game.loading) {
                GameSyncingText()
            } else {
                Text(
                    stringResource(R.string.inventory_empty),
                    fontFamily = CuteFont,
                    fontSize = 15.sp,
                    color = InkLight,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                CuteButton(stringResource(R.string.profile_shop), onOpenShop)
            }
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                    Text(
                        stringResource(
                            R.string.inventory_count,
                            game.inventory.sumOf { it.quantity }
                        ),
                        fontFamily = CuteFont,
                        fontSize = 14.sp,
                        color = InkLight
                    )
                    if (game.loading) {
                        Spacer(Modifier.height(4.dp))
                        GameSyncingText()
                    }
                }
            }
            items(ownedItems, key = { it.first.id }) { (item, quantity) ->
                SoftCard(
                    modifier = Modifier.fillMaxWidth(),
                    fill = CardWhite,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ItemIcon(item)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(item.nameRes),
                                fontFamily = CuteFont,
                                fontSize = 16.sp,
                                color = Ink
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(item.descriptionRes),
                                fontFamily = CuteFont,
                                fontSize = 12.sp,
                                color = InkLight,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.inventory_quantity, quantity),
                            fontFamily = NumberFont,
                            fontSize = 15.sp,
                            color = OrangeDeep,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(LavenderSoft)
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShopContent(game: GameUiState, onPurchase: (String) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SoftCard(
                modifier = Modifier.fillMaxWidth(),
                fill = YellowSoft,
                contentPadding = PaddingValues(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.deco_star),
                        contentDescription = null,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            stringResource(R.string.shop_balance, game.coins),
                            fontFamily = NumberFont,
                            fontSize = 18.sp,
                            color = OrangeDeep
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            stringResource(
                                if (game.loading) R.string.game_syncing else R.string.shop_subtitle
                            ),
                            fontFamily = CuteFont,
                            fontSize = 12.sp,
                            color = InkLight
                        )
                    }
                }
            }
        }
        items(GameCatalog.items, key = { it.id }) { item ->
            SoftCard(
                modifier = Modifier.fillMaxWidth(),
                fill = CardWhite,
                contentPadding = PaddingValues(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ItemIcon(item)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(item.nameRes),
                            fontFamily = CuteFont,
                            fontSize = 16.sp,
                            color = Ink
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(item.descriptionRes),
                            fontFamily = CuteFont,
                            fontSize = 12.sp,
                            color = InkLight,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(
                            stringResource(R.string.shop_price, item.price),
                            fontFamily = NumberFont,
                            fontSize = 14.sp,
                            color = OrangeDeep
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.shop_buy),
                        fontFamily = CuteFont,
                        fontSize = 14.sp,
                        color = OrangeDeep,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MintSoft)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onPurchase(item.id) }
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GameSyncingText() {
    Text(
        stringResource(R.string.game_syncing),
        fontFamily = CuteFont,
        fontSize = 12.sp,
        color = InkLight,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun ItemIcon(item: ShopItem) {
    Box(
        Modifier
            .size(58.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(YellowSoft),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(item.iconRes),
            contentDescription = null,
            modifier = Modifier.size(42.dp)
        )
    }
}

package com.calorie.diary.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.calorie.diary.R
import com.calorie.diary.data.game.CheckInReward
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.MintSoft
import com.calorie.diary.ui.theme.NumberFont
import com.calorie.diary.ui.theme.OrangeDeep
import com.calorie.diary.ui.theme.YellowSoft

/**
 * 每日签到成功弹窗：居中展示奖励，必须点击按钮才能关闭，
 * 点外部、返回键都不会关闭。
 */
@Composable
fun CheckInSuccessDialog(reward: CheckInReward, onConfirm: () -> Unit) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(22.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(CardWhite)
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.mascot_celebrate),
                contentDescription = null,
                modifier = Modifier.size(96.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.checkin_success_title),
                fontFamily = CuteFont,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = Ink
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RewardPill(
                    text = stringResource(R.string.checkin_reward_coins, reward.coins),
                    background = YellowSoft
                )
                Spacer(Modifier.width(10.dp))
                RewardPill(
                    text = stringResource(R.string.checkin_reward_exp, reward.exp),
                    background = MintSoft
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(
                    if (reward.doubled) R.string.checkin_doubled_note
                    else R.string.checkin_streak_note,
                    reward.streak
                ),
                fontFamily = CuteFont,
                fontSize = 12.sp,
                color = InkLight,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            CuteButton(
                text = stringResource(R.string.checkin_confirm),
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun RewardPill(text: String, background: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        fontFamily = NumberFont,
        fontSize = 16.sp,
        color = OrangeDeep,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    )
}
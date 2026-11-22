package com.calorie.diary.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorie.diary.R
import com.calorie.diary.data.supabase.SocialSummary
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.NumberFont

/** 关注 / 粉丝 / 互关 横向统计，我的页与个人主页共用。 */
@Composable
fun SocialStatsCard(
    summary: SocialSummary?,
    onFollowing: () -> Unit,
    onFollowers: () -> Unit,
    onMutual: () -> Unit,
    modifier: Modifier = Modifier
) {
    SoftCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 15.dp)) {
        Row(Modifier.fillMaxWidth()) {
            SocialStatItem(
                label = stringResource(R.string.social_following),
                value = summary?.following?.toString() ?: "-",
                onClick = onFollowing,
                modifier = Modifier.weight(1f)
            )
            SocialStatItem(
                label = stringResource(R.string.social_followers),
                value = summary?.followers?.toString() ?: "-",
                onClick = onFollowers,
                modifier = Modifier.weight(1f)
            )
            SocialStatItem(
                label = stringResource(R.string.social_mutual),
                value = summary?.mutual?.toString() ?: "-",
                onClick = onMutual,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SocialStatItem(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Column(
        modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontFamily = NumberFont, fontSize = 23.sp, color = Ink)
        Spacer(Modifier.height(4.dp))
        Text(label, fontFamily = CuteFont, fontSize = 11.sp, color = InkLight)
    }
}

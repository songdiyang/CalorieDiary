package com.calorie.diary.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorie.diary.R
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.OrangeDeep
import com.calorie.diary.ui.theme.YellowSoft

@Composable
internal fun CloudLoginPanel(onLogin: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(YellowSoft)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.game_login_required),
            fontFamily = CuteFont, fontSize = 13.sp, color = Ink,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.game_login_action),
            fontFamily = CuteFont, fontSize = 12.sp, color = OrangeDeep,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(CardWhite)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onLogin
                )
                .padding(horizontal = 12.dp, vertical = 9.dp)
        )
    }
}

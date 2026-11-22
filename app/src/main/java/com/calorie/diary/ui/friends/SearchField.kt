package com.calorie.diary.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Hairline
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.Orange

/** 圆角搜索输入框，消息页与搜索好友页共用。 */
@Composable
internal fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardWhite)
            .border(1.dp, Hairline, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(fontFamily = CuteFont, color = Ink, fontSize = 14.sp),
            cursorBrush = SolidColor(Orange),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = if (focusRequester != null) {
                Modifier.fillMaxWidth().focusRequester(focusRequester)
            } else {
                Modifier.fillMaxWidth()
            },
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, fontFamily = CuteFont, fontSize = 13.sp, color = InkLight)
                }
                inner()
            }
        )
    }
}

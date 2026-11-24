package com.calorie.diary.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp

/**
 * 输入框里的 emoji 用更大的字号渲染，让表情像“表情”而不是普通文字。
 * 只改变显示样式，不改变实际文本与光标位置。
 */
object EmojiInputVisualTransformation : VisualTransformation {

    private const val EMOJI_FONT_SIZE_SP = 20f

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isEmpty()) return TransformedText(text, OffsetMapping.Identity)

        val styled = buildAnnotatedString {
            append(raw)
            var index = 0
            while (index < raw.length) {
                val codePoint = Character.codePointAt(raw, index)
                val charCount = Character.charCount(codePoint)
                if (isEmoji(codePoint)) {
                    addStyle(
                        SpanStyle(fontSize = EMOJI_FONT_SIZE_SP.sp),
                        index,
                        (index + charCount).coerceAtMost(raw.length)
                    )
                }
                index += charCount
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }

    private fun isEmoji(codePoint: Int): Boolean =
        codePoint in 0x1F000..0x1FAFF ||
            codePoint in 0x2600..0x27BF ||
            codePoint in 0x2B00..0x2BFF ||
            codePoint in 0xFE00..0xFE0F
}
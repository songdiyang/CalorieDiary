package com.calorie.diary.ui.components

import androidx.annotation.DrawableRes
import com.calorie.diary.R
import com.calorie.diary.data.ChatEmojis

/**
 * 聊天表情 -> 手绘贴纸资源映射。
 * 与 [ChatEmojis] 中的字符串一一对应；找不到时由调用方回退为原 emoji 文本。
 */
object ChatEmojiArt {

    private val map: Map<String, Int> = mapOf(
        "😀" to R.drawable.emoji_grin,
        "😄" to R.drawable.emoji_grin_big,
        "😂" to R.drawable.emoji_laugh,
        "😁" to R.drawable.emoji_chuckle,
        "😊" to R.drawable.emoji_smile,
        "😆" to R.drawable.emoji_giggle,
        "😍" to R.drawable.emoji_heart_eyes,
        "😎" to R.drawable.emoji_cool,
        "🤔" to R.drawable.emoji_thinking,
        "😴" to R.drawable.emoji_sleepy,
        "😭" to R.drawable.emoji_cry,
        "😡" to R.drawable.emoji_angry,
        "🙂" to R.drawable.emoji_slight_smile,
        "🤗" to R.drawable.emoji_hug,
        "😅" to R.drawable.emoji_sweat,
        "😇" to R.drawable.emoji_angel,
        "🙃" to R.drawable.emoji_flip,
        "😉" to R.drawable.emoji_wink,
        "👍" to R.drawable.emoji_thumbs_up,
        "👏" to R.drawable.emoji_clap,
        "🙏" to R.drawable.emoji_pray,
        "💪" to R.drawable.emoji_muscle,
        "❤️" to R.drawable.emoji_heart,
        "💔" to R.drawable.emoji_broken_heart,
        "🔥" to R.drawable.emoji_fire,
        "✨" to R.drawable.emoji_sparkle,
        "🎉" to R.drawable.emoji_party,
        ChatEmojis.RICE to R.drawable.emoji_rice
    )

    @DrawableRes
    fun resId(emoji: String): Int? = map[emoji.trim()]
}

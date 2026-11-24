package com.calorie.diary.data

/** 聊天表情表：rice 为特殊表情，发送后触发全屏米饭特效。 */
object ChatEmojis {
    const val RICE = "🍚"

    val all: List<String> = listOf(
        "😀", "😄", "😂", "😁", "😊", "😆", "😍",
        "😎", "🤔", "😴", "😭", "😡", "🙂", "🤗",
        "😅", "😇", "🙃", "😉", "👍", "👏", "🙏",
        "💪", "❤️", "💔", "🔥", "✨", "🎉", RICE
    )

    fun isRice(text: String): Boolean = text.trim() == RICE
}
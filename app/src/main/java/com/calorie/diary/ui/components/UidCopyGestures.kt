package com.calorie.diary.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.calorie.diary.data.UiMessenger

/**
 * UID 文本的点按与长按复制。onTap 为空时点按也复制；长按始终复制。
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.uidCopyGestures(
    uid: String,
    clipboardManager: ClipboardManager,
    copiedHint: String,
    onTap: (() -> Unit)? = null
): Modifier = combinedClickable(
    interactionSource = MutableInteractionSource(),
    indication = null,
    onClick = {
        if (onTap != null) onTap() else copyUid(uid, clipboardManager, copiedHint)
    },
    onLongClick = { copyUid(uid, clipboardManager, copiedHint) }
)

private fun copyUid(uid: String, clipboardManager: ClipboardManager, copiedHint: String) {
    if (uid.isBlank()) return
    clipboardManager.setText(AnnotatedString(uid))
    UiMessenger.show(copiedHint, isError = false)
}
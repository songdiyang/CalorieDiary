package com.calorie.diary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.calorie.diary.R
import com.calorie.diary.data.update.UpdateError
import com.calorie.diary.data.update.UpdateInfo
import com.calorie.diary.data.update.UpdateState
import com.calorie.diary.data.update.formatUpdateSize
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Danger
import com.calorie.diary.ui.theme.Hairline
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.NumberFont
import com.calorie.diary.ui.theme.OrangeDeep
import com.calorie.diary.ui.theme.ScreenBg

/** 版本检查 / 下载 / 安装共用的弹窗；设置页与启动自动检查都会复用它。 */
@Composable
fun UpdateDialog(
    state: UpdateState,
    currentVersionName: String,
    currentVersionCode: Int,
    onDismiss: () -> Unit,
    onCheckAgain: () -> Unit,
    onDownload: (UpdateInfo) -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(CardWhite)
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Text(
                text = when (state) {
                    is UpdateState.Available ->
                        stringResource(R.string.update_new_version_title, state.info.versionName)
                    is UpdateState.Downloading ->
                        stringResource(R.string.update_downloading_title)
                    is UpdateState.Ready -> stringResource(R.string.update_ready_title)
                    is UpdateState.Failed -> stringResource(R.string.update_failed_title)
                    is UpdateState.Checking -> stringResource(R.string.update_checking_title)
                    UpdateState.UpToDate, UpdateState.Idle ->
                        stringResource(R.string.update_up_to_date_title)
                },
                fontFamily = CuteFont,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                color = Ink
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(
                    R.string.update_current_version,
                    currentVersionName,
                    currentVersionCode
                ),
                fontFamily = CuteFont,
                fontSize = 12.sp,
                color = InkLight
            )
            Spacer(Modifier.height(14.dp))

            when (state) {
                is UpdateState.Checking -> CheckStatus()
                UpdateState.Idle, UpdateState.UpToDate -> Text(
                    stringResource(R.string.update_up_to_date_body),
                    fontFamily = CuteFont,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = InkLight
                )
                is UpdateState.Available -> AvailableContent(state.info)
                is UpdateState.Downloading -> DownloadingContent(
                    state = state,
                    onCancelDownload = onCancelDownload
                )
                is UpdateState.Ready -> ReadyContent(state)
                is UpdateState.Failed -> FailedContent(state.error)
            }

            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DialogSecondaryAction(
                    text = if (state is UpdateState.Checking ||
                        state is UpdateState.UpToDate ||
                        state is UpdateState.Failed
                    ) {
                        stringResource(R.string.common_close)
                    } else {
                        stringResource(R.string.update_later)
                    },
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                when (state) {
                    is UpdateState.Available -> {
                        Spacer(Modifier.width(12.dp))
                        CuteButton(
                            text = stringResource(R.string.update_install_now),
                            onClick = { onDownload(state.info) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    is UpdateState.Ready -> {
                        Spacer(Modifier.width(12.dp))
                        CuteButton(
                            text = stringResource(R.string.update_install),
                            onClick = onInstall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    is UpdateState.Failed -> {
                        Spacer(Modifier.width(12.dp))
                        CuteButton(
                            text = stringResource(R.string.update_retry),
                            onClick = {
                                val info = state.info
                                if (info != null) onDownload(info) else onCheckAgain()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun CheckStatus() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = OrangeDeep
        )
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.update_checking_hint),
            fontFamily = CuteFont,
            fontSize = 14.sp,
            color = InkLight
        )
    }
}

@Composable
private fun AvailableContent(info: UpdateInfo) {
    val size = info.sizeLabel.ifBlank {
        if (info.sizeBytes > 0L) formatUpdateSize(info.sizeBytes) else ""
    }
    val meta = listOfNotNull(
        if (size.isNotBlank()) stringResource(R.string.update_size_label, size) else null,
        info.releasedAt?.let { stringResource(R.string.update_release_date, it) }
    ).joinToString(" · ")
    if (meta.isNotBlank()) {
        Text(meta, fontFamily = CuteFont, fontSize = 12.sp, color = InkLight)
        Spacer(Modifier.height(10.dp))
    }
    if (info.releaseNotes.isNotEmpty()) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ScreenBg)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                stringResource(R.string.update_release_notes),
                fontFamily = CuteFont,
                fontSize = 12.sp,
                color = Ink
            )
            info.releaseNotes.forEach { note ->
                Spacer(Modifier.height(4.dp))
                Text(
                    "· $note",
                    fontFamily = CuteFont,
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    color = InkLight
                )
            }
        }
    }
}

@Composable
private fun DownloadingContent(state: UpdateState.Downloading, onCancelDownload: () -> Unit) {
    val percent = state.percent
    if (percent != null) {
        LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50)),
            color = OrangeDeep,
            trackColor = Hairline
        )
    } else {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50)),
            color = OrangeDeep,
            trackColor = Hairline
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = if (percent != null) {
            stringResource(
                R.string.update_progress,
                percent,
                formatUpdateSize(state.bytesRead),
                formatUpdateSize(state.totalBytes)
            )
        } else {
            stringResource(R.string.update_downloaded, formatUpdateSize(state.bytesRead))
        },
        fontFamily = NumberFont,
        fontSize = 12.sp,
        color = Ink
    )
    Spacer(Modifier.height(6.dp))
    Text(
        stringResource(R.string.update_download_hint),
        fontFamily = CuteFont,
        fontSize = 12.sp,
        lineHeight = 19.sp,
        color = InkLight
    )
    Text(
        stringResource(R.string.update_cancel_download),
        fontFamily = CuteFont,
        fontSize = 13.sp,
        color = OrangeDeep,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCancelDownload
            )
            .padding(top = 10.dp, bottom = 2.dp)
    )
}

@Composable
private fun ReadyContent(state: UpdateState.Ready) {
    Text(
        stringResource(R.string.update_ready_body),
        fontFamily = CuteFont,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        color = InkLight
    )
    Spacer(Modifier.height(8.dp))
    Text(
        formatUpdateSize(state.apk.length()),
        fontFamily = NumberFont,
        fontSize = 12.sp,
        color = InkLight
    )
}

@Composable
private fun FailedContent(error: UpdateError) {
    Text(
        text = stringResource(
            when (error) {
                UpdateError.NETWORK -> R.string.update_failed_network
                UpdateError.DOWNLOAD -> R.string.update_failed_download
                UpdateError.CHECKSUM -> R.string.update_failed_checksum
                UpdateError.APK_INVALID -> R.string.update_failed_apk_invalid
            }
        ),
        fontFamily = CuteFont,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        color = Danger
    )
}

@Composable
private fun DialogSecondaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .clip(shape)
            .border(1.dp, Hairline, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontFamily = CuteFont, fontSize = 15.sp, color = Ink)
    }
}
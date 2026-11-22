package com.calorie.diary.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 消息页「+」下拉菜单用的黑色线条图标。
 * 只用描边不填充，圆头圆角，由调用方用 tint 染成墨色，直接放在白色菜单卡上。
 */
object MenuLineIcons {

    /** 双人线条：发起群聊。 */
    val Group: ImageVector by lazy {
        lineIcon(
            name = "MenuLineGroup",
            // 前一个人的头
            "M8.5 5 C10.27 5 11.7 6.43 11.7 8.2 C11.7 9.97 10.27 11.4 8.5 11.4 " +
                "C6.73 11.4 5.3 9.97 5.3 8.2 C5.3 6.43 6.73 5 8.5 5 Z",
            // 前一个人的肩
            "M3.2 19.8 V18.6 C3.2 16.06 5.26 14 7.8 14 H9.4 C11.94 14 14 16.06 14 18.6 V19.8",
            // 后一个人的头
            "M16.6 5.4 C18.15 5.4 19.4 6.65 19.4 8.2 C19.4 9.75 18.15 11 16.6 11 " +
                "C15.05 11 13.8 9.75 13.8 8.2 C13.8 6.65 15.05 5.4 16.6 5.4 Z",
            // 后一个人的肩
            "M15.4 19.8 C15.4 17.2 16.9 15.4 18.6 15.4 C20.3 15.4 21.8 17.2 21.8 19.8"
        )
    }

    /** 单人线条：添加好友。 */
    val Person: ImageVector by lazy {
        lineIcon(
            name = "MenuLinePerson",
            "M12 3.8 C13.99 3.8 15.6 5.41 15.6 7.4 C15.6 9.39 13.99 11 12 11 " +
                "C10.01 11 8.4 9.39 8.4 7.4 C8.4 5.41 10.01 3.8 12 3.8 Z",
            "M5.6 19.8 V18.4 C5.6 15.86 7.66 13.8 10.2 13.8 H13.8 " +
                "C16.34 13.8 18.4 15.86 18.4 18.4 V19.8"
        )
    }

    /** 钥匙线条：添加群聊（群号即钥匙）。 */
    val Key: ImageVector by lazy {
        lineIcon(
            name = "MenuLineKey",
            // 钥匙环
            "M6.4 8.1 C8.55 8.1 10.3 9.85 10.3 12 C10.3 14.15 8.55 15.9 6.4 15.9 " +
                "C4.25 15.9 2.5 14.15 2.5 12 C2.5 9.85 4.25 8.1 6.4 8.1 Z",
            // 钥匙杆
            "M10.3 12 H21",
            // 两段齿
            "M17.4 12 V15.6",
            "M20.2 12 V14.8"
        )
    }
}

private fun lineIcon(name: String, vararg pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        pathData.forEach { data ->
            addPath(
                pathData = PathParser().parsePathString(data).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
    }.build()

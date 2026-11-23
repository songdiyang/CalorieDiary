package com.calorie.diary.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import kotlin.math.min

/**
 * 设备倾角（弧度，-1 ~ 1 归一化的倾斜量）。
 * 读取加速度计的水平分量：手机向左倾 x 为负、向右倾 x 为正；
 * 缺少加速度计或平放时返回 0（水平）。
 */
data class TiltState(
    /** 左右倾斜归一化量，范围约 -1f（左）.. 1f（右） */
    val x: Float = 0f,
    /** 前后倾斜归一化量，范围约 -1f（前倾）.. 1f（后仰） */
    val y: Float = 0f
)

/**
 * 注册加速度计监听，把设备姿态转成平滑后的倾角。
 * Compose 重组安全：监听在离开组合时自动注销。
 */
@Composable
fun rememberDeviceTilt(): TiltState {
    val context = LocalContext.current
    var tilt by remember { mutableStateOf(TiltState()) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // 低通滤波后的重力分量，避免手抖动直接传进动画。
        var gravityX = 0f
        var gravityY = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
                val alpha = 0.12f
                gravityX = alpha * event.values[0] + (1 - alpha) * gravityX
                gravityY = alpha * event.values[1] + (1 - alpha) * gravityY
                // 把加速度分量映射成 -1..1 的倾斜量；9.8m/s² 为平放基准。
                val x = (gravityX / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
                val y = (gravityY / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
                tilt = TiltState(x = x, y = y)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (accelerometer != null) {
            sensorManager?.registerListener(
                listener, accelerometer, SensorManager.SENSOR_DELAY_UI
            )
        }

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    return tilt
}

/** 倾角的安全缩放值，用于驱动米粒横向滑动/卡片轻微视差。 */
internal fun TiltState.slanted(maxShift: Float = 1f): Float =
    min(kotlin.math.abs(x), 1f) * maxShift

package com.calorie.diary.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 全局轻提示内容。 */
data class UiMessage(val text: String, val isError: Boolean = true)

/**
 * 进程级轻提示总线。
 *
 * 导航栈中每个路由会各自持有 AppViewModel 实例，若把提示状态放在 ViewModel 内，
 * 只有当前页面的实例能收到消息。这里用单例总线保证任何页面触发的提示都能被
 * 顶层 AppToastHost 消费。
 */
object UiMessenger {

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    fun show(text: String, isError: Boolean = true) {
        _message.value = UiMessage(text, isError)
    }

    fun clear() {
        _message.value = null
    }
}

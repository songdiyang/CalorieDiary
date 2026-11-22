package com.calorie.diary.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 全局下拉手势只负责发布当前路由，各页面决定自己的数据刷新动作。 */
object AppRefreshBus {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun refresh(route: String) {
        _events.tryEmit(route)
    }
}

package com.calorie.diary.data.sync

import android.content.Context
import com.calorie.diary.data.db.AppDatabase
import com.calorie.diary.data.db.UserIdentity

/**
 * 根据用户配置选择云端实现：
 * 未启用远程 / 未配置地址 → 本地模拟实现；
 * 启用并配置 BaseUrl → HTTP REST 实现。
 */
object SyncProvider {

    @Volatile
    private var local: RemoteSyncApi? = null

    fun local(context: Context): RemoteSyncApi {
        return local ?: synchronized(this) {
            val db = AppDatabase.get(context)
            local ?: LocalRemoteSyncApi(db.groupDao(), db.mealDao()).also { local = it }
        }
    }

    fun resolve(context: Context, identity: UserIdentity): RemoteSyncApi {
        val url = identity.serverBaseUrl.trim().trimEnd('/')
        return if (identity.remoteEnabled && url.isNotBlank()) {
            HttpRemoteSyncApi(url)
        } else {
            local(context)
        }
    }
}

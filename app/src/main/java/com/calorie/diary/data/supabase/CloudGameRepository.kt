package com.calorie.diary.data.supabase

import android.content.Context
import com.calorie.diary.data.game.CheckInReward
import com.calorie.diary.data.game.NewbieTaskSyncResult
import com.calorie.diary.data.game.PhotoTaskResult
import com.calorie.diary.data.game.PurchaseResult

class CloudGameRepository private constructor(context: Context) {
    private val auth = SupabaseAuthRepository.get(context)

    suspend fun loadState(): CloudGameState = auth.withValidSession { api, session ->
        api.getGameState(session)
    }

    /** 自动签到：应用打开时调用，服务端保证同一天只发一次并回传实际奖励。 */
    suspend fun claimDailyCheckIn(): CheckInReward = auth.withValidSession { api, session ->
        api.claimGameCheckIn(session)
    }

    /** 每日拍照记录任务：记录一次饮食即完成，每天只发一次经验。 */
    suspend fun completePhotoTask(clientRef: String, date: String): PhotoTaskResult =
        auth.withValidSession { api, session ->
            api.completePhotoTask(session, clientRef, date)
        }

    /** 新手任务：修改用户名 / 首个好友 / 首条饮食记录，由服务端校验后发奖。 */
    suspend fun syncNewbieTasks(): NewbieTaskSyncResult = auth.withValidSession { api, session ->
        api.syncNewbieTasks(session)
    }

    suspend fun purchaseItem(itemId: String): PurchaseResult = auth.withValidSession { api, session ->
        when (api.purchaseGameItem(session, itemId)) {
            "success" -> PurchaseResult.SUCCESS
            "insufficient_coins" -> PurchaseResult.INSUFFICIENT_COINS
            else -> PurchaseResult.UNKNOWN_ITEM
        }
    }

    companion object {
        @Volatile
        private var instance: CloudGameRepository? = null

        fun get(context: Context): CloudGameRepository =
            instance ?: synchronized(this) {
                instance ?: CloudGameRepository(context.applicationContext).also { instance = it }
            }
    }
}

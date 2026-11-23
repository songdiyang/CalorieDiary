package com.calorie.diary.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calorie.diary.R
import com.calorie.diary.data.DateUtils
import com.calorie.diary.data.UiMessage
import com.calorie.diary.data.UiMessenger
import com.calorie.diary.data.db.DateTotal
import com.calorie.diary.data.db.FoodEntity
import com.calorie.diary.data.db.GroupEntity
import com.calorie.diary.data.db.GroupMemberEntity
import com.calorie.diary.data.db.MealEntryEntity
import com.calorie.diary.data.db.MemberTotal
import com.calorie.diary.data.db.UserIdentity
import com.calorie.diary.data.db.UserProfile
import com.calorie.diary.data.db.WaterEntity
import com.calorie.diary.data.game.CheckInReward
import com.calorie.diary.data.game.DAILY_PHOTO_TASK_EXP
import com.calorie.diary.data.game.GameInventoryItem
import com.calorie.diary.data.game.NewbieTaskState
import com.calorie.diary.data.game.PurchaseResult
import com.calorie.diary.data.network.NetworkErrorKind
import com.calorie.diary.data.network.NetworkException
import com.calorie.diary.data.network.NetworkMonitor
import com.calorie.diary.data.network.retryOnTransient
import com.calorie.diary.data.network.toNetworkErrorKind
import com.calorie.diary.data.parser.ParseIntent
import com.calorie.diary.data.parser.ParsedFood
import com.calorie.diary.data.parser.TextParseResult
import com.calorie.diary.data.repo.AppRepository
import com.calorie.diary.data.supabase.CloudGameRepository
import com.calorie.diary.data.supabase.DefaultAvatarCatalog
import com.calorie.diary.data.supabase.SupabaseApiException
import com.calorie.diary.data.supabase.SupabaseAuthRepository
import com.calorie.diary.data.vision.ImageLoadException
import com.calorie.diary.data.vision.ImageUtils
import com.calorie.diary.data.vision.ProxyVisionClient
import com.calorie.diary.data.vision.RecognizedFood
import com.calorie.diary.data.vision.VisionErrorKind
import com.calorie.diary.data.vision.VisionServiceException
import com.calorie.diary.data.vision.VisionState
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GameUiState(
    val coins: Int = 0,
    val exp: Int = 0,
    val level: Int = 1,
    val levelExp: Int = 0,
    val levelNext: Int = 100,
    val dailyTaskDone: Boolean = false,
    val lastCheckInDate: String = "",
    val checkInStreak: Int = 0,
    val checkInCoinReward: Int = 5,
    val checkInExpReward: Int = 10,
    val checkInStreakDays: Int = 7,
    val checkInMultiplier: Int = 2,
    val serverDate: String = "",
    val inventory: List<GameInventoryItem> = emptyList(),
    val newbieTasks: List<NewbieTaskState> = emptyList(),
    val todayFoodCount: Int = 0,
    val requiresLogin: Boolean = false,
    val loading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AppRepository.get(app)
    private val gameRepo = CloudGameRepository.get(app)
    private val authRepo = SupabaseAuthRepository.get(app)

    private fun str(resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)

    /** 全局轻提示：写库失败、网络异常等静默场景的兜底反馈（跨路由共享）。 */
    val uiMessage: StateFlow<UiMessage?> = UiMessenger.message

    fun showMessage(text: String, isError: Boolean = true) = UiMessenger.show(text, isError)

    fun clearUiMessage() = UiMessenger.clear()

    /**
     * 安全启动：本地的数据库/偏好写入失败时不再直接崩溃，转为可感知的提示。
     */
    private fun launchSafe(errorRes: Int, block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            showMessage(str(errorRes))
        }
    }

    val profile: StateFlow<UserProfile?> = repo.profile
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun selectAvatar(avatarId: String) {
        val marker = DefaultAvatarCatalog.marker(avatarId) ?: return
        viewModelScope.launch {
            try {
                repo.setAvatarSelection(avatarId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                showMessage(str(R.string.error_save_failed))
                return@launch
            }
            if (authRepo.account.first() == null) return@launch
            try {
                authRepo.updateAvatar(marker)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                showMessage(str(R.string.profile_avatar_sync_failed))
            }
        }
    }

    private val _selectedDate = MutableStateFlow(DateUtils.todayString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    fun selectDate(date: String) {
        _selectedDate.value = date
    }

    // 当日记录
    val dayEntries: StateFlow<List<MealEntryEntity>> = _selectedDate
        .flatMapLatest { repo.observeEntries(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val dayTotalCalories: StateFlow<Int> = _selectedDate
        .flatMapLatest { repo.observeTotalCalories(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun entriesOfMeal(mealType: Int): Flow<List<MealEntryEntity>> =
        _selectedDate.flatMapLatest { repo.observeEntries(it, mealType) }

    fun entriesOfMeal(date: String, mealType: Int): Flow<List<MealEntryEntity>> =
        repo.observeEntries(date, mealType)

    // 食物
    private val _searchKeyword = MutableStateFlow("")
    val searchKeyword: StateFlow<String> = _searchKeyword.asStateFlow()

    val foods: StateFlow<List<FoodEntity>> = _searchKeyword
        .flatMapLatest { repo.searchFoods(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun updateKeyword(keyword: String) {
        _searchKeyword.value = keyword
    }

    // 月汇总
    val monthTotals: StateFlow<List<DateTotal>> = _selectedDate
        .flatMapLatest { repo.observeMonthTotals(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // 我的页统计概览
    val userStats: StateFlow<UserStats> = combine(
        repo.observeActiveDays(),
        repo.observeEntryCount(),
        repo.observeTotalCaloriesAll(),
        repo.observeRecordedDates()
    ) { activeDays, entryCount, totalCalories, dates ->
        UserStats(
            activeDays = activeDays,
            entryCount = entryCount,
            totalCalories = totalCalories,
            streak = computeStreak(dates.toSet())
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UserStats())

    private fun computeStreak(recorded: Set<String>): Int {
        var anchor = DateUtils.todayString()
        if (anchor !in recorded) {
            // 今天还没记录不视为中断，从昨天起算
            anchor = DateUtils.addDays(anchor, -1)
        }
        var streak = 0
        var day = anchor
        while (day in recorded) {
            streak++
            day = DateUtils.addDays(day, -1)
        }
        return streak
    }

    private val _gameState = MutableStateFlow(GameUiState())
    val gameState: StateFlow<GameUiState> = _gameState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeEntries(DateUtils.todayString()).collect { entries ->
                _gameState.update { it.copy(todayFoodCount = entries.size) }
            }
        }
        viewModelScope.launch {
            authRepo.account.map { it?.userId }.distinctUntilChanged().collect { userId ->
                if (userId == null) {
                    repo.setActiveUser("")
                    _gameState.update {
                        GameUiState(
                            todayFoodCount = it.todayFoodCount,
                            requiresLogin = true,
                            loading = false
                        )
                    }
                } else {
                    repo.setActiveUser(userId)
                    try {
                        repo.prepareAccount(userId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                        showMessage(str(R.string.error_generic))
                    }
                    syncMealEntriesSafely()
                    refreshGameState()
                }
            }
        }
    }

    private fun syncMealEntriesSafely() {
        viewModelScope.launch {
            try {
                repo.syncMealEntries()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // 本地记录保持可用；下次登录或启动时继续重试云端同步。
            }
        }
    }

    fun refreshGameState() {
        viewModelScope.launch {
            _gameState.update { it.copy(loading = true, requiresLogin = false) }
            try {
                val cloud = gameRepo.loadState()
                // 账户切换或退出后，旧请求不能把其他账户的数据写回界面。
                if (authRepo.account.first()?.userId != cloud.userId) return@launch
                _gameState.update {
                    it.copy(
                        coins = cloud.coins,
                        exp = cloud.exp,
                        level = cloud.level,
                        levelExp = cloud.levelExp,
                        levelNext = cloud.levelNext,
                        dailyTaskDone = cloud.dailyTaskDone,
                        lastCheckInDate = cloud.lastCheckInDate,
                        checkInStreak = cloud.checkInStreak,
                        checkInCoinReward = cloud.checkInCoinReward,
                        checkInExpReward = cloud.checkInExpReward,
                        checkInStreakDays = cloud.checkInStreakDays,
                        checkInMultiplier = cloud.checkInMultiplier,
                        serverDate = cloud.serverDate,
                        inventory = cloud.inventory,
                        newbieTasks = cloud.newbieTasks,
                        requiresLogin = false,
                        loading = false
                    )
                }
                // 打开应用即签到：当天还没签过就自动领取，无需手动操作。
                if (cloud.lastCheckInDate != cloud.serverDate) autoDailyCheckIn()
                if (cloud.newbieTasks.any { !it.done }) syncNewbieTasks()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (e is SupabaseApiException && e.statusCode == 401) {
                    _gameState.update {
                        GameUiState(
                            todayFoodCount = it.todayFoodCount,
                            requiresLogin = true,
                            loading = false
                        )
                    }
                } else {
                    _gameState.update { it.copy(loading = false) }
                    showMessage(str(R.string.error_generic))
                }
            }
        }
    }

    /** 自动签到进行中的去重标记：多个页面同时刷新时不重复请求。 */
    private var autoCheckInRunning = false

    private val _checkInReward = MutableStateFlow<CheckInReward?>(null)

    /** 当日签到成功的奖励；非空时全局弹窗展示，必须由用户确认关闭。 */
    val checkInReward: StateFlow<CheckInReward?> = _checkInReward.asStateFlow()

    fun dismissCheckInReward() {
        _checkInReward.value = null
    }

    private fun autoDailyCheckIn() {
        if (autoCheckInRunning) return
        autoCheckInRunning = true
        viewModelScope.launch {
            try {
                val reward = gameRepo.claimDailyCheckIn()
                if (reward.success) {
                    // 签到成功不再一闪而过的轻提示，改为需要用户确认的居中弹窗。
                    _checkInReward.value = reward
                    refreshGameState()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // 静默：离线时下次打开应用或刷新会再次尝试。
            } finally {
                autoCheckInRunning = false
            }
        }
    }

    /** 每日拍照记录任务：记录一次饮食即完成，每天奖励一次经验；失败静默等待下次补发。 */
    private fun completeDailyPhotoTask() {
        viewModelScope.launch {
            if (_gameState.value.requiresLogin || _gameState.value.dailyTaskDone) return@launch
            try {
                // 先取服务端日期，避免设备时区与云端日期不一致。
                val serverDate = gameRepo.loadState().serverDate
                val clientRef = "photo-task-$serverDate-${System.currentTimeMillis()}"
                val result = gameRepo.completePhotoTask(clientRef, serverDate)
                if (result.success) {
                    showMessage(
                        if (result.leveledUp) str(R.string.level_up, result.level)
                        else str(R.string.daily_task_success, DAILY_PHOTO_TASK_EXP),
                        false
                    )
                    refreshGameState()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // 静默：下次记录仍会重试，服务端按天幂等不会重复发奖。
            }
        }
    }

    /** 新手任务去重标记：多个记录入口同时触发时不重复请求。 */
    private var newbieTaskSyncRunning = false

    /** 新手任务：服务端按真实行为判定并幂等发奖，失败静默等待下次刷新。 */
    private fun syncNewbieTasks() {
        if (_gameState.value.requiresLogin || newbieTaskSyncRunning) return
        newbieTaskSyncRunning = true
        viewModelScope.launch {
            try {
                val result = gameRepo.syncNewbieTasks()
                if (result.awarded.isNotEmpty()) {
                    notifyNewbieTaskResult(getApplication(), result)
                    refreshGameState()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // 静默：任务页刷新或下次记录时会再次尝试。
            } finally {
                newbieTaskSyncRunning = false
            }
        }
    }

    /** 记录饮食时每日任务提示会先出现，延迟新手提示避免两条轻提示互相覆盖。 */
    private fun syncNewbieTasksAfterFood() {
        val dailyPending = !_gameState.value.dailyTaskDone
        viewModelScope.launch {
            if (dailyPending) delay(2800)
            syncNewbieTasks()
        }
    }

    fun purchaseItem(itemId: String) = launchSafe(R.string.error_save_failed) {
        when (gameRepo.purchaseItem(itemId)) {
            PurchaseResult.SUCCESS -> showMessage(str(R.string.shop_purchase_success), false)
            PurchaseResult.INSUFFICIENT_COINS -> showMessage(str(R.string.shop_not_enough))
            PurchaseResult.UNKNOWN_ITEM -> showMessage(str(R.string.error_generic))
        }
        refreshGameState()
    }

    // 饮水
    val water: StateFlow<WaterEntity?> = _selectedDate
        .flatMapLatest { repo.observeWater(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun adjustWater(deltaMl: Int) = launchSafe(R.string.error_save_failed) {
        repo.adjustWater(_selectedDate.value, deltaMl)
    }

    // 操作
    fun addEntry(mealType: Int, food: FoodEntity, grams: Int, date: String? = null) =
        launchSafe(R.string.error_save_failed) {
            repo.addEntry(date ?: _selectedDate.value, mealType, food, grams)
            completeDailyPhotoTask()
            syncNewbieTasksAfterFood()
        }

    fun deleteEntry(id: Long) = launchSafe(R.string.error_delete_failed) {
        repo.deleteEntry(id)
    }

    fun updateEntryGrams(entry: MealEntryEntity, grams: Int) =
        launchSafe(R.string.error_save_failed) {
            repo.updateEntryGrams(entry, grams)
        }

    fun addCustomFood(name: String, calories: Int, emoji: String, category: String) =
        launchSafe(R.string.error_save_failed) {
            repo.addCustomFood(name, calories, emoji, category)
        }

    fun setCalorieGoal(value: Int) = launchSafe(R.string.error_save_failed) { repo.setCalorieGoal(value) }
    fun setWaterGoal(value: Int) = launchSafe(R.string.error_save_failed) { repo.setWaterGoal(value) }
    fun setGender(value: String) = launchSafe(R.string.error_save_failed) { repo.setGender(value) }
    fun setHeight(value: Int) = launchSafe(R.string.error_save_failed) { repo.setHeight(value) }
    fun setWeight(value: Float) = launchSafe(R.string.error_save_failed) { repo.setWeight(value) }
    fun setAge(value: Int) = launchSafe(R.string.error_save_failed) { repo.setAge(value) }
    fun setFastingStart(value: Long) = launchSafe(R.string.error_save_failed) { repo.setFastingStart(value) }
    fun setFastingHours(value: Int) = launchSafe(R.string.error_save_failed) { repo.setFastingHours(value) }
    fun setWaterReminder(value: Boolean) = launchSafe(R.string.error_save_failed) { repo.setWaterReminder(value) }

    // 拍照识别
    private val _visionState = MutableStateFlow<VisionState>(VisionState.Idle)
    val visionState: StateFlow<VisionState> = _visionState.asStateFlow()

    fun recognizeImage(context: Context, uri: Uri) = viewModelScope.launch {
        // 防重入：识别中重复点击不重复发请求
        if (_visionState.value == VisionState.Loading) return@launch
        // 无网时直接给出“检查网络”提示，不浪费一次超时等待
        if (!NetworkMonitor.isOnline()) {
            _visionState.value = VisionState.Error(VisionErrorKind.OFFLINE)
            return@launch
        }
        _visionState.value = VisionState.Loading
        val outcome = withContext(Dispatchers.IO) {
            runCatching {
                val session = authRepo.currentSession() ?: throw NetworkException(
                    NetworkErrorKind.AUTH, "登录会话缺失"
                )
                val bytes = ImageUtils.loadJpeg(context.applicationContext, uri)
                // 超时/服务5xx/连接中断自动重试 1 次，鉴权错误和离线不重试
                retryOnTransient(attempts = 2) {
                    ProxyVisionClient.recognize(bytes, session.accessToken)
                }
            }
        }
        outcome.onSuccess { foods ->
            _visionState.value = if (foods.isEmpty()) {
                VisionState.Error(VisionErrorKind.NO_FOOD)
            } else {
                VisionState.Success(foods)
            }
        }.onFailure { e ->
            if (e is CancellationException) throw e
            if (e is NetworkException && e.kind == NetworkErrorKind.AUTH) {
                authRepo.clearSession()
            }
            _visionState.value = VisionState.Error(classifyError(e))
        }
    }

    private fun classifyError(e: Throwable): VisionErrorKind = when {
        e is VisionServiceException -> VisionErrorKind.SERVICE
        e is ImageLoadException -> VisionErrorKind.IMAGE
        e is NetworkException -> when (e.kind) {
            NetworkErrorKind.OFFLINE -> VisionErrorKind.OFFLINE
            NetworkErrorKind.TIMEOUT -> VisionErrorKind.TIMEOUT
            NetworkErrorKind.SERVER -> VisionErrorKind.SERVER
            NetworkErrorKind.RATE_LIMIT -> VisionErrorKind.RATE_LIMIT
            NetworkErrorKind.AUTH -> VisionErrorKind.AUTH
            NetworkErrorKind.BAD_RESPONSE -> VisionErrorKind.UNKNOWN
            else -> VisionErrorKind.NETWORK
        }
        e is IOException ->
            if (NetworkMonitor.isOnline()) VisionErrorKind.NETWORK else VisionErrorKind.OFFLINE
        else -> VisionErrorKind.UNKNOWN
    }

    fun addRecognized(mealType: Int, foods: List<RecognizedFood>, date: String? = null) =
        launchSafe(R.string.error_save_failed) {
            val d = date ?: _selectedDate.value
            // 彩蛋物品（核弹/燃气罐/汽油等）只是趣味提示，绝不写入饮食记录
            val realFoods = foods.filterNot { it.easterEgg }
            realFoods.forEach { f ->
                repo.addQuickEntry(d, mealType, f.name, "🍴", f.grams, f.calories)
            }
            if (realFoods.isNotEmpty()) {
                completeDailyPhotoTask()
                syncNewbieTasksAfterFood()
            }
        }

    fun resetVision() {
        _visionState.value = VisionState.Idle
    }

    suspend fun parseTextWithAi(text: String, availableFoods: List<FoodEntity>): TextParseResult {
        try {
            val session = authRepo.currentSession() ?: throw NetworkException(
                NetworkErrorKind.AUTH, "登录会话缺失"
            )
            return withContext(Dispatchers.IO) {
                retryOnTransient(attempts = 2) {
                    ProxyVisionClient.parseText(text, session.accessToken, availableFoods)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (e is NetworkException && e.kind == NetworkErrorKind.AUTH) {
                authRepo.clearSession()
            }
            throw e
        }
    }

    // 文字输入记录：按意图统一应用解析结果（添加/删除/直接热量）
    fun applyParseResult(
        result: TextParseResult,
        mealType: Int,
        date: String
    ) = launchSafe(R.string.error_save_failed) {
        when (result.intent) {
            ParseIntent.DELETE -> {
                result.removeNames.forEach { name -> repo.deleteByDateAndName(date, name) }
            }
            else -> {
                result.addItems.forEach { p ->
                    repo.addQuickEntry(date, mealType, p.food.name, p.food.emoji, p.totalGrams, p.calories)
                }
                result.directCalories?.let { cal ->
                    repo.addQuickEntry(date, mealType, "手动记录", "🍴", 100, cal)
                }
                if (result.addItems.isNotEmpty() || result.directCalories != null) {
                    completeDailyPhotoTask()
                    syncNewbieTasksAfterFood()
                }
            }
        }
    }

    suspend fun entriesForDate(date: String): List<MealEntryEntity> = repo.entriesForDate(date)

    // —— 多人协作 ——
    val identity: StateFlow<UserIdentity?> = repo.identity
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val groups: StateFlow<List<GroupEntity>> = repo.observeGroups()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _activeGroupId = MutableStateFlow<String?>(null)
    val activeGroupId: StateFlow<String?> = _activeGroupId.asStateFlow()

    fun selectGroup(id: String?) {
        _activeGroupId.value = id
    }

    val groupMembers: StateFlow<List<GroupMemberEntity>> = _activeGroupId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repo.observeMembers(id)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val groupEntries: StateFlow<List<MealEntryEntity>> =
        combine(_activeGroupId, _selectedDate) { id, date -> id to date }
            .flatMapLatest { (id, date) ->
                if (id == null) flowOf(emptyList())
                else repo.observeGroupEntries(id, date)
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val memberTotals: StateFlow<List<MemberTotal>> =
        combine(_activeGroupId, _selectedDate) { id, date -> id to date }
            .flatMapLatest { (id, date) ->
                if (id == null) flowOf(emptyList())
                else repo.observeMemberTotals(id, date)
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _groupMessage = MutableStateFlow<String?>(null)
    val groupMessage: StateFlow<String?> = _groupMessage.asStateFlow()

    fun clearGroupMessage() {
        _groupMessage.value = null
    }

    fun createGroup(name: String, goal: Int) = launchSafe(R.string.error_generic) {
        if (!remoteAvailable()) {
            _groupMessage.value = str(R.string.error_network_offline)
            return@launchSafe
        }
        runCatching { repo.createGroup(name, goal) }
            .onSuccess {
                _activeGroupId.value = it.groupId
                _groupMessage.value = "ok:${it.groupId}"
            }
            .onFailure { e ->
                if (e is CancellationException) throw e
                _groupMessage.value = groupErrorMessage(e)
            }
    }

    fun joinGroup(code: String) = launchSafe(R.string.error_generic) {
        if (!remoteAvailable()) {
            _groupMessage.value = str(R.string.error_network_offline)
            return@launchSafe
        }
        runCatching { repo.joinGroup(code.trim()) }
            .onSuccess {
                _activeGroupId.value = it.groupId
                _groupMessage.value = "ok:${it.groupId}"
            }
            .onFailure { e ->
                if (e is CancellationException) throw e
                _groupMessage.value = groupErrorMessage(e)
            }
    }

    /** 本地模拟模式下无需联网；启用远程同步时要求网络可用。 */
    private fun remoteAvailable(): Boolean =
        identity.value?.remoteEnabled != true || NetworkMonitor.isOnline()

    /** 把技术异常翻译成用户能执行的提示，避免直接暴露 IOException 文本。 */
    private fun groupErrorMessage(e: Throwable): String = when (e.toNetworkErrorKind()) {
        NetworkErrorKind.OFFLINE -> str(R.string.error_network_offline)
        NetworkErrorKind.TIMEOUT -> str(R.string.error_network_timeout)
        NetworkErrorKind.DNS, NetworkErrorKind.CONNECTION -> str(R.string.error_network_unreachable)
        NetworkErrorKind.SERVER -> str(R.string.error_server_busy)
        NetworkErrorKind.RATE_LIMIT -> str(R.string.error_rate_limited)
        NetworkErrorKind.AUTH -> str(R.string.error_auth_failed)
        NetworkErrorKind.NOT_FOUND -> str(R.string.group_not_found)
        else -> str(R.string.error_generic)
    }

    fun leaveActiveGroup() = launchSafe(R.string.error_generic) {
        val id = _activeGroupId.value ?: return@launchSafe
        val me = identity.value?.userId ?: return@launchSafe
        repo.leaveGroup(id, me)
        _activeGroupId.value = null
    }
}

package com.calorie.diary.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.calorie.diary.data.AppRefreshBus
import com.calorie.diary.data.game.NewbieTaskAction
import com.calorie.diary.data.supabase.SupabaseAuthRepository
import com.calorie.diary.ui.account.AccountScreen
import com.calorie.diary.ui.account.RegisterScreen
import com.calorie.diary.ui.bmi.BmiScreen
import com.calorie.diary.ui.calendar.CalendarScreen
import com.calorie.diary.ui.components.AppToastHost
import com.calorie.diary.ui.components.OfflineBanner
import com.calorie.diary.ui.exercise.ExerciseScreen
import com.calorie.diary.ui.fasting.FastingScreen
import com.calorie.diary.ui.friends.FriendChatScreen
import com.calorie.diary.ui.friends.FriendSearchScreen
import com.calorie.diary.ui.friends.FriendsScreen
import com.calorie.diary.ui.friends.GroupAnnouncementScreen
import com.calorie.diary.ui.friends.GroupChatScreen
import com.calorie.diary.ui.friends.GroupInfoScreen
import com.calorie.diary.ui.group.GroupScreen
import com.calorie.diary.ui.home.HomeScreen
import com.calorie.diary.ui.home.appVm
import com.calorie.diary.ui.profile.BackpackScreen
import com.calorie.diary.ui.profile.PersonalPageScreen
import com.calorie.diary.ui.profile.ProfileScreen
import com.calorie.diary.ui.profile.ShopScreen
import com.calorie.diary.ui.profile.TasksScreen
import com.calorie.diary.ui.social.SocialListScreen
import com.calorie.diary.ui.search.FoodSearchScreen
import com.calorie.diary.ui.settings.SettingsScreen
import com.calorie.diary.ui.textlog.TextLogScreen
import com.calorie.diary.R
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.Cream
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.Orange
import com.calorie.diary.ui.theme.OrangeDeep
import com.calorie.diary.ui.theme.YellowSoft
import com.calorie.diary.data.vision.ImageUtils
import com.calorie.diary.ui.vision.RecognitionScreen
import com.calorie.diary.ui.water.WaterScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val pullRefreshState = rememberPullToRefreshState()
    LaunchedEffect(pullRefreshState.isRefreshing) {
        if (pullRefreshState.isRefreshing) {
            AppRefreshBus.refresh(currentRoute ?: Routes.HOME)
            delay(350)
            pullRefreshState.endRefresh()
        }
    }
    val vm = appVm()
    val uiMessage by vm.uiMessage.collectAsState()

    // 相机直拍：在导航层注册，点击中间按钮直接调起系统相机
    val context = LocalContext.current
    val authRepo = remember { SupabaseAuthRepository.get(context) }
    val authAccount by authRepo.account.collectAsState(initial = null)
    var authAccountReady by remember { mutableStateOf(false) }
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(Unit) {
        authRepo.account.first()
        authAccountReady = true
    }
    LaunchedEffect(authAccountReady, authAccount?.userId) {
        if (!authAccountReady) return@LaunchedEffect
        val target = if (authAccount == null) Routes.ACCOUNT else Routes.HOME
        navController.navigate(target) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }
    LaunchedEffect(authAccountReady, authAccount?.userId, currentRoute) {
        if (authAccountReady && authAccount == null &&
            !Routes.isAuthenticationRoute(currentRoute)
        ) {
            navController.navigate(Routes.ACCOUNT) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val showBottomBar = authAccountReady && authAccount != null &&
        BottomTab.entries.any { it.route == currentRoute }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val uri = pendingPhotoUri
        if (ok && uri != null) {
            if (!ImageUtils.isPhotoValid(context, uri)) {
                vm.showMessage(context.getString(R.string.camera_failed))
            } else {
                navController.navigate(Routes.recognition(uri.toString())) {
                    launchSingleTop = true
                }
            }
        }
    }

    fun startCamera() {
        val uri = ImageUtils.newCameraPhotoUri(context, "quick")
        if (uri == null) {
            vm.showMessage(context.getString(R.string.camera_unavailable))
            return
        }
        pendingPhotoUri = uri
        runCatching { cameraLauncher.launch(uri) }
            .onFailure { vm.showMessage(context.getString(R.string.camera_failed)) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            vm.showMessage(context.getString(R.string.camera_permission_denied))
        }
    }

    fun launchCamera() {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            vm.showMessage(context.getString(R.string.camera_not_available))
            return
        }
        if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun openRecognition() {
        val target = if (authAccount == null) Routes.ACCOUNT else Routes.recognition()
        navController.navigate(target) { launchSingleTop = true }
    }

    Column(Modifier.background(com.calorie.diary.ui.theme.ScreenBg)) {
        OfflineBanner()
        Box(
            Modifier
                .weight(1f)
                .nestedScroll(pullRefreshState.nestedScrollConnection)
        ) {
            if (!authAccountReady) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Orange)
                }
            } else {
                NavHost(
                    navController = navController,
                    startDestination = if (authAccount == null) Routes.ACCOUNT else Routes.HOME
                ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onAddFood = { mealType, date ->
                            navController.navigate(Routes.foodSearch(mealType, date))
                        },
                        onOpenTools = { navController.navigate(Routes.TOOLS) }
                    )
                }
                composable(Routes.CALENDAR) {
                    CalendarScreen()
                }
                composable(Routes.TOOLS) {
                    ToolsScreen(
                        onOpenWater = { navController.navigate(Routes.WATER) },
                        onOpenFasting = { navController.navigate(Routes.FASTING) },
                        onOpenBmi = { navController.navigate(Routes.BMI) },
                        onOpenRecognition = { openRecognition() },
                        onOpenExercise = { navController.navigate(Routes.EXERCISE) },
                        onOpenTextLog = { navController.navigate(Routes.TEXTLOG) },
                        onOpenGroup = { navController.navigate(Routes.GROUP) }
                    )
                }
                composable(Routes.FRIENDS) {
                    FriendsScreen(
                        onOpenChat = { navController.navigate(Routes.friendChat(it)) },
                        onOpenGroupChat = { navController.navigate(Routes.groupChat(it)) },
                        onOpenProfile = { navController.navigate(Routes.personalPage(it)) },
                        onOpenSearch = { navController.navigate(Routes.FRIEND_SEARCH) },
                        onLogin = { navController.navigate(Routes.account()) }
                    )
                }
                composable(Routes.FRIEND_SEARCH) {
                    FriendSearchScreen(
                        onBack = { navController.popBackStack() },
                        onOpenChat = { navController.navigate(Routes.friendChat(it)) }
                    )
                }
                composable(Routes.PROFILE) {
                    if (authAccount == null) {
                        AccountScreen(
                            onBack = { navController.popBackStack() },
                            onRegister = { navController.navigate(Routes.REGISTER) }
                        )
                    } else {
                        ProfileScreen(
                            onAvatarClick = { navController.navigate(Routes.personalPage()) },
                            onSettings = { navController.navigate(Routes.SETTINGS) },
                            onAccount = { navController.navigate(Routes.account()) },
                            onOpenTasks = { navController.navigate(Routes.TASKS) },
                            onOpenSocialList = { tab ->
                                navController.navigate(Routes.socialList(authAccount?.userId, tab))
                            }
                        )
                    }
                }
                composable(Routes.TASKS) {
                    TasksScreen(
                        onBack = { navController.popBackStack() },
                        onLogin = { navController.navigate(Routes.account()) },
                        onNewbieAction = { action ->
                            when (action) {
                                NewbieTaskAction.EDIT_NICKNAME ->
                                    navController.navigate(Routes.personalPage()) {
                                        launchSingleTop = true
                                    }
                                NewbieTaskAction.ADD_FRIEND ->
                                    navController.navigate(Routes.FRIEND_SEARCH) {
                                        launchSingleTop = true
                                    }
                                NewbieTaskAction.RECORD_FOOD ->
                                    navController.navigate(Routes.HOME) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                            }
                        }
                    )
                }
                composable(Routes.BACKPACK) {
                    BackpackScreen(
                        onBack = { navController.popBackStack() },
                        onOpenShop = {
                            navController.navigate(Routes.SHOP) { launchSingleTop = true }
                        },
                        onLogin = { navController.navigate(Routes.account()) }
                    )
                }
                composable(Routes.SHOP) {
                    ShopScreen(
                        onBack = { navController.popBackStack() },
                        onLogin = { navController.navigate(Routes.account()) }
                    )
                }
                composable(
                    route = Routes.PERSONAL_PAGE,
                    arguments = listOf(navArgument("userId") { defaultValue = "" })
                ) { entry ->
                    val pageUserId = entry.arguments?.getString("userId").orEmpty()
                    PersonalPageScreen(
                        userId = pageUserId,
                        onBack = { navController.popBackStack() },
                        onLogin = { navController.navigate(Routes.account()) },
                        onOpenSocialList = { tab ->
                            navController.navigate(Routes.socialList(pageUserId, tab))
                        }
                    )
                }
                composable(
                    route = Routes.SOCIAL_LIST,
                    arguments = listOf(
                        navArgument("userId") { defaultValue = "" },
                        navArgument("tab") { defaultValue = "following" }
                    )
                ) { entry ->
                    SocialListScreen(
                        userId = entry.arguments?.getString("userId").orEmpty(),
                        tabValue = entry.arguments?.getString("tab").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onOpenProfile = { id ->
                            navController.navigate(Routes.personalPage(id)) {
                                launchSingleTop = true
                            }
                        },
                        onLogin = { navController.navigate(Routes.account()) }
                    )
                }
                composable(
                    route = Routes.ACCOUNT,
                    arguments = listOf(
                        navArgument("addAccount") {
                            type = NavType.BoolType
                            defaultValue = false
                        }
                    )
                ) { entry ->
                    AccountScreen(
                        onBack = { navController.popBackStack() },
                        onRegister = { navController.navigate(Routes.REGISTER) },
                        addAccount = entry.arguments?.getBoolean("addAccount") == true
                    )
                }
                composable(Routes.REGISTER) {
                    RegisterScreen(
                        onBack = { navController.popBackStack() },
                        onLogin = { navController.popBackStack() }
                    )
                }
                composable(Routes.FRIEND_CHAT) { entry ->
                    FriendChatScreen(
                        friendId = entry.arguments?.getString("friendId").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onOpenPersonal = { navController.navigate(Routes.personalPage(it)) }
                    )
                }
                composable(Routes.GROUP_CHAT) { entry ->
                    val groupId = entry.arguments?.getString("groupId").orEmpty()
                    GroupChatScreen(
                        groupId = groupId,
                        onBack = { navController.popBackStack() },
                        onOpenInfo = { navController.navigate(Routes.groupInfo(groupId)) }
                    )
                }
                composable(Routes.GROUP_INFO) { entry ->
                    val groupId = entry.arguments?.getString("groupId").orEmpty()
                    GroupInfoScreen(
                        groupId = groupId,
                        onBack = { navController.popBackStack() },
                        // 解散/退出后群已不可见，直接回到消息页。
                        onGroupClosed = { navController.popBackStack(Routes.FRIENDS, false) },
                        onOpenAnnouncement = {
                            navController.navigate(Routes.groupAnnouncement(groupId))
                        }
                    )
                }
                composable(Routes.GROUP_ANNOUNCEMENT) { entry ->
                    GroupAnnouncementScreen(
                        groupId = entry.arguments?.getString("groupId").orEmpty(),
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.FOOD_SEARCH) { entry ->
                    val mealType = entry.arguments?.getString("mealType")?.toIntOrNull() ?: 0
                    val date = entry.arguments?.getString("date") ?: ""
                    FoodSearchScreen(
                        mealType = mealType,
                        date = date,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.WATER) { WaterScreen(onBack = { navController.popBackStack() }) }
                composable(Routes.FASTING) { FastingScreen(onBack = { navController.popBackStack() }) }
                composable(Routes.BMI) { BmiScreen(onBack = { navController.popBackStack() }) }
                composable(
                    route = Routes.RECOGNITION,
                    arguments = listOf(
                        navArgument("captureUri") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = ""
                        }
                    )
                ) { entry ->
                    val captureUri = entry.arguments?.getString("captureUri")
                        ?.takeIf { it.isNotBlank() }
                    RecognitionScreen(
                        onBack = { navController.popBackStack() },
                        initialImageUri = captureUri
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onAccount = { navController.navigate(Routes.account()) },
                        onAddAccount = { navController.navigate(Routes.account(addAccount = true)) }
                    )
                }
                composable(Routes.EXERCISE) {
                    ExerciseScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.TEXTLOG) {
                    TextLogScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.GROUP) {
                    GroupScreen(onBack = { navController.popBackStack() })
                }
                }
            }

            // 全局轻提示：保存失败 / 网络异常等不阻断操作的反馈
            AppToastHost(
                message = uiMessage,
                onDismiss = { vm.clearUiMessage() },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        if (showBottomBar) {
            BottomBar(
                currentRoute = currentRoute,
                onSelect = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onCamera = {
                    if (authAccount == null) openRecognition() else launchCamera()
                },
                onTextLog = {
                    navController.navigate(Routes.TEXTLOG) { launchSingleTop = true }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BottomBar(
    currentRoute: String?,
    onSelect: (String) -> Unit,
    onCamera: () -> Unit,
    onTextLog: () -> Unit
) {
    // 闲鱼式底部栏：标签栏与中间凸出按钮分层绘制，按钮不参与栏的高度计算
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(CardWhite)
                .padding(horizontal = 12.dp)
                .padding(top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomTabItem(BottomTab.HOME, currentRoute, onSelect, Modifier.weight(1f))
            BottomTabItem(BottomTab.CALENDAR, currentRoute, onSelect, Modifier.weight(1f))
            // 中间占位：给凸出的拍照按钮留位置
            Spacer(
                Modifier
                    .weight(1f)
                    .height(50.dp)
            )
            BottomTabItem(BottomTab.FRIENDS, currentRoute, onSelect, Modifier.weight(1f))
            BottomTabItem(BottomTab.PROFILE, currentRoute, onSelect, Modifier.weight(1f))
        }

        // 中间凸起拍照按钮：白色托盘与橙色主按钮同心，上半截凸出栏外
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-28).dp)
        ) {
            // 白色托盘：与栏同色的一圈底座，让按钮像嵌在栏上的凹槽里
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(80.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        clip = false,
                        ambientColor = Ink.copy(alpha = 0.18f),
                        spotColor = Ink.copy(alpha = 0.22f)
                    )
                    .clip(CircleShape)
                    .background(CardWhite)
            )
            // 主按钮：阴影营造悬浮感，按下有弹性缩放
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (pressed) 0.9f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "cameraScale"
            )
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .shadow(
                        elevation = 10.dp,
                        shape = CircleShape,
                        clip = false,
                        ambientColor = Ink.copy(alpha = 0.26f),
                        spotColor = Ink.copy(alpha = 0.32f)
                    )
                    .clip(CircleShape)
                    .background(Orange)
                    .combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onCamera,
                        onLongClick = onTextLog,
                        onLongClickLabel = stringResource(R.string.tools_textlog)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(R.drawable.ic_camera_white),
                        contentDescription = stringResource(R.string.rec_take_photo),
                        modifier = Modifier.size(26.dp)
                    )
                    Text(
                        text = stringResource(R.string.rec_take_photo),
                        fontFamily = CuteFont,
                        fontSize = 11.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomTabItem(
    tab: BottomTab,
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = currentRoute == tab.route
    Box(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (selected) com.calorie.diary.ui.theme.Orange.copy(alpha = 0.14f)
                else Color.Transparent
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onSelect(tab.route) }
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(tab.iconRes),
                contentDescription = null,
                modifier = Modifier.size(if (selected) 30.dp else 26.dp)
            )
            Text(
                stringResource(tab.labelRes),
                fontFamily = CuteFont,
                fontSize = 12.sp,
                color = if (selected) OrangeDeep else Ink
            )
        }
    }
}

@Composable
internal fun BottomBarPreviewContent() {
    Column(Modifier.fillMaxWidth()) {
        // 上方留白模拟内容区，便于看到中间凸起按钮
        Box(Modifier.height(28.dp))
        BottomBar(
            currentRoute = Routes.HOME,
            onSelect = {},
            onCamera = {},
            onTextLog = {}
        )
    }
}

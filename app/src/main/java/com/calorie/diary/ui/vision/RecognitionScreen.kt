package com.calorie.diary.ui.vision

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorie.diary.R
import com.calorie.diary.data.network.NetworkMonitor
import com.calorie.diary.data.vision.ImageUtils
import com.calorie.diary.data.vision.RecognizedFood
import com.calorie.diary.data.vision.VisionErrorKind
import com.calorie.diary.data.vision.VisionState
import com.calorie.diary.data.vision.caloriesForGrams
import com.calorie.diary.ui.components.CuteButton
import com.calorie.diary.ui.components.CuteChip
import com.calorie.diary.ui.components.FoodIcon
import com.calorie.diary.ui.components.SoftCard
import com.calorie.diary.ui.components.localizedFoodName
import com.calorie.diary.ui.components.openNetworkSettings
import com.calorie.diary.ui.home.appVm
import com.calorie.diary.ui.theme.CardWhite
import com.calorie.diary.ui.theme.Cream
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Danger
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight
import com.calorie.diary.ui.theme.NumberFont
import com.calorie.diary.ui.theme.Orange
import com.calorie.diary.ui.theme.OrangeDeep
import com.calorie.diary.ui.theme.YellowSoft
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val mealTitleRes = listOf(
    R.string.meal_breakfast, R.string.meal_lunch,
    R.string.meal_dinner, R.string.meal_snack
)

@Composable
fun RecognitionScreen(
    onBack: () -> Unit,
    initialImageUri: String? = null
) {
    val vm = appVm()
    val context = LocalContext.current
    val visionState by vm.visionState.collectAsState()
    val online by NetworkMonitor.online.collectAsState()

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var mealType by remember { mutableStateOf(defaultMeal()) }
    val checked = remember { mutableStateMapOf<Int, Boolean>() }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewFailed by remember { mutableStateOf(false) }

    // 从底部快捷相机进入：直接加载已拍照片并开始识别
    LaunchedEffect(initialImageUri) {
        val raw = initialImageUri?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val parsed = runCatching { Uri.parse(raw) }.getOrNull() ?: return@LaunchedEffect
        imageUri = parsed
        vm.recognizeImage(context, parsed)
    }

    // 解码拍照预览：限制长边省内存，并按 EXIF 摆正方向（相机照片常以横向存储）
    LaunchedEffect(imageUri) {
        checked.clear()
        previewBitmap = null
        previewFailed = false
        val uri = imageUri ?: return@LaunchedEffect
        val decoded = withContext(Dispatchers.Default) {
            runCatching { ImageUtils.loadDisplayBitmap(context, uri) }
        }
        decoded.onSuccess { previewBitmap = it }.onFailure {
            previewFailed = true
            vm.showMessage(context.getString(R.string.rec_image_load_failed))
        }
    }

    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val uri = pendingPhotoUri
        if (ok && uri != null) {
            if (!ImageUtils.isPhotoValid(context, uri)) {
                vm.showMessage(context.getString(R.string.camera_failed))
            } else {
                imageUri = uri
                vm.recognizeImage(context, uri)
            }
        }
    }

    fun startCamera() {
        val uri = ImageUtils.newCameraPhotoUri(context, "photo")
        if (uri == null) {
            vm.showMessage(context.getString(R.string.camera_unavailable))
            return
        }
        pendingPhotoUri = uri
        runCatching { cameraLauncher.launch(uri) }
            .onFailure { vm.showMessage(context.getString(R.string.camera_failed)) }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) { imageUri = uri; vm.recognizeImage(context, uri) } }

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

    fun launchGallery() {
        galleryLauncher.launch(
            androidx.activity.result.PickVisualMediaRequest(
                ActivityResultContracts.PickVisualMedia.ImageOnly
            )
        )
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackArrow(onBack)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.rec_title), fontFamily = CuteFont, fontSize = 22.sp, color = Ink)
        }

        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CaptureButton(stringResource(R.string.rec_take_photo), Modifier.weight(1f)) { launchCamera() }
            CaptureButton(stringResource(R.string.rec_pick_gallery), Modifier.weight(1f)) { launchGallery() }
        }

        imageUri?.let {
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardWhite),
                contentAlignment = Alignment.Center
            ) {
                val bmp = previewBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (previewFailed) {
                    Text(
                        stringResource(R.string.rec_preview_failed),
                        fontFamily = CuteFont, fontSize = 13.sp, color = InkLight
                    )
                } else {
                    CircularProgressIndicator(color = Orange)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        when (val state = visionState) {
            VisionState.Loading -> LoadingCard()
            is VisionState.Error -> ErrorCard(state.kind) {
                imageUri?.let { vm.recognizeImage(context, it) }
            }
            is VisionState.Success -> {
                val egg = state.foods.firstOrNull { it.easterEgg }
                if (egg != null) {
                    // 彩蛋物品：只看不吃，不提供“确认添加”入口
                    EasterEggCard(
                        name = egg.name,
                        onRetake = { launchCamera() },
                        onDismiss = {
                            vm.resetVision()
                            imageUri = null
                        }
                    )
                } else {
                    ResultCard(
                        foods = state.foods,
                        checked = checked,
                        mealType = mealType,
                        onMealChange = { mealType = it },
                        onConfirm = { adjustedFoods ->
                            vm.addRecognized(mealType, adjustedFoods)
                            vm.resetVision()
                            imageUri = null
                            onBack()
                        }
                    )
                }
            }
            else -> if (online) HintCard() else OfflineHintCard()
        }
    }
}

private data class EditableRecognizedFood(
    val original: RecognizedFood,
    val gramsText: String,
    val caloriesText: String
) {
    fun toFoodOrNull(): RecognizedFood? {
        val grams = gramsText.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val calories = caloriesText.toIntOrNull()?.takeIf { it >= 0 } ?: return null
        return RecognizedFood(original.name, grams, calories)
    }
}

@Composable
private fun ResultCard(
    foods: List<RecognizedFood>,
    checked: MutableMap<Int, Boolean>,
    mealType: Int,
    onMealChange: (Int) -> Unit,
    onConfirm: (List<RecognizedFood>) -> Unit
) {
    var edits by remember(foods) {
        mutableStateOf(
            foods.map {
                EditableRecognizedFood(it, it.grams.toString(), it.calories.toString())
            }
        )
    }
    val parsedFoods = edits.map { it.toFoodOrNull() }
    val selectedFoods = parsedFoods.filterIndexed { i, _ -> checked[i] != false }
    val total = selectedFoods.sumOf { it?.calories ?: 0 }
    val canConfirm = selectedFoods.isNotEmpty() && selectedFoods.all { it != null }

    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        fill = CardWhite,
        contentPadding = PaddingValues(16.dp)
    ) {
        Column {
            Text(stringResource(R.string.rec_result), fontFamily = CuteFont, fontSize = 17.sp, color = Ink)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.rec_adjust_hint),
                fontFamily = CuteFont, fontSize = 11.sp, color = InkLight
            )
            Spacer(Modifier.height(10.dp))
            foods.forEachIndexed { index, food ->
                val isChecked = checked[index] != false
                val edit = edits[index]
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { checked[index] = !isChecked }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FoodIcon(name = food.name, fallbackEmoji = "🍴", size = 40.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            localizedFoodName(food.name),
                            modifier = Modifier.weight(1f),
                            fontFamily = CuteFont, fontSize = 15.sp, color = Ink
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (isChecked) "☑" else "☐", fontSize = 18.sp, color = Orange)
                    }
                    if (isChecked) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 50.dp, top = 4.dp, end = 28.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            NumberInputField(
                                label = stringResource(R.string.rec_grams_label),
                                unit = stringResource(R.string.unit_gram),
                                value = edit.gramsText,
                                onValueChange = { value ->
                                    val newGrams = value.toIntOrNull()
                                    edits = edits.toMutableList().also { items ->
                                        items[index] = items[index].copy(
                                            gramsText = value,
                                            caloriesText = newGrams
                                                ?.let { food.caloriesForGrams(it).toString() }
                                                .orEmpty()
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            NumberInputField(
                                label = stringResource(R.string.rec_calories_label),
                                unit = stringResource(R.string.unit_kcal),
                                value = edit.caloriesText,
                                onValueChange = { value ->
                                    edits = edits.toMutableList().also { items ->
                                        items[index] = items[index].copy(caloriesText = value)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.rec_add_to), fontFamily = CuteFont, fontSize = 14.sp, color = Ink)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                mealTitleRes.forEachIndexed { i, res ->
                    CuteChip(stringResource(res), mealType == i, { onMealChange(i) })
                }
            }

            if (selectedFoods.any { it == null }) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.rec_invalid_adjustment),
                    fontFamily = CuteFont, fontSize = 11.sp, color = OrangeDeep
                )
            }
            Spacer(Modifier.height(16.dp))
            CuteButton(
                text = stringResource(R.string.rec_confirm_total, total),
                onClick = { if (canConfirm) onConfirm(selectedFoods.filterNotNull()) },
                modifier = Modifier.fillMaxWidth(),
                enabled = canConfirm
            )
        }
    }
}

@Composable
private fun NumberInputField(
    label: String,
    unit: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(label, fontFamily = CuteFont, fontSize = 11.sp, color = InkLight)
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Cream)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = { input ->
                    onValueChange(input.filter { it.isDigit() }.take(5))
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(fontFamily = NumberFont, fontSize = 14.sp, color = Ink),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                cursorBrush = SolidColor(Orange)
            )
            Spacer(Modifier.width(4.dp))
            Text(unit, fontFamily = CuteFont, fontSize = 10.sp, color = InkLight)
        }
    }
}

@Composable
private fun LoadingCard() {
    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        fill = CardWhite,
        contentPadding = PaddingValues(28.dp)
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = Orange)
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.rec_loading),
                fontFamily = CuteFont, fontSize = 14.sp, color = InkLight
            )
        }
    }
}

@Composable
private fun ErrorCard(kind: VisionErrorKind, onRetry: () -> Unit) {
    val context = LocalContext.current
    val messageRes = when (kind) {
        VisionErrorKind.NO_FOOD -> R.string.rec_no_food
        VisionErrorKind.OFFLINE -> R.string.rec_offline_error
        VisionErrorKind.TIMEOUT -> R.string.rec_timeout_error
        VisionErrorKind.NETWORK -> R.string.rec_network_error
        VisionErrorKind.SERVER -> R.string.rec_server_error
        VisionErrorKind.SERVICE -> R.string.rec_service_error
        VisionErrorKind.AUTH -> R.string.rec_auth_required
        VisionErrorKind.RATE_LIMIT -> R.string.rec_rate_limit_error
        VisionErrorKind.IMAGE -> R.string.rec_image_error
        VisionErrorKind.UNKNOWN -> R.string.rec_generic_error
    }
    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        fill = CardWhite,
        contentPadding = PaddingValues(20.dp)
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(messageRes),
                fontFamily = CuteFont, fontSize = 14.sp, color = OrangeDeep,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (kind == VisionErrorKind.OFFLINE) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.rec_offline_tip),
                    fontFamily = CuteFont, fontSize = 12.sp, color = InkLight,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            Spacer(Modifier.height(12.dp))
            CuteButton(stringResource(R.string.rec_retry), onRetry)
            if (kind == VisionErrorKind.OFFLINE) {
                Spacer(Modifier.height(10.dp))
                CuteButton(
                    text = stringResource(R.string.net_open_settings),
                    onClick = { openNetworkSettings(context) },
                    containerColor = CardWhite,
                    contentColor = OrangeDeep,
                    shadowColor = Ink.copy(alpha = 0.35f),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp)
                )
            }
        }
    }
}

/** 彩蛋物品（核弹、燃气罐、汽油…）：只做趣味展示，绝不写入饮食记录。 */
@Composable
private fun EasterEggCard(name: String, onRetake: () -> Unit, onDismiss: () -> Unit) {
    val (emoji, labelRes) = easterEggLook(name)
    val transition = rememberInfiniteTransition(label = "easterEgg")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(tween(480), RepeatMode.Reverse),
        label = "pulse"
    )
    val tilt by transition.animateFloat(
        initialValue = -7f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(tween(760), RepeatMode.Reverse),
        label = "tilt"
    )
    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        fill = YellowSoft,
        contentPadding = PaddingValues(24.dp)
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                emoji,
                fontSize = 64.sp,
                modifier = Modifier.graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                    rotationZ = tilt
                }
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.rec_easter_title),
                fontFamily = CuteFont, fontSize = 20.sp, color = Danger
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(labelRes),
                fontFamily = CuteFont, fontSize = 28.sp, color = Ink
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "∞ ${stringResource(R.string.unit_kcal)}",
                fontFamily = NumberFont, fontSize = 32.sp, color = OrangeDeep
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.rec_easter_desc),
                fontFamily = CuteFont, fontSize = 12.sp, color = InkLight,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            CuteButton(
                text = stringResource(R.string.rec_easter_ok),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.rec_easter_retake),
                fontFamily = CuteFont, fontSize = 13.sp, color = OrangeDeep,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null, onClick = onRetake
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.rec_easter_disclaimer),
                fontFamily = CuteFont, fontSize = 10.sp, color = InkLight
            )
        }
    }
}

/** 根据模型返回的物品名匹配本地化名称与图标（模型名称可能是核弹/导弹、煤气罐等等）。 */
private fun easterEggLook(name: String): Pair<String, Int> = when {
    listOf("核", "导弹", "nuke", "bomb").any { name.contains(it, ignoreCase = true) } ->
        "☢️" to R.string.rec_easter_nuke
    listOf("燃气", "煤气", "液化气", "gas").any { name.contains(it, ignoreCase = true) } ->
        "🔥" to R.string.rec_easter_gas
    listOf("汽油", "燃油", "油桶", "gasoline", "petrol", "fuel")
        .any { name.contains(it, ignoreCase = true) } ->
        "⛽" to R.string.rec_easter_fuel
    else -> "⚠️" to R.string.rec_easter_unknown
}

@Composable
private fun HintCard() {
    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        fill = CardWhite,
        contentPadding = PaddingValues(24.dp)
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.icon_camera),
                contentDescription = null,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.rec_hint),
                fontFamily = CuteFont, fontSize = 13.sp, color = InkLight
            )
        }
    }
}

/** 离线时的占位提示：进入页面就能看到原因与去设置入口，不用等识别失败。 */
@Composable
private fun OfflineHintCard() {
    val context = LocalContext.current
    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        fill = CardWhite,
        contentPadding = PaddingValues(24.dp)
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("📶", fontSize = 30.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.rec_offline_error),
                fontFamily = CuteFont, fontSize = 14.sp, color = OrangeDeep,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.rec_offline_tip),
                fontFamily = CuteFont, fontSize = 12.sp, color = InkLight,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            CuteButton(
                text = stringResource(R.string.net_open_settings),
                onClick = { openNetworkSettings(context) },
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun CaptureButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Orange)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, onClick = onClick
            )
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontFamily = CuteFont, fontSize = 16.sp, color = androidx.compose.ui.graphics.Color.White)
    }
}

private fun defaultMeal(): Int {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (h) {
        in 5..9 -> 0
        in 10..13 -> 1
        in 14..17 -> 3
        else -> 2
    }
}

@Composable
private fun BackArrow(onBack: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null, onClick = onBack
        ).padding(8.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.icon_back),
            contentDescription = null,
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
internal fun RecognitionPreviewContent() {
    val foods = listOf(
        RecognizedFood("米饭", 200, 232),
        RecognizedFood("番茄炒蛋", 150, 120),
        RecognizedFood("西兰花", 100, 36)
    )
    val checked = remember { mutableStateMapOf<Int, Boolean>() }
    Column {
        ResultCard(
            foods = foods,
            checked = checked,
            mealType = 1,
            onMealChange = {},
            onConfirm = {}
        )
        Spacer(Modifier.height(12.dp))
        LoadingCard()
        Spacer(Modifier.height(12.dp))
        HintCard()
    }
}

@Composable
internal fun RecognitionEasterEggPreviewContent() {
    Column {
        EasterEggCard(name = "核弹", onRetake = {}, onDismiss = {})
        Spacer(Modifier.height(12.dp))
        EasterEggCard(name = "煤气罐", onRetake = {}, onDismiss = {})
        Spacer(Modifier.height(12.dp))
        EasterEggCard(name = "汽油桶", onRetake = {}, onDismiss = {})
    }
}

@Composable
internal fun RecognitionErrorPreviewContent() {
    Column {
        VisionErrorKind.values().forEachIndexed { index, kind ->
            ErrorCard(kind = kind, onRetry = {})
            if (index < VisionErrorKind.values().lastIndex) {
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}


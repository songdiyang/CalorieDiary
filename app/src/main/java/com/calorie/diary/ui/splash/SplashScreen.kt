package com.calorie.diary.ui.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorie.diary.R
import com.calorie.diary.ui.theme.Cream
import com.calorie.diary.ui.theme.CuteFont
import com.calorie.diary.ui.theme.Ink
import com.calorie.diary.ui.theme.InkLight

@Composable
fun SplashScreen() {
    SplashContent()
}

@Composable
internal fun SplashContent(animate: Boolean = true) {
    var visible by remember { mutableStateOf(!animate) }
    LaunchedEffect(animate) {
        if (animate) visible = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(650, easing = FastOutSlowInEasing),
        label = "splashAlpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.88f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "splashScale"
    )

    Box(Modifier.fillMaxSize().background(Cream)) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_fox),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier
                    .size(112.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
            )
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.app_name),
                fontFamily = CuteFont, fontSize = 27.sp, color = Ink,
                modifier = Modifier.graphicsLayer { this.alpha = alpha }
            )
        }
        Text(
            stringResource(R.string.splash_ai_support),
            fontFamily = CuteFont, fontSize = 12.sp, color = InkLight,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 38.dp)
        )
    }
}

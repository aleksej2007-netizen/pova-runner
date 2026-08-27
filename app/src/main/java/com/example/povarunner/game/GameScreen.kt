package com.example.povarunner.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive

private val SKY_TOP = Color(0xFF4FA8FF)
private val SKY_HORIZON = Color(0xFFBFE3FF)
private val ROAD_COLOR = Color(0xFF3A3F4B)
private val ROAD_LINE = Color(0xFF5A6072)
private val HEAT_COLOR = Color(0xFFFF5A36)
private val HEAT_DARK = Color(0xFFB33A1F)
private val PHONE_BODY = Color(0xFF20242C)
private val PHONE_SCREEN = Color(0xFF4FD1FF)
private val GAME_ICON_COLOR = Color(0xFF7C4DFF)
private val GAME_ICON_DARK = Color(0xFF4A2C9E)

@Composable
fun GameScreen() {
    val engine = remember { GameEngine() }
    var groundYState by remember { mutableStateOf(0f) }
    var screenW by remember { mutableStateOf(0f) }
    var screenH by remember { mutableStateOf(0f) }
    var started by remember { mutableStateOf(false) }
    var restartKey by remember { mutableStateOf(0) }
    var bestScore by remember { mutableStateOf(0) }

    LaunchedEffect(started, restartKey) {
        if (!started) return@LaunchedEffect
        engine.start()
        var lastFrame = withFrameNanos { it }
        while (isActive && !engine.isGameOver) {
            withFrameNanos { frameTime ->
                val dt = (frameTime - lastFrame) / 1_000_000_000f
                lastFrame = frameTime
                if (screenW > 0f) {
                    engine.update(dt, screenW, screenH, groundYState)
                }
            }
        }
        if (engine.score > bestScore) bestScore = engine.score
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF102030))
            .pointerInput(started, engine.isGameOver) {
                detectTapGestures(onTap = {
                    when {
                        !started -> started = true
                        engine.isGameOver -> restartKey++
                        else -> engine.jump()
                    }
                })
            }
            .pointerInput(started, engine.isGameOver) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (started && !engine.isGameOver && dragAmount > 12f) {
                        engine.duck()
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            screenW = size.width
            screenH = size.height
            val groundY = size.height * 0.72f
            groundYState = groundY

            drawSky(size.width, size.height, groundY)
            drawParallaxHills(size.width, groundY, engine.groundOffset)
            drawPerspectiveRoad(size.width, size.height, groundY, engine.groundOffset)
            drawPlayer(size.width, groundY, engine.playerJumpHeight, engine.isDucking)
            for (obstacle in engine.obstacles) {
                drawObstacle(obstacle, groundY)
            }
        }

        // HUD — счёт
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp)
        ) {
            Text(
                text = "Счёт: ${engine.score}",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            if (bestScore > 0) {
                Text(
                    text = "Рекорд: $bestScore",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            }
        }

        if (!started) {
            CenterOverlay(
                title = "Pova Runner",
                subtitle = "Тапни, чтобы прыгнуть\nСвайп вниз — присесть",
                buttonText = "Начать"
            ) { started = true }
        } else if (engine.isGameOver) {
            CenterOverlay(
                title = "Игра окончена",
                subtitle = "Счёт: ${engine.score}",
                buttonText = "Заново"
            ) { restartKey++ }
        }
    }
}

@Composable
private fun BoxScope.CenterOverlay(
    title: String,
    subtitle: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onClick) { Text(buttonText) }
    }
}

// ---------- Отрисовка сцены ----------

private fun DrawScope.drawSky(width: Float, height: Float, groundY: Float) {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(SKY_TOP, SKY_HORIZON),
            startY = 0f,
            endY = groundY
        ),
        size = androidx.compose.ui.geometry.Size(width, groundY)
    )
}

/** Дальние "горы" — медленный параллакс-слой для ощущения глубины. */
private fun DrawScope.drawParallaxHills(width: Float, groundY: Float, offset: Float) {
    val parallaxOffset = (offset * 0.15f) % (width + 300f)
    val hillColor = Color(0xFF8FC7EE)
    var startX = -parallaxOffset
    while (startX < width + 300f) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(startX, groundY)
            lineTo(startX + 90f, groundY - 110f)
            lineTo(startX + 180f, groundY)
            close()
        }
        drawPath(path, color = hillColor)
        startX += 220f
    }
}

/** Перспективная "дорога": сходящиеся линии + бегущие поперечные засечки для ощущения 3D. */
private fun DrawScope.drawPerspectiveRoad(width: Float, height: Float, groundY: Float, offset: Float) {
    drawRect(
        color = ROAD_COLOR,
        topLeft = Offset(0f, groundY),
        size = androidx.compose.ui.geometry.Size(width, height - groundY)
    )
    val vanishX = width * 0.5f
    val vanishY = groundY - 40f
    // сходящиеся боковые линии
    drawLine(ROAD_LINE, Offset(0f, height), Offset(vanishX, vanishY), strokeWidth = 4f)
    drawLine(ROAD_LINE, Offset(width, height), Offset(vanishX, vanishY), strokeWidth = 4f)
    drawLine(ROAD_LINE, Offset(width * 0.22f, height), Offset(vanishX, vanishY), strokeWidth = 2f)
    drawLine(ROAD_LINE, Offset(width * 0.78f, height), Offset(vanishX, vanishY), strokeWidth = 2f)

    // бегущие поперечные засечки, ускоряющиеся к низу (эффект перспективной скорости)
    val rungCount = 8
    val cycled = offset % 140f
    for (i in 0 until rungCount) {
        val t = (i.toFloat() / rungCount) + (cycled / 140f) / rungCount
        val clampedT = t.coerceIn(0f, 1f)
        val y = groundY + (height - groundY) * (clampedT * clampedT)
        val halfWidth = (width * 0.5f) * clampedT
        drawLine(
            ROAD_LINE.copy(alpha = 0.5f + 0.5f * clampedT),
            Offset(vanishX - halfWidth, y),
            Offset(vanishX + halfWidth, y),
            strokeWidth = 2f + 3f * clampedT
        )
    }
}

/** Игрок — иконка телефона с приподнятой "объёмной" тенью-дубликатом для псевдо-3D. */
private fun DrawScope.drawPlayer(screenWidth: Float, groundY: Float, jumpHeight: Float, isDucking: Boolean) {
    val playerX = screenWidth * GameEngine.PLAYER_X_FRACTION
    val h = if (isDucking) GameEngine.PLAYER_DUCK_HEIGHT else GameEngine.PLAYER_HEIGHT
    val w = GameEngine.PLAYER_WIDTH
    val bottom = groundY - jumpHeight
    val top = bottom - h

    // тень на земле
    val shadowScale = (1f - (jumpHeight / 500f)).coerceIn(0.35f, 1f)
    drawOval(
        color = Color.Black.copy(alpha = 0.25f * shadowScale),
        topLeft = Offset(playerX - (w / 2f) * shadowScale, groundY - 10f),
        size = androidx.compose.ui.geometry.Size(w * shadowScale, 16f * shadowScale)
    )

    // объёмный дубликат позади (эффект "толщины")
    val depthOffset = 8f
    drawRoundRect(
        color = PHONE_BODY.copy(alpha = 0.55f),
        topLeft = Offset(playerX - w / 2f + depthOffset, top + depthOffset),
        size = androidx.compose.ui.geometry.Size(w, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f)
    )
    // корпус телефона
    drawRoundRect(
        color = PHONE_BODY,
        topLeft = Offset(playerX - w / 2f, top),
        size = androidx.compose.ui.geometry.Size(w, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f)
    )
    // экран
    val screenMargin = w * 0.12f
    drawRoundRect(
        color = PHONE_SCREEN,
        topLeft = Offset(playerX - w / 2f + screenMargin, top + h * 0.08f),
        size = androidx.compose.ui.geometry.Size(w - screenMargin * 2f, h * 0.7f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
    )
    // камера сверху
    drawCircle(
        color = Color.White.copy(alpha = 0.6f),
        radius = w * 0.05f,
        center = Offset(playerX, top + h * 0.05f)
    )
}

private fun DrawScope.drawObstacle(obstacle: Obstacle, groundY: Float) {
    when (obstacle.type) {
        ObstacleType.HEAT -> drawHeatIcon(obstacle, groundY)
        ObstacleType.FLYING -> drawFlyingIcon(obstacle, groundY)
    }
}

/** Значок высокой температуры — треугольник-предупреждение с термометром. */
private fun DrawScope.drawHeatIcon(obstacle: Obstacle, groundY: Float) {
    val cx = obstacle.x
    val bottom = groundY
    val top = bottom - obstacle.height
    val halfW = obstacle.width / 2f

    // объёмный дубликат
    val depth = 6f
    val trianglePathDark = androidx.compose.ui.graphics.Path().apply {
        moveTo(cx + depth, top + depth)
        lineTo(cx - halfW + depth, bottom + depth)
        lineTo(cx + halfW + depth, bottom + depth)
        close()
    }
    drawPath(trianglePathDark, color = HEAT_DARK.copy(alpha = 0.6f))

    val trianglePath = androidx.compose.ui.graphics.Path().apply {
        moveTo(cx, top)
        lineTo(cx - halfW, bottom)
        lineTo(cx + halfW, bottom)
        close()
    }
    drawPath(trianglePath, color = HEAT_COLOR)

    // восклицательный знак / термометр внутри
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(cx - 5f, top + obstacle.height * 0.35f),
        size = androidx.compose.ui.geometry.Size(10f, obstacle.height * 0.32f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
    )
    drawCircle(
        color = Color.White,
        radius = 7f,
        center = Offset(cx, bottom - 10f)
    )
}

/** Летающее препятствие — обобщённая иконка геймпада (не конкретный бренд). */
private fun DrawScope.drawFlyingIcon(obstacle: Obstacle, groundY: Float) {
    val cx = obstacle.x
    val cy = groundY - GameEngine.FLYING_GROUND_GAP - obstacle.height / 2f
    val w = obstacle.width
    val h = obstacle.height

    val depth = 6f
    drawRoundRect(
        color = GAME_ICON_DARK.copy(alpha = 0.55f),
        topLeft = Offset(cx - w / 2f + depth, cy - h / 2f + depth),
        size = androidx.compose.ui.geometry.Size(w, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(28f, 28f)
    )
    drawRoundRect(
        color = GAME_ICON_COLOR,
        topLeft = Offset(cx - w / 2f, cy - h / 2f),
        size = androidx.compose.ui.geometry.Size(w, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(28f, 28f)
    )
    // кнопки геймпада
    drawCircle(Color.White.copy(alpha = 0.85f), radius = 7f, center = Offset(cx + w * 0.22f, cy - h * 0.08f))
    drawCircle(Color.White.copy(alpha = 0.85f), radius = 7f, center = Offset(cx + w * 0.32f, cy + h * 0.08f))
    // крестовина слева
    drawRoundRect(
        color = Color.White.copy(alpha = 0.85f),
        topLeft = Offset(cx - w * 0.34f, cy - 3f),
        size = androidx.compose.ui.geometry.Size(w * 0.18f, 6f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.85f),
        topLeft = Offset(cx - w * 0.28f, cy - h * 0.15f),
        size = androidx.compose.ui.geometry.Size(6f, h * 0.3f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
    )
}

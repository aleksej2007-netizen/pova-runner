package com.example.povarunner.game

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import kotlin.random.Random

enum class ObstacleType { HEAT, FLYING }

data class Obstacle(
    val id: Long,
    val type: ObstacleType,
    var x: Float,      // позиция по X в px, движется справа налево
    val width: Float,
    val height: Float
)

/**
 * Игровой движок раннера: физика прыжка/приседа игрока, спавн препятствий,
 * увеличение скорости со временем и проверка столкновений.
 * Все поля — Compose State, поэтому Canvas сам перерисовывается на каждом кадре.
 */
class GameEngine {

    companion object {
        const val GRAVITY = 3200f          // px/s^2
        const val JUMP_VELOCITY = 1300f    // px/s
        const val BASE_SPEED = 420f        // px/s
        const val SPEED_ACCEL = 6f         // px/s^2 — постепенный разгон
        const val DUCK_DURATION = 0.55f    // сек

        const val PLAYER_WIDTH = 90f
        const val PLAYER_HEIGHT = 120f
        const val PLAYER_DUCK_HEIGHT = 65f
        const val PLAYER_X_FRACTION = 0.18f // позиция игрока по X, доля ширины экрана

        const val HEAT_WIDTH = 70f
        const val HEAT_HEIGHT = 80f
        const val FLYING_WIDTH = 95f
        const val FLYING_HEIGHT = 60f
        const val FLYING_GROUND_GAP = 150f // высота полёта над землёй
    }

    var playerJumpHeight by mutableStateOf(0f) // 0 = на земле
        private set
    var velocityY by mutableStateOf(0f)
        private set
    var isDucking by mutableStateOf(false)
        private set
    private var duckTimer = 0f

    var score by mutableStateOf(0)
        private set
    private var scoreAccum = 0f

    var speed by mutableStateOf(BASE_SPEED)
        private set

    var isGameOver by mutableStateOf(false)
        private set

    var groundOffset by mutableStateOf(0f) // для скролла перспективных линий дороги
        private set

    val obstacles = mutableStateListOf<Obstacle>()

    private var spawnTimer = 0f
    private var nextSpawnInterval = 1.4f
    private var idCounter = 0L
    private var running = false

    fun start() {
        obstacles.clear()
        playerJumpHeight = 0f
        velocityY = 0f
        isDucking = false
        duckTimer = 0f
        score = 0
        scoreAccum = 0f
        speed = BASE_SPEED
        isGameOver = false
        groundOffset = 0f
        spawnTimer = 0f
        nextSpawnInterval = 1.4f
        running = true
    }

    fun jump() {
        if (running && !isGameOver && playerJumpHeight <= 0.5f && !isDucking) {
            velocityY = JUMP_VELOCITY
        }
    }

    fun duck() {
        if (running && !isGameOver && playerJumpHeight <= 0.5f) {
            isDucking = true
            duckTimer = DUCK_DURATION
        }
    }

    fun update(dt: Float, screenWidthPx: Float, screenHeightPx: Float, groundY: Float) {
        if (!running || isGameOver) return
        val clampedDt = dt.coerceAtMost(0.033f) // защита от скачков при лагах

        // физика прыжка
        velocityY -= GRAVITY * clampedDt
        playerJumpHeight = (playerJumpHeight + velocityY * clampedDt).coerceAtLeast(0f)
        if (playerJumpHeight <= 0f) velocityY = 0f

        // присед
        if (duckTimer > 0f) {
            duckTimer -= clampedDt
            if (duckTimer <= 0f) isDucking = false
        }

        // разгон и счёт
        speed += SPEED_ACCEL * clampedDt
        scoreAccum += clampedDt * (speed / BASE_SPEED) * 12f
        score = scoreAccum.toInt()

        // скролл дороги (для перспективных линий)
        groundOffset += speed * clampedDt

        // движение препятствий
        for (o in obstacles) o.x -= speed * clampedDt
        obstacles.removeAll { it.x < -200f }

        // спавн новых препятствий
        spawnTimer += clampedDt
        if (spawnTimer >= nextSpawnInterval) {
            spawnTimer = 0f
            val minInterval = 0.65f
            val maxInterval = (1.6f - speed / 2200f).coerceAtLeast(minInterval + 0.2f)
            nextSpawnInterval = Random.nextFloat() * (maxInterval - minInterval) + minInterval
            spawnObstacle(screenWidthPx)
        }

        checkCollisions(screenWidthPx, groundY)
    }

    private fun spawnObstacle(screenWidthPx: Float) {
        val type = if (Random.nextFloat() < 0.7f) ObstacleType.HEAT else ObstacleType.FLYING
        val obstacle = when (type) {
            ObstacleType.HEAT -> Obstacle(idCounter++, type, screenWidthPx + 50f, HEAT_WIDTH, HEAT_HEIGHT)
            ObstacleType.FLYING -> Obstacle(idCounter++, type, screenWidthPx + 50f, FLYING_WIDTH, FLYING_HEIGHT)
        }
        obstacles.add(obstacle)
    }

    private fun checkCollisions(screenWidthPx: Float, groundY: Float) {
        val playerX = screenWidthPx * PLAYER_X_FRACTION
        val playerHeight = if (isDucking) PLAYER_DUCK_HEIGHT else PLAYER_HEIGHT
        val playerTop = groundY - playerHeight - playerJumpHeight
        val playerBottom = groundY - playerJumpHeight
        val playerLeft = playerX - PLAYER_WIDTH / 2f
        val playerRight = playerX + PLAYER_WIDTH / 2f

        for (o in obstacles) {
            val (obsTop, obsBottom) = when (o.type) {
                ObstacleType.HEAT -> (groundY - o.height) to groundY
                ObstacleType.FLYING -> (groundY - FLYING_GROUND_GAP - o.height) to (groundY - FLYING_GROUND_GAP)
            }
            val obsLeft = o.x - o.width / 2f
            val obsRight = o.x + o.width / 2f

            val overlapsX = playerRight > obsLeft && playerLeft < obsRight
            val overlapsY = playerBottom > obsTop && playerTop < obsBottom
            if (overlapsX && overlapsY) {
                isGameOver = true
                running = false
                return
            }
        }
    }
}

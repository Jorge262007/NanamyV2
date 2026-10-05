package com.nanamy.launcher.eyes

import kotlin.random.Random

class LookAssistant {

    enum class Direction { CENTER, N, NE, E, SE, S, SW, W, NW }

    var currentX: Float = 0f
        private set
    var currentY: Float = 0f
        private set

    private var velocityX: Float = 0f
    private var velocityY: Float = 0f

    private var targetX: Float = 0f
    private var targetY: Float = 0f

    var currentDirection: Direction = Direction.CENTER
        private set
    var isAtEdge: Boolean = false
        private set

    private var timeSinceLastTarget: Long = 0L
    private var nextChangeIntervalMs: Long = randomInterval()

    private val stiffness = 45f
    private val damping = 12f

    var curiousHeightBoost: Float = 0f
        private set
    private var curiousVelocity: Float = 0f

    private val normalMagnitude = 0.55f
    private val edgeMagnitude = 0.95f

    private val diagonals = setOf(Direction.NE, Direction.SE, Direction.SW, Direction.NW)
    private val straightEdges = setOf(Direction.N, Direction.S, Direction.E, Direction.W)
    private val outer8 = diagonals + straightEdges
    private val inner9 = setOf(Direction.CENTER) + Direction.values().filter { it != Direction.CENTER }.toSet() - outer8

    private fun randomInterval(): Long = Random.nextLong(2500L, 6000L)

    private fun toXY(dir: Direction, magnitude: Float): Pair<Float, Float> = when (dir) {
        Direction.CENTER -> 0f to 0f
        Direction.N -> 0f to -magnitude
        Direction.NE -> magnitude * 0.7071f to -magnitude * 0.7071f
        Direction.E -> magnitude to 0f
        Direction.SE -> magnitude * 0.7071f to magnitude * 0.7071f
        Direction.S -> 0f to magnitude
        Direction.SW -> -magnitude * 0.7071f to magnitude * 0.7071f
        Direction.W -> -magnitude to 0f
        Direction.NW -> -magnitude * 0.7071f to -magnitude * 0.7071f
    }

    private fun applyPosition(dir: Direction, isEdge: Boolean) {
        currentDirection = dir
        isAtEdge = isEdge
        val magnitude = if (isEdge) edgeMagnitude else normalMagnitude
        val (x, y) = toXY(dir, magnitude)
        targetX = x; targetY = y
    }

    private fun pickNextPosition() {
        if (isAtEdge) {
            // From outer, must go to an inner position (center or normal ring)
            val innerList = inner9.toList()
            val dir = innerList[Random.nextInt(innerList.size)]
            applyPosition(dir, isEdge = false)
            return
        }

        // From inner, free choice among all 17, slight center bias
        val centerWeight = 2
        val otherWeight = 1
        val totalWeight = centerWeight + 16 * otherWeight
        val roll = Random.nextInt(totalWeight)

        if (roll < centerWeight) {
            applyPosition(Direction.CENTER, isEdge = false)
            return
        }

        val remaining = roll - centerWeight
        val dirIndex = remaining / 2
        val isEdge = remaining % 2 == 1
        val dir = Direction.values().filter { it != Direction.CENTER }[dirIndex]
        applyPosition(dir, isEdge)
    }

    private fun springStep(
        current: Float, velocity: Float, target: Float, dtSec: Float
    ): Pair<Float, Float> {
        val displacement = current - target
        val springForce = -stiffness * displacement
        val dampingForce = -damping * velocity
        val acceleration = springForce + dampingForce
        val newVelocity = velocity + acceleration * dtSec
        val newPosition = current + newVelocity * dtSec
        return newPosition to newVelocity
    }

    fun lookAtCenter() {
        applyPosition(Direction.CENTER, isEdge = false)
        timeSinceLastTarget = 0L // Reset timer so it doesn't jump immediately after releasing
    }

    fun update(deltaMs: Long, randomLook: Boolean = true) {
        if (randomLook) {
            timeSinceLastTarget += deltaMs
            if (timeSinceLastTarget >= nextChangeIntervalMs) {
                timeSinceLastTarget = 0L
                nextChangeIntervalMs = randomInterval()
                pickNextPosition()
            }
        }

        var remainingMs = deltaMs
        val stepMs = 4L
        while (remainingMs > 0) {
            val step = minOf(stepMs, remainingMs)
            val dtSec = step / 1000f

            val (nx, nvx) = springStep(currentX, velocityX, targetX, dtSec)
            currentX = nx; velocityX = nvx

            val (ny, nvy) = springStep(currentY, velocityY, targetY, dtSec)
            currentY = ny; velocityY = nvy

            val shouldDeform = isAtEdge && currentDirection != Direction.N && currentDirection != Direction.S
            val targetBoost = if (shouldDeform) 1f else 0f
            val (nb, nbv) = springStep(curiousHeightBoost, curiousVelocity, targetBoost, dtSec)
            curiousHeightBoost = nb; curiousVelocity = nbv

            remainingMs -= step
        }
    }
}
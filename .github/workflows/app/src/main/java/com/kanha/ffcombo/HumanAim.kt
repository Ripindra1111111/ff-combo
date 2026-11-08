package com.kanha.ffcombo

import android.graphics.PointF
import kotlin.math.*
import kotlin.random.Random

object HumanAim {

    data class Step(val x: Float, val y: Float, val delayMs: Long)

    fun buildPath(
        start: PointF,
        target: PointF,
        baseDurationMs: Long = 140,
        jitterPx: Float = 4f,
        overshoot: Float = 0.08f
    ): List<Step> {
        val dx = target.x - start.x
        val dy = target.y - start.y
        val dist = sqrt(dx * dx + dy * dy)
        if (dist < 1f) return emptyList()

        val osAmount = dist * overshoot * Random.nextFloat()
        val dirX = dx / dist
        val dirY = dy / dist
        val overX = target.x + dirX * osAmount
        val overY = target.y + dirY * osAmount

        val steps = (dist / 40f).toInt().coerceIn(4, 14)
        val totalMs = baseDurationMs + Random.nextLong(0, 80)

        val path = mutableListOf<Step>()
        for (i in 1..steps) {
            val t = i.toFloat() / steps
            val ease = easeInOutCubic(t)

            var x = start.x + (overX - start.x) * ease
            var y = start.y + (overY - start.y) * ease

            x += Random.nextFloat() * jitterPx * 2 - jitterPx
            y += Random.nextFloat() * jitterPx * 2 - jitterPx

            val delay = (totalMs / steps) + Random.nextLong(-8, 8)
            path.add(Step(x, y, delay.coerceAtLeast(2)))
        }

        path.add(Step(
            target.x + Random.nextFloat() * 2 - 1,
            target.y + Random.nextFloat() * 2 - 1,
            20
        ))
        return path
    }

    private fun easeInOutCubic(t: Float): Float {
        return if (t < 0.5f) 4f * t * t * t
        else 1f - (-2f * t + 2f).pow(3) / 2f
    }
}
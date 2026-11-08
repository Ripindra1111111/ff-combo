package com.kanha.ffcombo

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class AimAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AimAccessibilityService? = null
        var enabled = true
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastAimTime = 0L
    private val cooldownMs = 350L
    private var lastTarget: PointF? = null

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun aimTo(screenW: Int, screenH: Int, headTarget: PointF, distancePx: Float) {
        if (!enabled) return
        val now = System.currentTimeMillis()
        if (now - lastAimTime < cooldownMs) return

        val prev = lastTarget
        if (prev != null && abs(prev.x - headTarget.x) < 8f &&
            abs(prev.y - headTarget.y) < 8f) return

        val cx = screenW / 2f
        val cy = screenH / 2f
        val distFromCenter = sqrt((headTarget.x - cx).pow(2) + (headTarget.y - cy).pow(2))
        if (distFromCenter > screenW * 0.45f) return

        lastAimTime = now
        lastTarget = PointF(headTarget.x, headTarget.y)

        val start = PointF(cx, cy)
        val path = HumanAim.buildPath(start, headTarget)
        if (path.isEmpty()) return

        scope.launch {
            for (step in path) {
                if (!enabled) return@launch
                dispatchTouch(step.x, step.y, step.delayMs)
                delay(step.delayMs)
            }
        }
    }

    private fun dispatchTouch(x: Float, y: Float, durationMs: Long) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(
                path, 0, durationMs.coerceAtLeast(5)
            ))
            .build()
        dispatchGesture(gesture, null, null)
    }

    override fun onDestroy() {
        instance = null
        scope.cancel()
        super.onDestroy()
    }
}
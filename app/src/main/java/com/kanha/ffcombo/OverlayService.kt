package com.kanha.ffcombo

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.view.*
import kotlinx.coroutines.*
import kotlin.math.abs

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: OverlayView? = null
    private var detector: Detector? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        detector = Detector(this)

        ScreenCaptureService.frameCallback = { bmp ->
            scope.launch {
                val detections = detector?.detect(bmp) ?: emptyList()
                val dm = resources.displayMetrics
                val scaleX = dm.widthPixels.toFloat() / bmp.width
                val scaleY = dm.heightPixels.toFloat() / bmp.height

                val scaled = detections.map {
                    Detection(
                        RectF(it.box.left * scaleX, it.box.top * scaleY,
                            it.box.right * scaleX, it.box.bottom * scaleY),
                        it.score
                    )
                }.filter { isEnemy(bmp, it, scaleX, scaleY) }

                withContext(Dispatchers.Main) { overlayView?.update(scaled) }

                val target = scaled.minByOrNull {
                    val cx = (it.box.left + it.box.right) / 2f
                    val cy = (it.box.top + it.box.bottom) / 2f
                    abs(cx - dm.widthPixels / 2f) + abs(cy - dm.heightPixels / 2f)
                }
                if (target != null) {
                    val headX = (target.box.left + target.box.right) / 2f
                    val headY = target.box.top + target.box.height() * 0.12f
                    AimAccessibilityService.instance?.aimTo(
                        dm.widthPixels, dm.heightPixels,
                        PointF(headX, headY),
                        target.box.height()
                    )
                }
            }
        }
        addOverlay()
    }

    private fun isEnemy(bmp: Bitmap, det: Detection, sx: Float, sy: Float): Boolean {
        val left = (det.box.left / sx).toInt().coerceIn(0, bmp.width - 1)
        val right = (det.box.right / sx).toInt().coerceIn(0, bmp.width - 1)
        val top = (det.box.top / sy).toInt().coerceIn(0, bmp.height - 1)

        val sampleY = (top - 20).coerceAtLeast(0)
        val cx = (left + right) / 2
        var greenish = 0
        var total = 0

        for (dx in -30..30 step 5) {
            val x = (cx + dx).coerceIn(0, bmp.width - 1)
            val p = bmp.getPixel(x, sampleY)
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            total++
            if (g > 140 && g > r + 40 && g > b + 20) greenish++
        }
        return !(total > 0 && greenish.toFloat() / total > 0.4f)
    }

    private fun addOverlay() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        overlayView = OverlayView(this)
        windowManager.addView(overlayView, params)
    }

    override fun onDestroy() {
        ScreenCaptureService.frameCallback = null
        overlayView?.let { windowManager.removeView(it) }
        detector?.close()
        scope.cancel()
        super.onDestroy()
    }

    class OverlayView(context: Context) : View(context) {
        private val boxes = mutableListOf<Detection>()
        private val boxPaint = Paint().apply {
            color = Color.RED; style = Paint.Style.STROKE
            strokeWidth = 4f; isAntiAlias = true
        }
        private val headPaint = Paint().apply {
            color = Color.CYAN; style = Paint.Style.FILL; isAntiAlias = true
        }
        fun update(list: List<Detection>) {
            boxes.clear(); boxes.addAll(list); invalidate()
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            for (d in boxes) {
                canvas.drawRect(d.box, boxPaint)
                val cx = (d.box.left + d.box.right) / 2f
                val cy = d.box.top + d.box.height() * 0.12f
                canvas.drawCircle(cx, cy, 6f, headPaint)
            }
        }
    }
}
package com.kanha.ffcombo

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class ScreenCaptureService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "rc"
        const val EXTRA_DATA = "data"
        const val CHANNEL_ID = "ff_combo_capture"
        const val NOTIF_ID = 1001
        var frameCallback: ((Bitmap) -> Unit)? = null
        var running = false
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val rc = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: return START_NOT_STICKY
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA) ?: return START_NOT_STICKY

        createChannel()
        startForeground(NOTIF_ID, buildNotification())

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(rc, data)

        val dm = DisplayMetrics()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(dm)

        imageReader = ImageReader.newInstance(
            dm.widthPixels, dm.heightPixels, PixelFormat.RGBA_8888, 2
        )
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
        }, null)

        virtualDisplay = projection?.createVirtualDisplay(
            "ff_capture",
            dm.widthPixels, dm.heightPixels, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )

        running = true
        startProcessing()
        return START_STICKY
    }

    private fun startProcessing() {
        CoroutineScope(Dispatchers.Default).launch {
            while (running) {
                val image = imageReader?.acquireLatestImage()
                if (image != null) {
                    try {
                        val plane = image.planes[0]
                        val buffer = plane.buffer
                        val pixelStride = plane.pixelStride
                        val rowStride = plane.rowStride
                        val rowPadding = rowStride - pixelStride * image.width

                        val bmp = Bitmap.createBitmap(
                            image.width + rowPadding / pixelStride,
                            image.height, Bitmap.Config.ARGB_8888
                        )
                        bmp.copyPixelsFromBuffer(buffer)
                        val cropped = Bitmap.createBitmap(bmp, 0, 0, image.width, image.height)
                        frameCallback?.invoke(cropped)
                    } finally { image.close() }
                }
                delay(60)
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "FF Combo", NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FF Combo")
            .setContentText("đang chạy")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
    }

    override fun onDestroy() {
        running = false
        virtualDisplay?.release()
        imageReader?.close()
        projection?.stop()
        super.onDestroy()
    }
}
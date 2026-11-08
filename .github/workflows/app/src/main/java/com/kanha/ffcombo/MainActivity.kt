package com.kanha.ffcombo

import android.Manifest
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val projLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val i = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(i) else startService(i)
            Toast.makeText(this, "capture started", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 120, 60, 60)
        }

        root.addView(btn("1. Cấp quyền overlay") {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                ))
            } else Toast.makeText(this, "đã có", Toast.LENGTH_SHORT).show()
        })

        root.addView(btn("2. Bật Accessibility (cho aim)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "bật FF Combo trong danh sách", Toast.LENGTH_LONG).show()
        })

        root.addView(btn("3. Bắt đầu capture") {
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projLauncher.launch(mpm.createScreenCaptureIntent())
        })

        val aimSwitch = Switch(this).apply {
            text = "Aimlock bật"
            isChecked = true
            setOnCheckedChangeListener { _, checked ->
                AimAccessibilityService.enabled = checked
            }
        }
        root.addView(aimSwitch)

        root.addView(btn("4. Start overlay + aim") {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "chưa cấp overlay", Toast.LENGTH_SHORT).show()
                return@btn
            }
            if (AimAccessibilityService.instance == null) {
                Toast.makeText(this, "chưa bật Accessibility", Toast.LENGTH_SHORT).show()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(Intent(this, OverlayService::class.java))
            else startService(Intent(this, OverlayService::class.java))
        })

        root.addView(btn("5. Stop") {
            stopService(Intent(this, OverlayService::class.java))
            stopService(Intent(this, ScreenCaptureService::class.java))
        })

        setContentView(root)
        requestNotificationPermission()
    }

    private fun btn(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        setOnClickListener { action() }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }
}
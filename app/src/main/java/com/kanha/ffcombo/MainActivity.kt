package com.kanha.ffcombo

import android.Manifest
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.*

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
            Toast.makeText(this, "Capture started!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initLoginScreen()
        requestNotificationPermission()
    }

    private fun initLoginScreen() {
        val editKey = findViewById<TextInputEditText>(R.id.editKey)
        val btnLogin = findViewById<MaterialButton>(R.id.btnLogin)

        btnLogin.setOnClickListener {
            val key = editKey.text.toString().trim()
            if (key.isEmpty()) {
                Toast.makeText(this, "Nhập key vào!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            btnLogin.text = "CHECKING..."
            btnLogin.isEnabled = false

            CoroutineScope(Dispatchers.IO).launch {
                if (!KeyAuth.init()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Lỗi kết nối server auth", Toast.LENGTH_SHORT).show()
                        btnLogin.text = "LOGIN"
                        btnLogin.isEnabled = true
                    }
                    return@launch
                }

                val error = KeyAuth.login(key)
                withContext(Dispatchers.Main) {
                    if (error == null) {
                        Toast.makeText(this@MainActivity, "Thành công!", Toast.LENGTH_SHORT).show()
                        initMainMenu()
                    } else {
                        Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show()
                        btnLogin.text = "LOGIN"
                        btnLogin.isEnabled = true
                    }
                }
            }
        }
    }

    private fun initMainMenu() {
        setContentView(R.layout.menu_main)

        val btnOverlay = findViewById<MaterialButton>(R.id.btnOverlay)
        val btnAccessibility = findViewById<MaterialButton>(R.id.btnAccessibility)
        val btnCapture = findViewById<MaterialButton>(R.id.btnCapture)
        val switchAim = findViewById<MaterialSwitch>(R.id.switchAim)
        val btnStart = findViewById<MaterialButton>(R.id.btnStart)
        val btnStop = findViewById<MaterialButton>(R.id.btnStop)

        btnOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else Toast.makeText(this, "Overlay đã có quyền!", Toast.LENGTH_SHORT).show()
        }

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Bật FF Combo trong danh sách", Toast.LENGTH_LONG).show()
        }

        btnCapture.setOnClickListener {
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projLauncher.launch(mpm.createScreenCaptureIntent())
        }

        switchAim.isChecked = true
        switchAim.setOnCheckedChangeListener { _, checked ->
            AimAccessibilityService.enabled = checked
        }

        btnStart.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Chưa cấp quyền Overlay!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (AimAccessibilityService.instance == null) {
                Toast.makeText(this, "Chưa bật Accessibility!", Toast.LENGTH_SHORT).show()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(Intent(this, OverlayService::class.java))
            else startService(Intent(this, OverlayService::class.java))
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
            stopService(Intent(this, ScreenCaptureService::class.java))
            Toast.makeText(this, "Đã dừng", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }
}

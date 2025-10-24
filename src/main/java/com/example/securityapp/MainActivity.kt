package com.example.securityapp

import android.app.AlertDialog
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var etKey: EditText
    private lateinit var btnSubmit: Button
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var deviceId: String
    private var overlayView: View? = null
    private var inputOverlayView: View? = null
    private var isLocked = false
    private lateinit var windowManager: WindowManager
    private var isDeviceAdmin: Boolean = false
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    companion object {
        private const val TAG = "SecurityApp"
        private const val PREFS_NAME = "SecurityApp"
        private const val KEY_ENCRYPTED = "encrypted"
        private const val OVERLAY_PERMISSION_REQUEST = 1001
        private const val ADMIN_PERMISSION_REQUEST = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")

        // Thiết lập Device Admin
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, AdminReceiver::class.java)
        isDeviceAdmin = devicePolicyManager.isAdminActive(adminComponent)

        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            // LUÔN ĐẶT KEY MẶC ĐỊNH LÀ "123456"
            sharedPreferences.edit().putString("VALID_KEY", "123456").apply()

            // Kiểm tra trạng thái khóa
            isLocked = sharedPreferences.getBoolean(KEY_ENCRYPTED, false)
            Log.d(TAG, "isLocked: $isLocked, isDeviceAdmin: $isDeviceAdmin")

            if (isLocked) {
                // Nếu đang khóa, tạo overlay ngay lập tức
                createFullScreenOverlay()
                startLockProtectionService()
            } else {
                // Hiển thị màn hình bình thường để cấu hình
                showNormalSetup()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}")
            showNormalSetup()
        }
    }

    private fun showNormalSetup() {
        try {
            setContentView(R.layout.activity_main)
            initVariables()
            setupViews()

            // HIỂN THỊ KEY CHO NGƯỜI DÙNG BIẾT
            val currentKey = sharedPreferences.getString("VALID_KEY", "123456")
            findViewById<TextView>(R.id.tvInstruction).text =
                "Nhấn nút để kích hoạt khóa toàn màn hình\n\n🔑 KEY: $currentKey"

            // Kiểm tra và yêu cầu quyền
            if (!hasOverlayPermission()) {
                requestOverlayPermission()
            } else if (!isDeviceAdmin) {
                requestAdminPermission()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in showNormalSetup: ${e.message}")
        }
    }

    private fun initVariables() {
        try {
            deviceId = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "unknown"

            etKey = findViewById(R.id.etKey)
            btnSubmit = findViewById(R.id.btnSubmit)
        } catch (e: Exception) {
            Log.e(TAG, "Error in initVariables: ${e.message}")
        }
    }

    private fun setupViews() {
        try {
            btnSubmit.setOnClickListener {
                startLockMode()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in setupViews: ${e.message}")
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(this)
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking overlay permission: ${e.message}")
            false
        }
    }

    private fun requestOverlayPermission() {
        try {
            val alertDialog = AlertDialog.Builder(this)
                .setTitle("CẤP QUYỀN OVERLAY")
                .setMessage("Ứng dụng cần quyền hiển thị trên các app khác để khóa thiết bị.\n\n🔑 KEY MỞ KHÓA: 123456")
                .setPositiveButton("CẤP QUYỀN") { _, _ ->
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName"))
                        startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting overlay settings: ${e.message}")
                        if (!isDeviceAdmin) {
                            requestAdminPermission()
                        } else {
                            startLockMode()
                        }
                    }
                }
                .setCancelable(false)
                .create()

            alertDialog.show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing permission dialog: ${e.message}")
        }
    }

    private fun requestAdminPermission() {
        try {
            val alertDialog = AlertDialog.Builder(this)
                .setTitle("CẤP QUYỀN QUẢN TRỊ VIÊN")
                .setMessage("Ứng dụng cần quyền quản trị viên để ngăn chặn gỡ ứng dụng.")
                .setPositiveButton("CẤP QUYỀN") { _, _ ->
                    try {
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Cần quyền để ngăn chặn gỡ ứng dụng")
                        startActivityForResult(intent, ADMIN_PERMISSION_REQUEST)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting admin settings: ${e.message}")
                        startLockMode()
                    }
                }
                .setCancelable(false)
                .create()

            alertDialog.show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing admin dialog: ${e.message}")
        }
    }

    private fun startLockMode() {
        try {
            // HIỂN THỊ KEY LẦN CUỐI TRƯỚC KHI KHÓA
            Toast.makeText(this, "🔑 KEY MỞ KHÓA: 123456", Toast.LENGTH_LONG).show()

            sharedPreferences.edit().putBoolean(KEY_ENCRYPTED, true).apply()
            isLocked = true

            // Đợi 1 giây để người dùng thấy key rồi mới khóa
            android.os.Handler().postDelayed({
                createFullScreenOverlay()
                startLockProtectionService()

                // Kích hoạt Device Admin features
                if (isDeviceAdmin) {
                    activateDeviceAdminFeatures()
                }
            }, 1000)

        } catch (e: Exception) {
            Log.e(TAG, "Error in startLockMode: ${e.message}")
        }
    }

    private fun activateDeviceAdminFeatures() {
        try {
            // Ngăn chặn gỡ ứng dụng
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                devicePolicyManager.setUninstallBlocked(adminComponent, packageName, true)
            }

            // Khóa ngay lập tức
            devicePolicyManager.lockNow()

            Log.d(TAG, "Device admin features activated")
        } catch (e: Exception) {
            Log.e(TAG, "Error activating device admin: ${e.message}")
        }
    }

    private fun createFullScreenOverlay() {
        try {
            // Xóa overlay cũ nếu có
            removeAllOverlays()

            val params = WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                }
                format = PixelFormat.TRANSLUCENT
                flags = (WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                        or WindowManager.LayoutParams.FLAG_FULLSCREEN
                        or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            }

            // Tạo LAYER 1: Overlay chặn toàn bộ màn hình
            val blockingOverlay = FrameLayout(this).apply {
                setBackgroundColor(Color.parseColor("#CC000000"))
                setOnTouchListener { _, _ -> true } // Chặn tất cả touch events
            }

            // Tạo LAYER 2: Overlay chứa ô nhập key
            val inputOverlayParams = WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                }
                format = PixelFormat.TRANSLUCENT
                flags = (WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
                gravity = Gravity.CENTER
            }

            val inputOverlay = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.TRANSPARENT)
                gravity = Gravity.CENTER
                setPadding(50, 50, 50, 50)

                val mainLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(Color.BLACK)
                    gravity = Gravity.CENTER
                    setPadding(40, 40, 40, 40)

                    val title = TextView(context).apply {
                        text = "🔐 THIẾT BỊ ĐÃ BỊ KHÓA"
                        setTextColor(Color.RED)
                        textSize = 24f
                        gravity = Gravity.CENTER
                        setPadding(0, 0, 0, 20)
                    }

                    val deviceInfo = TextView(context).apply {
                        text = "Device: $deviceId"
                        setTextColor(Color.WHITE)
                        textSize = 14f
                        gravity = Gravity.CENTER
                        setPadding(0, 0, 0, 10)
                    }

                    val instruction = TextView(context).apply {
                        text = "KHÔNG THỂ THOÁT ỨNG DỤNG\nLiên hệ admin để lấy key\n\n🔑 KEY: 123456"
                        setTextColor(Color.WHITE)
                        textSize = 16f
                        gravity = Gravity.CENTER
                        setPadding(0, 0, 0, 30)
                    }

                    val keyInput = EditText(context).apply {
                        hint = "Nhập key 123456 để mở khóa..."
                        setTextColor(Color.WHITE)
                        setHintTextColor(Color.GRAY)
                        setBackgroundColor(Color.DKGRAY)
                        setPadding(30, 20, 30, 20)
                        textSize = 18f
                    }

                    val submitBtn = Button(context).apply {
                        text = "MỞ KHÓA"
                        setTextColor(Color.WHITE)
                        setBackgroundColor(Color.RED)
                        setPadding(60, 20, 60, 20)
                        textSize = 16f

                        setOnClickListener {
                            val inputKey = keyInput.text.toString().trim()
                            if (validateKey(inputKey)) {
                                // GỌI HÀM UNLOCK KHI KEY ĐÚNG
                                unlockDevice()
                            } else {
                                Toast.makeText(context, "Key sai! Nhập 123456", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    addView(title)
                    addView(deviceInfo)
                    addView(instruction)
                    addView(keyInput)
                    addView(submitBtn)
                }

                addView(mainLayout)
            }

            // Thêm cả 2 overlay
            overlayView = blockingOverlay
            inputOverlayView = inputOverlay

            windowManager.addView(blockingOverlay, params)
            windowManager.addView(inputOverlay, inputOverlayParams)

            Log.d(TAG, "Full screen overlay created successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error creating overlay: ${e.message}")
        }
    }

    private fun removeAllOverlays() {
        try {
            // Xóa blocking overlay
            overlayView?.let { view ->
                windowManager.removeView(view)
                overlayView = null
            }

            // Xóa input overlay
            inputOverlayView?.let { view ->
                windowManager.removeView(view)
                inputOverlayView = null
            }

            Log.d(TAG, "All overlays removed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing overlays: ${e.message}")
        }
    }

    private fun validateKey(inputKey: String): Boolean {
        return try {
            inputKey == "123456"
        } catch (e: Exception) {
            Log.e(TAG, "Error validating key: ${e.message}")
            false
        }
    }

    private fun unlockDevice() {
        try {
            Log.d(TAG, "UNLOCK DEVICE CALLED - Removing overlays and showing normal screen")

            // 1. XÓA TẤT CẢ OVERLAY TRƯỚC
            removeAllOverlays()

            // 2. ĐÁNH DẤU ĐÃ MỞ KHÓA
            sharedPreferences.edit().putBoolean(KEY_ENCRYPTED, false).apply()
            isLocked = false

            // 3. DỪNG SERVICE BẢO VỆ
            stopLockProtectionService()

            // 4. VÔ HIỆU HÓA DEVICE ADMIN (nếu có)
            if (isDeviceAdmin) {
                try {
                    devicePolicyManager.removeActiveAdmin(adminComponent)
                    isDeviceAdmin = false
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing device admin: ${e.message}")
                }
            }

            // 5. HIỂN THỊ MÀN HÌNH BÌNH THƯỜNG
            showUnlockSuccessScreen()

            Log.d(TAG, "Device unlocked successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error unlocking device: ${e.message}")
            // Fallback: vẫn hiển thị màn hình unlock
            showUnlockSuccessScreen()
        }
    }

    private fun showUnlockSuccessScreen() {
        try {
            // CHUYỂN SANG MÀN HÌNH UNLOCK THÀNH CÔNG
            setContentView(R.layout.activity_normal)

            // HIỂN THỊ THÔNG BÁO
            Toast.makeText(this, "✅ Đã mở khóa thiết bị thành công!", Toast.LENGTH_LONG).show()

            // TỰ ĐỘNG ĐÓNG APP SAU 3 GIÂY
            android.os.Handler().postDelayed({
                finish()
            }, 3000)

        } catch (e: Exception) {
            Log.e(TAG, "Error showing unlock screen: ${e.message}")
            // Fallback cuối cùng: finish app
            finish()
        }
    }

    // Hàm xử lý nút đóng ngay (được gọi từ XML)
    fun onCloseButtonClick(view: View) {
        finish()
    }

    private fun startLockProtectionService() {
        try {
            val serviceIntent = Intent(this, LockProtectionService::class.java)
            startService(serviceIntent)
            Log.d(TAG, "Lock protection service started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting protection service: ${e.message}")
        }
    }

    private fun stopLockProtectionService() {
        try {
            val serviceIntent = Intent(this, LockProtectionService::class.java)
            stopService(serviceIntent)
            Log.d(TAG, "Lock protection service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping protection service: ${e.message}")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult: $requestCode, resultCode: $resultCode")

        when (requestCode) {
            OVERLAY_PERMISSION_REQUEST -> {
                if (!isDeviceAdmin) {
                    requestAdminPermission()
                } else {
                    startLockMode()
                }
            }
            ADMIN_PERMISSION_REQUEST -> {
                isDeviceAdmin = devicePolicyManager.isAdminActive(adminComponent)
                startLockMode()
            }
        }
    }

    override fun onBackPressed() {
        if (isLocked) return
        super.onBackPressed()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isLocked) {
            // Tự động mở lại app khi người dùng cố thoát
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy - isLocked: $isLocked")

        if (!isLocked) {
            removeAllOverlays()
        }
    }
}
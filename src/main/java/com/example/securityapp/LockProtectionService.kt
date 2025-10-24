package com.example.securityapp

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class LockProtectionService : Service() {

    private val TAG = "LockProtectionService"

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Protection service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Protection service started")

        // Tự động khởi động lại MainActivity nếu bị kill
        val restartIntent = Intent(this, MainActivity::class.java)
        restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(restartIntent)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Tự động khởi động lại khi bị remove từ recent apps
        val restartIntent = Intent(this, MainActivity::class.java)
        restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(restartIntent)

        val restartService = Intent(this, LockProtectionService::class.java)
        startService(restartService)

        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Protection service destroyed")

        // Tự động khởi động lại service
        val restartService = Intent(this, LockProtectionService::class.java)
        startService(restartService)
    }
}
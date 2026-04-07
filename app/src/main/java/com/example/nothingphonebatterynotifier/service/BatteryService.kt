package com.example.nothingphonebatterynotifier.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.nothingphonebatterynotifier.data.ProfileRepository
import com.example.nothingphonebatterynotifier.glyph.GlyphManager
import com.example.nothingphonebatterynotifier.model.GlyphProfile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class BatteryService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var profileRepository: ProfileRepository
    private lateinit var glyphManager: GlyphManager
    private var blinkJob: Job? = null
    private var currentLevel: Int = 100
    private var isTesting = false
    private var currentBlinkingProfileId: String? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level != -1 && scale != -1) {
                val batteryPct = (level * 100 / scale.toFloat()).toInt()
                if (batteryPct != currentLevel) {
                    currentLevel = batteryPct
                    if (!isTesting) evaluateProfiles()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        profileRepository = ProfileRepository(this)
        glyphManager = GlyphManager(this)
        glyphManager.init()

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        startMonitoring()

        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = registerReceiver(batteryReceiver, intentFilter)
        
        batteryStatus?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level != -1 && scale != -1) {
                currentLevel = (level * 100 / scale.toFloat()).toInt()
                evaluateProfiles()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> stopSelf()
            ACTION_TEST_BLINK -> runTestBlink()
            ACTION_PREVIEW_PATTERN -> {
                val count = intent.getIntExtra("count", 1)
                val duration = intent.getLongExtra("duration", 100L)
                val gap = intent.getLongExtra("gap", 100L)
                runPreview(count, duration, gap)
            }
        }
        return START_STICKY
    }

    private fun runPreview(count: Int, duration: Long, gap: Long) {
        serviceScope.launch {
            isTesting = true
            stopBlinking()
            
            glyphManager.previewBlink(count, duration, gap, this)
            
            isTesting = false
            // Re-evaluate current profiles after preview finishes
            val profiles = profileRepository.profilesFlow.first()
            evaluateProfiles(profiles)
        }
    }

    private fun runTestBlink() {
        serviceScope.launch {
            isTesting = true
            stopBlinking()
            
            // Pulse the red LED twice to confirm operation
            repeat(2) {
                glyphManager.toggleRedLed(true)
                delay(500)
                glyphManager.toggleRedLed(false)
                delay(200)
            }
            
            isTesting = false
            evaluateProfiles()
        }
    }

    private fun startMonitoring() {
        serviceScope.launch {
            profileRepository.profilesFlow.collect { profiles ->
                if (!isTesting) evaluateProfiles(profiles)
            }
        }
    }

    private fun evaluateProfiles(profiles: List<GlyphProfile>) {
        val activeProfile = profiles
            .filter { it.enabled && currentLevel <= it.batteryThreshold }
            .minByOrNull { it.batteryThreshold }

        if (activeProfile != null) {
            if (currentBlinkingProfileId != activeProfile.id) {
                startBlinking(activeProfile)
            }
        } else {
            stopBlinking()
        }
        
        updateNotification()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun startBlinking(profile: GlyphProfile) {
        blinkJob?.cancel()
        currentBlinkingProfileId = profile.id
        blinkJob = serviceScope.launch {
            while (isActive) {
                repeat(profile.repeatCount) {
                    glyphManager.toggleRedLed(true)
                    delay(profile.blinkDurationMs)
                    glyphManager.toggleRedLed(false)
                    if (it < profile.repeatCount - 1) {
                        delay(profile.blinkGapMs)
                    }
                }
                delay(profile.intervalMs)
            }
        }
    }

    private fun stopBlinking() {
        blinkJob?.cancel()
        blinkJob = null
        currentBlinkingProfileId = null
        glyphManager.toggleRedLed(false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // Already unregistered or not registered
        }
        stopBlinking()
        glyphManager.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Battery Glyph Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, BatteryService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Glyph Battery Notifier")
            .setContentText("Monitoring battery: $currentLevel%")
            .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Service", stopPendingIntent)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "battery_glyph_service_channel"
        const val ACTION_STOP_SERVICE = "com.example.nothingphonebatterynotifier.STOP_SERVICE"
        const val ACTION_TEST_BLINK = "com.example.nothingphonebatterynotifier.TEST_BLINK"
        const val ACTION_PREVIEW_PATTERN = "com.example.nothingphonebatterynotifier.PREVIEW_PATTERN"
    }
}

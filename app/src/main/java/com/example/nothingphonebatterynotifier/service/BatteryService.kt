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
import android.os.PowerManager
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
    private var isCharging: Boolean = false
    private var isTesting = false
    private var activeProfile: GlyphProfile? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            
            if (level != -1 && scale != -1) {
                val batteryPct = (level * 100 / scale.toFloat()).toInt()
                val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || 
                               status == BatteryManager.BATTERY_STATUS_FULL
                
                if (batteryPct != currentLevel || charging != isCharging) {
                    currentLevel = batteryPct
                    isCharging = charging
                    if (!isTesting) {
                        serviceScope.launch {
                            val profiles = profileRepository.profilesFlow.first()
                            evaluateProfiles(profiles)
                        }
                    }
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
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            if (level != -1 && scale != -1) {
                currentLevel = (level * 100 / scale.toFloat()).toInt()
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || 
                             status == BatteryManager.BATTERY_STATUS_FULL
                serviceScope.launch {
                    val profiles = profileRepository.profilesFlow.first()
                    evaluateProfiles(profiles)
                }
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
            try {
                isTesting = true
                stopBlinking()
                glyphManager.previewBlink(count, duration, gap)
            } finally {
                isTesting = false
                val profiles = profileRepository.profilesFlow.first()
                evaluateProfiles(profiles)
            }
        }
    }

    private fun runTestBlink() {
        serviceScope.launch {
            isTesting = true
            stopBlinking()
            repeat(2) {
                glyphManager.toggleRedLed(true)
                delay(500)
                glyphManager.toggleRedLed(false)
                delay(200)
            }
            isTesting = false
            val profiles = profileRepository.profilesFlow.first()
            evaluateProfiles(profiles)
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
        val nextProfile = if (isCharging) null else profiles
            .filter { it.enabled && currentLevel <= it.batteryThreshold }
            .minByOrNull { it.batteryThreshold }

        if (nextProfile != null) {
            // Check if profile changed or values within profile changed
            if (activeProfile != nextProfile) {
                activeProfile = nextProfile
                startBlinking(nextProfile)
            }
        } else {
            activeProfile = null
            stopBlinking()
        }
        updateNotification()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun startBlinking(profile: GlyphProfile) {
        blinkJob?.cancel()

        if (wakeLock == null) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BatteryGlyph:Blinking")
            // Use a 12 hour timeout as a safety measure
            wakeLock?.acquire(12 * 60 * 60 * 1000L)
        }

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

        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null

        glyphManager.toggleRedLed(false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {}
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
        val stopIntent = Intent(this, BatteryService::class.java).apply { action = ACTION_STOP_SERVICE }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

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

package com.example.nothingphonebatterynotifier

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.nothingphonebatterynotifier.data.ProfileRepository
import com.example.nothingphonebatterynotifier.glyph.GlyphManager
import com.example.nothingphonebatterynotifier.model.GlyphProfile
import com.example.nothingphonebatterynotifier.service.BatteryService
import com.example.nothingphonebatterynotifier.ui.theme.NothingPhoneBatteryNotifierTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var profileRepository: ProfileRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        profileRepository = ProfileRepository(this)
        enableEdgeToEdge()
        
        startForegroundService()

        setContent {
            NothingPhoneBatteryNotifierTheme {
                MainScreen(profileRepository)
            }
        }
    }

    private fun startForegroundService() {
        val intent = Intent(this, BatteryService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(repository: ProfileRepository) {
    val profiles by repository.profilesFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var profileToEdit by remember { mutableStateOf<GlyphProfile?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Glyph Battery Notifier") },
                actions = {
                    val context = LocalContext.current
                    IconButton(onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "App Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Profile")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(profiles, key = { it.id }) { profile ->
                ProfileItem(
                    profile = profile,
                    onClick = { profileToEdit = profile },
                    onDelete = { scope.launch { repository.deleteProfile(profile.id) } },
                    onToggle = { scope.launch { repository.updateProfile(profile.copy(enabled = !profile.enabled)) } }
                )
            }
        }

        if (showAddDialog) {
            EditProfileDialog(
                title = "Add Profile",
                onDismiss = { showAddDialog = false },
                onSave = { profile ->
                    scope.launch { repository.addProfile(profile) }
                    showAddDialog = false
                }
            )
        }

        BatteryOptimizationDialog()
        NotificationHiderDialog()

        profileToEdit?.let { profile ->
            EditProfileDialog(
                profile = profile,
                title = "Edit Profile",
                onDismiss = { profileToEdit = null },
                onSave = { updated ->
                    scope.launch { repository.updateProfile(updated) }
                    profileToEdit = null
                }
            )
        }
    }
}

@Composable
fun BatteryOptimizationDialog() {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                showDialog = true
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Disable Battery Optimization") },
            text = { Text("To ensure battery notifications work reliably in the background, please set battery usage to 'Unrestricted'.") },
            confirmButton = {
                Button(onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                    showDialog = false
                }) {
                    Text("Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Dismiss")
                }
            }
        )
    }
}

@Composable
fun NotificationHiderDialog() {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    val channelId = "battery_glyph_service_channel"

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = manager.getNotificationChannel(channelId)
            // If the channel exists and is not disabled, we can show the option to hide it
            if (channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE) {
                // We use a preference to only show this once, or show it as an optional setting
                // For now, let's just make it available via a button in the UI or a one-time dialog
                // Since the user asked for a modal on startup:
                val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
                val hasShown = prefs.getBoolean("notification_hider_shown", false)
                if (!hasShown) {
                    showDialog = true
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Hide Status Bar Icon?") },
            text = { Text("The 'Always On' notification is required by Android, but you can hide the icon from your status bar. Would you like to go to settings to disable the 'Battery Glyph Service' notification category?") },
            confirmButton = {
                Button(onClick = {
                    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
                    }
                    context.startActivity(intent)
                    context.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit()
                        .putBoolean("notification_hider_shown", true).apply()
                    showDialog = false
                }) {
                    Text("Go to Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    context.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit()
                        .putBoolean("notification_hider_shown", true).apply()
                    showDialog = false 
                }) {
                    Text("Keep Visible")
                }
            }
        )
    }
}

@Composable
fun ProfileItem(
    profile: GlyphProfile,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = profile.name, style = MaterialTheme.typography.titleMedium)
                Text(text = "Below ${profile.batteryThreshold}%")
                Text(
                    text = "${profile.repeatCount} blinks (${profile.blinkDurationMs}ms) every ${profile.intervalMs}ms",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(checked = profile.enabled, onCheckedChange = { onToggle() })
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}

@Composable
fun EditProfileDialog(
    profile: GlyphProfile? = null,
    title: String,
    onDismiss: () -> Unit,
    onSave: (GlyphProfile) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val glyphManager = remember { GlyphManager(context).apply { init() } }
    
    DisposableEffect(Unit) {
        onDispose {
            glyphManager.release()
        }
    }

    var name by remember { mutableStateOf(profile?.name ?: "") }
    var threshold by remember { mutableStateOf(profile?.batteryThreshold?.toString() ?: "20") }
    var duration by remember { mutableStateOf(profile?.blinkDurationMs?.toString() ?: "100") }
    var gap by remember { mutableStateOf(profile?.blinkGapMs?.toString() ?: "100") }
    var repeatCount by remember { mutableStateOf(profile?.repeatCount?.toString() ?: "1") }
    var interval by remember { mutableStateOf(profile?.intervalMs?.toString() ?: "1000") }

    val thresholdInt = threshold.toIntOrNull() ?: 0
    val durationLong = duration.toLongOrNull() ?: 0L
    val gapLong = gap.toLongOrNull() ?: 0L
    val repeatCountInt = repeatCount.toIntOrNull() ?: 0
    val intervalLong = interval.toLongOrNull() ?: 0L

    val isGapEnabled = repeatCountInt > 1
    
    val thresholdError = thresholdInt !in 1..100
    val repeatCountError = repeatCountInt < 1
    val intervalError = intervalLong <= 0
    val gapError = isGapEnabled && gapLong <= 0
    
    val totalBlinkTime = (repeatCountInt * durationLong) + (if (isGapEnabled) (repeatCountInt - 1) * gapLong else 0L)
    val durationError = durationLong <= 0 || totalBlinkTime >= intervalLong

    val hasError = thresholdError || repeatCountError || durationError || gapError || intervalError || name.isBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    isError = name.isBlank(),
                    singleLine = true
                )
                TextField(
                    value = threshold,
                    onValueChange = { if (it.all { c -> c.isDigit() }) threshold = it },
                    label = { Text("Battery Threshold (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = thresholdError,
                    singleLine = true
                )
                
                Text("Blink Pattern", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        modifier = Modifier.weight(1f),
                        value = repeatCount, 
                        onValueChange = { 
                            if (it.all { c -> c.isDigit() }) {
                                repeatCount = it
                            }
                        }, 
                        label = { Text("Count") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = repeatCountError,
                        singleLine = true
                    )
                    TextField(
                        modifier = Modifier.weight(1f),
                        value = duration, 
                        onValueChange = { if (it.all { c -> c.isDigit() }) duration = it }, 
                        label = { Text("Duration (ms)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = durationError,
                        singleLine = true,
                        supportingText = {
                            if (durationError && durationLong > 0) {
                                Text("Total cycle too long for Interval", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        modifier = Modifier.weight(1f),
                        value = gap, 
                        onValueChange = { if (it.all { c -> c.isDigit() }) gap = it }, 
                        label = { Text("Gap (ms)") },
                        enabled = isGapEnabled,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = gapError,
                        singleLine = true
                    )
                    TextField(
                        modifier = Modifier.weight(1f),
                        value = interval, 
                        onValueChange = { if (it.all { c -> c.isDigit() }) interval = it }, 
                        label = { Text("Interval (ms)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = intervalError,
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        glyphManager.previewBlink(
                            repeatCount = repeatCountInt,
                            blinkDurationMs = durationLong,
                            blinkGapMs = gapLong,
                            scope = scope
                        )
                    },
                    enabled = !hasError
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Test")
                }

                Row {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = {
                            val newProfile = profile?.copy(
                                name = name.trim(),
                                batteryThreshold = thresholdInt,
                                blinkDurationMs = durationLong,
                                blinkGapMs = if (isGapEnabled) gapLong else 0L,
                                repeatCount = repeatCountInt,
                                intervalMs = intervalLong
                            ) ?: GlyphProfile(
                                name = name.trim(),
                                batteryThreshold = thresholdInt,
                                blinkDurationMs = durationLong,
                                blinkGapMs = if (isGapEnabled) gapLong else 0L,
                                repeatCount = repeatCountInt,
                                intervalMs = intervalLong
                            )
                            onSave(newProfile)
                        },
                        enabled = !hasError
                    ) { Text("Save") }
                }
            }
        },
        dismissButton = {}
    )
}

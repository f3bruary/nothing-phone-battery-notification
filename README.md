# Nothing Phone (4a) Red LED Battery Notifier

A lightweight, robust background service for the Nothing Phone (4a) that repurposes the unique red Glyph LED to provide customizable battery level notifications.

## 🎯 The Goal

Standard battery notifications are often easy to miss or require turning on the screen. This app utilizes the Phone (4a)'s undocumented red LED to give you a persistent, ambient visual cue when your battery drops below specific thresholds, allowing you to monitor your power status without ever touching your phone.

## ✨ Key Perks

### 🔋 Ultra-Efficient Monitoring

Unlike other apps that constantly poll the system for battery data, this app uses a reactive broadcast architecture. It listens for system-level `ACTION_BATTERY_CHANGED` events, meaning it consumes zero CPU cycles until your battery percentage actually changes.

### 🛡️ Rock-Solid Background Stability

Built as a Foreground Service with a persistent notification, the app is designed to resist aggressive Android task-killing. It includes built-in guidance to help users navigate "Unrestricted" battery settings, ensuring the LED blinks reliably even during deep sleep.

### 🪶 Minimal Footprint (~2MB)

While many modern Compose apps exceed 50–60MB, this project has been surgically optimized:

- **Asset Stripping**: Removed heavy Material Icon libraries in favor of custom-coded XML vectors  
- **R8 Optimized**: Fully configured ProGuard/R8 rules to strip unused code while preserving the essential Nothing Glyph SDK and DataStore signatures  
- **Result**: A tiny, production-ready APK that doesn't bloat your storage  

### 🎨 Fully Customizable Profiles

- **Multiple Thresholds**: Create different blinking patterns for different levels (e.g., a slow blink at 20%, a frantic double-blink at 5%)  
- **Granular Pattern Control**: Customize Blink Duration, Blink Gap, Repeat Count, and Interval  
- **Live Testing**: Test your patterns directly from the edit screen—the app intelligently pauses background monitoring to give you a real-time hardware preview of your current settings before you save  

## 🛠 Technical Details

### The "Red LED" Secret

The Nothing Phone (4a) features a red LED that is not standard across the Nothing lineup. This app accesses it by:

1. Targeting the A069P (Phone 4a) device profile  
2. Utilizing an undocumented 7-element array in the Glyph SDK  
3. Injecting the brightness value at index 6 to trigger the red hardware component  

### Stack

- **Language**: Kotlin  
- **UI**: Jetpack Compose (Material 3)  
- **Persistence**: Jetpack DataStore (Preferences) + Gson  
- **Concurrency**: Kotlin Coroutines & Flows  
- **Hardware**: Nothing Ketchum SDK (2.0)  

## 🚀 Installation & Setup

1. Download the Release APK: Grab the latest `app-release.apk` from the releases section  
2. Notification Permissions: Grant notification access so the Foreground Service can remain active  
3. Battery Settings: For best results, go to *App Info > Battery* and select **"Unrestricted"**  
4. Hide the Icon (Optional): Use the built-in shortcut to hide the "Always On" notification icon while keeping the service running in the background  

## ⚠️ Requirements

- Nothing Phone (4a) (the red LED hardware is exclusive to this model)  

## 📜 License

This project is intended for personal use and enthusiasts. It is not affiliated with Nothing Technology Ltd.

---

Created with ❤️ for the Nothing Community

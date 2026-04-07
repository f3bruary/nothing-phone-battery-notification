# Gson specific rules
# Preserve all generic signatures and annotations
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# Keep Gson's own classes
-keep class com.google.gson.** { *; }

# Keep all subclasses of TypeToken, specifically for anonymous inner classes
# used for generic type capture.
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers class * extends com.google.gson.reflect.TypeToken { *; }

# Keep our data models and their fields so Gson can map them
-keep class com.example.nothingphonebatterynotifier.model.** { *; }
-keepclassmembers class com.example.nothingphonebatterynotifier.model.** { *; }

# Keep the Nothing Glyph SDK
-keep class com.nothing.ketchum.** { *; }
-keep interface com.nothing.ketchum.** { *; }

# Preserve reflection for the Glyph Manager
-keepclassmembers class com.example.nothingphonebatterynotifier.glyph.GlyphManager {
    public void toggleRedLed(boolean);
}

# Optional: keep line numbers for better crash reports
-keepattributes SourceFile, LineNumberTable

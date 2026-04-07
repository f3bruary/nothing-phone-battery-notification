package com.example.nothingphonebatterynotifier.glyph

import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.nothing.ketchum.GlyphFrame
import com.nothing.ketchum.GlyphManager as NothingGlyphManager
import kotlinx.coroutines.*

class GlyphManager(private val context: Context) {
    private var glyphManager: NothingGlyphManager? = null
    private var glyphCallback: NothingGlyphManager.Callback? = null

    fun init() {
        try {
            glyphManager = NothingGlyphManager.getInstance(context)
            glyphCallback = object : NothingGlyphManager.Callback {
                override fun onServiceConnected(componentName: ComponentName) {
                    try {
                        // Registering as A069P allows us to access the red LED via high index/undocumented methods
                        glyphManager?.register("A069P")
                        glyphManager?.openSession()
                    } catch (e: Exception) {
                        Log.e("GlyphManager", "Error in onServiceConnected", e)
                    }
                }

                override fun onServiceDisconnected(componentName: ComponentName) {}
            }
            glyphManager?.init(glyphCallback)
        } catch (e: Exception) {
            Log.e("GlyphManager", "Init failed", e)
        }
    }

    fun toggleRedLed(on: Boolean) {
        try {
            val managerClass = glyphManager?.javaClass
            val setFrameColors = managerClass?.methods?.find { 
                it.name == "setFrameColors" && it.parameterCount == 1 && it.parameterTypes[0] == IntArray::class.java 
            }

            if (setFrameColors != null) {
                // The undocumented 7-element array for Phone 2a Red LED (Index 6)
                val colors = IntArray(7) { 0 }
                if (on) colors[6] = 255 
                setFrameColors.invoke(glyphManager, colors)
            } else {
                if (!on) {
                    try {
                        glyphManager?.turnOff()
                    } catch (e: Exception) {
                        Log.e("GlyphManager", "Turn off error", e)
                    }
                    return
                }
                // Fallback attempt for different SDK versions
                val builder = glyphManager?.glyphFrameBuilder
                val buildChannel = try {
                    builder?.javaClass?.getMethod("buildChannel", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                } catch (e: Exception) { null }
                
                buildChannel?.invoke(builder, 6, 255)
                builder?.build()?.let { glyphManager?.toggle(it) }
            }
        } catch (e: Exception) {
            Log.e("GlyphManager", "Toggle error", e)
        }
    }

    fun release() {
        try {
            glyphManager?.closeSession()
            glyphManager?.unInit()
        } catch (e: Exception) {
            Log.e("GlyphManager", "Release error", e)
        }
    }

    /**
     * Executes a single blink pattern cycle once.
     */
    suspend fun previewBlink(
        repeatCount: Int,
        blinkDurationMs: Long,
        blinkGapMs: Long
    ) {
        for (i in 0 until repeatCount) {
            toggleRedLed(true)
            delay(blinkDurationMs)
            toggleRedLed(false)
            if (i < repeatCount - 1) {
                delay(blinkGapMs)
            }
        }
    }
}

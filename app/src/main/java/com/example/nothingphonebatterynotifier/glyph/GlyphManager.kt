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
    @Volatile private var isConnected = false
    private val connectionLock = Any()

    fun init() {
        synchronized(connectionLock) {
            if (isConnected) return
            try {
                Log.d("GlyphManager", "Initializing Glyph SDK...")
                glyphManager = NothingGlyphManager.getInstance(context)
                glyphCallback = object : NothingGlyphManager.Callback {
                    override fun onServiceConnected(componentName: ComponentName) {
                        try {
                            glyphManager?.register("A069P")
                            glyphManager?.openSession()
                            isConnected = true
                            Log.d("GlyphManager", "Glyph Service Connected & Registered")
                        } catch (e: Exception) {
                            Log.e("GlyphManager", "Registration failed", e)
                            isConnected = false
                        }
                    }

                    override fun onServiceDisconnected(componentName: ComponentName) {
                        isConnected = false
                        Log.d("GlyphManager", "Glyph Service Disconnected")
                    }
                }
                glyphManager?.init(glyphCallback)
            } catch (e: Exception) {
                Log.e("GlyphManager", "Init exception", e)
                isConnected = false
            }
        }
    }

    private fun ensureConnected() {
        if (!isConnected) {
            init()
            // Small wait to allow async connection to start
            var attempts = 0
            while (!isConnected && attempts < 10) {
                Thread.sleep(100)
                attempts++
            }
        }
    }

    fun toggleRedLed(on: Boolean) {
        ensureConnected()
        if (!isConnected) return

        try {
            val manager = glyphManager ?: return
            
            // Proactively try to keep session open
            try { manager.openSession() } catch (e: Exception) {}

            val managerClass = manager.javaClass
            val setFrameColors = managerClass.methods.find { 
                it.name == "setFrameColors" && it.parameterCount == 1 && it.parameterTypes[0] == IntArray::class.java 
            }

            if (setFrameColors != null) {
                val colors = IntArray(7) { 0 }
                if (on) colors[6] = 255 
                setFrameColors.invoke(manager, colors)
            } else {
                if (!on) {
                    manager.turnOff()
                } else {
                    val builder = manager.glyphFrameBuilder
                    val buildChannel = try {
                        builder?.javaClass?.getMethod("buildChannel", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    } catch (e: Exception) { null }
                    
                    buildChannel?.invoke(builder, 6, 255)
                    builder?.build()?.let { manager.toggle(it) }
                }
            }
        } catch (e: Exception) {
            Log.e("GlyphManager", "Toggle failed", e)
            isConnected = false
        }
    }

    fun release() {
        synchronized(connectionLock) {
            try {
                glyphManager?.closeSession()
                glyphManager?.unInit()
                isConnected = false
            } catch (e: Exception) {
                Log.e("GlyphManager", "Release error", e)
            }
        }
    }

    suspend fun previewBlink(repeatCount: Int, duration: Long, gap: Long) {
        for (i in 0 until repeatCount) {
            toggleRedLed(true)
            delay(duration)
            toggleRedLed(false)
            if (i < repeatCount - 1) delay(gap)
        }
    }
}

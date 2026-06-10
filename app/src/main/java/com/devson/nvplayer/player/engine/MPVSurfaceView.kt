package com.devson.nvplayer.player.engine

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import `is`.xyz.mpv.MPVLib
import java.lang.ref.WeakReference

/**
 * A custom SurfaceView that handles surface callbacks and automatically
 * bridges the lifecycle of the Surface to the native MPV engine.
 */
class MPVSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    companion object {
        private var activeSurfaceRef = WeakReference<Surface>(null)

        fun getActiveSurface(): Surface? {
            return activeSurfaceRef.get()
        }

        fun setActiveSurface(surface: Surface?) {
            activeSurfaceRef = WeakReference(surface)
        }
    }

    var onSurfaceCreatedListener: (() -> Unit)? = null
    var onSurfaceDestroyedListener: (() -> Unit)? = null

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d("MPVSurfaceView", "Surface created - attaching native surface to MPVLib")
        synchronized(MPVSurfaceView) {
            try {
                setActiveSurface(holder.surface)
                MPVLib.attachSurface(holder.surface)
                MPVLib.setOptionString("force-window", "yes")
                MPVLib.setPropertyString("vo", "gpu")
                onSurfaceCreatedListener?.invoke()
            } catch (e: Exception) {
                Log.e("MPVSurfaceView", "Error attaching surface to MPVLib", e)
            }
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d("MPVSurfaceView", "Surface changed - format: $format, width: $width, height: $height")
        synchronized(MPVSurfaceView) {
            try {
                MPVLib.setPropertyString("android-surface-size", "${width}x${height}")
            } catch (e: Exception) {
                Log.e("MPVSurfaceView", "Error setting surface size", e)
            }
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d("MPVSurfaceView", "Surface destroyed - detaching native surface from MPVLib")
        val surface = holder.surface
        synchronized(MPVSurfaceView) { // Synchronize on companion object to serialize native calls
            try {
                if (getActiveSurface() === surface) {
                    setActiveSurface(null)
                    
                    // MUST BE SYNCHRONOUS. Do not run this inside a background thread.
                    // The surface is invalidated by the system immediately when this method returns.
                    MPVLib.setPropertyString("vo", "null")
                    MPVLib.setOptionString("force-window", "no")
                    MPVLib.detachSurface()
                } else {
                    Log.d("MPVSurfaceView", "Surface destroyed matches an inactive surface. Skipping detach.")
                }
            } catch (e: Exception) {
                Log.e("MPVSurfaceView", "Error in surfaceDestroyed", e)
            }
        }
        onSurfaceDestroyedListener?.invoke()
    }
}
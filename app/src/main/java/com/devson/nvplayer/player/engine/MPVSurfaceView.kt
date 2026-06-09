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

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d("MPVSurfaceView", "Surface changed - format: $format, width: $width, height: $height")
        try {
            MPVLib.setPropertyString("android-surface-size", "${width}x${height}")
        } catch (e: Exception) {
            Log.e("MPVSurfaceView", "Error setting surface size", e)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d("MPVSurfaceView", "Surface destroyed - detaching native surface from MPVLib")
        val surface = holder.surface
        try {
            if (getActiveSurface() === surface) {
                setActiveSurface(null)
                Thread {
                    try {
                        MPVLib.setPropertyString("vo", "null")
                        MPVLib.setOptionString("force-window", "no")
                        MPVLib.detachSurface()
                    } catch (e: Exception) {
                        Log.e("MPVSurfaceView", "Error detaching surface from MPVLib in background thread", e)
                    }
                }.start()
            } else {
                Log.d("MPVSurfaceView", "Surface destroyed matches an inactive surface. Skipping detach.")
            }
        } catch (e: Exception) {
            Log.e("MPVSurfaceView", "Error in surfaceDestroyed", e)
        }
        onSurfaceDestroyedListener?.invoke()
    }
}
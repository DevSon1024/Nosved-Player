package com.devson.nvplayer.player

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import `is`.xyz.mpv.MPVLib

/**
 * A custom SurfaceView that handles surface callbacks and automatically
 * bridges the lifecycle of the Surface to the native MPV engine.
 */
class MPVSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d("MPVSurfaceView", "Surface created - attaching native surface to MPVLib")
        try {
            MPVLib.attachSurface(holder.surface)
        } catch (e: Exception) {
            Log.e("MPVSurfaceView", "Error attaching surface to MPVLib", e)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d("MPVSurfaceView", "Surface changed - format: $format, width: $width, height: $height")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d("MPVSurfaceView", "Surface destroyed - detaching native surface from MPVLib")
        try {
            MPVLib.detachSurface()
        } catch (e: Exception) {
            Log.e("MPVSurfaceView", "Error detaching surface from MPVLib", e)
        }
    }
}

package com.devson.nvplayer.util

import android.os.Build
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight, non-intrusive Phase 0 performance instrumentation using Android [Trace]
 * markers and monotonic [SystemClock.elapsedRealtime] timestamps.
 */
object PerfTracer {
    private const val TAG = "NosvedPerf"
    private const val COOKIE_APP_STARTUP = 1001
    private const val COOKIE_LIBRARY_LOAD = 1002
    private const val COOKIE_VIDEO_LAUNCH = 1003

    private val appInitStartMs = AtomicLong(0L)
    private val firstScreenComposeLogged = AtomicBoolean(false)
    private val firstScreenDisplayedLogged = AtomicBoolean(false)
    private val initialLibraryLoadLogged = AtomicBoolean(false)
    private val libraryLoadStartMs = AtomicLong(0L)

    private val videoLaunchActive = AtomicBoolean(false)
    private val videoClickStartMs = AtomicLong(0L)
    private val playerScreenComposeLogged = AtomicBoolean(false)
    private val fileLoadedForCurrentLaunch = AtomicBoolean(false)
    private val firstVideoFrameLogged = AtomicBoolean(false)

    inline fun <T> traceSection(name: String, block: () -> T): T {
        Trace.beginSection(name)
        return try {
            block()
        } finally {
            Trace.endSection()
        }
    }

    fun markAppInitStart() {
        val now = SystemClock.elapsedRealtime()
        appInitStartMs.compareAndSet(0L, now)
        beginAsyncTrace("Nosved:AppStartup", COOKIE_APP_STARTUP)
        Log.i(TAG, "[STARTUP] app_init_start t=${now}ms")
    }

    fun markAppInitEnd() {
        val now = SystemClock.elapsedRealtime()
        val delta = now - appInitStartMs.get()
        Log.i(TAG, "[STARTUP] app_init_end t=${now}ms (+${delta}ms)")
    }

    fun markMainActivityCreateStart() {
        val now = SystemClock.elapsedRealtime()
        Log.i(TAG, "[STARTUP] main_activity_create_start t=${now}ms (+${sinceAppStart(now)}ms)")
    }

    fun markMainActivityCreateEnd() {
        val now = SystemClock.elapsedRealtime()
        Log.i(TAG, "[STARTUP] main_activity_create_end t=${now}ms (+${sinceAppStart(now)}ms)")
    }

    fun markFirstScreenComposition() {
        if (firstScreenComposeLogged.compareAndSet(false, true)) {
            val now = SystemClock.elapsedRealtime()
            Log.i(TAG, "[STARTUP] first_screen_composition t=${now}ms (+${sinceAppStart(now)}ms)")
        }
    }

    fun markFirstScreenDisplayed() {
        if (firstScreenDisplayedLogged.compareAndSet(false, true)) {
            val now = SystemClock.elapsedRealtime()
            endAsyncTrace("Nosved:AppStartup", COOKIE_APP_STARTUP)
            Log.i(TAG, "[STARTUP] first_screen_displayed t=${now}ms (+${sinceAppStart(now)}ms)")
        }
    }

    fun markLibraryLoadStart() {
        if (libraryLoadStartMs.compareAndSet(0L, SystemClock.elapsedRealtime())) {
            beginAsyncTrace("Nosved:LibraryLoad", COOKIE_LIBRARY_LOAD)
            val start = libraryLoadStartMs.get()
            Log.i(TAG, "[STARTUP] initial_library_load_start t=${start}ms (+${sinceAppStart(start)}ms)")
        }
    }

    fun markLibraryLoadEnd(videoCount: Int, folderCount: Int) {
        if (initialLibraryLoadLogged.compareAndSet(false, true)) {
            val now = SystemClock.elapsedRealtime()
            val start = libraryLoadStartMs.get()
            val loadDelta = if (start > 0L) (now - start).coerceAtLeast(0L) else 0L
            endAsyncTrace("Nosved:LibraryLoad", COOKIE_LIBRARY_LOAD)
            Log.i(
                TAG,
                "[STARTUP] initial_library_load_end t=${now}ms loadDuration=${loadDelta}ms (+${sinceAppStart(now)}ms) videos=$videoCount folders=$folderCount"
            )
        }
    }

    fun markVideoClick(source: String) {
        val now = SystemClock.elapsedRealtime()
        videoClickStartMs.set(now)
        videoLaunchActive.set(true)
        playerScreenComposeLogged.set(false)
        fileLoadedForCurrentLaunch.set(false)
        firstVideoFrameLogged.set(false)
        beginAsyncTrace("Nosved:VideoLaunch", COOKIE_VIDEO_LAUNCH)
        Log.i(TAG, "[VIDEO] video_click source=$source t=${now}ms")
    }

    fun markPlayerNavigationStart(uriString: String) {
        val now = SystemClock.elapsedRealtime()
        if (!videoLaunchActive.get()) {
            videoClickStartMs.set(now)
            videoLaunchActive.set(true)
            playerScreenComposeLogged.set(false)
            fileLoadedForCurrentLaunch.set(false)
            firstVideoFrameLogged.set(false)
            beginAsyncTrace("Nosved:VideoLaunch", COOKIE_VIDEO_LAUNCH)
        }
        Log.i(TAG, "[VIDEO] player_navigation_start t=${now}ms (+${sinceVideoClick(now)}ms) uri=$uriString")
    }

    fun markPlayerScreenComposition() {
        if (videoLaunchActive.get() && playerScreenComposeLogged.compareAndSet(false, true)) {
            val now = SystemClock.elapsedRealtime()
            Log.i(TAG, "[VIDEO] player_screen_composition t=${now}ms (+${sinceVideoClick(now)}ms)")
        }
    }

    fun markSurfaceCreated() {
        if (videoLaunchActive.get()) {
            val now = SystemClock.elapsedRealtime()
            Log.i(TAG, "[VIDEO] surface_created t=${now}ms (+${sinceVideoClick(now)}ms)")
        }
    }

    fun markSurfaceAttached() {
        if (videoLaunchActive.get()) {
            val now = SystemClock.elapsedRealtime()
            Log.i(TAG, "[VIDEO] surface_attached t=${now}ms (+${sinceVideoClick(now)}ms)")
        }
    }

    fun markMpvLoadfileInvoked() {
        if (videoLaunchActive.get()) {
            val now = SystemClock.elapsedRealtime()
            Log.i(TAG, "[VIDEO] mpv_loadfile_invoked t=${now}ms (+${sinceVideoClick(now)}ms)")
        }
    }

    fun markMpvFileLoaded() {
        if (videoLaunchActive.get()) {
            fileLoadedForCurrentLaunch.set(true)
            val now = SystemClock.elapsedRealtime()
            Log.i(TAG, "[VIDEO] mpv_file_loaded t=${now}ms (+${sinceVideoClick(now)}ms)")
        }
    }

    fun markFirstVideoFrameRendered(trigger: String) {
        if (videoLaunchActive.get() &&
            fileLoadedForCurrentLaunch.get() &&
            firstVideoFrameLogged.compareAndSet(false, true)
        ) {
            val now = SystemClock.elapsedRealtime()
            val totalMs = sinceVideoClick(now)
            videoLaunchActive.set(false)
            endAsyncTrace("Nosved:VideoLaunch", COOKIE_VIDEO_LAUNCH)
            Log.i(TAG, "[VIDEO] first_video_frame_rendered trigger=$trigger t=${now}ms total=${totalMs}ms")
        }
    }

    private fun sinceAppStart(nowMs: Long): Long {
        val start = appInitStartMs.get()
        return if (start > 0L) (nowMs - start).coerceAtLeast(0L) else 0L
    }

    private fun sinceVideoClick(nowMs: Long): Long {
        val start = videoClickStartMs.get()
        return if (start > 0L) (nowMs - start).coerceAtLeast(0L) else 0L
    }

    private fun beginAsyncTrace(name: String, cookie: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Trace.beginAsyncSection(name, cookie)
        }
    }

    private fun endAsyncTrace(name: String, cookie: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Trace.endAsyncSection(name, cookie)
        }
    }
}


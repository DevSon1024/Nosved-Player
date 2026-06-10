package com.devson.nvplayer.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object MpvConfigRepository {
    private const val FILE_NAME = "mpv.conf"

    private val DEFAULT_CONFIG = """
        # Nosved Player Custom MPV Configuration (mpv.conf)
        # You can add/modify any standard mpv options here.
        # Lines starting with # are comments.

        # --- Video Output Settings ---
        # vo: specifies the video output driver. e.g. gpu or gpu-next
        vo=gpu
        # gpu-context: specifies the graphics context. e.g. android
        gpu-context=android

        # --- Audio Output Settings ---
        # ao: specifies the audio output driver. e.g. aaudio, audiotrack, opensles
        ao=aaudio

        # --- Hardware Decoding ---
        # hwdec: specifies hardware decoder. e.g. mediacodec, mediacodec-copy, no
        hwdec=mediacodec

        # --- Other Settings ---
        # profile: e.g. fast
        profile=fast
        # keep-open: yes/no
        keep-open=yes
    """.trimIndent()

    suspend fun loadMpvConfig(context: Context): String = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) {
            try {
                file.readText()
            } catch (e: Exception) {
                DEFAULT_CONFIG
            }
        } else {
            // Write default config so the file exists next time
            try {
                file.writeText(DEFAULT_CONFIG)
            } catch (e: Exception) {
                // Ignore writing error and return default
            }
            DEFAULT_CONFIG
        }
    }

    suspend fun saveMpvConfig(context: Context, content: String): Boolean =
        withContext(Dispatchers.IO) {
            val file = File(context.filesDir, FILE_NAME)
            try {
                file.writeText(content)
                true
            } catch (e: Exception) {
                false
            }
        }
}
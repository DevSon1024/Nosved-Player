package com.devson.nosvedplayer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.os.ResultReceiver
import androidx.core.app.NotificationCompat
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode

class MediaConverterService : Service() {

    companion object {
        const val ACTION_CONVERT = "com.devson.nosvedplayer.CONVERT"
        const val EXTRA_RECEIVER = "receiver"
        const val EXTRA_CMD_ARGS = "cmd_args"
        const val RESULT_PROGRESS = 1
        const val RESULT_SUCCESS = 2
        const val RESULT_ERROR = 3
        const val KEY_PROGRESS = "progress"
        const val KEY_PATH = "path"
        const val KEY_MSG = "msg"
        private const val CHANNEL_ID = "converter_channel"
        private const val NOTIF_ID = 9001
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_CONVERT) { stopSelf(startId); return START_NOT_STICKY }

        val receiver = intent.getParcelableExtra<ResultReceiver>(EXTRA_RECEIVER)
        val args = intent.getStringArrayListExtra(EXTRA_CMD_ARGS)

        if (args == null || receiver == null) { stopSelf(startId); return START_NOT_STICKY }

        Thread {
            val cmdArray = args.toTypedArray()
            val session = FFmpegKit.executeWithArguments(cmdArray)
            val rc = session.returnCode
            val resultData = android.os.Bundle()
            if (ReturnCode.isSuccess(rc)) {
                resultData.putString(KEY_PATH, args.last())
                receiver.send(RESULT_SUCCESS, resultData)
            } else {
                resultData.putString(KEY_MSG, session.failStackTrace ?: "FFmpeg failed")
                receiver.send(RESULT_ERROR, resultData)
            }
            stopSelf(startId)
        }.start()

        return START_NOT_STICKY
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Media Converter", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Converting media...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
}

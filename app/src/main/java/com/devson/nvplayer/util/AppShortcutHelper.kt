package com.devson.nvplayer.util

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.devson.nvplayer.MainActivity
import com.devson.nvplayer.R

object AppShortcutHelper {

    const val ACTION_SHORTCUT_VIDEO_LIST = "com.devson.nvplayer.action.SHORTCUT_VIDEO_LIST"
    const val ACTION_SHORTCUT_VAULT = "com.devson.nvplayer.action.SHORTCUT_VAULT"
    const val ACTION_SHORTCUT_RECYCLE_BIN = "com.devson.nvplayer.action.SHORTCUT_RECYCLE_BIN"
    const val ACTION_SHORTCUT_NETWORK_STREAM = "com.devson.nvplayer.action.SHORTCUT_NETWORK_STREAM"

    const val EXTRA_SHORTCUT_DESTINATION = "shortcut_destination"

    fun syncShortcuts(context: Context) {
        try {
            val videoListShortcut = ShortcutInfoCompat.Builder(context, "shortcut_video_list")
                .setShortLabel(context.getString(R.string.shortcut_videos_short))
                .setLongLabel(context.getString(R.string.shortcut_videos_long))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_videos))
                .setIntent(
                    Intent(context, MainActivity::class.java).apply {
                        setPackage(context.packageName)
                        action = Intent.ACTION_VIEW
                        putExtra(EXTRA_SHORTCUT_DESTINATION, "video_list")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                )
                .setRank(0)
                .build()

            val vaultShortcut = ShortcutInfoCompat.Builder(context, "shortcut_vault")
                .setShortLabel(context.getString(R.string.shortcut_vault_short))
                .setLongLabel(context.getString(R.string.shortcut_vault_long))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_vault))
                .setIntent(
                    Intent(context, MainActivity::class.java).apply {
                        setPackage(context.packageName)
                        action = Intent.ACTION_VIEW
                        putExtra(EXTRA_SHORTCUT_DESTINATION, "vault")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                )
                .setRank(1)
                .build()

            val recycleBinShortcut = ShortcutInfoCompat.Builder(context, "shortcut_recycle_bin")
                .setShortLabel(context.getString(R.string.shortcut_recycle_bin_short))
                .setLongLabel(context.getString(R.string.shortcut_recycle_bin_long))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_recycle_bin))
                .setIntent(
                    Intent(context, MainActivity::class.java).apply {
                        setPackage(context.packageName)
                        action = Intent.ACTION_VIEW
                        putExtra(EXTRA_SHORTCUT_DESTINATION, "recycle_bin")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                )
                .setRank(2)
                .build()

            val networkShortcut = ShortcutInfoCompat.Builder(context, "shortcut_network_streaming")
                .setShortLabel(context.getString(R.string.shortcut_network_short))
                .setLongLabel(context.getString(R.string.shortcut_network_long))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_network))
                .setIntent(
                    Intent(context, MainActivity::class.java).apply {
                        setPackage(context.packageName)
                        action = Intent.ACTION_VIEW
                        putExtra(EXTRA_SHORTCUT_DESTINATION, "network_history")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                )
                .setRank(3)
                .build()

            ShortcutManagerCompat.setDynamicShortcuts(
                context,
                listOf(videoListShortcut, vaultShortcut, recycleBinShortcut, networkShortcut)
            )
        } catch (_: Exception) {
            // Graceful degradation
        }
    }
}

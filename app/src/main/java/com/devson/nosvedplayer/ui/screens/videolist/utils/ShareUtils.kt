package com.devson.nosvedplayer.ui.screens.videolist.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.devson.nosvedplayer.model.Video
import java.util.ArrayList

fun shareVideos(context: Context, videos: List<Video>) {
    if (videos.isEmpty()) return
    val uris = videos.map { Uri.parse(it.uri) }
    
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, uris.first())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "video/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    
    context.startActivity(Intent.createChooser(intent, "Share Video"))
}

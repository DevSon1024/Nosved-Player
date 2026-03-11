package com.devson.nosvedplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.ui.screens.VideoListScreen
import com.devson.nosvedplayer.ui.screens.VideoScreen
import com.devson.nosvedplayer.ui.theme.NosvedPlayerTheme
import com.devson.nosvedplayer.viewmodel.VideoViewModel

class MainActivity : ComponentActivity() {

    private var videoViewModelRef: VideoViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NosvedPlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentVideo by remember { mutableStateOf<Video?>(null) }
                    val videoViewModel: VideoViewModel = viewModel()
                    videoViewModelRef = videoViewModel

                    if (currentVideo == null) {
                        VideoListScreen(
                            onVideoSelected = { video ->
                                currentVideo = video
                                videoViewModel.playVideo(video)
                            }
                        )
                    } else {
                        // Always pause (never toggle) when leaving the player screen
                        BackHandler {
                            videoViewModel.pauseVideo()
                            currentVideo = null
                        }

                        VideoScreen(
                            viewModel = videoViewModel
                        )
                    }
                }
            }
        }
    }

    // Pause playback when app goes to background
    override fun onPause() {
        super.onPause()
        videoViewModelRef?.pauseVideo()
    }

    // Resume playback when app returns to foreground (only if there's an active video)
    override fun onResume() {
        super.onResume()
        videoViewModelRef?.resumeVideo()
    }
}
package com.devson.nvplayer

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModelProvider
import com.devson.nvplayer.player.MPVPlayerEngine
import com.devson.nvplayer.ui.screen.PlayerScreen
import com.devson.nvplayer.ui.theme.nvplayerTheme
import com.devson.nvplayer.viewmodel.PlayerViewModel

class MainActivity : ComponentActivity() {

    private lateinit var playerViewModel: PlayerViewModel

    // Setup Storage Access Framework (SAF) File Picker Launcher
    private val openVideoLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            playerViewModel.loadVideo(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Initialize native MPV Player Engine
        val playerEngine = MPVPlayerEngine(applicationContext)

        // 2. Instantiate ViewModel using customized factory
        val factory = PlayerViewModel.Factory(application, playerEngine)
        playerViewModel = ViewModelProvider(this, factory)[PlayerViewModel::class.java]

        // 3. Render modern player application layout
        setContent {
            nvplayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    val playbackState by playerViewModel.playbackState.collectAsState()
                    val isPlaying by playerViewModel.isPlaying.collectAsState()
                    val currentPosition by playerViewModel.currentPosition.collectAsState()
                    val duration by playerViewModel.duration.collectAsState()
                    val currentUri by playerViewModel.currentUri.collectAsState()

                    PlayerScreen(
                        playbackState = playbackState,
                        isPlaying = isPlaying,
                        currentPosition = currentPosition,
                        duration = duration,
                        currentUri = currentUri,
                        onPlayPauseToggle = { playerViewModel.togglePlayback() },
                        onSeek = { playerViewModel.seekTo(it) },
                        onOpenFilePicker = {
                            // Only query video and audio content formats
                            openVideoLauncher.launch(
                                arrayOf("video/*", "audio/*")
                            )
                        }
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Auto-pause media playback when activity transitions to background
        if (::playerViewModel.isInitialized) {
            playerViewModel.pause()
        }
    }
}
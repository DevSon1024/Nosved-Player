package com.devson.nvplayer.ui.screen.vault

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devson.nvplayer.data.database.VaultEntity
import com.devson.nvplayer.domain.model.Video
import com.devson.nvplayer.viewmodel.VaultAuthState
import com.devson.nvplayer.viewmodel.VaultAuthViewModel
import com.devson.nvplayer.viewmodel.VaultGalleryViewModel
import java.io.File

@Composable
fun VaultScreen(
    authViewModel: VaultAuthViewModel,
    galleryViewModel: VaultGalleryViewModel,
    onPlayMedia: (VaultEntity, File, Video) -> Unit,
    modifier: Modifier = Modifier
) {
    val authState by authViewModel.authState.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (authViewModel.authState.value !is VaultAuthState.Authenticated && !authViewModel.isConfiguringStorage) {
                    authViewModel.checkPinStatus()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        if (authState !is VaultAuthState.Authenticated) {
            authViewModel.checkPinStatus()
        }
    }

    AnimatedContent(
        targetState = authState is VaultAuthState.Authenticated,
        transitionSpec = {
            (fadeIn(animationSpec = tween(350)) togetherWith fadeOut(animationSpec = tween(250))).using(null)
        },
        label = "vault_screen_transition",
        modifier = modifier.fillMaxSize()
    ) { isAuthenticated ->
        if (isAuthenticated) {
            VaultGalleryScreen(
                viewModel = galleryViewModel,
                onLockClick = { authViewModel.lockVault() },
                onPlayMedia = onPlayMedia,
                onResetVaultClick = { authViewModel.requestVaultReset() },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            VaultAuthScreen(
                viewModel = authViewModel,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

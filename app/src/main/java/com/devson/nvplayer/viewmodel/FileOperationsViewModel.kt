package com.devson.nvplayer.viewmodel

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FileOperationsViewModel(application: Application) : AndroidViewModel(application) {

    private val _pendingIntentSender = MutableStateFlow<IntentSender?>(null)
    val pendingIntentSender: StateFlow<IntentSender?> = _pendingIntentSender.asStateFlow()

    private val _operationResult = MutableStateFlow<String?>(null)
    val operationResult: StateFlow<String?> = _operationResult.asStateFlow()

    private val _needsRefresh = MutableStateFlow(false)
    val needsRefresh: StateFlow<Boolean> = _needsRefresh.asStateFlow()

    private val _operationInProgress = MutableStateFlow(false)
    val operationInProgress: StateFlow<Boolean> = _operationInProgress.asStateFlow()

    private var pendingOperation: (suspend (Context) -> Unit)? = null

    fun clearPendingIntentSender() {
        _pendingIntentSender.value = null
    }

    fun clearResult() {
        _operationResult.value = null
    }

    fun onRefreshHandled() {
        _needsRefresh.value = false
    }

    fun onPermissionGranted(context: Context) {
        viewModelScope.launch {
            val op = pendingOperation
            pendingOperation = null
            if (op != null) {
                _operationInProgress.value = true
                try {
                    op(context)
                } catch (e: Exception) {
                    _operationResult.value = "Error executing operation: ${e.message}"
                } finally {
                    _operationInProgress.value = false
                }
            }
        }
    }

    fun renameVideo(context: Context, uri: Uri, newName: String) {
        viewModelScope.launch {
            _operationInProgress.value = true
            val success = withContext(Dispatchers.IO) {
                try {
                    val resolver = context.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, "$newName.mp4")
                    }
                    resolver.update(uri, values, null, null)
                    true
                } catch (securityException: SecurityException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && securityException is RecoverableSecurityException) {
                        _pendingIntentSender.value = securityException.userAction.actionIntent.intentSender
                        pendingOperation = { ctx -> renameVideo(ctx, uri, newName) }
                    } else {
                        securityException.printStackTrace()
                    }
                    false
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }

            _operationInProgress.value = false
            if (success) {
                _operationResult.value = "Video renamed successfully"
                _needsRefresh.value = true
            }
        }
    }

    fun renameFolder(context: Context, folderPath: String, newName: String) {
        viewModelScope.launch {
            _operationInProgress.value = true
            val success = withContext(Dispatchers.IO) {
                try {
                    val folder = File(folderPath)
                    if (folder.exists() && folder.isDirectory) {
                        val parent = folder.parentFile
                        val newFolder = File(parent, newName)
                        folder.renameTo(newFolder)
                    } else {
                        false
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }

            _operationInProgress.value = false
            if (success) {
                _operationResult.value = "Folder renamed successfully"
                _needsRefresh.value = true
            } else {
                _operationResult.value = "Failed to rename folder (Scoped Storage limitation)"
            }
        }
    }

    fun deleteVideos(context: Context, uris: List<Uri>, trash: Boolean) {
        viewModelScope.launch {
            if (uris.isEmpty()) return@launch
            _operationInProgress.value = true
            val success = withContext(Dispatchers.IO) {
                try {
                    val resolver = context.contentResolver
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && trash) {
                        // Use MediaStore.createTrashRequest on Android 11+
                        val pendingIntent = MediaStore.createTrashRequest(resolver, uris, true)
                        _pendingIntentSender.value = pendingIntent.intentSender
                        pendingOperation = { ctx -> deleteVideos(ctx, uris, trash = true) }
                        false
                    } else {
                        // Permanent delete or legacy delete
                        var count = 0
                        uris.forEach { uri ->
                            count += resolver.delete(uri, null, null)
                        }
                        count > 0
                    }
                } catch (securityException: SecurityException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && securityException is RecoverableSecurityException) {
                        _pendingIntentSender.value = securityException.userAction.actionIntent.intentSender
                        pendingOperation = { ctx -> deleteVideos(ctx, uris, trash) }
                    } else {
                        securityException.printStackTrace()
                    }
                    false
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }

            _operationInProgress.value = false
            if (success) {
                _operationResult.value = "${uris.size} video(s) deleted"
                _needsRefresh.value = true
            }
        }
    }
}

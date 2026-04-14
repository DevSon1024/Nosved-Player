package com.devson.nosvedplayer.viewmodel

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.media.MediaScannerConnection
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tracks which file action is waiting for a system permission grant (IntentSender result).
 */
sealed class PendingFileAction {
    data class Delete(val uris: List<Uri>, val trash: Boolean = false) : PendingFileAction()
    data class Restore(val uris: List<Uri>) : PendingFileAction()
    data class Rename(val uri: Uri, val newName: String) : PendingFileAction()
}

/**
 * ViewModel that handles all MediaStore / Scoped Storage file operations
 * (delete, rename, copy, move) with correct API-level handling.
 */
class FileOperationsViewModel(application: Application) : AndroidViewModel(application) {

    private val _operationInProgress = MutableStateFlow(false)
    val operationInProgress: StateFlow<Boolean> = _operationInProgress.asStateFlow()

    /** Non-null = show this message as a Toast then clear. */
    private val _operationResult = MutableStateFlow<String?>(null)
    val operationResult: StateFlow<String?> = _operationResult.asStateFlow()

    /**
     * Non-null = the screen must launch this IntentSender via the ActivityResult launcher.
     * After the sender is consumed/launched, call [clearPendingIntentSender].
     */
    private val _pendingIntentSender = MutableStateFlow<android.content.IntentSender?>(null)
    val pendingIntentSender: StateFlow<android.content.IntentSender?> = _pendingIntentSender.asStateFlow()

    /** Stored so we know what to execute once the user grants permission. */
    private var pendingAction: PendingFileAction? = null

    // 
    // PUBLIC API
    // 

    fun clearResult() { _operationResult.value = null }
    fun clearPendingIntentSender() { _pendingIntentSender.value = null }

    //  DELETE 

    fun deleteVideos(context: Context, uris: List<Uri>, trash: Boolean = false) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _operationInProgress.value = true
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val pi = if (trash) MediaStore.createTrashRequest(context.contentResolver, uris, true)
                             else MediaStore.createDeleteRequest(context.contentResolver, uris)
                    pendingAction = PendingFileAction.Delete(uris, trash)
                    _pendingIntentSender.value = pi.intentSender
                } else {
                    executeDeleteApi29(context, uris)
                }
            } catch (e: Exception) {
                _operationResult.value = "Delete failed: ${e.localizedMessage}"
            } finally {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    _operationInProgress.value = false
                }
                // For API 30+ we leave progress=true until onPermissionGranted is called
            }
        }
    }

    /** Single entry point called by the screen on any RESULT_OK from the IntentSender launcher. */
    fun onPermissionGranted(context: Context) {
        when (pendingAction) {
            is PendingFileAction.Delete -> onDeletePermissionGranted(context)
            is PendingFileAction.Restore -> onRestorePermissionGranted(context)
            is PendingFileAction.Rename -> onRenamePermissionGranted(context)
            null -> {}
        }
    }

    fun restoreVideos(context: Context, uris: List<Uri>) {
        if (uris.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        viewModelScope.launch {
            _operationInProgress.value = true
            try {
                val pi = MediaStore.createTrashRequest(context.contentResolver, uris, false)
                pendingAction = PendingFileAction.Restore(uris)
                _pendingIntentSender.value = pi.intentSender
            } catch (e: Exception) {
                _operationResult.value = "Restore failed: ${e.localizedMessage}"
                _operationInProgress.value = false
            }
        }
    }

    fun onRestorePermissionGranted(context: Context) {
        val action = pendingAction as? PendingFileAction.Restore ?: return
        pendingAction = null
        _operationResult.value = "Successfully restored ${action.uris.size} videos."
        _needsRefresh.value = true
        _operationInProgress.value = false
    }

    private val _needsRefresh = MutableStateFlow(false)
    val needsRefresh: StateFlow<Boolean> = _needsRefresh.asStateFlow()

    fun onRefreshHandled() { _needsRefresh.value = false }

    /** Called by the screen when the ActivityResult from the delete IntentSender returns RESULT_OK. */
    fun onDeletePermissionGranted(context: Context) {
        val action = pendingAction as? PendingFileAction.Delete ?: return
        pendingAction = null
        viewModelScope.launch {
            try {
                val deletedCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    action.uris.size 
                } else {
                    withContext(Dispatchers.IO) {
                        action.uris.count { uri ->
                            try { context.contentResolver.delete(uri, null, null) > 0 } catch (e: Exception) { false }
                        }
                    }
                }
                _operationResult.value = "Successfully deleted $deletedCount videos."
                _needsRefresh.value = true
            } catch (e: Exception) {
                _operationResult.value = "Delete failed: ${e.localizedMessage}"
            } finally {
                _operationInProgress.value = false
            }
        }
    }

    //  RENAME 
    fun renameVideo(context: Context, uri: Uri, newName: String) {
        viewModelScope.launch {
            _operationInProgress.value = true
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // API 30+: request write permission first
                    val pi = MediaStore.createWriteRequest(context.contentResolver, listOf(uri))
                    pendingAction = PendingFileAction.Rename(uri, newName)
                    _pendingIntentSender.value = pi.intentSender
                } else {
                    // API 29: try direct update
                    executeRenameApi29(context, uri, newName)
                    _operationInProgress.value = false
                }
            } catch (e: Exception) {
                _operationResult.value = "Rename failed: ${e.localizedMessage}"
                _operationInProgress.value = false
            }
        }
    }

    /**
     * Renames a folder using the File API.
     * Assumes MANAGE_EXTERNAL_STORAGE is granted on API 30+.
     */
    fun renameFolder(context: Context, folderPath: String, newName: String) {
        viewModelScope.launch {
            _operationInProgress.value = true
            try {
                withContext(Dispatchers.IO) {
                    val oldFolder = java.io.File(folderPath)
                    if (!oldFolder.exists()) throw IllegalStateException("Folder does not exist.")
                    
                    val newFolder = java.io.File(oldFolder.parentFile, newName)
                    if (newFolder.exists()) throw IllegalStateException("A folder with this name already exists.")
                    
                    if (oldFolder.renameTo(newFolder)) {
                        // Scan all files in the new folder to update MediaStore
                        val filesToScan = mutableListOf<String>()
                        fun collectFiles(file: java.io.File) {
                            if (file.isDirectory) {
                                file.listFiles()?.forEach { collectFiles(it) }
                            } else {
                                filesToScan.add(file.absolutePath)
                            }
                        }
                        collectFiles(newFolder)
                        
                        if (filesToScan.isNotEmpty()) {
                            MediaScannerConnection.scanFile(context, filesToScan.toTypedArray(), null, null)
                        }
                        _operationResult.value = "Folder renamed successfully."
                    } else {
                        throw IllegalStateException("Rename failed.")
                    }
                }
            } catch (e: Exception) {
                _operationResult.value = "Rename failed: ${e.localizedMessage}"
            } finally {
                _operationInProgress.value = false
            }
        }
    }

    /** Called by the screen when the ActivityResult from the rename IntentSender returns RESULT_OK. */
    fun onRenamePermissionGranted(context: Context) {
        val action = pendingAction as? PendingFileAction.Rename ?: return
        pendingAction = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, action.newName)
                    }
                    context.contentResolver.update(action.uri, values, null, null)
                }
                _operationResult.value = "Renamed to \"${action.newName}\" successfully."
            } catch (e: Exception) {
                _operationResult.value = "Rename failed: ${e.localizedMessage}"
            } finally {
                _operationInProgress.value = false
            }
        }
    }

    //  COPY 

    /**
     * Copies all [uris] into the [targetTreeUri] directory chosen via OpenDocumentTree.
     * Uses SAF DocumentFile API which works across all API levels without permission dialogs.
     */
    fun copyVideos(context: Context, uris: List<Uri>, targetTreeUri: Uri) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _operationInProgress.value = true
            var successCount = 0
            var failCount = 0
            try {
                withContext(Dispatchers.IO) {
                    val targetDir = DocumentFile.fromTreeUri(context, targetTreeUri)
                        ?: throw IllegalStateException("Cannot access target directory.")
                    for (uri in uris) {
                        try {
                            // Derive filename from MediaStore display name
                            val fileName = getDisplayName(context, uri) ?: uri.lastPathSegment ?: "video_${System.currentTimeMillis()}"
                            val mimeType = context.contentResolver.getType(uri) ?: "video/mp4"
                            val destFile = targetDir.createFile(mimeType, fileName)
                                ?: throw IllegalStateException("Could not create file in target.")

                            context.contentResolver.openInputStream(uri)?.use { input ->
                                context.contentResolver.openOutputStream(destFile.uri)?.use { output ->
                                    input.copyTo(output)
                                }
                            }
                            successCount++
                        } catch (e: Exception) {
                            failCount++
                        }
                    }
                }
                _operationResult.value = buildOpResult("Copied", successCount, failCount)
            } catch (e: Exception) {
                _operationResult.value = "Copy failed: ${e.localizedMessage}"
            } finally {
                _operationInProgress.value = false
            }
        }
    }

    //  MOVE 

    /**
     * Moves all [uris] into the [targetTreeUri] directory: copy first, then delete originals.
     */
    fun moveVideos(context: Context, uris: List<Uri>, targetTreeUri: Uri) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _operationInProgress.value = true
            var successCount = 0
            var failCount = 0
            try {
                withContext(Dispatchers.IO) {
                    val targetDir = DocumentFile.fromTreeUri(context, targetTreeUri)
                        ?: throw IllegalStateException("Cannot access target directory.")
                    for (uri in uris) {
                        try {
                            val fileName = getDisplayName(context, uri) ?: "video_${System.currentTimeMillis()}"
                            val mimeType = context.contentResolver.getType(uri) ?: "video/mp4"
                            val destFile = targetDir.createFile(mimeType, fileName)
                                ?: throw IllegalStateException("Could not create file in target.")

                            context.contentResolver.openInputStream(uri)?.use { input ->
                                context.contentResolver.openOutputStream(destFile.uri)?.use { output ->
                                    input.copyTo(output)
                                }
                            }
                            // Delete original after successful copy
                            try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                            successCount++
                        } catch (e: Exception) {
                            failCount++
                        }
                    }
                }
                _operationResult.value = buildOpResult("Moved", successCount, failCount)
            } catch (e: Exception) {
                _operationResult.value = "Move failed: ${e.localizedMessage}"
            } finally {
                _operationInProgress.value = false
            }
        }
    }

    //  CUSTOM STORAGE EXPLORER Move/Copy 

    /**
     * Copies all files from [uris] to [destinationFile] directory.
     * Assumes MANAGE_EXTERNAL_STORAGE is granted on API 30+, or WRITE_EXTERNAL_STORAGE on API 29-.
     */
    fun copyItemsToPath(context: Context, uris: List<Uri>, destinationFile: java.io.File) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _operationInProgress.value = true
            var successCount = 0
            var failCount = 0
            try {
                withContext(Dispatchers.IO) {
                    if (!destinationFile.exists()) destinationFile.mkdirs()
                    for (uri in uris) {
                        try {
                            val fileName = getDisplayName(context, uri) ?: "video_${System.currentTimeMillis()}"
                            val destFile = java.io.File(destinationFile, fileName)
                            
                            var bytesCopied = 0L
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                java.io.FileOutputStream(destFile).use { output ->
                                    bytesCopied = input.copyTo(output)
                                }
                            }
                            
                            if (bytesCopied <= 0L && uris.size == 1) {
                                throw IllegalStateException("Failed to copy data or file is empty.")
                            }
                            
                            if (bytesCopied > 0L) {
                                // Trigger MediaStore scan for the new file and wait up to 3 seconds
                                kotlinx.coroutines.withTimeoutOrNull(3000L) {
                                    kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
                                        MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null) { _, _ ->
                                            if (continuation.isActive) continuation.resumeWith(Result.success(Unit))
                                        }
                                    }
                                }
                                successCount++
                            } else {
                                failCount++
                                if (destFile.exists()) destFile.delete()
                            }
                        } catch (e: Exception) {
                            failCount++
                        }
                    }
                }
                _operationResult.value = buildOpResult("Copied", successCount, failCount)
            } catch (e: Exception) {
                _operationResult.value = "Copy failed: ${e.localizedMessage}"
            } finally {
                _operationInProgress.value = false
            }
        }
    }

    /**
     * Moves all files from [uris] to [destinationFile] directory.
     */
    fun moveItemsToPath(context: Context, uris: List<Uri>, destinationFile: java.io.File) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _operationInProgress.value = true
            var successCount = 0
            var failCount = 0
            try {
                withContext(Dispatchers.IO) {
                    if (!destinationFile.exists()) destinationFile.mkdirs()
                    for (uri in uris) {
                        try {
                            val fileName = getDisplayName(context, uri) ?: "video_${System.currentTimeMillis()}"
                            val destFile = java.io.File(destinationFile, fileName)
                            
                            // For moving via SAF URI to File path, we copy then delete.
                            var bytesCopied = 0L
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                java.io.FileOutputStream(destFile).use { output ->
                                    bytesCopied = input.copyTo(output)
                                }
                            }
                            
                            if (bytesCopied <= 0L && uris.size == 1) {
                                throw IllegalStateException("Failed to copy data or file is empty.")
                            }
                            
                            // Trigger MediaStore scan for the new file and wait up to 3 seconds
                            kotlinx.coroutines.withTimeoutOrNull(3000L) {
                                kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
                                    MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null) { _, _ ->
                                        if (continuation.isActive) continuation.resumeWith(Result.success(Unit))
                                    }
                                }
                            }
                            
                            if (bytesCopied > 0L) {
                                try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                                successCount++
                            } else {
                                failCount++
                                if (destFile.exists()) destFile.delete()
                            }
                        } catch (e: Exception) {
                            failCount++
                        }
                    }
                }
                _operationResult.value = buildOpResult("Moved", successCount, failCount)
            } catch (e: Exception) {
                _operationResult.value = "Move failed: ${e.localizedMessage}"
            } finally {
                _operationInProgress.value = false
            }
        }
    }

    // 
    // PRIVATE HELPERS
    // 

    private suspend fun executeDeleteApi29(context: Context, uris: List<Uri>) {
        withContext(Dispatchers.IO) {
            val failedUris = mutableListOf<Uri>()
            for (uri in uris) {
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (e: RecoverableSecurityException) {
                    // Collect one IntentSender for first failed file only (API 29 limitation)
                    // Store the full list as pending so all can be retried
                    if (pendingAction == null) {
                        pendingAction = PendingFileAction.Delete(uris)
                        _pendingIntentSender.value = e.userAction.actionIntent.intentSender
                    }
                    failedUris.add(uri)
                } catch (e: Exception) {
                    failedUris.add(uri)
                }
            }
            val deletedCount = uris.size - failedUris.size
            if (failedUris.isEmpty()) {
                _operationResult.value = "Successfully deleted ${deletedCount} videos."
            }
            // If there are failed URIs with a RecoverableSecurityException,
            // UI will see pendingIntentSender and launch it.
        }
    }

    private suspend fun executeRenameApi29(context: Context, uri: Uri, newName: String) {
        withContext(Dispatchers.IO) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
                }
                context.contentResolver.update(uri, values, null, null)
                _operationResult.value = "Renamed to \"$newName\" successfully."
            } catch (e: RecoverableSecurityException) {
                pendingAction = PendingFileAction.Rename(uri, newName)
                _pendingIntentSender.value = e.userAction.actionIntent.intentSender
            }
        }
    }

    private fun getDisplayName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    private fun buildOpResult(verb: String, success: Int, fail: Int): String {
        return if (fail == 0) "$verb $success file(s) successfully."
        else "$verb $success file(s). $fail failed."
    }
}

package com.devson.nvplayer.viewmodel

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
import com.devson.nvplayer.data.media.FileTransferOps
import com.devson.nvplayer.data.model.VideoItem
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FileOperationState(
    val isOperating: Boolean = false,
    val title: String = "",
    val currentFileName: String = "",
    val overallProgress: Float = 0f,
    val fileProgressPercentage: Int = 0,
    val isMove: Boolean = true,
    val isCancelled: Boolean = false
)

/**
 * Tracks which file action is waiting for a system permission grant (IntentSender result).
 */
sealed class PendingFileAction {
    data class Delete(val uris: List<Uri>, val trash: Boolean = false) : PendingFileAction()
    data class Restore(val uris: List<Uri>) : PendingFileAction()
    data class Rename(val uri: Uri, val newName: String) : PendingFileAction()
    data class Move(val videos: List<VideoItem>, val destRelativePath: String) : PendingFileAction()
    data class Copy(val videos: List<VideoItem>, val destRelativePath: String) : PendingFileAction()
}

/**
 * ViewModel that handles all MediaStore / Scoped Storage file operations
 * (delete, rename, copy, move) with correct API-level handling.
 */
class FileOperationsViewModel(application: Application) : AndroidViewModel(application) {

    private val _operationInProgress = MutableStateFlow(false)
    val operationInProgress: StateFlow<Boolean> = _operationInProgress.asStateFlow()

    private val _operationState = MutableStateFlow(FileOperationState())
    val operationState: StateFlow<FileOperationState> = _operationState.asStateFlow()

    @Volatile
    private var cancelRequested = false

    fun cancelCurrentOperation() {
        cancelRequested = true
    }

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

    private val _transferringFileName = MutableStateFlow<String?>(null)
    val transferringFileName: StateFlow<String?> = _transferringFileName.asStateFlow()

    private val _transferPercentage = MutableStateFlow(0)
    val transferPercentage: StateFlow<Int> = _transferPercentage.asStateFlow()

    private val _isTransferring = MutableStateFlow(false)
    val isTransferring: StateFlow<Boolean> = _isTransferring.asStateFlow()

    private val _pendingDeletionsFlow = MutableSharedFlow<List<Uri>>()
    val pendingDeletionsFlow: SharedFlow<List<Uri>> = _pendingDeletionsFlow.asSharedFlow()

    private val _showOverwriteDialog = MutableStateFlow(false)
    val showOverwriteDialog: StateFlow<Boolean> = _showOverwriteDialog.asStateFlow()

    private var pendingVideos: List<VideoItem> = emptyList()
    private var pendingDestRelativePath: String = ""

    // PUBLIC API

    fun moveVideos(context: Context, videos: List<VideoItem>, destRelativePath: String, overwrite: Boolean = false) {
        if (!overwrite) {
            val hasConflict = videos.any { video ->
                val sourceFile = java.io.File(video.path)
                FileTransferOps.isFileConflict(context, destRelativePath, sourceFile.name)
            }
            if (hasConflict) {
                pendingVideos = videos
                pendingDestRelativePath = destRelativePath
                _showOverwriteDialog.value = true
                return
            }
        }

        cancelRequested = false
        viewModelScope.launch {
            _isTransferring.value = true
            _operationInProgress.value = true
            var successCount = 0
            var failCount = 0
            val total = videos.size

            for ((index, video) in videos.withIndex()) {
                if (cancelRequested) break

                _transferringFileName.value = video.title
                _transferPercentage.value = 0

                _operationState.value = FileOperationState(
                    isOperating = true,
                    title = "Moving ${index + 1} of $total file${if (total != 1) "s" else ""}...",
                    currentFileName = video.title,
                    overallProgress = index.toFloat() / total,
                    fileProgressPercentage = 0,
                    isMove = true
                )

                try {
                    val result = FileTransferOps.moveVideoScoped(
                        context = context,
                        sourceVideo = video,
                        destRelativePath = destRelativePath,
                        overwrite = overwrite,
                        isCancelled = { cancelRequested }
                    ) { progress ->
                        _transferPercentage.value = progress
                        _operationState.value = _operationState.value.copy(
                            fileProgressPercentage = progress
                        )
                    }
                    if (result.isSuccess) {
                        successCount++
                    } else {
                        failCount++
                    }
                } catch (e: android.app.RecoverableSecurityException) {
                    pendingAction = PendingFileAction.Move(videos.drop(index), destRelativePath)
                    _pendingIntentSender.value = e.userAction.actionIntent.intentSender
                    break
                } catch (e: Exception) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && e is SecurityException) {
                        val intentSender = MediaStore.createWriteRequest(context.contentResolver, listOf(video.uri)).intentSender
                        pendingAction = PendingFileAction.Move(videos.drop(index), destRelativePath)
                        _pendingIntentSender.value = intentSender
                        break
                    }
                    failCount++
                }
            }

            _operationResult.value = if (cancelRequested) "Move cancelled" else buildOpResult("Moved", successCount, failCount)
            _isTransferring.value = false
            _transferringFileName.value = null
            _transferPercentage.value = 0
            _operationInProgress.value = false
            _operationState.value = FileOperationState(isOperating = false)
            _needsRefresh.value = true
        }
    }

    fun confirmOverwrite(context: Context) {
        _showOverwriteDialog.value = false
        val videos = pendingVideos
        val destPath = pendingDestRelativePath
        pendingVideos = emptyList()
        pendingDestRelativePath = ""
        if (videos.isNotEmpty() && destPath.isNotEmpty()) {
            moveVideos(context, videos, destPath, overwrite = true)
        }
    }

    fun cancelOverwrite() {
        _showOverwriteDialog.value = false
        pendingVideos = emptyList()
        pendingDestRelativePath = ""
    }

    fun copyVideos(context: Context, videos: List<VideoItem>, destRelativePath: String) {
        cancelRequested = false
        viewModelScope.launch {
            _isTransferring.value = true
            _operationInProgress.value = true
            var successCount = 0
            var failCount = 0
            val total = videos.size

            for ((index, video) in videos.withIndex()) {
                if (cancelRequested) break

                _transferringFileName.value = video.title
                _transferPercentage.value = 0

                _operationState.value = FileOperationState(
                    isOperating = true,
                    title = "Copying ${index + 1} of $total file${if (total != 1) "s" else ""}...",
                    currentFileName = video.title,
                    overallProgress = index.toFloat() / total,
                    fileProgressPercentage = 0,
                    isMove = false
                )

                try {
                    val result = FileTransferOps.copyVideoScoped(
                        context = context,
                        sourceVideo = video,
                        destRelativePath = destRelativePath,
                        overwrite = false,
                        isCancelled = { cancelRequested }
                    ) { progress ->
                        _transferPercentage.value = progress
                        _operationState.value = _operationState.value.copy(
                            fileProgressPercentage = progress
                        )
                    }
                    if (result.isSuccess) {
                        successCount++
                    } else {
                        failCount++
                    }
                } catch (e: Exception) {
                    failCount++
                }
            }
            _operationResult.value = if (cancelRequested) "Copy cancelled" else buildOpResult("Copied", successCount, failCount)
            _isTransferring.value = false
            _transferringFileName.value = null
            _transferPercentage.value = 0
            _operationInProgress.value = false
            _operationState.value = FileOperationState(isOperating = false)
            _needsRefresh.value = true
        }
    }

    fun copyVideosToTreeUri(context: Context, videos: List<VideoItem>, destTreeUri: Uri) {
        viewModelScope.launch {
            _isTransferring.value = true
            _operationInProgress.value = true
            var successCount = 0
            var failCount = 0
            try {
                for (video in videos) {
                    _transferringFileName.value = video.title
                    _transferPercentage.value = 0
                    val result = FileTransferOps.copyVideoToTreeUri(context, video, destTreeUri) { progress ->
                        _transferPercentage.value = progress
                    }
                    if (result.isSuccess) {
                        successCount++
                    } else {
                        failCount++
                    }
                }
                _operationResult.value = buildOpResult("Copied", successCount, failCount)
            } catch (e: Exception) {
                _operationResult.value = "Copy failed: ${e.localizedMessage}"
            } finally {
                _isTransferring.value = false
                _transferringFileName.value = null
                _transferPercentage.value = 0
                _operationInProgress.value = false
                _needsRefresh.value = true
            }
        }
    }

    fun clearResult() { _operationResult.value = null }
    fun clearPendingIntentSender() {
        _pendingIntentSender.value = null
        if (pendingAction == null) {
            _operationInProgress.value = false
        }
    }

    /** Called when the user cancels or denies the system permission prompt (e.g. MediaStore / SAF dialog). */
    fun onPermissionDenied() {
        pendingAction = null
        _pendingIntentSender.value = null
        _operationInProgress.value = false
        _isTransferring.value = false
        _transferringFileName.value = null
        _transferPercentage.value = 0
        _operationState.value = FileOperationState(isOperating = false)
    }

    //  DELETE 

    fun deleteVideos(context: Context, uris: List<Uri>, trash: Boolean = false) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val contentUris = mutableListOf<Uri>()
            val fileUris = mutableListOf<Uri>()

            uris.forEach { uri ->
                if (uri.scheme == "file") {
                    fileUris.add(uri)
                } else {
                    contentUris.add(uri)
                }
            }

            // Direct file deletion (e.g. USB OTG or non-MediaStore paths)
            if (fileUris.isNotEmpty()) {
                _operationInProgress.value = true
                var deletedFilesCount = 0
                val scannedPaths = mutableListOf<String>()
                withContext(Dispatchers.IO) {
                    fileUris.forEach { uri ->
                        val path = uri.path
                        if (path != null) {
                            val file = java.io.File(path)
                            if (file.exists() && file.delete()) {
                                deletedFilesCount++
                                scannedPaths.add(file.absolutePath)
                            }
                        }
                    }
                    if (scannedPaths.isNotEmpty()) {
                        MediaScannerConnection.scanFile(context, scannedPaths.toTypedArray(), null, null)
                    }
                }
                if (contentUris.isEmpty()) {
                    _operationInProgress.value = false
                    _operationResult.value = "Successfully deleted $deletedFilesCount video(s)."
                    _needsRefresh.value = true
                    return@launch
                }
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val pi = if (trash) MediaStore.createTrashRequest(context.contentResolver, contentUris, true)
                             else MediaStore.createDeleteRequest(context.contentResolver, contentUris)
                    pendingAction = PendingFileAction.Delete(contentUris, trash)
                    // Note: _operationInProgress is intentionally kept false while user considers the system prompt
                    _pendingIntentSender.value = pi.intentSender
                } else {
                    _operationInProgress.value = true
                    executeDeleteApi29(context, contentUris)
                    _operationInProgress.value = false
                }
            } catch (e: Exception) {
                _operationResult.value = "Delete failed: ${e.localizedMessage}"
                _operationInProgress.value = false
                pendingAction = null
            }
        }
    }

    /** Single entry point called by the screen on any RESULT_OK from the IntentSender launcher. */
    fun onPermissionGranted(context: Context) {
        when (val action = pendingAction) {
            is PendingFileAction.Delete -> onDeletePermissionGranted(context)
            is PendingFileAction.Restore -> onRestorePermissionGranted(context)
            is PendingFileAction.Rename -> onRenamePermissionGranted(context)
            is PendingFileAction.Move -> {
                pendingAction = null
                moveVideos(context, action.videos, action.destRelativePath, overwrite = true)
            }
            is PendingFileAction.Copy -> {
                pendingAction = null
                copyVideos(context, action.videos, action.destRelativePath)
            }
            null -> {}
        }
    }

    fun restoreVideos(context: Context, uris: List<Uri>) {
        if (uris.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        viewModelScope.launch {
            try {
                val pi = MediaStore.createTrashRequest(context.contentResolver, uris, false)
                pendingAction = PendingFileAction.Restore(uris)
                _pendingIntentSender.value = pi.intentSender
            } catch (e: Exception) {
                _operationResult.value = "Restore failed: ${e.localizedMessage}"
                _operationInProgress.value = false
                pendingAction = null
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
            _operationInProgress.value = true
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
                _operationResult.value = if (action.trash) {
                    "Thrown $deletedCount video(s) to Recycle Bin"
                } else {
                    "Successfully deleted $deletedCount videos."
                }
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
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // API 30+: request write permission first
                    val pi = MediaStore.createWriteRequest(context.contentResolver, listOf(uri))
                    pendingAction = PendingFileAction.Rename(uri, newName)
                    _pendingIntentSender.value = pi.intentSender
                } else {
                    _operationInProgress.value = true
                    // API 29: try direct update
                    executeRenameApi29(context, uri, newName)
                    _operationInProgress.value = false
                }
            } catch (e: Exception) {
                _operationResult.value = "Rename failed: ${e.localizedMessage}"
                _operationInProgress.value = false
                pendingAction = null
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
            _operationInProgress.value = true
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
                _needsRefresh.value = true
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
                _needsRefresh.value = true
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
                _needsRefresh.value = true
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
                _needsRefresh.value = true
            }
        }
    }

    // PRIVATE HELPERS
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
                _needsRefresh.value = true
            } else if (deletedCount > 0) {
                _needsRefresh.value = true
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

    private fun resolveUrisToVideoItems(context: Context, uris: List<Uri>): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )
        for (uri in uris) {
            try {
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                        val title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)) ?: "Unknown"
                        val duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION))
                        val data = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)) ?: ""
                        val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE))
                        val width = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH))
                        val height = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT))
                        val contentUri = android.content.ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        val folderName = java.io.File(data).parentFile?.name ?: "Unknown"
                        list.add(
                            VideoItem(
                                uri = contentUri,
                                title = title,
                                duration = duration,
                                folderName = folderName,
                                path = data,
                                thumbnailUri = contentUri,
                                size = size,
                                width = width,
                                height = height
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("FileOperationsViewModel", "Failed to resolve URI to VideoItem: $uri", e)
            }
        }
        return list
    }

    fun moveVideosByUri(context: Context, uris: List<Uri>, destRelativePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val videos = resolveUrisToVideoItems(context, uris)
            withContext(Dispatchers.Main) {
                moveVideos(context, videos, destRelativePath)
            }
        }
    }

    fun copyVideosByUri(context: Context, uris: List<Uri>, destRelativePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val videos = resolveUrisToVideoItems(context, uris)
            withContext(Dispatchers.Main) {
                copyVideos(context, videos, destRelativePath)
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

package com.devson.nvplayer.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.devson.nvplayer.data.database.VaultDao
import com.devson.nvplayer.data.database.VaultEntity
import com.devson.nvplayer.data.security.VaultFileManager
import com.devson.nvplayer.data.security.VaultSecurityManager
import com.devson.nvplayer.domain.model.VaultStorageMode
import com.devson.nvplayer.domain.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class VideoConversionState(
    val isConverting: Boolean = false,
    val item: VaultEntity? = null,
    val targetMode: VaultStorageMode? = null,
    val progress: Float = 0f,
    val error: String? = null
)

class VaultGalleryViewModel(
    application: Application,
    private val vaultDao: VaultDao,
    private val vaultFileManager: VaultFileManager,
    val vaultSecurityManager: VaultSecurityManager = VaultSecurityManager(application)
) : AndroidViewModel(application) {

    val vaultMediaList: StateFlow<List<VaultEntity>> = vaultDao.getAllVaultMediaFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _defaultStorageMode = MutableStateFlow(vaultSecurityManager.getDefaultStorageMode())
    val defaultStorageMode: StateFlow<VaultStorageMode> = _defaultStorageMode.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _conversionState = MutableStateFlow(VideoConversionState())
    val conversionState: StateFlow<VideoConversionState> = _conversionState.asStateFlow()

    @Volatile
    private var isConversionCancelled = false
    private var conversionJob: kotlinx.coroutines.Job? = null

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _pendingIntentSender = MutableStateFlow<android.content.IntentSender?>(null)
    val pendingIntentSender: StateFlow<android.content.IntentSender?> = _pendingIntentSender.asStateFlow()

    fun verifyCredential(credential: String): Boolean {
        return vaultSecurityManager.verifyVaultCredential(credential)
    }

    fun startProtectionConversion(item: VaultEntity, targetMode: VaultStorageMode, credential: String) {
        if (_conversionState.value.isConverting) return
        isConversionCancelled = false
        _conversionState.value = VideoConversionState(
            isConverting = true,
            item = item,
            targetMode = targetMode,
            progress = 0f,
            error = null
        )

        conversionJob = viewModelScope.launch(Dispatchers.IO) {
            val result = vaultFileManager.convertVideoProtection(
                vaultEntity = item,
                targetMode = targetMode,
                credential = credential,
                securityManager = vaultSecurityManager,
                onProgress = { p ->
                    _conversionState.value = _conversionState.value.copy(progress = p)
                },
                isCancelled = { isConversionCancelled }
            )

            _conversionState.value = VideoConversionState(isConverting = false)

            if (result.isSuccess) {
                val targetLabel = if (targetMode == VaultStorageMode.ENCRYPTED) "Encrypted" else "Hidden / No Encryption"
                _statusMessage.value = "Converted \"${item.title}\" to $targetLabel."
            } else {
                val ex = result.exceptionOrNull()
                if (ex is kotlinx.coroutines.CancellationException) {
                    _statusMessage.value = "Conversion cancelled."
                } else {
                    _statusMessage.value = "Conversion failed: ${ex?.message ?: "Unknown error"}"
                }
            }
        }
    }

    fun cancelConversion() {
        isConversionCancelled = true
        conversionJob?.cancel()
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun clearPendingIntentSender() {
        _pendingIntentSender.value = null
    }

    fun refreshStorageMode() {
        _defaultStorageMode.value = vaultSecurityManager.getDefaultStorageMode()
    }

    fun setStorageMode(mode: VaultStorageMode): Boolean {
        val success = vaultSecurityManager.setDefaultStorageMode(mode)
        if (success) {
            _defaultStorageMode.value = mode
        }
        return success
    }

    fun verifyAndSetStorageMode(credential: String, mode: VaultStorageMode): Boolean {
        val success = vaultSecurityManager.verifyAndSetStorageMode(credential, mode)
        if (success) {
            _defaultStorageMode.value = mode
        }
        return success
    }

    fun importVideos(uris: List<Uri>, titles: List<String>, paths: List<String>? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            var importedCount = 0
            val urisToRequestDelete = mutableListOf<Uri>()
            val currentMode = _defaultStorageMode.value

            for (i in uris.indices) {
                val uri = uris[i]
                val title = titles.getOrNull(i) ?: "Protected Video"
                val path = paths?.getOrNull(i)
                val result = vaultFileManager.importVideoToVault(
                    sourceUri = uri,
                    title = title,
                    storageMode = currentMode,
                    originalPath = path
                )
                if (result.isSuccess) {
                    importedCount++
                    val pendingDeleteUri = result.getOrNull()?.pendingDeleteUri
                    if (pendingDeleteUri != null) {
                        urisToRequestDelete.add(pendingDeleteUri)
                    }
                }
            }

            if (urisToRequestDelete.isNotEmpty() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                try {
                    val mediaStoreUris = urisToRequestDelete.filter {
                        it.scheme == "content" && it.authority == android.provider.MediaStore.AUTHORITY
                    }
                    if (mediaStoreUris.isNotEmpty()) {
                        val intentSender = android.provider.MediaStore.createDeleteRequest(
                            getApplication<Application>().contentResolver,
                            mediaStoreUris
                        ).intentSender
                        _pendingIntentSender.value = intentSender
                    }
                } catch (_: Exception) {}
            }

            _isProcessing.value = false
            _statusMessage.value = if (importedCount > 0) "Moved $importedCount video(s) to Vault." else "Failed to import video(s)."
        }
    }

    fun getRestoreTargetFolderName(vaultEntity: VaultEntity): String {
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            ?: File(getApplication<Application>().filesDir, "Restored")
        val targetDir = vaultFileManager.resolveRestoreDestination(vaultEntity, moviesDir)
        return if (targetDir.absolutePath == moviesDir.absolutePath) "Movies" else targetDir.name
    }

    fun restoreVideo(vaultEntity: VaultEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                ?: File(getApplication<Application>().filesDir, "Restored")
            val targetDir = vaultFileManager.resolveRestoreDestination(vaultEntity, moviesDir)
            val credential = vaultSecurityManager.getActiveCredential() ?: ""
            val result = vaultFileManager.restoreVideoFromVault(vaultEntity, targetDir, credential)
            _isProcessing.value = false
            if (result.isSuccess) {
                val finalFile = result.getOrNull()
                val folderName = finalFile?.parentFile?.name
                    ?: if (targetDir.absolutePath == moviesDir.absolutePath) "Movies" else targetDir.name
                _statusMessage.value = "Restored ${vaultEntity.title} to $folderName."
            } else {
                _statusMessage.value = "Failed to restore: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun deletePermanently(vaultEntity: VaultEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            val result = vaultFileManager.deletePermanently(vaultEntity)
            _isProcessing.value = false
            if (result.isSuccess) {
                _statusMessage.value = "Permanently deleted ${vaultEntity.title}."
            } else {
                _statusMessage.value = "Failed to delete: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    suspend fun preparePlaybackVideo(vaultEntity: VaultEntity): Pair<File, Video> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val credential = vaultSecurityManager.getActiveCredential() ?: ""
        val playbackFile = vaultFileManager.getPlaybackFile(vaultEntity, credential)
        val uri = Uri.fromFile(playbackFile)
        val video = Video(
            uri = uri.toString(),
            title = vaultEntity.title,
            duration = vaultEntity.durationMs,
            folderName = "Vault",
            path = playbackFile.absolutePath,
            size = vaultEntity.fileSize,
            width = 0,
            height = 0
        )
        Pair(playbackFile, video)
    }

    fun getPlaybackFile(vaultEntity: VaultEntity): File {
        val credential = vaultSecurityManager.getActiveCredential() ?: ""
        return vaultFileManager.getPlaybackFile(vaultEntity, credential)
    }

    class Factory(
        private val application: Application,
        private val vaultDao: VaultDao,
        private val vaultFileManager: VaultFileManager,
        private val vaultSecurityManager: VaultSecurityManager? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(VaultGalleryViewModel::class.java)) {
                val secManager = vaultSecurityManager ?: VaultSecurityManager(application)
                return VaultGalleryViewModel(application, vaultDao, vaultFileManager, secManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

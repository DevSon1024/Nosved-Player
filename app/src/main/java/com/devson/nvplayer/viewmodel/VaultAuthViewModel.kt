package com.devson.nvplayer.viewmodel

import android.app.Application
import android.net.Uri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.devson.nvplayer.data.security.VaultBiometricHelper
import com.devson.nvplayer.data.security.VaultFileManager
import com.devson.nvplayer.data.security.VaultMetadataStatus
import com.devson.nvplayer.data.security.VaultSecurityManager
import com.devson.nvplayer.data.security.storage.SafVaultStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface VaultAuthState {
    data class NeedsStorageAccess(val isReconnect: Boolean = false, val message: String? = null) : VaultAuthState
    data object SetupPin : VaultAuthState
    data object ConfirmPin : VaultAuthState
    data class SetupSecurityQuestion(val pin: String) : VaultAuthState
    data object EnterPin : VaultAuthState
    data class ExistingVaultFound(val fileCount: Int, val isMetadataValid: Boolean) : VaultAuthState
    data class RestorePinEntry(val fileCount: Int) : VaultAuthState
    data class IncorrectPin(
        val message: String,
        val isFromRestore: Boolean = false,
        val remainingFiles: Int = 0
    ) : VaultAuthState
    data class ConfirmRemoveVault(val step: Int, val fileCount: Int) : VaultAuthState
    data class EnterPinForReset(
        val fileCount: Int,
        val encryptedCount: Int,
        val error: String? = null
    ) : VaultAuthState
    data class ConfirmDeleteVault(
        val step: Int,
        val fileCount: Int,
        val encryptedCount: Int,
        val hasEncryptedContent: Boolean,
        val authenticatedPin: String
    ) : VaultAuthState
    data class Restoring(val fileCount: Int) : VaultAuthState
    data class RestoreSuccess(val restoredCount: Int) : VaultAuthState
    data class RestoreExistingVault(val fileCount: Int) : VaultAuthState
    data class AnswerSecurityQuestion(val isFromRestore: Boolean = false) : VaultAuthState
    data object ResetPin : VaultAuthState
    data object ConfirmResetPin : VaultAuthState
    data object Authenticated : VaultAuthState
    data class Error(val message: String) : VaultAuthState
}

class VaultAuthViewModel(
    application: Application? = null,
    val securityManager: VaultSecurityManager,
    val vaultFileManager: VaultFileManager? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = coroutineScope ?: viewModelScope

    private val _authState = MutableStateFlow<VaultAuthState>(VaultAuthState.EnterPin)
    val authState: StateFlow<VaultAuthState> = _authState.asStateFlow()

    private val _pinDigits = MutableStateFlow("")
    val pinDigits: StateFlow<String> = _pinDigits.asStateFlow()

    private var setupPinTemp: String = ""
    private var resetPinTemp: String = ""
    private var verifiedSecurityAnswer: String? = null

    init {
        checkPinStatus()
    }

    fun checkPinStatus() {
        _pinDigits.value = ""
        setupPinTemp = ""
        resetPinTemp = ""
        verifiedSecurityAnswer = null

        val storage = securityManager.vaultStorage
        if (!storage.isStorageAccessible()) {
            val hasSavedUri = (storage as? SafVaultStorage)?.getSavedTreeUri() != null
            _authState.value = VaultAuthState.NeedsStorageAccess(
                isReconnect = hasSavedUri,
                message = if (hasSavedUri) "Storage access revoked. Please reconnect the vault folder." else null
            )
            return
        }

        scope.launch(mainDispatcher) {
            val (hasFilesOnDisk, fileCount, isMetadataValid) = withContext(ioDispatcher) {
                Triple(
                    securityManager.hasExistingVaultOnDisk(),
                    securityManager.getExistingVaultFileCount(),
                    securityManager.validateVaultMetadata() == VaultMetadataStatus.VALID
                )
            }
            val isLocallyInitialized = securityManager.isVaultInitializedLocally()

            if (hasFilesOnDisk && !isLocallyInitialized) {
                _authState.value = VaultAuthState.ExistingVaultFound(
                    fileCount = fileCount,
                    isMetadataValid = isMetadataValid
                )
            } else if (isMetadataValid || securityManager.isPinSet()) {
                _authState.value = VaultAuthState.EnterPin
            } else if (hasFilesOnDisk) {
                _authState.value = VaultAuthState.ExistingVaultFound(
                    fileCount = fileCount,
                    isMetadataValid = isMetadataValid
                )
            } else {
                _authState.value = VaultAuthState.SetupPin
            }
        }
    }

    fun onStorageFolderSelected(treeUri: Uri) {
        val safStorage = securityManager.vaultStorage as? SafVaultStorage
        if (safStorage != null) {
            val success = safStorage.setTreeUri(treeUri)
            if (success) {
                checkPinStatus()
            } else {
                _authState.value = VaultAuthState.NeedsStorageAccess(
                    isReconnect = true,
                    message = "Could not access the selected directory. Please ensure read and write permissions are granted."
                )
            }
        } else {
            checkPinStatus()
        }
    }

    fun onRestoreExistingVaultClicked() {
        _pinDigits.value = ""
        _authState.value = VaultAuthState.RestorePinEntry(securityManager.getExistingVaultFileCount())
    }

    fun onRemoveOldVaultClicked() {
        val count = securityManager.getExistingVaultFileCount()
        _authState.value = VaultAuthState.ConfirmRemoveVault(step = 1, fileCount = count)
    }

    fun onCancelRemoveVault() {
        _pinDigits.value = ""
        checkPinStatus()
    }

    fun onRetryPin() {
        _pinDigits.value = ""
        val current = _authState.value
        if (current is VaultAuthState.IncorrectPin && current.isFromRestore) {
            _authState.value = VaultAuthState.RestorePinEntry(current.remainingFiles)
        } else {
            _authState.value = VaultAuthState.EnterPin
        }
    }

    fun requestVaultReset() {
        scope.launch(mainDispatcher) {
            val (totalFiles, encryptedFiles) = withContext(ioDispatcher) {
                val vltFiles = securityManager.vaultStorage.listVaultFiles()
                var encCount = 0
                for (entry in vltFiles) {
                    try {
                        val header = securityManager.vaultStorage.openInputStream(entry.filename).use { stream ->
                            vaultFileManager?.vaultContainer?.inspectVaultStream(stream)
                        }
                        if (header?.storageMode == com.devson.nvplayer.domain.model.VaultStorageMode.ENCRYPTED) {
                            encCount++
                        }
                    } catch (_: Exception) {
                        encCount++
                    }
                }
                Pair(vltFiles.size, encCount)
            }

            _pinDigits.value = ""
            if (securityManager.hasPersistentVaultMetadata() || securityManager.isPinSet()) {
                _authState.value = VaultAuthState.EnterPinForReset(
                    fileCount = totalFiles,
                    encryptedCount = encryptedFiles
                )
            } else {
                _authState.value = VaultAuthState.ConfirmDeleteVault(
                    step = 1,
                    fileCount = totalFiles,
                    encryptedCount = encryptedFiles,
                    hasEncryptedContent = encryptedFiles > 0,
                    authenticatedPin = ""
                )
            }
        }
    }

    fun onConfirmResetStep1() {
        val state = _authState.value as? VaultAuthState.ConfirmDeleteVault ?: return
        _authState.value = state.copy(step = 2)
    }

    fun onCancelReset() {
        _pinDigits.value = ""
        checkPinStatus()
    }

    fun executeVaultReset(authenticatedPin: String) {
        scope.launch(mainDispatcher) {
            val result = withContext(ioDispatcher) {
                try {
                    vaultFileManager?.removeAllVaultData(securityManager, authenticatedPin)
                        ?: run {
                            securityManager.resetVault(deleteFiles = true)
                            com.devson.nvplayer.data.security.VaultDeletionResult(
                                success = true,
                                deletedMediaCount = 0,
                                failedMediaCount = 0,
                                remainingFiles = emptyList()
                            )
                        }
                } catch (e: Exception) {
                    com.devson.nvplayer.data.security.VaultDeletionResult(
                        success = false,
                        deletedMediaCount = 0,
                        failedMediaCount = 1,
                        remainingFiles = listOf(e.message ?: "Authentication/Deletion failed")
                    )
                }
            }

            if (result.success) {
                securityManager.setVaultInitializedLocally(false)
                _pinDigits.value = ""
                setupPinTemp = ""
                resetPinTemp = ""
                _authState.value = VaultAuthState.SetupPin
            } else {
                _authState.value = VaultAuthState.Error(
                    "Partial deletion failure: ${result.remainingFiles.joinToString(", ")}. Remaining data was preserved."
                )
            }
        }
    }

    fun onConfirmRemoveStep1() {
        val state = _authState.value as? VaultAuthState.ConfirmRemoveVault ?: return
        _authState.value = state.copy(step = 2)
    }

    fun onConfirmRemoveFinal() {
        scope.launch(mainDispatcher) {
            withContext(ioDispatcher) {
                try {
                    vaultFileManager?.removeAllVaultData(securityManager, credential = null, force = true)
                        ?: securityManager.resetVault(deleteFiles = true)
                } catch (_: Exception) {
                    securityManager.resetVault(deleteFiles = true)
                }
            }
            securityManager.setVaultInitializedLocally(false)
            _pinDigits.value = ""
            setupPinTemp = ""
            resetPinTemp = ""
            _authState.value = VaultAuthState.SetupPin
        }
    }

    fun onDigit(digit: String) {
        if (_pinDigits.value.length < 4) {
            _pinDigits.value += digit
            if (_pinDigits.value.length == 4) {
                onPinComplete(_pinDigits.value)
            }
        }
    }

    fun onDeleteDigit() {
        if (_pinDigits.value.isNotEmpty()) {
            _pinDigits.value = _pinDigits.value.dropLast(1)
        }
    }

    fun onClearDigits() {
        _pinDigits.value = ""
    }

    private fun onPinComplete(pin: String) {
        scope.launch(mainDispatcher) {
            when (val current = _authState.value) {
                is VaultAuthState.SetupPin -> {
                    setupPinTemp = pin
                    _pinDigits.value = ""
                    _authState.value = VaultAuthState.ConfirmPin
                }
                is VaultAuthState.ConfirmPin -> {
                    if (pin == setupPinTemp) {
                        _pinDigits.value = ""
                        _authState.value = VaultAuthState.SetupSecurityQuestion(pin = setupPinTemp)
                    } else {
                        _pinDigits.value = ""
                        _authState.value = VaultAuthState.Error("PINs do not match. Try again.")
                    }
                }
                is VaultAuthState.EnterPin -> {
                    val isValid = withContext(ioDispatcher) {
                        securityManager.verifyPin(pin)
                    }
                    if (isValid) {
                        _pinDigits.value = ""
                        securityManager.setVaultInitializedLocally(true)
                        withContext(ioDispatcher) {
                            vaultFileManager?.rebuildDatabaseFromStorage(credential = pin)
                        }
                        _authState.value = VaultAuthState.Authenticated
                    } else {
                        _pinDigits.value = ""
                        _authState.value = VaultAuthState.IncorrectPin("Incorrect PIN. Please try again.")
                    }
                }
                is VaultAuthState.RestorePinEntry, is VaultAuthState.RestoreExistingVault -> {
                    val fileCount = (current as? VaultAuthState.RestorePinEntry)?.fileCount
                        ?: (current as? VaultAuthState.RestoreExistingVault)?.fileCount
                        ?: withContext(ioDispatcher) { securityManager.getExistingVaultFileCount() }
                    _authState.value = VaultAuthState.Restoring(fileCount)
                    val valid = withContext(ioDispatcher) {
                        securityManager.verifyPin(pin)
                    }
                    if (valid) {
                        _pinDigits.value = ""
                        securityManager.setVaultInitializedLocally(true)
                        try {
                            withContext(ioDispatcher) {
                                vaultFileManager?.rebuildDatabaseFromStorage(credential = pin)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        _authState.value = VaultAuthState.Authenticated
                    } else {
                        _pinDigits.value = ""
                        _authState.value = VaultAuthState.IncorrectPin(
                            message = "Incorrect Vault PIN. Your existing vault data has not been changed.",
                            isFromRestore = true,
                            remainingFiles = fileCount
                        )
                    }
                }
                is VaultAuthState.IncorrectPin -> {
                    val wasFromRestore = current.isFromRestore
                    val valid = withContext(ioDispatcher) {
                        securityManager.verifyPin(pin)
                    }
                    if (valid) {
                        _pinDigits.value = ""
                        _authState.value = VaultAuthState.Restoring(current.remainingFiles)
                        securityManager.setVaultInitializedLocally(true)
                        try {
                            withContext(ioDispatcher) {
                                vaultFileManager?.rebuildDatabaseFromStorage(credential = pin)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        _authState.value = VaultAuthState.Authenticated
                    } else {
                        _pinDigits.value = ""
                        _authState.value = VaultAuthState.IncorrectPin(
                            message = "Incorrect Vault PIN. Your existing vault data has not been changed.",
                            isFromRestore = wasFromRestore,
                            remainingFiles = current.remainingFiles
                        )
                    }
                }
                is VaultAuthState.EnterPinForReset -> {
                    val isValid = withContext(ioDispatcher) {
                        securityManager.verifyPin(pin)
                    }
                    if (isValid) {
                        _pinDigits.value = ""
                        _authState.value = VaultAuthState.ConfirmDeleteVault(
                            step = 1,
                            fileCount = current.fileCount,
                            encryptedCount = current.encryptedCount,
                            hasEncryptedContent = current.encryptedCount > 0,
                            authenticatedPin = pin
                        )
                    } else {
                        _pinDigits.value = ""
                        _authState.value = current.copy(error = "Incorrect PIN. Cannot proceed with vault deletion.")
                    }
                }
                is VaultAuthState.ResetPin -> {
                    resetPinTemp = pin
                    _pinDigits.value = ""
                    _authState.value = VaultAuthState.ConfirmResetPin
                }
                is VaultAuthState.ConfirmResetPin -> {
                    if (pin == resetPinTemp) {
                        val answer = verifiedSecurityAnswer
                        if (answer != null) {
                            val success = withContext(ioDispatcher) {
                                securityManager.resetPinWithSecurityAnswer(answer, pin)
                            }
                            if (success) {
                                _pinDigits.value = ""
                                verifiedSecurityAnswer = null
                                securityManager.setVaultInitializedLocally(true)
                                withContext(ioDispatcher) {
                                    vaultFileManager?.rebuildDatabaseFromStorage(credential = pin)
                                }
                                _authState.value = VaultAuthState.Authenticated
                            } else {
                                _pinDigits.value = ""
                                _authState.value = VaultAuthState.Error(
                                    "Cannot reset PIN: Encrypted media requires original PIN key."
                                )
                            }
                        } else {
                            _pinDigits.value = ""
                            _authState.value = VaultAuthState.Error("Security verification expired.")
                        }
                    } else {
                        _pinDigits.value = ""
                        _authState.value = VaultAuthState.Error("PINs do not match. Try again.")
                    }
                }
                is VaultAuthState.Error -> {
                    val isValid = withContext(ioDispatcher) {
                        securityManager.verifyPin(pin)
                    }
                    if (isValid) {
                        _pinDigits.value = ""
                        securityManager.setVaultInitializedLocally(true)
                        withContext(ioDispatcher) {
                            vaultFileManager?.rebuildDatabaseFromStorage(credential = pin)
                        }
                        _authState.value = VaultAuthState.Authenticated
                    } else {
                        _pinDigits.value = ""
                        _authState.value = VaultAuthState.Error("Incorrect PIN")
                    }
                }
                else -> {}
            }
        }
    }

    fun completeSecurityQuestionSetup(question: String, answer: String) {
        val current = _authState.value
        if (current is VaultAuthState.SetupSecurityQuestion) {
            scope.launch(mainDispatcher) {
                withContext(ioDispatcher) {
                    securityManager.setPin(current.pin, question, answer)
                    securityManager.setVaultInitializedLocally(true)
                    vaultFileManager?.rebuildDatabaseFromStorage(credential = current.pin)
                }
                _pinDigits.value = ""
                _authState.value = VaultAuthState.Authenticated
            }
        }
    }

    fun onForgotPinClicked() {
        val isRestore = _authState.value is VaultAuthState.RestoreExistingVault ||
                _authState.value is VaultAuthState.RestorePinEntry ||
                _authState.value is VaultAuthState.ExistingVaultFound ||
                (_authState.value as? VaultAuthState.IncorrectPin)?.isFromRestore == true
        val question = securityManager.getSecurityQuestion()
        if (!question.isNullOrBlank()) {
            _pinDigits.value = ""
            _authState.value = VaultAuthState.AnswerSecurityQuestion(isFromRestore = isRestore)
        } else {
            _authState.value = VaultAuthState.Error("No security question set. Please enter PIN or remove old vault data.")
        }
    }

    fun verifySecurityAnswerAndProceed(answer: String): Boolean {
        return if (securityManager.verifySecurityAnswer(answer)) {
            verifiedSecurityAnswer = answer
            _pinDigits.value = ""
            _authState.value = VaultAuthState.ResetPin
            true
        } else {
            false
        }
    }

    fun startFreshVault(deleteExistingFiles: Boolean) {
        securityManager.setVaultInitializedLocally(false)
        if (deleteExistingFiles) {
            scope.launch(mainDispatcher) {
                withContext(ioDispatcher) {
                    securityManager.resetVault(deleteFiles = true)
                    vaultFileManager?.removeAllVaultData(securityManager, credential = null, force = true)
                }
                _pinDigits.value = ""
                setupPinTemp = ""
                resetPinTemp = ""
                _authState.value = VaultAuthState.SetupPin
            }
        } else {
            _pinDigits.value = ""
            setupPinTemp = ""
            resetPinTemp = ""
            _authState.value = VaultAuthState.SetupPin
        }
    }

    fun onBackspace() {
        onDeleteDigit()
    }

    fun authenticateWithBiometrics(activity: FragmentActivity) {
        if (!securityManager.isBiometricEnabled()) {
            _authState.value = VaultAuthState.Error("Biometric authentication is disabled in settings")
            return
        }
        if (!VaultBiometricHelper.canAuthenticate(activity)) {
            _authState.value = VaultAuthState.Error("Biometrics not enrolled or supported on this device")
            return
        }

        VaultBiometricHelper.promptBiometric(
            activity = activity,
            onSuccess = {
                scope.launch(mainDispatcher) {
                    securityManager.setVaultInitializedLocally(true)
                    val activePin = securityManager.getActiveCredential() ?: ""
                    withContext(ioDispatcher) {
                        vaultFileManager?.rebuildDatabaseFromStorage(credential = activePin)
                    }
                    _authState.value = VaultAuthState.Authenticated
                }
            },
            onError = { errorCode: Int, errString: CharSequence ->
                if (errorCode != androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    errorCode != androidx.biometric.BiometricPrompt.ERROR_CANCELED
                ) {
                    _authState.value = VaultAuthState.Error(errString.toString())
                }
            }
        )
    }

    fun onDismissError() {
        _pinDigits.value = ""
        checkPinStatus()
    }

    fun clearActiveSession() {
        securityManager.lockVault()
        _pinDigits.value = ""
        setupPinTemp = ""
        resetPinTemp = ""
        verifiedSecurityAnswer = null
        checkPinStatus()
    }

    fun lockVault() {
        clearActiveSession()
    }

    fun resetAuth() {
        _pinDigits.value = ""
        setupPinTemp = ""
        resetPinTemp = ""
        verifiedSecurityAnswer = null
        checkPinStatus()
    }

    class Factory(
        private val application: Application? = null,
        private val securityManager: VaultSecurityManager,
        private val vaultFileManager: VaultFileManager? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(VaultAuthViewModel::class.java)) {
                return VaultAuthViewModel(application, securityManager, vaultFileManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

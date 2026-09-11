package com.devson.nvplayer.viewmodel

import android.app.Application
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.devson.nvplayer.data.security.VaultBiometricHelper
import com.devson.nvplayer.data.security.VaultFileManager
import com.devson.nvplayer.data.security.VaultMetadataStatus
import com.devson.nvplayer.data.security.VaultSecurityManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface VaultAuthState {
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

    init {
        checkPinStatus()
    }

    fun checkPinStatus() {
        _pinDigits.value = ""
        setupPinTemp = ""
        resetPinTemp = ""

        val hasFilesOnDisk = securityManager.hasExistingVaultOnDisk()
        val fileCount = securityManager.getExistingVaultFileCount()
        val isMetadataValid = securityManager.validateVaultMetadata() == VaultMetadataStatus.VALID
        val isLocallyInitialized = securityManager.isVaultInitializedLocally()

        if (hasFilesOnDisk && !isLocallyInitialized) {
            _authState.value = VaultAuthState.ExistingVaultFound(
                fileCount = fileCount,
                isMetadataValid = isMetadataValid
            )
        } else if (securityManager.isPinSet()) {
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

    fun onRestoreExistingVaultClicked() {
        _pinDigits.value = ""
        _authState.value = VaultAuthState.RestorePinEntry(securityManager.getExistingVaultFileCount())
    }

    fun onRemoveOldVaultClicked() {
        val count = securityManager.getExistingVaultFileCount()
        _authState.value = VaultAuthState.ConfirmRemoveVault(step = 1, fileCount = count)
    }

    fun requestVaultReset() {
        scope.launch(mainDispatcher) {
            val (totalFiles, encryptedFiles) = withContext(ioDispatcher) {
                val vaultDir = securityManager.persistentVaultDirectory
                val vltFiles = vaultDir.listFiles { f -> f.extension == "vlt" } ?: emptyArray()
                var encCount = 0
                for (file in vltFiles) {
                    try {
                        val header = vaultFileManager?.vaultContainer?.inspectVaultFile(file)
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

    fun onCancelRemoveVault() {
        _pinDigits.value = ""
        checkPinStatus()
    }

    fun onRetryPin() {
        _pinDigits.value = ""
        _authState.value = VaultAuthState.RestorePinEntry(securityManager.getExistingVaultFileCount())
    }

    fun onDigit(digit: String) {
        if (_pinDigits.value.length < 4) {
            _pinDigits.value += digit
            if (_pinDigits.value.length == 4) {
                processCompletedPin(_pinDigits.value)
            }
        }
    }

    fun onBackspace() {
        if (_pinDigits.value.isNotEmpty()) {
            _pinDigits.value = _pinDigits.value.dropLast(1)
        }
    }

    fun onClear() {
        _pinDigits.value = ""
    }

    private fun processCompletedPin(pin: String) {
        scope.launch(mainDispatcher) {
            when (val state = _authState.value) {
                is VaultAuthState.SetupPin -> {
                    setupPinTemp = pin
                    _pinDigits.value = ""
                    _authState.value = VaultAuthState.ConfirmPin
                }
                is VaultAuthState.ConfirmPin -> {
                    if (pin == setupPinTemp) {
                        _pinDigits.value = ""
                        _authState.value = VaultAuthState.SetupSecurityQuestion(pin)
                    } else {
                        _pinDigits.value = ""
                        _authState.value = VaultAuthState.Error("PINs do not match. Try again.")
                    }
                }
                is VaultAuthState.EnterPin -> {
                    if (securityManager.verifyPin(pin)) {
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
                is VaultAuthState.EnterPinForReset -> {
                    if (securityManager.verifyPin(pin)) {
                        _pinDigits.value = ""
                        _authState.value = VaultAuthState.ConfirmDeleteVault(
                            step = 1,
                            fileCount = state.fileCount,
                            encryptedCount = state.encryptedCount,
                            hasEncryptedContent = state.encryptedCount > 0,
                            authenticatedPin = pin
                        )
                    } else {
                        _pinDigits.value = ""
                        _authState.value = state.copy(error = "Incorrect PIN")
                    }
                }
                is VaultAuthState.RestorePinEntry, is VaultAuthState.RestoreExistingVault -> {
                    val fileCount = (state as? VaultAuthState.RestorePinEntry)?.fileCount
                        ?: (state as? VaultAuthState.RestoreExistingVault)?.fileCount
                        ?: securityManager.getExistingVaultFileCount()

                    if (securityManager.verifyPin(pin)) {
                        _pinDigits.value = ""
                        _authState.value = VaultAuthState.Restoring(fileCount)
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
                    if (securityManager.verifyPin(pin)) {
                        _pinDigits.value = ""
                        _authState.value = VaultAuthState.Restoring(state.remainingFiles)
                        securityManager.setVaultInitializedLocally(true)
                        withContext(ioDispatcher) {
                            vaultFileManager?.rebuildDatabaseFromStorage(credential = pin)
                        }
                        _authState.value = VaultAuthState.Authenticated
                    } else {
                        _pinDigits.value = ""
                        _authState.value = VaultAuthState.IncorrectPin(
                            message = "Incorrect Vault PIN. Your existing vault data has not been changed.",
                            isFromRestore = true,
                            remainingFiles = state.remainingFiles
                        )
                    }
                }
                is VaultAuthState.ResetPin -> {
                    resetPinTemp = pin
                    _pinDigits.value = ""
                    _authState.value = VaultAuthState.ConfirmResetPin
                }
                is VaultAuthState.ConfirmResetPin -> {
                    if (pin == resetPinTemp) {
                        securityManager.setPin(pin)
                        securityManager.setVaultInitializedLocally(true)
                        _pinDigits.value = ""
                        withContext(ioDispatcher) {
                            vaultFileManager?.rebuildDatabaseFromStorage(credential = pin)
                        }
                        _authState.value = VaultAuthState.Authenticated
                    } else {
                        _pinDigits.value = ""
                        _authState.value = VaultAuthState.Error("New PINs do not match. Try again.")
                    }
                }
                is VaultAuthState.Error -> {
                    if (securityManager.verifyPin(pin)) {
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
            securityManager.setPin(current.pin, question, answer)
            securityManager.setVaultInitializedLocally(true)
            scope.launch(ioDispatcher) {
                vaultFileManager?.rebuildDatabaseFromStorage(credential = current.pin)
            }
            _pinDigits.value = ""
            _authState.value = VaultAuthState.Authenticated
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
                    vaultFileManager?.removeAllVaultData(securityManager)
                        ?: securityManager.resetVault(deleteFiles = true)
                }
                _pinDigits.value = ""
                setupPinTemp = ""
                resetPinTemp = ""
                _authState.value = VaultAuthState.SetupPin
            }
        } else {
            securityManager.resetVault(deleteFiles = false)
            _pinDigits.value = ""
            setupPinTemp = ""
            resetPinTemp = ""
            _authState.value = VaultAuthState.SetupPin
        }
    }

    fun authenticateWithBiometrics(activity: FragmentActivity) {
        if (!VaultBiometricHelper.canAuthenticate(activity)) {
            return
        }

        VaultBiometricHelper.promptBiometric(
            activity = activity,
            onSuccess = {
                _pinDigits.value = ""
                scope.launch(ioDispatcher) {
                    vaultFileManager?.rebuildDatabaseFromStorage()
                }
                _authState.value = VaultAuthState.Authenticated
            },
            onError = { _, _ -> }
        )
    }

    fun lockVault() {
        securityManager.clearSession()
        scope.launch(ioDispatcher) {
            vaultFileManager?.cleanPlaybackTemp()
        }
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

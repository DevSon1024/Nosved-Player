package com.devson.nvplayer.ui.screen.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Shield
import com.devson.nvplayer.data.security.VaultSecurityManager
import com.devson.nvplayer.domain.model.VaultStorageMode
import com.devson.nvplayer.ui.screen.vault.VaultProtectionContent

private enum class VaultSettingsFlow {
    MAIN,
    VAULT_PROTECTION_VERIFY_PIN,
    STORAGE_PROTECTION_MODE,
    CHANGE_PIN_OLD,
    CHANGE_PIN_NEW,
    CHANGE_PIN_CONFIRM,
    UPDATE_SECURITY_QUESTION_VERIFY_PIN,
    UPDATE_SECURITY_QUESTION_FORM
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultSettingsBottomSheet(
    onDismissRequest: () -> Unit,
    securityManager: VaultSecurityManager,
    onRequestVaultReset: () -> Unit = {}
) {
    val context = LocalContext.current
    var currentFlow by remember { mutableStateOf(VaultSettingsFlow.MAIN) }
    val isPinConfigured = remember { securityManager.isPinSet() || securityManager.hasExistingVaultOnDisk() }

    var oldPinInput by remember { mutableStateOf("") }
    var newPinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var selectedQuestion by remember { mutableStateOf(securityManager.getSecurityQuestion() ?: VaultSecurityManager.DEFAULT_SECURITY_QUESTIONS.first()) }
    var newAnswerText by remember { mutableStateOf("") }
    var questionDropdownExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedContent(
                targetState = currentFlow,
                transitionSpec = { (fadeIn() togetherWith fadeOut()).using(null) },
                label = "vault_settings_flow"
            ) { flow ->
                when (flow) {
                    VaultSettingsFlow.MAIN -> {
                        VaultSettingsMainContent(
                            isPinConfigured = isPinConfigured,
                            currentStorageMode = securityManager.getDefaultStorageMode(),
                            onVaultProtectionClick = {
                                oldPinInput = ""
                                errorMessage = null
                                currentFlow = VaultSettingsFlow.VAULT_PROTECTION_VERIFY_PIN
                            },
                            onChangePinClick = {
                                oldPinInput = ""
                                newPinInput = ""
                                confirmPinInput = ""
                                errorMessage = null
                                currentFlow = VaultSettingsFlow.CHANGE_PIN_OLD
                            },
                            onUpdateSecurityQuestionClick = {
                                oldPinInput = ""
                                newAnswerText = ""
                                errorMessage = null
                                currentFlow = VaultSettingsFlow.UPDATE_SECURITY_QUESTION_VERIFY_PIN
                            },
                            onResetVaultClick = {
                                onDismissRequest()
                                onRequestVaultReset()
                            }
                        )
                    }

                    VaultSettingsFlow.VAULT_PROTECTION_VERIFY_PIN -> {
                        PinInputStep(
                            title = "Verify PIN",
                            subtitle = "Enter current PIN to manage Vault Protection",
                            pin = oldPinInput,
                            error = errorMessage,
                            onDigit = {
                                if (oldPinInput.length < 4) {
                                    oldPinInput += it
                                    errorMessage = null
                                    if (oldPinInput.length == 4) {
                                        if (securityManager.verifyPin(oldPinInput)) {
                                            currentFlow = VaultSettingsFlow.STORAGE_PROTECTION_MODE
                                        } else {
                                            oldPinInput = ""
                                            errorMessage = "Incorrect PIN"
                                        }
                                    }
                                }
                            },
                            onBackspace = { if (oldPinInput.isNotEmpty()) oldPinInput = oldPinInput.dropLast(1) },
                            onCancel = { currentFlow = VaultSettingsFlow.MAIN }
                        )
                    }

                    VaultSettingsFlow.STORAGE_PROTECTION_MODE -> {
                        var selectedMode by remember { mutableStateOf(securityManager.getDefaultStorageMode()) }
                        VaultProtectionContent(
                            currentMode = selectedMode,
                            onModeSelected = { newMode ->
                                selectedMode = newMode
                                securityManager.setDefaultStorageMode(newMode)
                                Toast.makeText(context, "Storage protection mode updated", Toast.LENGTH_SHORT).show()
                            },
                            onClose = { currentFlow = VaultSettingsFlow.MAIN },
                            isScrollable = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    VaultSettingsFlow.CHANGE_PIN_OLD -> {
                        PinInputStep(
                            title = "Enter Current PIN",
                            subtitle = "Verify your identity before setting a new PIN",
                            pin = oldPinInput,
                            error = errorMessage,
                            onDigit = {
                                if (oldPinInput.length < 4) {
                                    oldPinInput += it
                                    errorMessage = null
                                    if (oldPinInput.length == 4) {
                                        if (securityManager.verifyPin(oldPinInput)) {
                                            currentFlow = VaultSettingsFlow.CHANGE_PIN_NEW
                                        } else {
                                            oldPinInput = ""
                                            errorMessage = "Incorrect current PIN"
                                        }
                                    }
                                }
                            },
                            onBackspace = { if (oldPinInput.isNotEmpty()) oldPinInput = oldPinInput.dropLast(1) },
                            onCancel = { currentFlow = VaultSettingsFlow.MAIN }
                        )
                    }

                    VaultSettingsFlow.CHANGE_PIN_NEW -> {
                        PinInputStep(
                            title = "Enter New PIN",
                            subtitle = "Choose a new 4-digit PIN",
                            pin = newPinInput,
                            error = errorMessage,
                            onDigit = {
                                if (newPinInput.length < 4) {
                                    newPinInput += it
                                    errorMessage = null
                                    if (newPinInput.length == 4) {
                                        currentFlow = VaultSettingsFlow.CHANGE_PIN_CONFIRM
                                    }
                                }
                            },
                            onBackspace = { if (newPinInput.isNotEmpty()) newPinInput = newPinInput.dropLast(1) },
                            onCancel = { currentFlow = VaultSettingsFlow.MAIN }
                        )
                    }

                    VaultSettingsFlow.CHANGE_PIN_CONFIRM -> {
                        PinInputStep(
                            title = "Confirm New PIN",
                            subtitle = "Re-enter your new 4-digit PIN",
                            pin = confirmPinInput,
                            error = errorMessage,
                            onDigit = {
                                if (confirmPinInput.length < 4) {
                                    confirmPinInput += it
                                    errorMessage = null
                                    if (confirmPinInput.length == 4) {
                                        if (confirmPinInput == newPinInput) {
                                            securityManager.updatePin(oldPinInput, newPinInput)
                                            Toast.makeText(context, "PIN updated successfully", Toast.LENGTH_SHORT).show()
                                            currentFlow = VaultSettingsFlow.MAIN
                                        } else {
                                            confirmPinInput = ""
                                            errorMessage = "PINs do not match. Try again."
                                        }
                                    }
                                }
                            },
                            onBackspace = { if (confirmPinInput.isNotEmpty()) confirmPinInput = confirmPinInput.dropLast(1) },
                            onCancel = { currentFlow = VaultSettingsFlow.MAIN }
                        )
                    }

                    VaultSettingsFlow.UPDATE_SECURITY_QUESTION_VERIFY_PIN -> {
                        PinInputStep(
                            title = "Verify PIN",
                            subtitle = "Enter current PIN to update security question",
                            pin = oldPinInput,
                            error = errorMessage,
                            onDigit = {
                                if (oldPinInput.length < 4) {
                                    oldPinInput += it
                                    errorMessage = null
                                    if (oldPinInput.length == 4) {
                                        if (securityManager.verifyPin(oldPinInput)) {
                                            currentFlow = VaultSettingsFlow.UPDATE_SECURITY_QUESTION_FORM
                                        } else {
                                            oldPinInput = ""
                                            errorMessage = "Incorrect PIN"
                                        }
                                    }
                                }
                            },
                            onBackspace = { if (oldPinInput.isNotEmpty()) oldPinInput = oldPinInput.dropLast(1) },
                            onCancel = { currentFlow = VaultSettingsFlow.MAIN }
                        )
                    }

                    VaultSettingsFlow.UPDATE_SECURITY_QUESTION_FORM -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Update Security Question",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            ExposedDropdownMenuBox(
                                expanded = questionDropdownExpanded,
                                onExpandedChange = { questionDropdownExpanded = !questionDropdownExpanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedQuestion,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Select Question") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = questionDropdownExpanded) },
                                    modifier = Modifier
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                        .fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                ExposedDropdownMenu(
                                    expanded = questionDropdownExpanded,
                                    onDismissRequest = { questionDropdownExpanded = false }
                                ) {
                                    VaultSecurityManager.DEFAULT_SECURITY_QUESTIONS.forEach { q ->
                                        DropdownMenuItem(
                                            text = { Text(q) },
                                            onClick = {
                                                selectedQuestion = q
                                                questionDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = newAnswerText,
                                onValueChange = { newAnswerText = it },
                                label = { Text("Your Answer") },
                                placeholder = { Text("Enter answer") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = {
                                    if (newAnswerText.isNotBlank()) {
                                        securityManager.setPin(oldPinInput, selectedQuestion, newAnswerText)
                                        Toast.makeText(context, "Security question updated", Toast.LENGTH_SHORT).show()
                                        currentFlow = VaultSettingsFlow.MAIN
                                    }
                                },
                                enabled = newAnswerText.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Save Question & Answer")
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            TextButton(onClick = { currentFlow = VaultSettingsFlow.MAIN }) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultSettingsMainContent(
    isPinConfigured: Boolean,
    currentStorageMode: VaultStorageMode,
    onVaultProtectionClick: () -> Unit,
    onChangePinClick: () -> Unit,
    onUpdateSecurityQuestionClick: () -> Unit,
    onResetVaultClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Privacy Vault Settings",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Manage your PIN, recovery question, and vault security.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (!isPinConfigured) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Vault is not set up yet",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Navigate to the Vault tab to set your 4-digit PIN and security question.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    VaultSettingsRow(
                        icon = Icons.Filled.Shield,
                        title = "Vault Protection",
                        subtitle = if (currentStorageMode == VaultStorageMode.ENCRYPTED) "Encrypted" else "Hidden only \u2014 not encrypted.",
                        onClick = onVaultProtectionClick
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    VaultSettingsRow(
                        icon = Icons.Filled.Pin,
                        title = "Change Vault PIN",
                        subtitle = "Update your 4-digit security PIN",
                        onClick = onChangePinClick
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    VaultSettingsRow(
                        icon = Icons.Filled.QuestionAnswer,
                        title = "Security Question",
                        subtitle = "Update question & answer for PIN recovery",
                        onClick = onUpdateSecurityQuestionClick
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    VaultSettingsRow(
                        icon = Icons.Filled.LockReset,
                        title = "Reset Vault",
                        subtitle = "Clear PIN and security question",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = onResetVaultClick
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultSettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PinInputStep(
    title: String,
    subtitle: String,
    pin: String,
    error: String?,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(4.dp))
        Text(error ?: subtitle, style = MaterialTheme.typography.bodyMedium, color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(20.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until 4) {
                val isFilled = i < pin.length
                val color = if (error != null) MaterialTheme.colorScheme.error else if (isFilled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9")
        )
        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (d in row) {
                    MiniKeypadButton(digit = d, onClick = { onDigit(d) })
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.size(56.dp))
            MiniKeypadButton(digit = "0", onClick = { onDigit("0") })
            IconButton(
                onClick = onBackspace,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onCancel) {
            Text("Cancel")
        }
    }
}

@Composable
private fun MiniKeypadButton(digit: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable { onClick() }
    ) {
        Text(text = digit, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp), color = MaterialTheme.colorScheme.onSurface)
    }
}

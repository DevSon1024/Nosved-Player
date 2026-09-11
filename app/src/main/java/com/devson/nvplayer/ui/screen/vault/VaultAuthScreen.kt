package com.devson.nvplayer.ui.screen.vault

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devson.nvplayer.data.security.VaultSecurityManager
import com.devson.nvplayer.viewmodel.VaultAuthState
import com.devson.nvplayer.viewmodel.VaultAuthViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultAuthScreen(
    viewModel: VaultAuthViewModel,
    onVaultProtectionClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val pinDigits by viewModel.pinDigits.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val coroutineScope = rememberCoroutineScope()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(authState) {
        if (authState is VaultAuthState.Error) {
            snackbarHostState.showSnackbar((authState as VaultAuthState.Error).message)
        }
    }

    // BackHandler to cancel recovery steps gracefully without bypassing or triggering deletion
    BackHandler(
        enabled = authState is VaultAuthState.RestorePinEntry ||
                authState is VaultAuthState.ExistingVaultFound ||
                authState is VaultAuthState.IncorrectPin ||
                authState is VaultAuthState.ConfirmRemoveVault ||
                authState is VaultAuthState.AnswerSecurityQuestion
    ) {
        when (authState) {
            is VaultAuthState.ConfirmRemoveVault -> viewModel.onCancelRemoveVault()
            is VaultAuthState.IncorrectPin -> viewModel.onCancelRemoveVault()
            is VaultAuthState.RestorePinEntry -> viewModel.onCancelRemoveVault()
            is VaultAuthState.AnswerSecurityQuestion -> viewModel.checkPinStatus()
            else -> {}
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "vault_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Two-step confirmation dialogs for removing old vault data
    when (val state = authState) {
        is VaultAuthState.ConfirmRemoveVault -> {
            if (state.step == 1) {
                AlertDialog(
                    onDismissRequest = { viewModel.onCancelRemoveVault() },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(32.dp)
                        )
                    },
                    title = {
                        Text(
                            text = "Remove Old Vault Data?",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = "This will permanently delete all existing protected videos. Encrypted videos cannot be recovered without your previous PIN."
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = { viewModel.onConfirmRemoveStep1() },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Continue")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.onCancelRemoveVault() }) {
                            Text("Cancel")
                        }
                    }
                )
            } else {
                AlertDialog(
                    onDismissRequest = { viewModel.onCancelRemoveVault() },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(32.dp)
                        )
                    },
                    title = {
                        Text(
                            text = "Permanently Delete All Files?",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = "${state.fileCount} file(s) will be permanently deleted from device storage. This action cannot be undone."
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.onConfirmRemoveFinal() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Delete Permanently")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.onCancelRemoveVault() }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
        else -> {}
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 96.dp),
        contentAlignment = Alignment.Center
    ) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        )

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clickable {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Enter PIN to authenticate and manage Vault Protection")
                    }
                    onVaultProtectionClick()
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = "Vault Protection",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Protection Mode",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        when (val state = authState) {
            is VaultAuthState.ExistingVaultFound -> {
                ExistingVaultFoundContent(
                    fileCount = state.fileCount,
                    isMetadataValid = state.isMetadataValid,
                    pulseScale = pulseScale,
                    onRestore = { viewModel.onRestoreExistingVaultClicked() },
                    onRemove = { viewModel.onRemoveOldVaultClicked() }
                )
            }

            is VaultAuthState.IncorrectPin -> {
                IncorrectPinContent(
                    message = state.message,
                    onRetry = { viewModel.onRetryPin() },
                    onRemove = { viewModel.onRemoveOldVaultClicked() }
                )
            }

            is VaultAuthState.Restoring -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(56.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Restoring Vault Media",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Rebuilding vault catalog with ${state.fileCount} video(s)...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            is VaultAuthState.SetupSecurityQuestion -> {
                SetupSecurityQuestionContent(
                    onSave = { question, answer ->
                        viewModel.completeSecurityQuestionSetup(question, answer)
                    }
                )
            }

            is VaultAuthState.AnswerSecurityQuestion -> {
                AnswerSecurityQuestionContent(
                    question = viewModel.securityManager.getSecurityQuestion() ?: "Security Question",
                    onVerify = { answer ->
                        viewModel.verifySecurityAnswerAndProceed(answer)
                    },
                    onBack = { viewModel.checkPinStatus() }
                )
            }

            else -> {
                // Keypad view for EnterPin, RestorePinEntry, SetupPin, ConfirmPin, ResetPin, ConfirmResetPin, Error
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(4.dp))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(88.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                        )
                                    )
                                )
                        ) {
                            Icon(
                                imageVector = when (state) {
                                    is VaultAuthState.SetupPin, is VaultAuthState.ConfirmPin -> Icons.Filled.Shield
                                    is VaultAuthState.ResetPin, is VaultAuthState.ConfirmResetPin -> Icons.Filled.LockReset
                                    is VaultAuthState.RestorePinEntry, is VaultAuthState.RestoreExistingVault -> Icons.Filled.Restore
                                    else -> Icons.Filled.Lock
                                },
                                contentDescription = "Vault Security",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        val titleText = when (state) {
                            is VaultAuthState.SetupPin -> "Set Up Vault PIN"
                            is VaultAuthState.ConfirmPin -> "Confirm Your PIN"
                            is VaultAuthState.EnterPin -> "Privacy Vault"
                            is VaultAuthState.RestorePinEntry, is VaultAuthState.RestoreExistingVault -> "Enter Previous Vault PIN"
                            is VaultAuthState.ResetPin -> "Enter New PIN"
                            is VaultAuthState.ConfirmResetPin -> "Confirm New PIN"
                            is VaultAuthState.Error -> "Authentication Failed"
                            is VaultAuthState.Authenticated -> "Unlocked"
                            else -> "Privacy Vault"
                        }

                        val subtitleText = when (state) {
                            is VaultAuthState.SetupPin -> "Create a 4-digit PIN to securely protect your private media."
                            is VaultAuthState.ConfirmPin -> "Re-enter the 4-digit PIN to confirm."
                            is VaultAuthState.EnterPin -> "Enter your 4-digit PIN to access."
                            is VaultAuthState.RestorePinEntry, is VaultAuthState.RestoreExistingVault -> "Enter the 4-digit PIN used when these files were protected."
                            is VaultAuthState.ResetPin -> "Create a new 4-digit PIN for your Vault."
                            is VaultAuthState.ConfirmResetPin -> "Re-enter the new 4-digit PIN to confirm."
                            is VaultAuthState.Error -> (state as VaultAuthState.Error).message
                            is VaultAuthState.Authenticated -> "Access granted."
                            else -> ""
                        }

                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (state is VaultAuthState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (state is VaultAuthState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // PIN Dots
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (i in 0 until 4) {
                                val isFilled = i < pinDigits.length
                                val dotColor = if (state is VaultAuthState.Error) {
                                    MaterialTheme.colorScheme.error
                                } else if (isFilled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }

                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                            }
                        }

                        // Action links
                        if (state is VaultAuthState.EnterPin) {
                            Spacer(modifier = Modifier.height(12.dp))
                            TextButton(onClick = { viewModel.onForgotPinClicked() }) {
                                Text("Forgot PIN?", style = MaterialTheme.typography.labelLarge)
                            }
                        } else if (state is VaultAuthState.RestorePinEntry || state is VaultAuthState.RestoreExistingVault) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { viewModel.onForgotPinClicked() }) {
                                    Text("Forgot PIN?", style = MaterialTheme.typography.labelLarge)
                                }
                                TextButton(
                                    onClick = { viewModel.onRemoveOldVaultClicked() },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Remove Old Vault Data", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }

                    // Keypad
                    VaultKeypad(
                        onDigitClick = { viewModel.onDigit(it) },
                        onBackspaceClick = { viewModel.onBackspace() },
                        onBiometricClick = {
                            if (activity != null) {
                                viewModel.authenticateWithBiometrics(activity)
                            }
                        },
                        showBiometric = (state is VaultAuthState.EnterPin || state is VaultAuthState.Error) && viewModel.securityManager.isBiometricEnabled()
                    )
                }
            }
        }
    }
}

@Composable
private fun ExistingVaultFoundContent(
    fileCount: Int,
    isMetadataValid: Boolean,
    pulseScale: Float,
    onRestore: () -> Unit,
    onRemove: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(96.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                        )
                    )
                )
        ) {
            Icon(
                imageVector = Icons.Filled.FolderZip,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Existing Vault Data Found",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Nosved Player found protected vault data ($fileCount video${if (fileCount != 1) "s" else ""}) from a previous installation. You can restore it if you remember your previous Vault PIN.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Vault Recovery Details",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Protected Files: $fileCount video(s) detected\nSecurity Status: ${if (isMetadataValid) "Verified Cryptographic Metadata" else "Unverified / Orphaned Media"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRestore,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Restore,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Restore Existing Vault", fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onRemove,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Remove Old Vault Data", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun IncorrectPinContent(
    message: String,
    onRetry: () -> Unit,
    onRemove: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Incorrect Vault PIN",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Your existing vault data has not been changed.",
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "No files have been modified or deleted. Please try entering your PIN again, or remove the previous vault data to start a new vault.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Try Again", fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onRemove,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Remove Old Vault Data", fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupSecurityQuestionContent(
    onSave: (question: String, answer: String) -> Unit
) {
    var selectedQuestion by remember { mutableStateOf(VaultSecurityManager.DEFAULT_SECURITY_QUESTIONS.first()) }
    var answerText by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.QuestionAnswer,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Security Question",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Used to recover or reset your PIN if forgotten.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedQuestion,
                onValueChange = {},
                readOnly = true,
                label = { Text("Select Question") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                VaultSecurityManager.DEFAULT_SECURITY_QUESTIONS.forEach { q ->
                    DropdownMenuItem(
                        text = { Text(q) },
                        onClick = {
                            selectedQuestion = q
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = answerText,
            onValueChange = { answerText = it },
            label = { Text("Your Answer") },
            placeholder = { Text("Enter answer here") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = {
                if (answerText.isNotBlank()) {
                    onSave(selectedQuestion, answerText)
                }
            },
            enabled = answerText.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Save & Enter Vault", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AnswerSecurityQuestionContent(
    question: String,
    onVerify: (answer: String) -> Unit,
    onBack: () -> Unit
) {
    var answerText by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.LockReset,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Reset Vault PIN",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = question,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Explicit Security Question Notice
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Security Question Notice",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Resetting your PIN using the security question only restores access to unencrypted hidden files. Encrypted videos cannot be decrypted without the original PIN.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = answerText,
            onValueChange = {
                answerText = it
                hasError = false
            },
            label = { Text("Security Answer") },
            placeholder = { Text("Enter answer") },
            singleLine = true,
            isError = hasError,
            supportingText = if (hasError) {
                { Text("Incorrect answer. Please try again.") }
            } else null,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (answerText.isNotBlank()) {
                    onVerify(answerText)
                    hasError = true
                }
            },
            enabled = answerText.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Verify Answer", fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onBack) {
            Text("Back to PIN")
        }
    }
}

@Composable
private fun VaultKeypad(
    onDigitClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    onBiometricClick: () -> Unit,
    showBiometric: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
                for (digit in row) {
                    KeypadDigitButton(digit = digit, onClick = { onDigitClick(digit) })
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBiometric) {
                IconButton(
                    onClick = onBiometricClick,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Fingerprint,
                        contentDescription = "Biometric Unlock",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(68.dp))
            }

            KeypadDigitButton(digit = "0", onClick = { onDigitClick("0") })

            IconButton(
                onClick = onBackspaceClick,
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Backspace",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun KeypadDigitButton(
    digit: String,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { onClick() }
    ) {
        Text(
            text = digit,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 26.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

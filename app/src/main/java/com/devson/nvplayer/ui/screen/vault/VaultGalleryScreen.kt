package com.devson.nvplayer.ui.screen.vault

import android.app.Activity
import android.net.Uri
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.devson.nvplayer.data.database.VaultEntity
import com.devson.nvplayer.domain.model.VaultStorageMode
import com.devson.nvplayer.domain.model.Video
import com.devson.nvplayer.util.formatDuration
import com.devson.nvplayer.util.formatSize
import com.devson.nvplayer.viewmodel.VaultGalleryViewModel
import com.devson.nvplayer.viewmodel.VideoConversionState
import com.devson.nvplayer.ui.screen.settings.VaultSettingsBottomSheet
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultGalleryScreen(
    viewModel: VaultGalleryViewModel,
    onLockClick: () -> Unit,
    onPlayMedia: (VaultEntity, File, Video) -> Unit,
    initialOpenProtection: Boolean = false,
    onResetVaultClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isPreparingPlayback by remember { mutableStateOf(false) }
    val vaultItems by viewModel.vaultMediaList.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val currentStorageMode by viewModel.defaultStorageMode.collectAsStateWithLifecycle()
    var showProtectionSheet by remember(initialOpenProtection) { mutableStateOf(initialOpenProtection) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    val pendingIntentSender by viewModel.pendingIntentSender.collectAsStateWithLifecycle()
    val intentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) {
        viewModel.clearPendingIntentSender()
    }

    LaunchedEffect(pendingIntentSender) {
        pendingIntentSender?.let { sender ->
            intentSenderLauncher.launch(
                androidx.activity.result.IntentSenderRequest.Builder(sender).build()
            )
        }
    }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            for (uri in uris) {
                try {
                    val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                } catch (_: Exception) {}
            }
            val titles = uris.map { uri ->
                var name = "Protected Video"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        name = cursor.getString(nameIndex) ?: name
                    }
                }
                name
            }
            viewModel.importVideos(uris, titles)
        }
    }

    var selectedItemForDelete by remember { mutableStateOf<VaultEntity?>(null) }
    var selectedItemForRestore by remember { mutableStateOf<VaultEntity?>(null) }
    var itemToConvert by remember { mutableStateOf<Pair<VaultEntity, VaultStorageMode>?>(null) }
    val conversionState by viewModel.conversionState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 80.dp)
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Privacy Vault",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        val isEncrypted = currentStorageMode == VaultStorageMode.ENCRYPTED
                        val badgeLabel = if (isEncrypted) "Encrypted" else "Hidden (No Encryption)"
                        val badgeContainerColor = if (isEncrypted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                        val badgeContentColor = if (isEncrypted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(badgeContainerColor)
                                .clickable { showProtectionSheet = true }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badgeLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = badgeContentColor
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showProtectionSheet = true }) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = "Vault Protection",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { mediaPickerLauncher.launch(arrayOf("video/*")) }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Import Video",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onLockClick) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Lock Vault",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box {
                        var topMenuExpanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { topMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "More Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = topMenuExpanded,
                            onDismissRequest = { topMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Vault Settings") },
                                leadingIcon = { Icon(Icons.Filled.Shield, contentDescription = null) },
                                onClick = {
                                    topMenuExpanded = false
                                    showSettingsSheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Reset Privacy Vault", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    topMenuExpanded = false
                                    onResetVaultClick()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (isProcessing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                if (vaultItems.isEmpty() && !isProcessing) {
                    VaultEmptyState(
                        onImportClick = { mediaPickerLauncher.launch(arrayOf("video/*")) }
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(vaultItems, key = { it.id }) { item ->
                            VaultMediaCard(
                                item = item,
                                onClick = {
                                    if (!isPreparingPlayback) {
                                        coroutineScope.launch {
                                            isPreparingPlayback = true
                                            try {
                                                val (file, video) = viewModel.preparePlaybackVideo(item)
                                                onPlayMedia(item, file, video)
                                            } catch (e: Exception) {
                                                snackbarHostState.showSnackbar("Failed to prepare video: ${e.localizedMessage}")
                                            } finally {
                                                isPreparingPlayback = false
                                            }
                                        }
                                    }
                                },
                                onRestore = { selectedItemForRestore = item },
                                onDelete = { selectedItemForDelete = item },
                                onConvert = { entity, targetMode ->
                                    itemToConvert = Pair(entity, targetMode)
                                }
                            )
                        }
                    }
                }
            }

            if (isPreparingPlayback) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                            Text(
                                text = "Preparing secure video...",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }

    selectedItemForDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedItemForDelete = null },
            icon = { Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Permanently?") },
            text = { Text("Are you sure you want to permanently delete \"${item.title}\"? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePermanently(item)
                        selectedItemForDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedItemForDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    selectedItemForRestore?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedItemForRestore = null },
            icon = { Icon(Icons.Filled.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Restore to Public Storage?") },
            text = { Text("This will unencrypt and move \"${item.title}\" back to your public Movies folder.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.restoreVideo(item)
                        selectedItemForRestore = null
                    }
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedItemForRestore = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    itemToConvert?.let { (item, targetMode) ->
        ConversionConfirmationDialog(
            item = item,
            targetMode = targetMode,
            onConfirm = { pin ->
                val isValid = viewModel.verifyCredential(pin)
                if (isValid) {
                    viewModel.startProtectionConversion(item, targetMode, pin)
                    itemToConvert = null
                    true
                } else {
                    false
                }
            },
            onDismiss = { itemToConvert = null }
        )
    }

    if (conversionState.isConverting) {
        ConversionProgressDialog(
            conversionState = conversionState,
            onCancel = { viewModel.cancelConversion() }
        )
    }

    if (showProtectionSheet) {
        VaultProtectionBottomSheet(
            currentMode = currentStorageMode,
            onModeSelected = { newMode ->
                viewModel.setStorageMode(newMode)
            },
            onDismissRequest = { showProtectionSheet = false }
        )
    }

    if (showSettingsSheet) {
        VaultSettingsBottomSheet(
            securityManager = viewModel.vaultSecurityManager,
            onRequestVaultReset = onResetVaultClick,
            onDismissRequest = { showSettingsSheet = false }
        )
    }
}

@Composable
private fun VaultMediaCard(
    item: VaultEntity,
    onClick: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onConvert: (VaultEntity, VaultStorageMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                val imageModel = remember(item.thumbnailPath, item.vaultPath) {
                    val thumbFile = item.thumbnailPath?.let { File(it) }
                    if (thumbFile?.exists() == true) thumbFile else File(item.vaultPath)
                }

                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageModel)
                        .crossfade(true)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .build(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                startY = 60f
                            )
                        )
                )

                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        val isEnc = item.storageMode == VaultStorageMode.ENCRYPTED
                        Icon(
                            imageVector = if (isEnc) Icons.Filled.Lock else Icons.Rounded.LockOpen,
                            contentDescription = if (isEnc) "Encrypted" else "Hidden",
                            tint = if (isEnc) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (isEnc) "Encrypted" else "Hidden",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = Color.White
                        )
                    }
                }

                if (item.durationMs > 0) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = formatDuration(item.durationMs),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = formatSize(item.fileSize),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Play Video") },
                            leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onClick()
                            }
                        )
                        if (item.storageMode == VaultStorageMode.NONE) {
                            DropdownMenuItem(
                                text = { Text("Encrypt Video") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onConvert(item, VaultStorageMode.ENCRYPTED)
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Decrypt to Hidden") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.LockOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onConvert(item, VaultStorageMode.NONE)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Restore to Public") },
                            leadingIcon = { Icon(Icons.Filled.Restore, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRestore()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Permanently", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversionConfirmationDialog(
    item: VaultEntity,
    targetMode: VaultStorageMode,
    onConfirm: (String) -> Boolean,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val isEncrypting = targetMode == VaultStorageMode.ENCRYPTED

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (isEncrypting) Icons.Filled.Lock else Icons.Rounded.LockOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(if (isEncrypting) "Encrypt Video" else "Decrypt to Hidden")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (isEncrypting) {
                        "This will convert \"${item.title}\" to authenticated AES-256-GCM encryption. The video cannot be played outside Nosved Player."
                    } else {
                        "This will decrypt \"${item.title}\" and store it as Hidden / No Encryption. The video data will be preserved byte-for-byte in the private vault directory."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Enter your Vault PIN to proceed:",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 16) {
                            pin = it
                            isError = false
                            errorMessage = ""
                        }
                    },
                    label = { Text("Vault PIN") },
                    singleLine = true,
                    isError = isError,
                    supportingText = if (isError) {
                        { Text(errorMessage, color = MaterialTheme.colorScheme.error) }
                    } else null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pin.isBlank()) {
                        isError = true
                        errorMessage = "PIN cannot be empty"
                        return@Button
                    }
                    val success = onConfirm(pin)
                    if (!success) {
                        isError = true
                        errorMessage = "Incorrect Vault PIN"
                    }
                }
            ) {
                Text(if (isEncrypting) "Encrypt" else "Decrypt")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ConversionProgressDialog(
    conversionState: VideoConversionState,
    onCancel: () -> Unit
) {
    val isEncrypting = conversionState.targetMode == VaultStorageMode.ENCRYPTED

    AlertDialog(
        onDismissRequest = { /* Modal: do not dismiss on outside click */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        icon = {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(if (isEncrypting) "Encrypting Video..." else "Decrypting Video...")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = conversionState.item?.title ?: "Converting media...",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                LinearProgressIndicator(
                    progress = { conversionState.progress },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Progress",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(conversionState.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "Please keep Nosved Player open until conversion completes.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = MaterialTheme.colorScheme.error)
            }
        }
    )
}

@Composable
private fun VaultEmptyState(
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(start = 32.dp, top = 32.dp, end = 32.dp, bottom = 96.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Your Privacy Vault is Empty",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Move private videos here to encrypt and hide them from other gallery apps.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onImportClick,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import Videos")
            }
        }
    }
}

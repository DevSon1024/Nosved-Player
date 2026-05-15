package com.devson.nvplayer.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.devson.nvplayer.BuildConfig
import com.devson.nvplayer.R

private data class LibraryInfo(
    val name: String,
    val descriptionRes: Int? = null,
    val descriptionStr: String? = null,
    val url: String,
    val version: String,
    val license: String
)

private val libraries = listOf(
    LibraryInfo(
        name = "ExoPlayer / Media3",
        descriptionRes = R.string.about_lib_exoplayer_desc,
        url = "https://github.com/androidx/media",
        version = "1.5.1",
        license = "Apache License 2.0"
    ),
    LibraryInfo(
        name = "Nextlib",
        descriptionRes = R.string.about_lib_nextlib_desc,
        url = "https://github.com/anilbeesetti/nextlib",
        version = "1.9.1",
        license = "GPL-3.0 License"
    ),
    LibraryInfo(
        name = "Jetpack Compose",
        descriptionRes = R.string.about_lib_compose_desc,
        url = "https://developer.android.com/jetpack/compose",
        version = "1.11.0",
        license = "Apache License 2.0"
    ),
    LibraryInfo(
        name = "Material 3",
        descriptionRes = R.string.about_lib_material3_desc,
        url = "https://m3.material.io",
        version = "1.5.6",
        license = "Apache License 2.0"
    ),
    LibraryInfo(
        name = "Room",
        descriptionRes = R.string.about_lib_room_desc,
        url = "https://developer.android.com/training/data-storage/room",
        version = "2.7.0",
        license = "Apache License 2.0"
    ),
    LibraryInfo(
        name = "DataStore Preferences",
        descriptionRes = R.string.about_lib_datastore_desc,
        url = "https://developer.android.com/topic/libraries/architecture/datastore",
        version = "1.1.2",
        license = "Apache License 2.0"
    ),
    LibraryInfo(
        name = "Kotlin Coroutines",
        descriptionRes = R.string.about_lib_coroutines_desc,
        url = "https://kotlinlang.org/docs/coroutines-overview.html",
        version = "1.9.0",
        license = "Apache License 2.0"
    ),
    LibraryInfo(
        name = "Kotlin",
        descriptionRes = R.string.about_lib_kotlin_desc,
        url = "https://kotlinlang.org",
        version = "2.3.0",
        license = "Apache License 2.0"
    ),
    LibraryInfo(
        name = "Coil",
        descriptionStr = "Image loading for Android backed by Kotlin Coroutines.",
        url = "https://github.com/coil-kt/coil",
        version = "2.6.0",
        license = "Apache License 2.0"
    ),
    LibraryInfo(
        name = "FFmpegKit",
        descriptionStr = "A library to run FFmpeg/FFprobe commands in applications.",
        url = "https://github.com/jamaismagic/ffmpeg-kit",
        version = "6.1.4",
        license = "GPL-3.0 License"
    ),
    LibraryInfo(
        name = "DocumentFile",
        descriptionStr = "Helper for working with documents and trees in Android storage.",
        url = "https://developer.android.com/jetpack/androidx/releases/documentfile",
        version = "1.0.1",
        license = "Apache License 2.0"
    ),
    LibraryInfo(
        name = "AndroidX Core",
        descriptionStr = "Core components for Android development with backward compatibility.",
        url = "https://developer.android.com/jetpack/androidx",
        version = "1.13.1",
        license = "Apache License 2.0"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit, onEnableDeveloperMode: () -> Unit) {
    var showCredits by remember { mutableStateOf(false) }
    var showDonateSheet by remember { mutableStateOf(false) }

    if (showCredits) {
        CreditsSubScreen(onBack = { showCredits = false })
        return
    }

    val context = LocalContext.current
    var versionClicks by remember { mutableStateOf(0) }
    
    val versionName = BuildConfig.VERSION_NAME
    val versionCode = BuildConfig.VERSION_CODE
    val deviceName = Build.MODEL
    val androidVersion = Build.VERSION.RELEASE
    val apiLevel = Build.VERSION.SDK_INT
    val supportedAbis = Build.SUPPORTED_ABIS.joinToString(", ")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
            ) {
                // App identity card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AsyncImage(
                            model = R.mipmap.ic_launcher,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.settings_version, versionName, versionCode),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            modifier = Modifier.clickable {
                                versionClicks++
                                if (versionClicks >= 8) {
                                    onEnableDeveloperMode()
                                    Toast.makeText(context, context.getString(R.string.about_developer_mode_enabled), Toast.LENGTH_SHORT).show()
                                    versionClicks = 0
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (BuildConfig.DEBUG) stringResource(R.string.about_debug_build) else stringResource(R.string.about_stable_release),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (BuildConfig.DEBUG)
                                MaterialTheme.colorScheme.tertiary
                            else
                                MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Sponsor & Github buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                        ) {
                            Button(
                                onClick = { showDonateSheet = true },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sponsor", maxLines = 1)
                            }
                            
                            Button(
                                onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/DevSon1024/Nosved-Player".toUri()))
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("GitHub", maxLines = 1)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.about_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Links section
                Text(
                    text = stringResource(R.string.about_links_info),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                AboutItemRow(
                    title = stringResource(R.string.about_readme),
                    description = stringResource(R.string.about_readme_desc),
                    icon = Icons.Filled.Description,
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/DevSon1024/Nosved-Player".toUri()))
                    }
                )

                AboutItemRow(
                    title = stringResource(R.string.about_latest_release),
                    description = stringResource(R.string.about_latest_release_desc),
                    icon = Icons.Filled.NewReleases,
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/DevSon1024/Nosved-Player/releases".toUri()))
                    }
                )

                AboutItemRow(
                    title = stringResource(R.string.about_github_issue),
                    description = stringResource(R.string.about_github_issue_desc),
                    icon = Icons.Filled.BugReport,
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/DevSon1024/Nosved-Player/issues".toUri()))
                    }
                )

                AboutItemRow(
                    title = stringResource(R.string.about_telegram_channel),
                    description = "https://t.me/Nosved_Player",
                    icon = Icons.AutoMirrored.Filled.Send,
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, "https://t.me/Nosved_Player".toUri()))
                    }
                )

                AboutItemRow(
                    title = stringResource(R.string.about_credits),
                    description = stringResource(R.string.about_credits_desc),
                    icon = Icons.Filled.Code,
                    onClick = {
                        showCredits = true
                    }
                )

                AboutItemRow(
                    title = stringResource(R.string.about_auto_update),
                    description = stringResource(R.string.about_auto_update_desc),
                    icon = Icons.Filled.Update,
                    onClick = {
                        Toast.makeText(context, context.getString(R.string.about_coming_soon), Toast.LENGTH_SHORT).show()
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                AboutItemRow(
                    title = stringResource(R.string.about_app_version_info, versionName, versionCode),
                    description = stringResource(R.string.about_device_info, androidVersion, apiLevel, supportedAbis),
                    icon = Icons.Filled.Info,
                    onClick = {
                        versionClicks++
                        if (versionClicks >= 8) {
                            onEnableDeveloperMode()
                            Toast.makeText(context, context.getString(R.string.about_developer_mode_enabled), Toast.LENGTH_SHORT).show()
                            versionClicks = 0
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Made with ♥ footer
                Text(
                    text = stringResource(R.string.about_made_with),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                )
            }
        }
    }
    
    DonateSheet(showSheet = showDonateSheet, onDismiss = { showDonateSheet = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditsSubScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_credits), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
            ) {
                Text(
                    text = stringResource(R.string.about_open_source_libraries),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                libraries.forEach { lib ->
                    LibraryRow(
                        library = lib,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, lib.url.toUri())
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutItemRow(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LibraryRow(library: LibraryInfo, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = library.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = library.descriptionStr ?: library.descriptionRes?.let { stringResource(it) } ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Version ${library.version}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = library.license,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonateSheet(showSheet: Boolean, onDismiss: () -> Unit) {
    if (showSheet) {
        val sheetState = rememberModalBottomSheetState()
        val context = LocalContext.current
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Support the Project",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "If you like this app, consider supporting the development!",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                // UPI Option
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    onClick = {
                        val clip = ClipData.newPlainText("UPI ID", "devendraps0103@okicici")
                        clipboardManager.setPrimaryClip(clip)
                        Toast.makeText(context, "UPI ID copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Payment,
                            contentDescription = "UPI",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "UPI (India)",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "devendraps0103@okicici",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copy",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // PayPal
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    enabled = false,
                    onClick = {}
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Public, contentDescription = "PayPal", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "PayPal (Coming Soon)",
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Ko-fi
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    enabled = false,
                    onClick = {}
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.LocalCafe, contentDescription = "Ko-fi", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Ko-fi (Coming Soon)",
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

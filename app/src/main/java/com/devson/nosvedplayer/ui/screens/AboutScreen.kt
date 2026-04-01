package com.devson.nosvedplayer.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.devson.nosvedplayer.BuildConfig
import com.devson.nosvedplayer.R

private data class LibraryInfo(
    val name: String,
    val description: String,
    val url: String
)

private val libraries = listOf(
    LibraryInfo(
        name = "ExoPlayer / Media3",
        description = "The powerhouse behind all video & audio playback.",
        url = "https://github.com/androidx/media"
    ),
    LibraryInfo(
        name = "Nextlib",
        description = "Nextlib is a powerful media player library for Android that provides advanced features such as hardware acceleration, custom codecs, and more.",
        url = "https://github.com/anilbeesetti/nextlib"
    ),
    LibraryInfo(
        name = "Jetpack Compose",
        description = "Modern declarative UI toolkit for Android.",
        url = "https://developer.android.com/jetpack/compose"
    ),
    LibraryInfo(
        name = "Material 3",
        description = "Google's latest design system components.",
        url = "https://m3.material.io"
    ),
    LibraryInfo(
        name = "Room",
        description = "SQLite abstraction library for watch history storage.",
        url = "https://developer.android.com/training/data-storage/room"
    ),
    LibraryInfo(
        name = "DataStore Preferences",
        description = "Persistent key-value storage for settings & theme preferences.",
        url = "https://developer.android.com/topic/libraries/architecture/datastore"
    ),
    LibraryInfo(
        name = "Kotlin Coroutines",
        description = "Asynchronous programming for smooth UI and background tasks.",
        url = "https://kotlinlang.org/docs/coroutines-overview.html"
    ),
    LibraryInfo(
        name = "Kotlin",
        description = "The primary language powering this entire application.",
        url = "https://kotlinlang.org"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit, onEnableDeveloperMode: () -> Unit) {
    var showCredits by remember { mutableStateOf(false) }

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
        topBar = {
            TopAppBar(
                title = { Text("About", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
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
                        .padding(16.dp),
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
                            text = "Nosved Player",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Version $versionName ($versionCode)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            modifier = Modifier.clickable {
                                versionClicks++
                                if (versionClicks >= 8) {
                                    onEnableDeveloperMode()
                                    Toast.makeText(context, "Developer Mode Enabled", Toast.LENGTH_SHORT).show()
                                    versionClicks = 0
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (BuildConfig.DEBUG) "DEBUG BUILD" else "Stable Release",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (BuildConfig.DEBUG)
                                MaterialTheme.colorScheme.tertiary
                            else
                                MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "A clean, modern local video player built with love using Jetpack Compose and ExoPlayer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Links section
                Text(
                    text = "LINKS & INFO",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                AboutItemRow(
                    title = "Readme",
                    description = "Check the GitHub repository and the readme",
                    icon = Icons.Filled.Description,
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/DevSon1024/Nosved-Player".toUri()))
                    }
                )

                AboutItemRow(
                    title = "Latest Release",
                    description = "Look for changelogs and new versions",
                    icon = Icons.Filled.NewReleases,
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/DevSon1024/Nosved-Player/releases".toUri()))
                    }
                )

                AboutItemRow(
                    title = "GitHub Issue",
                    description = "Submit an issue for bug report or feature request",
                    icon = Icons.Filled.BugReport,
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/DevSon1024/Nosved-Player/issues".toUri()))
                    }
                )

                AboutItemRow(
                    title = "Sponsor",
                    description = "Support this app by sponsoring on GitHub (Coming Soon)",
                    icon = Icons.Filled.FavoriteBorder,
                    onClick = {
                        Toast.makeText(context, "Coming Soon!", Toast.LENGTH_SHORT).show()
                    }
                )

                AboutItemRow(
                    title = "Telegram Channel",
                    description = "https://t.me/Nosved",
                    icon = Icons.AutoMirrored.Filled.Send,
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, "https://t.me/Nosved".toUri()))
                    }
                )

                AboutItemRow(
                    title = "Credits",
                    description = "Open source libraries used in this app",
                    icon = Icons.Filled.Code,
                    onClick = {
                        showCredits = true
                    }
                )

                AboutItemRow(
                    title = "Auto Update",
                    description = "Coming Soon",
                    icon = Icons.Filled.Update,
                    onClick = {
                        Toast.makeText(context, "Coming Soon!", Toast.LENGTH_SHORT).show()
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                AboutItemRow(
                    title = "App Version $versionName ($versionCode)",
                    description = "Device Information: Android $androidVersion (API $apiLevel)\nSupported ABIs: [$supportedAbis]",
                    icon = Icons.Filled.Info,
                    onClick = {
                        versionClicks++
                        if (versionClicks >= 8) {
                            onEnableDeveloperMode()
                            Toast.makeText(context, "Developer Mode Enabled", Toast.LENGTH_SHORT).show()
                            versionClicks = 0
                        }
                    }
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Made with ♥ footer
                Text(
                    text = "Made with ♥ by DevSon",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 24.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditsSubScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Credits", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
            ) {
                Text(
                    text = "OPEN SOURCE LIBRARIES",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                libraries.forEachIndexed { index, lib ->
                    LibraryRow(
                        library = lib,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, lib.url.toUri())
                            context.startActivity(intent)
                        }
                    )
                    if (index < libraries.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp, end = 16.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Code,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = library.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = library.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = "Open",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

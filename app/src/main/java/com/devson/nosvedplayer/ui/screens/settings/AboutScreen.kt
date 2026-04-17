package com.devson.nosvedplayer.ui.screens.settings

import android.content.Intent
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
import androidx.compose.material3.TopAppBarDefaults
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
import com.devson.nosvedplayer.R
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.devson.nosvedplayer.BuildConfig

private data class LibraryInfo(
    val name: String,
    val descriptionRes: Int,
    val url: String
)

private val libraries = listOf(
    LibraryInfo(
        name = "ExoPlayer / Media3",
        descriptionRes = R.string.about_lib_exoplayer_desc,
        url = "https://github.com/androidx/media"
    ),
    LibraryInfo(
        name = "Nextlib",
        descriptionRes = R.string.about_lib_nextlib_desc,
        url = "https://github.com/anilbeesetti/nextlib"
    ),
    LibraryInfo(
        name = "Jetpack Compose",
        descriptionRes = R.string.about_lib_compose_desc,
        url = "https://developer.android.com/jetpack/compose"
    ),
    LibraryInfo(
        name = "Material 3",
        descriptionRes = R.string.about_lib_material3_desc,
        url = "https://m3.material.io"
    ),
    LibraryInfo(
        name = "Room",
        descriptionRes = R.string.about_lib_room_desc,
        url = "https://developer.android.com/training/data-storage/room"
    ),
    LibraryInfo(
        name = "DataStore Preferences",
        descriptionRes = R.string.about_lib_datastore_desc,
        url = "https://developer.android.com/topic/libraries/architecture/datastore"
    ),
    LibraryInfo(
        name = "Kotlin Coroutines",
        descriptionRes = R.string.about_lib_coroutines_desc,
        url = "https://kotlinlang.org/docs/coroutines-overview.html"
    ),
    LibraryInfo(
        name = "Kotlin",
        descriptionRes = R.string.about_lib_kotlin_desc,
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
                        Spacer(modifier = Modifier.height(8.dp))
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
                    title = stringResource(R.string.about_sponsor),
                    description = stringResource(R.string.about_sponsor_desc),
                    icon = Icons.Filled.FavoriteBorder,
                    onClick = {
                        Toast.makeText(context, context.getString(R.string.about_coming_soon), Toast.LENGTH_SHORT).show()
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
                text = stringResource(library.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = stringResource(R.string.about_open),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

package com.devson.nvplayer.ui.screen.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.ui.res.stringResource
import com.devson.nvplayer.R
import com.devson.nvplayer.ui.theme.AppThemePalette
import androidx.compose.foundation.isSystemInDarkTheme
import com.devson.nvplayer.viewmodel.SettingsViewModel
import com.devson.nvplayer.domain.model.ThumbnailMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    onNavigateBack: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val isDark        by settingsViewModel.isDarkTheme.collectAsState()
    val dynamicColor  by settingsViewModel.dynamicColor.collectAsState()
    val selectedPalette by settingsViewModel.selectedPalette.collectAsState()
    val navBarTransparent by settingsViewModel.isNavBarTransparent.collectAsState()
    val isAmoledTheme by settingsViewModel.isAmoledTheme.collectAsState()
    val isEffectivelyDark = isDark ?: isSystemInDarkTheme()

    var showLanguageDialog by remember { mutableStateOf(false) }

    // Language picker dialog
    if (showLanguageDialog) {
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        val isMarathi = currentLocales.toLanguageTags().contains("mr")
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.background,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(stringResource(R.string.appearance_language), style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                Column(modifier = Modifier.selectableGroup()) {
                    ThemeOption(
                        text        = stringResource(R.string.appearance_english),
                        selected    = !isMarathi,
                        icon        = Icons.Default.Check,
                        onClick     = {
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
                            showLanguageDialog = false
                        }
                    )
                    ThemeOption(
                        text        = stringResource(R.string.appearance_marathi_full),
                        selected    = isMarathi,
                        icon        = Icons.Default.Check,
                        onClick     = {
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("mr"))
                            showLanguageDialog = false
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) { Text(stringResource(R.string.appearance_close)) }
            }
        )
    }

    // Main scaffold
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_display),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding() + 16.dp
                )
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Colour Preview strip
            ColorPreviewStrip()

            // COLOUR PALETTE section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppearanceSectionLabel(stringResource(R.string.appearance_colour_palette))
                PalettePickerGrid(
                    selected    = selectedPalette,
                    isDark      = isDark ?: false,
                    onSelect    = { settingsViewModel.setSelectedPalette(it) }
                )
            }

            // THEME section
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppearanceSectionLabel(stringResource(R.string.appearance_theme))
                
                // Theme Selection Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            RoundedCornerShape(12.dp)
                        ),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.appearance_dark_theme),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val options = listOf(
                                stringResource(R.string.appearance_light),
                                stringResource(R.string.appearance_dark),
                                stringResource(R.string.appearance_system_default)
                            )
                            
                            options.forEachIndexed { index, label ->
                                val isSelected = when (index) {
                                    0 -> isDark == false
                                    1 -> isDark == true
                                    else -> isDark == null
                                }
                                SegmentedButton(
                                    selected = isSelected,
                                    onClick = {
                                        when (index) {
                                            0 -> settingsViewModel.setDarkTheme(false)
                                            1 -> settingsViewModel.setDarkTheme(true)
                                            2 -> settingsViewModel.resetDarkTheme()
                                        }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                                ) {
                                    Text(label)
                                }
                            }
                        }
                    }
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SettingToggleCard(
                        icon      = Icons.Default.Palette,
                        title     = stringResource(R.string.appearance_dynamic_colour),
                        subtitle  = stringResource(R.string.appearance_dynamic_colour_desc),
                        checked   = dynamicColor,
                        onCheckedChange = { settingsViewModel.setDynamicColor(it) }
                    )
                }

                if (isEffectivelyDark) {
                    SettingToggleCard(
                        icon      = Icons.Default.Brightness1,
                        title     = "AMOLED Theme",
                        subtitle  = "Pure black background for dark mode",
                        checked   = isAmoledTheme,
                        onCheckedChange = { settingsViewModel.setAmoledTheme(it) }
                    )
                }

                SettingToggleCard(
                    icon      = Icons.Default.WebAsset,
                    title     = "Transparent Navigation Buttons",
                    subtitle  = "Content scrolls behind the system navigation buttons",
                    checked   = navBarTransparent,
                    onCheckedChange = { settingsViewModel.setNavBarTransparent(it) }
                )
            }

            // LANGUAGE section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppearanceSectionLabel(stringResource(R.string.appearance_language))
                val currentLocales = AppCompatDelegate.getApplicationLocales()
                val isMarathi = currentLocales.toLanguageTags().contains("mr")
                SettingClickableCard(
                    icon     = Icons.Default.Language,
                    title    = stringResource(R.string.appearance_display_language),
                    subtitle = if (isMarathi) stringResource(R.string.appearance_marathi) else stringResource(R.string.appearance_english),
                    onClick  = { showLanguageDialog = true }
                )
            }

            // THUMBNAILS section
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppearanceSectionLabel("Thumbnails")
                var showModeDialog by remember { mutableStateOf(false) }
                var showClearConfirmDialog by remember { mutableStateOf(false) }
                val viewSettingsVal by settingsViewModel.viewSettings.collectAsState()

                SettingToggleCard(
                    icon = Icons.Default.Photo,
                    title = "Show Video Thumbnails",
                    subtitle = "Display generated thumbnails in video lists and folders",
                    checked = viewSettingsVal.showThumbnail,
                    onCheckedChange = { settingsViewModel.updateShowThumbnail(it) }
                )

                if (viewSettingsVal.showThumbnail) {
                    SettingClickableCard(
                        icon = Icons.Default.Tune,
                        title = "Thumbnail Strategy",
                        subtitle = viewSettingsVal.thumbnailMode.displayName,
                        onClick = { showModeDialog = true }
                    )

                    if (viewSettingsVal.thumbnailMode == ThumbnailMode.FRAME_AT_POSITION) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    RoundedCornerShape(12.dp)
                                ),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Frame Position",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${viewSettingsVal.thumbnailFramePosition.toInt()}%",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Slider(
                                    value = viewSettingsVal.thumbnailFramePosition,
                                    onValueChange = { settingsViewModel.updateThumbnailFramePosition(it) },
                                    valueRange = 1f..90f,
                                    steps = 88,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                SettingClickableCard(
                    icon = Icons.Default.DeleteSweep,
                    title = "Clear Thumbnail Cache",
                    subtitle = "Delete all cached video thumbnails from storage",
                    onClick = { showClearConfirmDialog = true }
                )

                if (showModeDialog) {
                    AlertDialog(
                        onDismissRequest = { showModeDialog = false },
                        shape = RoundedCornerShape(20.dp),
                        containerColor = MaterialTheme.colorScheme.background,
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text("Thumbnail Strategy", style = MaterialTheme.typography.titleMedium)
                            }
                        },
                        text = {
                            Column(modifier = Modifier.selectableGroup()) {
                                ThumbnailMode.entries.forEach { mode ->
                                    ThemeOption(
                                        text = mode.displayName,
                                        selected = viewSettingsVal.thumbnailMode == mode,
                                        icon = Icons.Default.Check,
                                        onClick = {
                                            settingsViewModel.updateThumbnailMode(mode)
                                            showModeDialog = false
                                        }
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showModeDialog = false }) { Text("Close") }
                        }
                    )
                }

                if (showClearConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showClearConfirmDialog = false },
                        title = { Text("Clear thumbnail cache?") },
                        text = { Text("This will delete cached thumbnails from storage. They will be regenerated as you browse.") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    settingsViewModel.clearThumbnailCache()
                                    showClearConfirmDialog = false
                                }
                            ) {
                                Text("Clear", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showClearConfirmDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }

            // INFO chip
            Surface(
                modifier       = Modifier.fillMaxWidth(),
                shape          = RoundedCornerShape(12.dp),
                color          = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier              = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint     = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text  = stringResource(R.string.appearance_restart_info),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// Palette Picker Grid
@Composable
private fun PalettePickerGrid(
    selected: AppThemePalette,
    isDark: Boolean,
    onSelect: (AppThemePalette) -> Unit
) {
    val palettes = AppThemePalette.entries
    val rows = palettes.chunked(2)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { palette ->
                    PaletteCard(
                        palette  = palette,
                        isDark   = isDark,
                        isSelected = palette == selected,
                        modifier   = Modifier.weight(1f),
                        onClick    = { onSelect(palette) }
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PaletteCard(
    palette: AppThemePalette,
    isDark: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val primary   = if (isDark) palette.darkPrimary   else palette.lightPrimary
    val secondary = if (isDark) palette.darkSecondary else palette.lightSecondary

    val borderWidth by animateDpAsState(
        targetValue   = if (isSelected) 2.5.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label         = "paletteBorder"
    )
    val borderColor by animateColorAsState(
        targetValue   = if (isSelected) primary else Color.Transparent,
        animationSpec = tween(250),
        label         = "paletteBorderColor"
    )

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape          = RoundedCornerShape(14.dp),
        color          = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = if (isSelected) 4.dp else 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.horizontalGradient(listOf(primary, secondary))
                    )
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = stringResource(R.string.appearance_selected),
                        tint     = Color.White,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 8.dp)
                            .size(18.dp)
                    )
                }
            }

            Row(
                verticalAlignment      = Alignment.CenterVertically,
                horizontalArrangement  = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(primary)
                )
                Text(
                    text  = palette.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) primary
                            else MaterialTheme.colorScheme.onSurface
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(primary)
                )
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(secondary)
                )
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(primary.copy(alpha = 0.4f))
                )
            }
        }
    }
}

@Composable
private fun ColorPreviewStrip() {
    val swatches = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.colorScheme.outline,
    )

    Surface(
        modifier       = Modifier.fillMaxWidth(),
        shape          = RoundedCornerShape(16.dp),
        color          = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text  = stringResource(R.string.appearance_current_palette),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                swatches.forEach { raw ->
                    val animColor by animateColorAsState(
                        targetValue   = raw,
                        animationSpec = tween(400),
                        label         = "swatchAnim"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(animColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun AppearanceSectionLabel(label: String) {
    Text(
        text       = label,
        style      = MaterialTheme.typography.labelLarge,
        color      = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier   = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

@Composable
private fun ThemeOption(
    text: String,
    selected: Boolean,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            modifier           = Modifier.size(20.dp),
            tint               = if (selected) MaterialTheme.colorScheme.primary
                                 else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        RadioButton(selected = selected, onClick = null)
    }
}

@Composable
private fun SettingToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (enabled) Modifier.clickable { onCheckedChange(!checked) } else Modifier)
            .alpha(if (enabled) 1f else 0.38f)
            .border(
                BorderStroke(
                    1.dp,
                    if (checked && enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
                RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (checked && enabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (checked && enabled) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.secondaryContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (checked && enabled) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}

@Composable
private fun SettingClickableCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .alpha(if (enabled) 1f else 0.38f)
            .border(
                BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
                RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

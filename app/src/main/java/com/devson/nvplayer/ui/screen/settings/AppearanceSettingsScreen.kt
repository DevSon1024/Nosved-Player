package com.devson.nvplayer.ui.screen.settings

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ShapeDefaults

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    val playbackSettings by settingsViewModel.playbackSettings.collectAsState()
    val seekBarStyle = playbackSettings.seekBarStyle

    var showLanguageDialog by remember { mutableStateOf(false) }

    //  Language picker dialog
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

    //  Main scaffold
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
                    containerColor = MaterialTheme.colorScheme.background
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
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            //  Colour preview strip 
            ColorPreviewStrip()

            Spacer(Modifier.height(20.dp))

            //  COLOUR PALETTE section
            AppearanceSectionLabel(stringResource(R.string.appearance_colour_palette))
            PalettePickerGrid(
                selected    = selectedPalette,
                isDark      = isDark ?: false,
                onSelect    = { settingsViewModel.setSelectedPalette(it) }
            )

            Spacer(Modifier.height(16.dp))

            //  THEME section 
            AppearanceSectionLabel(stringResource(R.string.appearance_theme))
            AppearanceCard {
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
                    
                    val startButtonShapes = ButtonShapes(
                        shape = ButtonGroupDefaults.connectedLeadingButtonShape,
                        pressedShape = ButtonGroupDefaults.connectedLeadingButtonPressShape,
                        checkedShape = ToggleButtonDefaults.checkedShape
                    )
                    val middleButtonShapes = ToggleButtonDefaults.shapes(
                        ShapeDefaults.Small,
                        ToggleButtonDefaults.pressedShape,
                        ToggleButtonDefaults.checkedShape
                    )
                    val endButtonShapes = ButtonShapes(
                        shape = ButtonGroupDefaults.connectedTrailingButtonShape,
                        pressedShape = ButtonGroupDefaults.connectedTrailingButtonPressShape,
                        checkedShape = ToggleButtonDefaults.checkedShape
                    )
                    val shapes = listOf(startButtonShapes, middleButtonShapes, endButtonShapes)
                    
                    ButtonGroup(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.connectedSpaceBetween),
                        animateFraction = 0f
                    ) {
                        val groupScope = this
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
                            ToggleButton(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        when (index) {
                                            0 -> settingsViewModel.setDarkTheme(false)
                                            1 -> settingsViewModel.setDarkTheme(true)
                                            2 -> settingsViewModel.resetDarkTheme()
                                        }
                                    }
                                },
                                shapes = shapes[index],
                                modifier = with(groupScope) { Modifier.weight(1f) }
                            ) {
                                AnimatedVisibility(
                                    visible = isSelected,
                                    enter = fadeIn() + expandHorizontally(),
                                    exit = fadeOut() + shrinkHorizontally()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                    }
                                }
                                Text(label)
                            }
                        }
                    }
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    AppearanceDivider()
                    AppearanceToggleRow(
                        icon      = Icons.Default.Palette,
                        title     = stringResource(R.string.appearance_dynamic_colour),
                        subtitle  = stringResource(R.string.appearance_dynamic_colour_desc),
                        checked   = dynamicColor,
                        onCheckedChange = { settingsViewModel.setDynamicColor(it) }
                    )
                }

                AppearanceDivider()
                if (isEffectivelyDark) {
                    AppearanceToggleRow(
                        icon      = Icons.Default.Brightness1,
                        title     = "AMOLED Theme",
                        subtitle  = "Pure black background for dark mode",
                        checked   = isAmoledTheme,
                        onCheckedChange = { settingsViewModel.setAmoledTheme(it) }
                    )
                    AppearanceDivider()
                }

                AppearanceToggleRow(
                    icon      = Icons.Default.WebAsset,
                    title     = "Transparent Navigation Buttons",
                    subtitle  = "Content scrolls behind the system navigation buttons",
                    checked   = navBarTransparent,
                    onCheckedChange = { settingsViewModel.setNavBarTransparent(it) }
                )

            }

            Spacer(Modifier.height(16.dp))

            //  LANGUAGE section 
            AppearanceSectionLabel(stringResource(R.string.appearance_language))
            AppearanceCard {
                val currentLocales = AppCompatDelegate.getApplicationLocales()
                val isMarathi = currentLocales.toLanguageTags().contains("mr")
                AppearanceNavRow(
                    icon     = Icons.Default.Language,
                    title    = stringResource(R.string.appearance_display_language),
                    subtitle = if (isMarathi) stringResource(R.string.appearance_marathi) else stringResource(R.string.appearance_english),
                    onClick  = { showLanguageDialog = true }
                )
            }

            Spacer(Modifier.height(16.dp))

            //  INFO chip
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

//  Palette Picker Grid

@Composable
private fun PalettePickerGrid(
    selected: AppThemePalette,
    isDark: Boolean,
    onSelect: (AppThemePalette) -> Unit
) {
    // Lay out in rows of 2
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
                // Fill blank slot in last row if odd count
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
            // Swatch row: gradient bar of primary + secondary
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

            // Palette name row (emoji removed, dot accent used instead)
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

            // Dot accents row
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

//  Shared private composables

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
private fun AppearanceCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier       = Modifier.fillMaxWidth(),
        shape          = RoundedCornerShape(16.dp),
        color          = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Column(content = content)
    }
}

@Composable
private fun AppearanceDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        color    = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun AppearanceNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier         = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                modifier           = Modifier.size(20.dp),
                tint               = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text  = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector        = Icons.Default.ChevronRight,
            contentDescription = null,
            modifier           = Modifier.size(18.dp),
            tint               = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AppearanceToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier         = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                modifier           = Modifier.size(20.dp),
                tint               = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text  = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
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


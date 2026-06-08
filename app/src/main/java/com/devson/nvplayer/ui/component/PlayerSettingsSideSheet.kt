package com.devson.nvplayer.ui.component

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.devson.nvplayer.repository.*
import com.devson.nvplayer.util.repeatingClickable
import com.devson.nvplayer.util.roundToTwoDecimals
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val SPEED_STEP = 0.05f

@Composable
fun PlayerSettingsSideSheet(
    visible: Boolean,
    currentSpeed: Float,
    playbackSettings: PlaybackSettings,
    onSpeedSelected: (Float) -> Unit,
    onUpdateDoubleTapAction: (DoubleTapAction) -> Unit = {},
    onUpdateDoubleTapSeekDuration: (Long) -> Unit = {},
    onUpdateLongPressEnabled: (Boolean) -> Unit = {},
    onUpdateTapAndHoldSpeed: (Float) -> Unit = {},
    onUpdateLongPressSpeed: (Float) -> Unit = {},
    onUpdateOrientationMode: (OrientationMode) -> Unit = {},
    onUpdateFullScreenMode: (FullScreenMode) -> Unit = {},
    onUpdateSoftButtonMode: (SoftButtonMode) -> Unit = {},
    onUpdateControlIconSize: (String) -> Unit = {},
    onUpdateSeekBarStyle: (String) -> Unit = {},
    onUpdateAutoPlayEnabled: (Boolean) -> Unit = {},
    onUpdateShowSeekButtons: (Boolean) -> Unit = {},
    onUpdateShowNextPrevButtons: (Boolean) -> Unit = {},
    onUpdateShowElapsedTimeOverlay: (Boolean) -> Unit = {},
    onUpdateShowRemainingTime: (Boolean) -> Unit = {},
    onUpdateShowBatteryClockOverlay: (Boolean) -> Unit = {},
    onUpdateShowScreenRotationButton: (Boolean) -> Unit = {},
    onUpdatePauseWhenObstructed: (Boolean) -> Unit = {},
    onUpdateKeepAwakeAlways: (Boolean) -> Unit = {},
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val currentOnUpdateDoubleTapAction by rememberUpdatedState(onUpdateDoubleTapAction)
    val currentOnUpdateDoubleTapSeekDuration by rememberUpdatedState(onUpdateDoubleTapSeekDuration)
    val currentOnSpeedSelected by rememberUpdatedState(onSpeedSelected)
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val sheetWidthPercent = if (isLandscape) 0.5f else 1.0f

    var activeTab by remember { mutableStateOf(0) }

    // Dialog trigger states
    var showDoubleTapDialog by remember { mutableStateOf(false) }
    var showSeekDurationDialog by remember { mutableStateOf(false) }
    var showOrientationDialog by remember { mutableStateOf(false) }
    var showScalingDialog by remember { mutableStateOf(false) }
    var showSoftButtonDialog by remember { mutableStateOf(false) }
    var showIconSizeDialog by remember { mutableStateOf(false) }
    var showSeekBarStyleDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = if (isLandscape) Alignment.CenterEnd else Alignment.BottomCenter
    ) {
        // Scrim
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(250)),
            exit = fadeOut(animationSpec = tween(250)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        // Sheet Content
        val enterAnim = if (isLandscape) {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(300, easing = LinearOutSlowInEasing)
            )
        } else {
            slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(300, easing = LinearOutSlowInEasing)
            )
        }

        val exitAnim = if (isLandscape) {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(300, easing = FastOutLinearInEasing)
            )
        } else {
            slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(300, easing = FastOutLinearInEasing)
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = enterAnim,
            exit = exitAnim,
            modifier = if (isLandscape) {
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(sheetWidthPercent)
            } else {
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.Bottom)
            }
        ) {
            Box(
                modifier = if (isLandscape) {
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.88f))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                        )
                } else {
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .heightIn(max = (configuration.screenHeightDp * 0.9f).dp)
                        .animateContentSize(animationSpec = tween(300, easing = FastOutSlowInEasing))
                        .background(
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                        )
                }
            ) {
                Column(
                    modifier = if (isLandscape) {
                        Modifier
                            .fillMaxSize()
                            .systemBarsPadding()
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .statusBarsPadding()
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Player Settings",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close sheet"
                            )
                        }
                    }

                    // Tab selector row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val tabs = listOf(
                            Icons.Rounded.Speed,
                            Icons.Rounded.TouchApp,
                            Icons.Rounded.Tune,
                            Icons.Rounded.Layers,
                            Icons.Rounded.AutoMode
                        )
                        tabs.forEachIndexed { index, icon ->
                            val isSelected = activeTab == index
                            val bgSelectedColor = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Transparent
                            }
                            val contentSelectedColor = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bgSelectedColor)
                                    .clickable { activeTab = index },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = "Tab $index",
                                    tint = contentSelectedColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Active Tab body
                    Column(
                        modifier = Modifier
                            .then(
                                if (isLandscape) Modifier.weight(1f)
                                else Modifier.weight(1f, fill = false)
                            )
                            .padding(horizontal = 24.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        when (activeTab) {
                            0 -> SpeedTab(
                                currentSpeed = currentSpeed,
                                onSpeedSelected = onSpeedSelected
                            )
                            1 -> GesturesTab(
                                playbackSettings = playbackSettings,
                                onUpdateDoubleTapAction = onUpdateDoubleTapAction,
                                onUpdateDoubleTapSeekDuration = onUpdateDoubleTapSeekDuration,
                                onUpdateLongPressEnabled = onUpdateLongPressEnabled,
                                onUpdateTapAndHoldSpeed = onUpdateTapAndHoldSpeed,
                                onUpdateLongPressSpeed = onUpdateLongPressSpeed,
                                onShowDoubleTapDialog = { showDoubleTapDialog = true },
                                onShowSeekDurationDialog = { showSeekDurationDialog = true }
                            )
                            2 -> InterfaceTab(
                                playbackSettings = playbackSettings,
                                onUpdateAutoPlayEnabled = onUpdateAutoPlayEnabled,
                                onUpdateShowSeekButtons = onUpdateShowSeekButtons,
                                onUpdateShowNextPrevButtons = onUpdateShowNextPrevButtons,
                                onShowOrientationDialog = { showOrientationDialog = true },
                                onShowScalingDialog = { showScalingDialog = true },
                                onShowSoftButtonDialog = { showSoftButtonDialog = true },
                                onShowIconSizeDialog = { showIconSizeDialog = true },
                                onShowSeekBarStyleDialog = { showSeekBarStyleDialog = true }
                            )
                            3 -> OverlaysTab(
                                playbackSettings = playbackSettings,
                                onUpdateShowElapsedTimeOverlay = onUpdateShowElapsedTimeOverlay,
                                onUpdateShowRemainingTime = onUpdateShowRemainingTime,
                                onUpdateShowBatteryClockOverlay = onUpdateShowBatteryClockOverlay,
                                onUpdateShowScreenRotationButton = onUpdateShowScreenRotationButton
                            )
                            4 -> AutomationTab(
                                playbackSettings = playbackSettings,
                                onUpdatePauseWhenObstructed = onUpdatePauseWhenObstructed,
                                onUpdateKeepAwakeAlways = onUpdateKeepAwakeAlways
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    // Dialog: Double Tap Action
    if (showDoubleTapDialog) {
        val actions = remember { DoubleTapAction.values().toList() }
        FullListDialog(
            title = "Double Tap Action",
            onDismiss = { showDoubleTapDialog = false },
            onReset = {
                currentOnUpdateDoubleTapAction(DoubleTapAction.BOTH)
                showDoubleTapDialog = false
            }
        ) {
            items(
                items = actions,
                key = { it.name },
                contentType = { "double_tap_action" }
            ) { action ->
                val selected = playbackSettings.doubleTapAction == action
                val onClick = remember(action) {
                    {
                        currentOnUpdateDoubleTapAction(action)
                        showDoubleTapDialog = false
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selected,
                            onClick = onClick,
                            role = Role.RadioButton
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = when (action) {
                                DoubleTapAction.BOTH         -> "Seek Left/Right"
                                DoubleTapAction.PLAY_PAUSE   -> "Play / Pause"
                                DoubleTapAction.FAST_FORWARD -> "Fast Forward Only"
                                DoubleTapAction.REWIND       -> "Rewind Only"
                                DoubleTapAction.NONE         -> "Disable Double Tap"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Text(
                            text = when (action) {
                                DoubleTapAction.BOTH         -> "Tap left to rewind, right to forward"
                                DoubleTapAction.PLAY_PAUSE   -> "Double tap anywhere to toggle playback"
                                DoubleTapAction.FAST_FORWARD -> "Double tap to jump forward"
                                DoubleTapAction.REWIND       -> "Double tap to jump backward"
                                DoubleTapAction.NONE         -> "No gesture on double tap"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (action != actions.last()) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }

    // Dialog: Double Tap Seek Duration
    if (showSeekDurationDialog) {
        val durations = remember { listOf(5000L, 10000L, 15000L, 30000L, 60000L) }
        FullListDialog(
            title = "Seek Duration",
            onDismiss = { showSeekDurationDialog = false },
            onReset = {
                currentOnUpdateDoubleTapSeekDuration(10000L)
                showSeekDurationDialog = false
            }
        ) {
            items(
                items = durations,
                key = { it },
                contentType = { "seek_duration" }
            ) { duration ->
                val selected = playbackSettings.doubleTapSeekDuration == duration
                val onClick = remember(duration) {
                    {
                        currentOnUpdateDoubleTapSeekDuration(duration)
                        showSeekDurationDialog = false
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selected,
                            onClick = onClick,
                            role = Role.RadioButton
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "${duration / 1000} seconds",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
                if (duration != durations.last()) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }

    // Dialog: Screen Orientation
    if (showOrientationDialog) {
        AlertDialog(
            onDismissRequest = { showOrientationDialog = false },
            title = { Text("Screen Orientation") },
            text = {
                Column(Modifier.selectableGroup()) {
                    OrientationMode.values().forEach { mode ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = (playbackSettings.orientationMode == mode),
                                    onClick = {
                                        onUpdateOrientationMode(mode)
                                        showOrientationDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (playbackSettings.orientationMode == mode),
                                onClick = null
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = when (mode) {
                                    OrientationMode.SYSTEM_DEFAULT -> "System Default"
                                    OrientationMode.LANDSCAPE -> "Landscape Only"
                                    OrientationMode.PORTRAIT -> "Portrait Only"
                                    OrientationMode.AUTO -> "Sensor Auto-Rotate"
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showOrientationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Scaling mode
    if (showScalingDialog) {
        AlertDialog(
            onDismissRequest = { showScalingDialog = false },
            title = { Text("Fullscreen Scaling") },
            text = {
                Column(Modifier.selectableGroup()) {
                    FullScreenMode.values().forEach { mode ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = (playbackSettings.fullScreenMode == mode),
                                    onClick = {
                                        onUpdateFullScreenMode(mode)
                                        showScalingDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (playbackSettings.fullScreenMode == mode),
                                onClick = null
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = when (mode) {
                                    FullScreenMode.AUTO_SWITCH -> "Auto Stretch/Crop"
                                    FullScreenMode.STRETCH -> "Stretch to Fill"
                                    FullScreenMode.CROP -> "Zoom Crop"
                                    FullScreenMode.FIT -> "Fit Letterbox"
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showScalingDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Soft Buttons mode
    if (showSoftButtonDialog) {
        AlertDialog(
            onDismissRequest = { showSoftButtonDialog = false },
            title = { Text("System Buttons Mode") },
            text = {
                Column(Modifier.selectableGroup()) {
                    SoftButtonMode.values().forEach { mode ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = (playbackSettings.softButtonMode == mode),
                                    onClick = {
                                        onUpdateSoftButtonMode(mode)
                                        showSoftButtonDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (playbackSettings.softButtonMode == mode),
                                onClick = null
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = when (mode) {
                                    SoftButtonMode.AUTO_HIDE -> "Auto Hide Navigation Bar"
                                    SoftButtonMode.SHOW -> "Always Show Navigation Bar"
                                    SoftButtonMode.HIDE -> "Always Hide Navigation Bar"
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSoftButtonDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Icon size
    if (showIconSizeDialog) {
        val sizes = listOf("small", "medium", "large")
        AlertDialog(
            onDismissRequest = { showIconSizeDialog = false },
            title = { Text("Controls Icon Size") },
            text = {
                Column(Modifier.selectableGroup()) {
                    sizes.forEach { size ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = (playbackSettings.controlIconSize == size),
                                    onClick = {
                                        onUpdateControlIconSize(size)
                                        showIconSizeDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (playbackSettings.controlIconSize == size),
                                onClick = null
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = size.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIconSizeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Seekbar style
    if (showSeekBarStyleDialog) {
        val styles = listOf("standard", "wavy", "thick")
        AlertDialog(
            onDismissRequest = { showSeekBarStyleDialog = false },
            title = { Text("Seekbar Style") },
            text = {
                Column(Modifier.selectableGroup()) {
                    styles.forEach { style ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = (playbackSettings.seekBarStyle == style),
                                    onClick = {
                                        onUpdateSeekBarStyle(style)
                                        showSeekBarStyleDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (playbackSettings.seekBarStyle == style),
                                onClick = null
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = when (style) {
                                    "wavy" -> "Wavy"
                                    "thick" -> "Thick"
                                    else -> "Standard"
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSeekBarStyleDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// Tab Subcomponents

@Composable
private fun SpeedTab(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit
) {
    val currentOnSpeedSelected by rememberUpdatedState(onSpeedSelected)
    var isEditing by remember { mutableStateOf(false) }
    var textValue by remember(currentSpeed) {
        mutableStateOf(String.format(java.util.Locale.US, "%.2f", currentSpeed))
    }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Playback Speed Title
        SheetSectionLabel("Playback Speed")

        // Click-to-Edit large display
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isEditing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { isEditing = true }
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = String.format(java.util.Locale.US, "%.2fx", currentSpeed),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit speed",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = "Tap speed value to type custom rate",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            } else {
                val parsedSpeed = textValue.toFloatOrNull()
                val isValid = parsedSpeed != null && parsedSpeed in 0.25f..4.0f

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = textValue,
                            onValueChange = { nv ->
                                val s = nv.filter { it.isDigit() || it == '.' }
                                if (s.count { it == '.' } <= 1) textValue = s
                            },
                            placeholder = { Text("1.00") },
                            singleLine = true,
                            isError = textValue.isNotEmpty() && !isValid,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (isValid && parsedSpeed != null) {
                                        onSpeedSelected(parsedSpeed)
                                        keyboardController?.hide()
                                        isEditing = false
                                    }
                                }
                            ),
                            modifier = Modifier.width(120.dp),
                            textStyle = LocalTextStyle.current.copy(
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                        Spacer(Modifier.width(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (isValid && parsedSpeed != null) {
                                        onSpeedSelected(parsedSpeed)
                                        keyboardController?.hide()
                                        isEditing = false
                                    }
                                },
                                enabled = isValid,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isValid) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Apply custom speed",
                                    tint = if (isValid) MaterialTheme.colorScheme.onPrimaryContainer else Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    textValue = String.format(java.util.Locale.US, "%.2f", currentSpeed)
                                    keyboardController?.hide()
                                    isEditing = false
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Cancel custom speed edit",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "Enter a speed between 0.25x and 4.0x",
                        color = if (isValid) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Speed Slider (0.25x – 4.00x)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SheetSectionLabel("Speed Slider")
            StepSlider(
                value = currentSpeed,
                onValueChange = { v ->
                    onSpeedSelected(v)
                    textValue = String.format(java.util.Locale.US, "%.2f", v)
                },
                valueRange = 0.25f..4f,
                step = SPEED_STEP,
                defaultValue = 1.0f
            )
            // Tick labels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("0.25x", "1x", "2x", "3x", "4x").forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    )
                }
            }
        }

        // Speed Presets
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SheetSectionLabel("Presets")
            val presets = remember { listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f) }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.chunked(3).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { speed ->
                            val isSelected = abs(currentSpeed - speed) < 0.01f
                            val formattedSpeed = remember(speed) { String.format(java.util.Locale.US, "%.2fx", speed) }
                            val onPresetClick = remember(speed) {
                                {
                                    currentOnSpeedSelected(speed)
                                    textValue = String.format(java.util.Locale.US, "%.2f", speed)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    )
                                    .border(
                                        width = 1.5.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable(onClick = onPresetClick),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = formattedSpeed,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GesturesTab(
    playbackSettings: PlaybackSettings,
    onUpdateDoubleTapAction: (DoubleTapAction) -> Unit,
    onUpdateDoubleTapSeekDuration: (Long) -> Unit,
    onUpdateLongPressEnabled: (Boolean) -> Unit,
    onUpdateTapAndHoldSpeed: (Float) -> Unit,
    onUpdateLongPressSpeed: (Float) -> Unit,
    onShowDoubleTapDialog: () -> Unit,
    onShowSeekDurationDialog: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SheetSectionLabel("Press & Tap Controls")

        SheetCard {
            // Double Tap Action row
            SheetRow(
                icon = Icons.Default.TouchApp,
                title = "Double Tap Action",
                subtitle = when (playbackSettings.doubleTapAction) {
                    DoubleTapAction.BOTH         -> "Seek on double-tap"
                    DoubleTapAction.PLAY_PAUSE   -> "Play / Pause"
                    DoubleTapAction.FAST_FORWARD -> "Fast Forward Only"
                    DoubleTapAction.REWIND       -> "Rewind Only"
                    DoubleTapAction.NONE         -> "No Action"
                },
                onClick = onShowDoubleTapDialog
            )

            SheetDivider()

            // Double Tap Seek Duration row
            SheetRow(
                icon = Icons.Default.Timer,
                title = "Double Tap Seek Duration",
                subtitle = "${playbackSettings.doubleTapSeekDuration / 1000} seconds",
                onClick = onShowSeekDurationDialog
            )

            SheetDivider()

            // Press & Hold toggle
            SheetToggleRow(
                icon = Icons.Rounded.Speed,
                title = "Press & Hold Acceleration",
                subtitle = "Hold screen to temporarily speed up video",
                checked = playbackSettings.longPressEnabled,
                onCheckedChange = onUpdateLongPressEnabled
            )

            // Speed sliders (shown when long press is enabled)
            if (playbackSettings.longPressEnabled) {
                SheetDivider()
                SpeedInlineSlider(
                    label = "Tap & Hold Speed Override",
                    value = playbackSettings.tapAndHoldSpeed,
                    valueRange = 1.5f..4.0f,
                    step = SPEED_STEP,
                    defaultValue = 2.0f,
                    onValueChange = onUpdateTapAndHoldSpeed
                )
                SheetDivider()
                SpeedInlineSlider(
                    label = "Long Press Default Speed",
                    value = playbackSettings.longPressSpeed,
                    valueRange = 1.5f..4.0f,
                    step = SPEED_STEP,
                    defaultValue = 2.0f,
                    onValueChange = onUpdateLongPressSpeed
                )
            }
        }
    }
}

@Composable
private fun InterfaceTab(
    playbackSettings: PlaybackSettings,
    onUpdateAutoPlayEnabled: (Boolean) -> Unit,
    onUpdateShowSeekButtons: (Boolean) -> Unit,
    onUpdateShowNextPrevButtons: (Boolean) -> Unit,
    onShowOrientationDialog: () -> Unit,
    onShowScalingDialog: () -> Unit,
    onShowSoftButtonDialog: () -> Unit,
    onShowIconSizeDialog: () -> Unit,
    onShowSeekBarStyleDialog: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SheetSectionLabel("Layout & Orientation")
        SheetCard {
            SheetRow(
                icon = Icons.Default.ScreenRotation,
                title = "Screen Orientation",
                subtitle = when (playbackSettings.orientationMode) {
                    OrientationMode.SYSTEM_DEFAULT -> "Follow System Setting"
                    OrientationMode.LANDSCAPE -> "Force Landscape Mode"
                    OrientationMode.PORTRAIT -> "Force Portrait Mode"
                    OrientationMode.AUTO -> "Auto-Rotate based on Sensor"
                },
                onClick = onShowOrientationDialog
            )

            SheetDivider()

            SheetRow(
                icon = Icons.Default.AspectRatio,
                title = "Fullscreen Scale Mode",
                subtitle = when (playbackSettings.fullScreenMode) {
                    FullScreenMode.AUTO_SWITCH -> "Auto Switch aspect ratio"
                    FullScreenMode.STRETCH -> "Stretch to fill screen"
                    FullScreenMode.CROP -> "Crop and zoom to fill"
                    FullScreenMode.FIT -> "Fit to screen (Letterbox)"
                },
                onClick = onShowScalingDialog
            )

            SheetDivider()

            SheetRow(
                icon = Icons.Default.Fullscreen,
                title = "System Navigation Buttons",
                subtitle = when (playbackSettings.softButtonMode) {
                    SoftButtonMode.AUTO_HIDE -> "Auto-hide with controls"
                    SoftButtonMode.SHOW -> "Always show navigation buttons"
                    SoftButtonMode.HIDE -> "Always hide (Immersive mode)"
                },
                onClick = onShowSoftButtonDialog
            )
        }

        Spacer(Modifier.height(4.dp))

        SheetSectionLabel("Player Controls Customization")
        SheetCard {
            SheetRow(
                icon = Icons.Default.PhotoSizeSelectLarge,
                title = "Controls Icon Size",
                subtitle = playbackSettings.controlIconSize.replaceFirstChar { it.uppercase() },
                onClick = onShowIconSizeDialog
            )

            SheetDivider()

            SheetRow(
                icon = Icons.Default.Waves,
                title = "Seekbar Style",
                subtitle = when (playbackSettings.seekBarStyle) {
                    "wavy" -> "Wavy"
                    "thick" -> "Thick"
                    else -> "Standard"
                },
                onClick = onShowSeekBarStyleDialog
            )

            SheetDivider()

            SheetToggleRow(
                icon = Icons.Default.SkipNext,
                title = "Auto-play Next Video",
                subtitle = "Automatically load and play next video in folder",
                checked = playbackSettings.autoPlayEnabled,
                onCheckedChange = onUpdateAutoPlayEnabled
            )

            SheetDivider()

            SheetToggleRow(
                icon = Icons.Default.FastForward,
                title = "Show Seek Buttons",
                subtitle = "Show fast forward and rewind seek buttons in player controls",
                checked = playbackSettings.showSeekButtons,
                onCheckedChange = onUpdateShowSeekButtons
            )

            SheetDivider()

            SheetToggleRow(
                icon = Icons.Default.SkipNext,
                title = "Show Skip Prev/Next Buttons",
                subtitle = "Show previous/next chapter skip buttons in player controls",
                checked = playbackSettings.showNextPrevButtons,
                onCheckedChange = onUpdateShowNextPrevButtons
            )
        }
    }
}

@Composable
private fun OverlaysTab(
    playbackSettings: PlaybackSettings,
    onUpdateShowElapsedTimeOverlay: (Boolean) -> Unit,
    onUpdateShowRemainingTime: (Boolean) -> Unit,
    onUpdateShowBatteryClockOverlay: (Boolean) -> Unit,
    onUpdateShowScreenRotationButton: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SheetSectionLabel("Info Overlays")
        SheetCard {
            SheetToggleRow(
                icon = Icons.Default.Schedule,
                title = "Show Elapsed Time",
                subtitle = "Always show current playback time at top edge",
                checked = playbackSettings.showElapsedTimeOverlay,
                onCheckedChange = onUpdateShowElapsedTimeOverlay
            )

            SheetDivider()

            SheetToggleRow(
                icon = Icons.Default.HourglassBottom,
                title = "Show Remaining Time",
                subtitle = "Display remaining time instead of total duration",
                checked = playbackSettings.showRemainingTime,
                onCheckedChange = onUpdateShowRemainingTime
            )

            SheetDivider()

            SheetToggleRow(
                icon = Icons.Default.BatteryChargingFull,
                title = "Show Battery & Clock Overlay",
                subtitle = "Display battery status and device clock on control bar",
                checked = playbackSettings.showBatteryClockOverlay,
                onCheckedChange = onUpdateShowBatteryClockOverlay
            )

            SheetDivider()

            SheetToggleRow(
                icon = Icons.AutoMirrored.Filled.RotateRight,
                title = "Show Quick Rotation Button",
                subtitle = "Display rotation lock toggle button in player interface",
                checked = playbackSettings.showScreenRotationButton,
                onCheckedChange = onUpdateShowScreenRotationButton
            )
        }
    }
}

@Composable
private fun AutomationTab(
    playbackSettings: PlaybackSettings,
    onUpdatePauseWhenObstructed: (Boolean) -> Unit,
    onUpdateKeepAwakeAlways: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SheetSectionLabel("Automation Behavior")
        SheetCard {
            SheetToggleRow(
                icon = Icons.Default.PauseCircle,
                title = "Pause on Obstruction",
                subtitle = "Pause video playback automatically if screen is covered",
                checked = playbackSettings.pauseWhenObstructed,
                onCheckedChange = onUpdatePauseWhenObstructed
            )

            SheetDivider()

            SheetToggleRow(
                icon = Icons.Default.WbSunny,
                title = "Keep Screen Awake Always",
                subtitle = "Prevent screen from turning off when playback is paused or active",
                checked = playbackSettings.keepAwakeAlways,
                onCheckedChange = onUpdateKeepAwakeAlways
            )
        }
    }
}

// Reusable UI Helpers

@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun SheetCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(content = content)
    }
}

@Composable
private fun SheetDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun SheetRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SheetToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.minimumInteractiveComponentSize(),
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
}

@Composable
private fun SpeedInlineSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float = SPEED_STEP,
    defaultValue: Float,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val formattedValue = remember(value) { String.format(java.util.Locale.US, "%.2fx", value) }
                Text(
                    text = formattedValue,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    onClick = { onValueChange(defaultValue) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        StepSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            step = step,
            defaultValue = defaultValue
        )
    }
}

@Composable
private fun StepSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float = 0.05f,
    defaultValue: Float = valueRange.start
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LongPressStepButton(
            icon = Icons.Default.Remove,
            contentDescription = "Decrease",
            onStep = {
                val next = (value - step).coerceIn(valueRange.start, valueRange.endInclusive).roundToTwoDecimals()
                val snapped = ((next / step).roundToInt() * step).roundToTwoDecimals()
                onValueChange(snapped.coerceIn(valueRange.start, valueRange.endInclusive))
            }
        )

        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = { raw ->
                val snapped = ((raw / step).roundToInt() * step).roundToTwoDecimals()
                    .coerceIn(valueRange.start, valueRange.endInclusive)
                onValueChange(snapped)
            },
            valueRange = valueRange,
            modifier = Modifier.weight(1f)
        )

        LongPressStepButton(
            icon = Icons.Default.Add,
            contentDescription = "Increase",
            onStep = {
                val next = (value + step).coerceIn(valueRange.start, valueRange.endInclusive).roundToTwoDecimals()
                val snapped = ((next / step).roundToInt() * step).roundToTwoDecimals()
                onValueChange(snapped.coerceIn(valueRange.start, valueRange.endInclusive))
            }
        )
    }
}

@Composable
private fun LongPressStepButton(
    icon: ImageVector,
    contentDescription: String,
    onStep: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(
                if (isPressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.secondaryContainer
            )
            .repeatingClickable(
                initialDelayMillis = 500,
                delayMillis = 100,
                interactionSource = interactionSource,
                onClick = onStep
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
            tint = if (isPressed) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun FullListDialog(
    title: String,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = onReset,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Reset", style = MaterialTheme.typography.labelMedium)
                    }
                }

                HorizontalDivider()

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    content = content
                )

                HorizontalDivider()

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(end = 12.dp, bottom = 8.dp, top = 4.dp)
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

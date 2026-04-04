package com.devson.nosvedplayer.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.nosvedplayer.viewmodel.ControlIconSize
import com.devson.nosvedplayer.viewmodel.SeekBarStyle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSettingsSheet(
    showSettingsSheet: Boolean,
    sheetState: SheetState,
    seekDurationSeconds: Int,
    seekBarStyle: SeekBarStyle,
    controlIconSize: ControlIconSize,
    autoPlayEnabled: Boolean,
    useYoutubeStyle: Boolean,
    showSeekButtons: Boolean = true,
    fastplaySpeed: Float = 2.0f,
    isLandscape: Boolean,
    onDismissRequest: () -> Unit,
    onSeekDurationChange: (Int) -> Unit,
    onSeekBarStyleChange: (SeekBarStyle) -> Unit,
    onControlIconSizeChange: (ControlIconSize) -> Unit,
    onAutoPlayChange: (Boolean) -> Unit,
    onYoutubeStyleChange: (Boolean) -> Unit,
    onShowSeekButtonsChange: (Boolean) -> Unit = {},
    onFastplaySpeedChange: (Float) -> Unit = {},
    showStats: Boolean = false,
    onShowStatsChange: (Boolean) -> Unit = {}
) {
    // In-sheet page state: false = main, true = seek settings
    var showSeekPage by remember(showSettingsSheet) { mutableStateOf(false) }

    if (showSettingsSheet) {
        if (isLandscape) {
            SideSheet(visible = showSettingsSheet, onDismissRequest = onDismissRequest) {
                SheetPageContent(
                    showSeekPage = showSeekPage,
                    seekDurationSeconds = seekDurationSeconds,
                    seekBarStyle = seekBarStyle,
                    controlIconSize = controlIconSize,
                    autoPlayEnabled = autoPlayEnabled,
                    useYoutubeStyle = useYoutubeStyle,
                    showSeekButtons = showSeekButtons,
                    fastplaySpeed = fastplaySpeed,
                    onControlIconSizeChange = onControlIconSizeChange,
                    onAutoPlayChange = onAutoPlayChange,
                    onYoutubeStyleChange = onYoutubeStyleChange,
                    onSeekDurationChange = onSeekDurationChange,
                    onSeekBarStyleChange = onSeekBarStyleChange,
                    onShowSeekButtonsChange = onShowSeekButtonsChange,
                    onFastplaySpeedChange = onFastplaySpeedChange,
                    showStats = showStats,
                    onShowStatsChange = onShowStatsChange,
                    onOpenSeekPage = { showSeekPage = true },
                    onBackToMain = { showSeekPage = false }
                )
            }
        } else {
            ModalBottomSheet(
                onDismissRequest = {
                    if (showSeekPage) showSeekPage = false else onDismissRequest()
                },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                com.devson.nosvedplayer.ui.theme.DialogNavigationBarThemeFix()
                SheetPageContent(
                    showSeekPage = showSeekPage,
                    seekDurationSeconds = seekDurationSeconds,
                    seekBarStyle = seekBarStyle,
                    controlIconSize = controlIconSize,
                    autoPlayEnabled = autoPlayEnabled,
                    useYoutubeStyle = useYoutubeStyle,
                    showSeekButtons = showSeekButtons,
                    fastplaySpeed = fastplaySpeed,
                    onControlIconSizeChange = onControlIconSizeChange,
                    onAutoPlayChange = onAutoPlayChange,
                    onYoutubeStyleChange = onYoutubeStyleChange,
                    onSeekDurationChange = onSeekDurationChange,
                    onSeekBarStyleChange = onSeekBarStyleChange,
                    onShowSeekButtonsChange = onShowSeekButtonsChange,
                    onFastplaySpeedChange = onFastplaySpeedChange,
                    showStats = showStats,
                    onShowStatsChange = onShowStatsChange,
                    onOpenSeekPage = { showSeekPage = true },
                    onBackToMain = { showSeekPage = false }
                )
            }
        }
    }
}

//  Animated page switcher 

@Composable
private fun SheetPageContent(
    showSeekPage: Boolean,
    seekDurationSeconds: Int,
    seekBarStyle: SeekBarStyle,
    controlIconSize: ControlIconSize,
    autoPlayEnabled: Boolean,
    useYoutubeStyle: Boolean,
    showSeekButtons: Boolean,
    fastplaySpeed: Float,
    onControlIconSizeChange: (ControlIconSize) -> Unit,
    onAutoPlayChange: (Boolean) -> Unit,
    onYoutubeStyleChange: (Boolean) -> Unit,
    onSeekDurationChange: (Int) -> Unit,
    onSeekBarStyleChange: (SeekBarStyle) -> Unit,
    onShowSeekButtonsChange: (Boolean) -> Unit,
    onFastplaySpeedChange: (Float) -> Unit,
    showStats: Boolean,
    onShowStatsChange: (Boolean) -> Unit,
    onOpenSeekPage: () -> Unit,
    onBackToMain: () -> Unit
) {
    AnimatedContent(
        targetState = showSeekPage,
        transitionSpec = {
            if (targetState) {
                // Opening seek page → slide in from right
                (slideInHorizontally { it } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it } + fadeOut())
            } else {
                // Going back → slide in from left
                (slideInHorizontally { -it } + fadeIn()) togetherWith
                        (slideOutHorizontally { it } + fadeOut())
            }
        },
        label = "sheet_page"
    ) { seekPage ->
        if (seekPage) {
            SeekSettingsPage(
                seekDurationSeconds = seekDurationSeconds,
                seekBarStyle = seekBarStyle,
                showSeekButtons = showSeekButtons,
                fastplaySpeed = fastplaySpeed,
                onSeekDurationChange = onSeekDurationChange,
                onSeekBarStyleChange = onSeekBarStyleChange,
                onShowSeekButtonsChange = onShowSeekButtonsChange,
                onFastplaySpeedChange = onFastplaySpeedChange,
                onBack = onBackToMain
            )
        } else {
            MainSettingsPage(
                controlIconSize = controlIconSize,
                autoPlayEnabled = autoPlayEnabled,
                useYoutubeStyle = useYoutubeStyle,
                showStats = showStats,
                onControlIconSizeChange = onControlIconSizeChange,
                onAutoPlayChange = onAutoPlayChange,
                onYoutubeStyleChange = onYoutubeStyleChange,
                onShowStatsChange = onShowStatsChange,
                onOpenSeekPage = onOpenSeekPage
            )
        }
    }
}

//  Page 1: Main settings 

@Composable
private fun MainSettingsPage(
    controlIconSize: ControlIconSize,
    autoPlayEnabled: Boolean,
    useYoutubeStyle: Boolean,
    showStats: Boolean,
    onControlIconSizeChange: (ControlIconSize) -> Unit,
    onAutoPlayChange: (Boolean) -> Unit,
    onYoutubeStyleChange: (Boolean) -> Unit,
    onShowStatsChange: (Boolean) -> Unit,
    onOpenSeekPage: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = "Playback Settings",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        // Seek & Speed entry row
        SettingsSection(title = "Seek & Playback") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenSeekPage
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Seek & Speed Settings",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Control Icon Size
        SettingsSection(title = "Control Icon Size") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(
                    "Small" to ControlIconSize.SMALL,
                    "Medium" to ControlIconSize.MEDIUM,
                    "Large" to ControlIconSize.LARGE
                ).forEach { (label, size) ->
                    ChipButton(
                        label = label,
                        selected = controlIconSize == size,
                        modifier = Modifier.weight(1f),
                        onClick = { onControlIconSizeChange(size) }
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Auto Play
        SettingsSection(title = "Auto Play") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Play next video automatically",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Switch(
                    checked = autoPlayEnabled,
                    onCheckedChange = { onAutoPlayChange(it) }
                )
            }
        }
        
        Spacer(Modifier.height(20.dp))

        // Player Style
        SettingsSection(title = "Player Style") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Use YouTube style controls",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Switch(
                    checked = useYoutubeStyle,
                    onCheckedChange = { onYoutubeStyleChange(it) }
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Device Stats
        SettingsSection(title = "Developer") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Show device stats overlay",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Switch(
                    checked = showStats,
                    onCheckedChange = { onShowStatsChange(it) }
                )
            }
        }
    }
}

//  Page 2: Seek & Speed settings 

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeekSettingsPage(
    seekDurationSeconds: Int,
    seekBarStyle: SeekBarStyle,
    showSeekButtons: Boolean,
    fastplaySpeed: Float,
    onSeekDurationChange: (Int) -> Unit,
    onSeekBarStyleChange: (SeekBarStyle) -> Unit,
    onShowSeekButtonsChange: (Boolean) -> Unit,
    onFastplaySpeedChange: (Float) -> Unit,
    onBack: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var seekText by remember(seekDurationSeconds) { mutableStateOf(seekDurationSeconds.toString()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 36.dp)
    ) {
        //  Header with back button 
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Seek & Speed Settings",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {

            //  Seek Duration 
            SettingsSection(title = "Seek Duration") {
                // Quick presets row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(5, 10, 15, 30).forEach { secs ->
                        ChipButton(
                            label = "${secs}s",
                            selected = seekDurationSeconds == secs,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                seekText = secs.toString()
                                onSeekDurationChange(secs)
                            }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                // Editable custom value
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Custom",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = seekText,
                        onValueChange = { v ->
                            if (v.length <= 3 && v.all { it.isDigit() }) seekText = v
                        },
                        modifier = Modifier.width(110.dp),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(
                            textAlign = TextAlign.Center,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            val parsed = seekText.toIntOrNull()?.coerceIn(1, 999)
                            if (parsed != null) {
                                seekText = parsed.toString()
                                onSeekDurationChange(parsed)
                            }
                            focusManager.clearFocus()
                        }),
                        suffix = { Text("seconds", fontSize = 12.sp) },
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        )
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            //  Seek Bar Style 
            SettingsSection(title = "Seek Bar Style") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ChipButton(
                        label = "Default",
                        selected = seekBarStyle == SeekBarStyle.DEFAULT,
                        modifier = Modifier.weight(1f),
                        onClick = { onSeekBarStyleChange(SeekBarStyle.DEFAULT) }
                    )
                    ChipButton(
                        label = "Flat / Slim",
                        selected = seekBarStyle == SeekBarStyle.FLAT,
                        modifier = Modifier.weight(1f),
                        onClick = { onSeekBarStyleChange(SeekBarStyle.FLAT) }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            //  Show Seek Buttons 
            SettingsSection(title = "Seek Buttons") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Show rewind / forward buttons",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Switch(
                        checked = showSeekButtons,
                        onCheckedChange = { onShowSeekButtonsChange(it) }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            //  Fastplay Speed 
            val speedMin = 1.0f
            val speedMax = 4.0f

            SettingsSection(title = "Tap & Hold Fast-Forward Speed") {
                Column {
                    // Current speed label
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        val rounded = (fastplaySpeed * 10).roundToInt() / 10f
                        // Always show one decimal: 2.0×, 2.5×, etc.
                        val label = "%.1f×".format(rounded)
                        Text(
                            text = label,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(6.dp))

                    // Slim slider with dot thumb
                    Slider(
                        value = fastplaySpeed,
                        onValueChange = { raw ->
                            val snapped = ((raw * 10).roundToInt() / 10f).coerceIn(speedMin, speedMax)
                            onFastplaySpeedChange(snapped)
                        },
                        valueRange = speedMin..speedMax,
                        steps = 29, // 30 positions: 1.0…4.0 step 0.1
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp),
                        thumb = {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        },
                        track = { sliderState ->
                            val fraction = ((sliderState.value - speedMin) / (speedMax - speedMin))
                                .coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction)
                                        .fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    )

                    // Axis labels
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("1.0×", "2.0×", "3.0×", "4.0×").forEach { lbl ->
                            Text(
                                text = lbl,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

//  Shared helpers 

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 10.dp)
    )
    content()
}

@Composable
fun ChipButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                  else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val textColor = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val borderColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                      else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}

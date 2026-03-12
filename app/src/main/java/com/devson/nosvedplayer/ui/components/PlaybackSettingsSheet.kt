package com.devson.nosvedplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.nosvedplayer.viewmodel.ControlIconSize
import com.devson.nosvedplayer.viewmodel.SeekBarStyle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSettingsSheet(
    showSettingsSheet: Boolean,
    sheetState: SheetState,
    seekDurationSeconds: Int,
    seekBarStyle: SeekBarStyle,
    controlIconSize: ControlIconSize,
    onDismissRequest: () -> Unit,
    onSeekDurationChange: (Int) -> Unit,
    onSeekBarStyleChange: (SeekBarStyle) -> Unit,
    onControlIconSizeChange: (ControlIconSize) -> Unit
) {
    val scope = rememberCoroutineScope()

    if (showSettingsSheet) {
        val dismissSheet: () -> Unit = {
            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismissRequest() }
        }

        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            containerColor = Color(0xFF12121F),
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Playback Settings",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                // Setting 1: Seek Duration
                SettingsSection(title = "Seek Duration") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(5, 10, 15, 20).forEach { secs ->
                            val selected = seekDurationSeconds == secs
                            ChipButton(
                                label = "${secs}s",
                                selected = selected,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    onSeekDurationChange(secs)
                                    dismissSheet()
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Setting 2: Seek Bar Style
                SettingsSection(title = "Seek Bar Style") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ChipButton(
                            label = "Default",
                            selected = seekBarStyle == SeekBarStyle.DEFAULT,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onSeekBarStyleChange(SeekBarStyle.DEFAULT)
                                dismissSheet()
                            }
                        )
                        ChipButton(
                            label = "Flat",
                            selected = seekBarStyle == SeekBarStyle.FLAT,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onSeekBarStyleChange(SeekBarStyle.FLAT)
                                dismissSheet()
                            }
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Setting 3: Control Icon Size
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
                                onClick = {
                                    onControlIconSizeChange(size)
                                    dismissSheet()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Text(
        text = title,
        color = Color.White.copy(alpha = 0.55f),
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
    val bgColor = if (selected) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f)
    val textColor = if (selected) Color.White else Color.White.copy(alpha = 0.55f)
    val border = if (selected) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.12f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, border, RoundedCornerShape(10.dp))
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

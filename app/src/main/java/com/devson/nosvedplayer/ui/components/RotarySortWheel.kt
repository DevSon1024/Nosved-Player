package com.devson.nosvedplayer.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.devson.nosvedplayer.model.SortDirection
import com.devson.nosvedplayer.model.SortField
import com.devson.nosvedplayer.util.formatSortField
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RotarySortWheelDialog(
    currentSortField: SortField,
    sortDirection: SortDirection,
    onSortFieldSelected: (SortField) -> Unit,
    onSortOrderToggled: (SortDirection) -> Unit,
    onDismissRequest: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                ),
            contentAlignment = Alignment.Center
        ) {
            // Prevent click-through to backdrop from the wheel
            Box(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    RotarySortWheel(
                        currentSortField = currentSortField,
                        sortDirection = sortDirection,
                        onSortFieldSelected = onSortFieldSelected,
                        onSortOrderToggled = onSortOrderToggled
                    )

                    // Selected field label below wheel
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = formatSortField(currentSortField),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val dirLabels = getSortDirectionLabels(currentSortField)
                        Text(
                            text = if (sortDirection == SortDirection.ASCENDING) dirLabels.first else dirLabels.second,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RotarySortWheel(
    currentSortField: SortField,
    sortDirection: SortDirection,
    onSortFieldSelected: (SortField) -> Unit,
    onSortOrderToggled: (SortDirection) -> Unit
) {
    val items = SortField.values()
    val itemCount = items.size
    val initialSelectedIndex = items.indexOf(currentSortField)
    val anglePerItem = 360f / itemCount

    var rotationAngle by remember { mutableStateOf(-(initialSelectedIndex * anglePerItem)) }

    val wheelDiameterDp = 300.dp
    val radiusDp = 110.dp
    val density = LocalDensity.current
    val radiusPx = with(density) { radiusDp.toPx() }

    // Outer wheel ring
    Box(
        modifier = Modifier
            .size(wheelDiameterDp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape
            )
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, _ ->
                        change.consume()
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val prevVector = change.previousPosition - center
                        val currVector = change.position - center

                        val prevAngle = atan2(prevVector.y, prevVector.x)
                        val currAngle = atan2(currVector.y, currVector.x)

                        var angleDiff = Math.toDegrees((currAngle - prevAngle).toDouble()).toFloat()
                        if (angleDiff > 180f) angleDiff -= 360f
                        if (angleDiff < -180f) angleDiff += 360f

                        rotationAngle += angleDiff
                    },
                    onDragEnd = {
                        val rawIndex = Math.round(-rotationAngle / anglePerItem)
                        var index = rawIndex % itemCount
                        if (index < 0) index += itemCount
                        val nextField = items[index]
                        onSortFieldSelected(nextField)
                        rotationAngle = -(index * anglePerItem)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Top indicator dot
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        )

        // Orbit items
        items.forEachIndexed { index, field ->
            val angleDeg = (index * anglePerItem) + rotationAngle - 90f
            val angleRad = angleDeg * (PI.toFloat() / 180f)

            val xOffset = cos(angleRad) * radiusPx
            val yOffset = sin(angleRad) * radiusPx

            val isSelected = field == currentSortField

            val labelAlpha by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0.45f,
                animationSpec = spring(),
                label = "labelAlpha_$index"
            )

            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { xOffset.toDp() },
                        y = with(density) { yOffset.toDp() }
                    )
                    .then(
                        if (isSelected) {
                            Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .border(
                                    width = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(20.dp)
                                )
                        } else Modifier
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onSortFieldSelected(field)
                        rotationAngle = -(index * anglePerItem)
                    }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = formatSortField(field),
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = labelAlpha),
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    fontSize = 12.sp,
                    letterSpacing = 0.3.sp
                )
            }
        }

        // Center hub
        Box(
            modifier = Modifier
                .size(148.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                val dirLabels = getSortDirectionLabels(currentSortField)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SortOrderButton(
                        label = dirLabels.first,
                        icon = Icons.Outlined.KeyboardArrowUp,
                        isActive = sortDirection == SortDirection.ASCENDING,
                        onClick = { onSortOrderToggled(SortDirection.ASCENDING) }
                    )
                    SortOrderButton(
                        label = dirLabels.second,
                        icon = Icons.Outlined.KeyboardArrowDown,
                        isActive = sortDirection == SortDirection.DESCENDING,
                        onClick = { onSortOrderToggled(SortDirection.DESCENDING) }
                    )
                }

                Text(
                    text = "SORT ORDER",
                    fontSize = 9.sp,
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun SortOrderButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val iconAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.35f,
        animationSpec = spring(),
        label = "iconAlpha_$label"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isActive) Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = iconAlpha),
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = iconAlpha),
            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
        )
    }
}

fun getSortDirectionLabels(field: SortField): Pair<String, String> {
    return when (field) {
        SortField.TITLE -> "A to z" to "Z to a"
        SortField.DATE -> "Oldest" to "Newest"
        SortField.PLAYED_TIME -> "Oldest" to "Newest"
        SortField.STATUS -> "Asc" to "Desc"
        SortField.LENGTH -> "Shortest" to "Longest"
        SortField.SIZE -> "Smallest" to "Largest"
        SortField.RESOLUTION -> "Lowest" to "Highest"
        SortField.PATH -> "Asc" to "Desc"
        SortField.FRAME_RATE -> "Lowest" to "Highest"
        SortField.TYPE -> "Asc" to "Desc"
    }
}
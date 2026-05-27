package com.devson.nvplayer.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devson.nvplayer.model.ControlRegion
import com.devson.nvplayer.model.PlayerButton
import com.devson.nvplayer.model.allPlayerButtons
import com.devson.nvplayer.viewmodel.SettingsViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ControlLayoutEditorScreen(
    onNavigateBack: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val playbackSettings by settingsViewModel.playbackSettings.collectAsState()

    // 1. Helper to parse regions safely
    fun parseRegion(value: String, region: ControlRegion): List<PlayerButton> {
        val parsed = value.split(',').mapNotNull { runCatching { PlayerButton.valueOf(it) }.getOrNull() }
        return if (region == ControlRegion.TOP_LEFT) {
            listOf(PlayerButton.BACK_ARROW, PlayerButton.VIDEO_TITLE) +
                    parsed.filter { it != PlayerButton.BACK_ARROW && it != PlayerButton.VIDEO_TITLE }
        } else {
            parsed
        }
    }

    // 2. Local State variables for all 5 regions
    var topLeftList by remember(playbackSettings) {
        mutableStateOf(parseRegion(playbackSettings.topLeftControls, ControlRegion.TOP_LEFT))
    }
    var topRightList by remember(playbackSettings) {
        mutableStateOf(parseRegion(playbackSettings.topRightControls, ControlRegion.TOP_RIGHT))
    }
    var bottomLeftList by remember(playbackSettings) {
        mutableStateOf(parseRegion(playbackSettings.bottomLeftControls, ControlRegion.BOTTOM_LEFT))
    }
    var bottomRightList by remember(playbackSettings) {
        mutableStateOf(parseRegion(playbackSettings.bottomRightControls, ControlRegion.BOTTOM_RIGHT))
    }
    var portraitBottomList by remember(playbackSettings) {
        mutableStateOf(parseRegion(playbackSettings.portraitBottomControls, ControlRegion.PORTRAIT_BOTTOM))
    }

    // Currently selected region and preview orientation
    var selectedRegion by remember { mutableStateOf(ControlRegion.TOP_LEFT) }
    var isPortraitPreview by remember { mutableStateOf(false) }

    // If Portrait Bottom is selected, force portrait preview orientation so user can see it
    LaunchedEffect(selectedRegion) {
        if (selectedRegion == ControlRegion.PORTRAIT_BOTTOM) {
            isPortraitPreview = true
        }
    }

    // 3. Save all updated control configurations on Leave/Dispose
    DisposableEffect(topLeftList, topRightList, bottomLeftList, bottomRightList, portraitBottomList) {
        onDispose {
            settingsViewModel.updateControls(ControlRegion.TOP_LEFT, topLeftList)
            settingsViewModel.updateControls(ControlRegion.TOP_RIGHT, topRightList)
            settingsViewModel.updateControls(ControlRegion.BOTTOM_LEFT, bottomLeftList)
            settingsViewModel.updateControls(ControlRegion.BOTTOM_RIGHT, bottomRightList)
            settingsViewModel.updateControls(ControlRegion.PORTRAIT_BOTTOM, portraitBottomList)
        }
    }

    // 4. State delegation helper for active region
    val activeList = remember(selectedRegion, topLeftList, topRightList, bottomLeftList, bottomRightList, portraitBottomList) {
        when (selectedRegion) {
            ControlRegion.TOP_LEFT -> topLeftList
            ControlRegion.TOP_RIGHT -> topRightList
            ControlRegion.BOTTOM_LEFT -> bottomLeftList
            ControlRegion.BOTTOM_RIGHT -> bottomRightList
            ControlRegion.PORTRAIT_BOTTOM -> portraitBottomList
        }
    }

    val onActiveListChange: (List<PlayerButton>) -> Unit = { newList ->
        when (selectedRegion) {
            ControlRegion.TOP_LEFT -> topLeftList = newList
            ControlRegion.TOP_RIGHT -> topRightList = newList
            ControlRegion.BOTTOM_LEFT -> bottomLeftList = newList
            ControlRegion.BOTTOM_RIGHT -> bottomRightList = newList
            ControlRegion.PORTRAIT_BOTTOM -> portraitBottomList = newList
        }
    }

    // 5. Orientation-aware palette logic
    //    Landscape context: the visible layout is TOP_LEFT + TOP_RIGHT + BOTTOM_LEFT + BOTTOM_RIGHT.
    //    Portrait context:  the visible layout is TOP_LEFT + TOP_RIGHT + PORTRAIT_BOTTOM.
    //    A button already used anywhere in the CURRENT orientation's layout is excluded from
    //    the palette, preventing the same control from appearing twice on screen.
    //    BACK_ARROW, VIDEO_TITLE, and NONE are always excluded (managed separately).
    val paletteButtons = remember(
        isPortraitPreview, topLeftList, topRightList, bottomLeftList, bottomRightList, portraitBottomList, activeList
    ) {
        val alreadyUsedInLayout = buildSet<PlayerButton> {
            // TOP_LEFT and TOP_RIGHT are visible in both orientations
            addAll(topLeftList)
            addAll(topRightList)
            if (isPortraitPreview) {
                // Portrait layout — only PORTRAIT_BOTTOM rows are active
                addAll(portraitBottomList)
            } else {
                // Landscape layout — BOTTOM_LEFT and BOTTOM_RIGHT are active
                addAll(bottomLeftList)
                addAll(bottomRightList)
            }
        }
        allPlayerButtons.filter { it !in alreadyUsedInLayout }
    }

    val lazyGridState = rememberLazyGridState()
    val reorderableLazyGridState = rememberReorderableLazyGridState(lazyGridState) { from, to ->
        if (selectedRegion == ControlRegion.TOP_LEFT && (from.index < 2 || to.index < 2)) {
            return@rememberReorderableLazyGridState
        }
        onActiveListChange(activeList.toMutableList().apply {
            add(to.index, removeAt(from.index))
        })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Control Layout Customizer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Segmented Orientation Toggle in Toolbar
                    OrientationToggle(
                        isPortrait = isPortraitPreview,
                        onToggle = { portrait ->
                            isPortraitPreview = portrait
                            // Auto-correct selectedRegion if it doesn't exist in the new orientation
                            if (portrait && selectedRegion in listOf(ControlRegion.BOTTOM_LEFT, ControlRegion.BOTTOM_RIGHT)) {
                                // BOTTOM_LEFT / BOTTOM_RIGHT are landscape-only → switch to PORTRAIT_BOTTOM
                                selectedRegion = ControlRegion.PORTRAIT_BOTTOM
                            } else if (!portrait && selectedRegion == ControlRegion.PORTRAIT_BOTTOM) {
                                // PORTRAIT_BOTTOM is portrait-only → switch to BOTTOM_RIGHT
                                selectedRegion = ControlRegion.BOTTOM_RIGHT
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Live Interactive Preview Mockup Box
            SimulatedPlayerPreview(
                isPortrait = isPortraitPreview,
                selectedRegion = selectedRegion,
                onRegionSelect = { selectedRegion = it },
                topLeft = topLeftList,
                topRight = topRightList,
                bottomLeft = bottomLeftList,
                bottomRight = bottomRightList,
                portraitBottom = portraitBottomList
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Scrollable Tab Selector — tabs are filtered by the active orientation
            RegionTabRow(
                selectedRegion = selectedRegion,
                isPortrait = isPortraitPreview,
                onRegionSelect = { selectedRegion = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Drop Zone Card Header
            Text(
                text = "Active Controls (Drag to Reorder)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )

            // Reorderable Drop Zone Grid
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.3f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                if (activeList.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No controls in this region.\nAdd controls from the palette below.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = lazyGridState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        items(activeList, key = { it.name }) { button ->
                            ReorderableItem(reorderableLazyGridState, key = button.name) { isDragging ->
                                val isMovable = button != PlayerButton.BACK_ARROW && button != PlayerButton.VIDEO_TITLE
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(4.dp)
                                        .then(if (isMovable) Modifier.longPressDraggableHandle() else Modifier)
                                        .border(
                                            width = if (isDragging) 2.dp else 1.dp,
                                            color = if (isDragging) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isDragging) {
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        }
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    elevation = CardDefaults.cardElevation(
                                        defaultElevation = if (isDragging) 8.dp else 1.dp
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = button.icon,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = button.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isMovable) {
                                            Icon(
                                                imageVector = Icons.Rounded.DragHandle,
                                                contentDescription = "Drag handle",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .draggableHandle()
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.errorContainer)
                                                    .clickable {
                                                        onActiveListChange(activeList.filter { it != button })
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Remove,
                                                    contentDescription = "Remove",
                                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Palette Header
            Text(
                text = "Available Controls Palette",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )

            // Available Palette Palette Scroll View
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                if (paletteButtons.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "All controls have been added to this region.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            paletteButtons.forEach { button ->
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            onActiveListChange(activeList + button)
                                        },
                                    color = MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = button.icon,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = button.displayName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Add,
                                                contentDescription = "Add",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(10.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SimulatedPlayerPreview(
    isPortrait: Boolean,
    selectedRegion: ControlRegion,
    onRegionSelect: (ControlRegion) -> Unit,
    topLeft: List<PlayerButton>,
    topRight: List<PlayerButton>,
    bottomLeft: List<PlayerButton>,
    bottomRight: List<PlayerButton>,
    portraitBottom: List<PlayerButton>
) {
    val widthFraction = if (isPortrait) 0.5f else 0.85f
    val aspectRatio = if (isPortrait) 9f / 16f else 16f / 9f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(widthFraction)
                .aspectRatio(aspectRatio)
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(12.dp)
                ),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF16161A))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                // Background Center Icon Mock
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.05f),
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.Center)
                )

                // Layout: Top Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // Top Left Region
                    MockRegionBox(
                        region = ControlRegion.TOP_LEFT,
                        isSelected = selectedRegion == ControlRegion.TOP_LEFT,
                        onClick = { onRegionSelect(ControlRegion.TOP_LEFT) },
                        buttons = topLeft,
                        modifier = Modifier.weight(1.3f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // Top Right Region
                    MockRegionBox(
                        region = ControlRegion.TOP_RIGHT,
                        isSelected = selectedRegion == ControlRegion.TOP_RIGHT,
                        onClick = { onRegionSelect(ControlRegion.TOP_RIGHT) },
                        buttons = topRight,
                        modifier = Modifier.weight(0.7f)
                    )
                }

                // Layout: Bottom Row (excluding Portrait Bottom - only visible in landscape)
                if (!isPortrait) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Bottom Left Region
                        MockRegionBox(
                            region = ControlRegion.BOTTOM_LEFT,
                            isSelected = selectedRegion == ControlRegion.BOTTOM_LEFT,
                            onClick = { onRegionSelect(ControlRegion.BOTTOM_LEFT) },
                            buttons = bottomLeft,
                            modifier = Modifier.weight(0.8f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        // Bottom Right Region
                        MockRegionBox(
                            region = ControlRegion.BOTTOM_RIGHT,
                            isSelected = selectedRegion == ControlRegion.BOTTOM_RIGHT,
                            onClick = { onRegionSelect(ControlRegion.BOTTOM_RIGHT) },
                            buttons = bottomRight,
                            modifier = Modifier.weight(1.2f)
                        )
                    }
                }

                // Layout: Portrait Bottom Row (only visible in Portrait mode)
                if (isPortrait) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                    ) {
                        MockRegionBox(
                            region = ControlRegion.PORTRAIT_BOTTOM,
                            isSelected = selectedRegion == ControlRegion.PORTRAIT_BOTTOM,
                            onClick = { onRegionSelect(ControlRegion.PORTRAIT_BOTTOM) },
                            buttons = portraitBottom,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MockRegionBox(
    region: ControlRegion,
    isSelected: Boolean,
    onClick: () -> Unit,
    buttons: List<PlayerButton>,
    modifier: Modifier = Modifier
) {
    val highlightColor = MaterialTheme.colorScheme.primary
    val normalBorderColor = Color.White.copy(alpha = 0.15f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isSelected) highlightColor.copy(alpha = 0.12f)
                else Color.White.copy(alpha = 0.03f)
            )
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) highlightColor else normalBorderColor,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Region Name Tag
            Text(
                text = region.displayName.substringBefore(" Controls"),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) highlightColor else Color.White.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))

            // Buttons Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (buttons.isEmpty()) {
                    Text(
                        text = "[Empty]",
                        fontSize = 7.sp,
                        color = Color.White.copy(alpha = 0.25f)
                    )
                } else {
                    buttons.take(4).forEach { button ->
                        if (button == PlayerButton.VIDEO_TITLE) {
                            // Render a tiny text box to represent video title
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .padding(horizontal = 2.dp, vertical = 0.5.dp)
                            ) {
                                Text(
                                    text = "Title",
                                    fontSize = 6.sp,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            Icon(
                                imageVector = button.icon,
                                contentDescription = null,
                                tint = if (isSelected) highlightColor else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                    if (buttons.size > 4) {
                        Text(
                            text = "+${buttons.size - 4}",
                            fontSize = 7.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OrientationToggle(
    isPortrait: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val selectedColor = MaterialTheme.colorScheme.primaryContainer
    val onSelectedColor = MaterialTheme.colorScheme.onPrimaryContainer
    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(containerColor)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Landscape Option
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (!isPortrait) selectedColor else Color.Transparent)
                .clickable { onToggle(false) }
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.StayCurrentLandscape,
                    contentDescription = null,
                    tint = if (!isPortrait) onSelectedColor else unselectedColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Landscape",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (!isPortrait) onSelectedColor else unselectedColor
                )
            }
        }

        // Portrait Option
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (isPortrait) selectedColor else Color.Transparent)
                .clickable { onToggle(true) }
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.StayCurrentPortrait,
                    contentDescription = null,
                    tint = if (isPortrait) onSelectedColor else unselectedColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Portrait",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isPortrait) onSelectedColor else unselectedColor
                )
            }
        }
    }
}

@Composable
fun RegionTabRow(
    selectedRegion: ControlRegion,
    isPortrait: Boolean,
    onRegionSelect: (ControlRegion) -> Unit,
    modifier: Modifier = Modifier
) {
    // Landscape-only regions; Portrait-only region
    val landscapeRegions = listOf(
        ControlRegion.TOP_LEFT,
        ControlRegion.TOP_RIGHT,
        ControlRegion.BOTTOM_LEFT,
        ControlRegion.BOTTOM_RIGHT
    )
    val portraitRegions = listOf(
        ControlRegion.TOP_LEFT,
        ControlRegion.TOP_RIGHT,
        ControlRegion.PORTRAIT_BOTTOM
    )
    val visibleRegions = if (isPortrait) portraitRegions else landscapeRegions
    val selectedIndex = visibleRegions.indexOf(selectedRegion).coerceAtLeast(0)

    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = 0.dp,
        containerColor = Color.Transparent,
        divider = {},
        indicator = { tabPositions ->
            if (selectedIndex < tabPositions.size) {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        modifier = modifier.fillMaxWidth()
    ) {
        visibleRegions.forEachIndexed { index, region ->
            val isSelected = selectedRegion == region
            Tab(
                selected = isSelected,
                onClick = { onRegionSelect(region) },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = region.displayName.substringBefore(" Controls"),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        // Badge portrait-only tab so the user knows it's portrait-exclusive
                        if (region == ControlRegion.PORTRAIT_BOTTOM) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "Portrait",
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

# Update Details

---

## Update: Multi-Selection & Contextual Action Bar for Video Folders

**Date:** 2026-03-20  
**File Changed:** `app/src/main/java/com/devson/nosvedplayer/ui/screens/VideoListScreen.kt`

### Summary

Implemented a robust long-press multi-selection feature (Contextual Action Bar / CAB pattern) for the Video Folders list.

### Changes Made

#### 1. State & Gestures (`@OptIn(ExperimentalFoundationApi::class)`)
- Added `var selectedFolders by remember { mutableStateOf(emptySet<VideoFolder>()) }` at the `VideoListScreen` level.
- Replaced `.clickable` with `.combinedClickable` on both `FolderListItem` and `FolderGridItem`.
  - **`onLongClick`**: Adds the folder to `selectedFolders` and triggers `HapticFeedbackType.LongPress` via `LocalHapticFeedback`.
  - **`onClick`**: Toggles folder in `selectedFolders` if selection is active, otherwise navigates normally via `viewModel.selectFolder(folder)`.
- Added visual selection indicators:
  - **List view**: Selected rows get a `primaryContainer` background plus a circular checkmark badge on the folder icon.
  - **Grid view**: Selected cards get a `primaryContainer` fill, a `primary` border stroke (2dp), and a circular checkmark overlay in the top-right corner.

#### 2. Contextual TopAppBar
- When `selectedFolders.isNotEmpty()` (and in folder view): renders a contextual `TopAppBar` with `primaryContainer` colors.
  - **Navigation Icon**: `Close` icon → clears `selectedFolders`.
  - **Title**: Shows `"${selectedFolders.size} / ${total}"`.
  - **Actions**: `SelectAll` icon — selects all folders; if all are already selected, it unselects all.
- When no selection is active: renders the original `TopAppBar` with View Settings and App Settings icons.

#### 3. Contextual BottomAppBar
- When `selectedFolders.isNotEmpty()` (and in folder view): renders a `BottomAppBar` with a horizontally scrollable `Row` of action items:
  - **Play All**: Combines all videos from selected folders and starts the player.
  - **Move**: Stub — shows a Toast `"Move: Not yet implemented"`.
  - **Copy**: Stub — shows a Toast `"Copy: Not yet implemented"`.
  - **Delete**: Stub — shows a Toast `"Delete: Not yet implemented"`.
  - **Rename**: Visible **only** when `selectedFolders.size == 1` — shows a Toast stub.
  - **Info**: Opens the `FolderInfoDialog`.

#### 4. FolderInfoDialog
- New `FolderInfoDialog` composable triggered by the Info action.
- Calculates and displays:
  - **Total Videos**: Sum of videos across all selected folders.
  - **Total Size**: Aggregated size using `formatSize()`.
  - **Location**: Folder `id` (single) or `"N folders selected"` (multiple).
  - **Creation Date**: Oldest `dateAdded` across all videos in the folder — shown **only** when `selectedFolders.size == 1`.

#### 5. Back Handler
- Updated `BackHandler` to handle both states:
  - If `selectedFolders.isNotEmpty()` → clears selection.
  - If `selectedFolder != null` → navigates back to folder list.

#### 6. Signature changes in `FolderListContent`, `FolderListItem`, `FolderGridItem`
- Added `isSelected: Boolean`, `onClick: () -> Unit`, and `onLongClick: () -> Unit` parameters.
- Removed the old `onClick: (VideoFolder) -> Unit` callback pattern from items (logic moved up to the screen).

---

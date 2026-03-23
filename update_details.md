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
  - **Actions**: `SelectAll` icon - selects all folders; if all are already selected, it unselects all.
- When no selection is active: renders the original `TopAppBar` with View Settings and App Settings icons.

#### 3. Contextual BottomAppBar

- When `selectedFolders.isNotEmpty()` (and in folder view): renders a `BottomAppBar` with a horizontally scrollable `Row` of action items:
  - **Play All**: Combines all videos from selected folders and starts the player.
  - **Move**: Stub - shows a Toast `"Move: Not yet implemented"`.
  - **Copy**: Stub - shows a Toast `"Copy: Not yet implemented"`.
  - **Delete**: Stub - shows a Toast `"Delete: Not yet implemented"`.
  - **Rename**: Visible **only** when `selectedFolders.size == 1` - shows a Toast stub.
  - **Info**: Opens the `FolderInfoDialog`.

#### 4. FolderInfoDialog

- New `FolderInfoDialog` composable triggered by the Info action.
- Calculates and displays:
  - **Total Videos**: Sum of videos across all selected folders.
  - **Total Size**: Aggregated size using `formatSize()`.
  - **Location**: Folder `id` (single) or `"N folders selected"` (multiple).
  - **Creation Date**: Oldest `dateAdded` across all videos in the folder - shown **only** when `selectedFolders.size == 1`.

#### 5. Back Handler

- Updated `BackHandler` to handle both states:
  - If `selectedFolders.isNotEmpty()` → clears selection.
  - If `selectedFolder != null` → navigates back to folder list.

#### 6. Signature changes in `FolderListContent`, `FolderListItem`, `FolderGridItem`

- Added `isSelected: Boolean`, `onClick: () -> Unit`, and `onLongClick: () -> Unit` parameters.
- Removed the old `onClick: (VideoFolder) -> Unit` callback pattern from items (logic moved up to the screen).

---

#### 1. Custom Storage Explorer

- Replaced SAF with a premium, custom-built **StorageExplorerScreen**.
- Support for **Move**, **Copy**, and **Create Folder** operations.
- **Improved Reliability:** Navigation now waits for the file operation to complete and the MediaStore to be scanned before returning to the list, preventing race conditions. 2. Video Metadata Fixes (0 Duration/Size)
  Hardened File Operations: Implemented strict byte-count checks to ensure files are fully copied/moved before deleting originals or reporting success. This prevents "0-byte" files from being created due to stream errors.
  Robust Fallback:
  VideoRepository.kt
  now uses MediaMetadataRetriever and the
  File
  API as a multi-layered fallback if MediaStore reports 0 duration or size.
  Synchronous Scanning:
  FileOperationsViewModel.kt
  now waits for the MediaStore scanner to finish before triggering a UI reload. 3. Watch History and Navigation
  Progress Visibility: Added progress bars to all video list items (List and Grid modes) showing watch history.
  Resume Playback: Videos now correctly resume from their last saved position when opened from any screen.
  Home Screen Optimization: Limited "Continue Watching" history to the latest 10 items and ensured consistent card sizes.
  Navigation fix: Resolved the bug where navigating back from Settings always went to the Home screen; it now returns to the actual previous screen. 4. Custom Rename Dialog & Folder Renaming
  Premium Design: Created a modern, glassmorphism-inspired
  CustomRenameDialog
  with rounded corners, subtle gradients, and improved spacing.
  No Character Limits: Removed character restrictions in the rename input.
  Folder Renaming: Implemented
  renameFolder
  in
  FileOperationsViewModel.kt
  to support renaming entire directories, with automatic MediaStore scanning for all contents.
  Full Integration: Integrated the new dialog into
  VideoListScreen.kt
  (for both videos and folders) and
  StorageExplorerScreen.kt
  (for creating new folders).
  Code Optimization: Removed the legacy
  RenameDialog
  from
  ViewSettingsBottomSheet.kt
  and fixed Material 3 API usage in the new component to ensure a clean build.
  Robust Path Resolution: Fixed a critical bug where folder renaming failed in the Library view because it was using the MediaStore BUCKET_ID as a path; it now correctly resolves the absolute path from the folder's videos.

---

1. App Logo in About Screen
   In
   AboutScreen.kt
   , I replaced the generic VideoLibrary material icon with the actual app logo (R.mipmap.ic_launcher).

Added a subtle corner radius to match the app's modern aesthetics.
Removed the tint to allow the logo's original colors to shine. 2. History Card Delete interaction
In
HomeScreen.kt
, I revamped how users remove videos from their "Continue Watching" history.

Trigger: Long-press on a video card.
Visuals: A red "Delete" button appears at the center of the card with a "bump" animation (scale-in with a spring effect).
Behavior: Clicking the centered button deletes the video from history. To cancel, users can click anywhere on the card's background.
Cleanup: The previous delete icon in the corner was removed for a cleaner look.

---

1. ViewSettingsBottomSheet Redesign
   File:
   ViewSettingsBottomSheet.kt

View Mode & Layout: Replaced RadioButton rows with a new
SegmentedToggleButton
composable - a Surface with a filled primaryContainer background when selected, outlined when not. Each option is fully clickable and equal-width.
Fields (3×3 grid): All 9 toggles (Thumbnail, Length, File Ext., Played Time, Resolution, Frame Rate, Path, Size, Date) are now organized into 3 rows of 3 columns using chunked(3), with a
CompactMetadataToggle
composable (Checkbox + bodySmall text).
Grid Columns: Wrapped the
(1..4)
boxes in
Row(horizontalArrangement = Arrangement.Center)
inside a fillMaxWidth outer
Row

- perfectly centered.
  Text sizes: Section headers → labelMedium (colored primary), toggle labels → bodySmall. Sheet title → titleMedium.

2. Frame Rate & Resolution Display Fix
   File:
   VideoRepository.kt
   ,
   VideoListScreen.kt

VideoRepository
Resolution: Removed the old "${height}p" conversion. The resolution MediaStore column already stores "WIDTHxHEIGHT" (e.g. "1920x1080") - now used as-is.
Frame Rate: Added MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE extraction after the path is confirmed non-empty. Result is stored as Float? in the 
Video
 object; null if the codec doesn't report it.
VideoMetadataRow (VideoListScreen.kt)
Fixed display: "${video.frameRate} fps" → "${video.frameRate.toInt()} fps" (e.g. "30 fps" not "30.0 fps").
Added video.frameRate > 0f guard to suppress 0 fps artifacts. 3. Show Hidden Files Fix
Files:
VideoRepository.kt
,
VideoListViewModel.kt

Root Cause
MediaStore never returns files inside hidden directories (paths with a segment starting with .). The previous toggle only filtered results after the query - but those results never included hidden files in the first place.

Fix
getAllVideos(showHiddenFiles, recognizeNoMedia)
now accepts two parameters.
When showHiddenFiles = true, a secondary File-walk runs after the MediaStore query:
Scans top-level hidden dirs under Environment.getExternalStorageDirectory().
Also scans .-prefixed subdirectories inside non-hidden top-level dirs.
Recursively walks subdirs, extracting duration/resolution/frameRate via MediaMetadataRetriever.
Uses file:// URIs (MediaStore won't have content URIs for these files).
Deduplicates against a seenPaths set populated during the normal MediaStore query.
If recognizeNoMedia = true, folders containing a .nomedia file are skipped.
VideoListViewModel.loadVideos() now reads \_viewSettings.value and passes showHiddenFiles / recognizeNoMedia to the repository. 4. Unified SelectionBottomAppBar
File:
VideoListScreen.kt

Root Cause
The old bottomBar branched on selectedFolder == null, so:

FILES mode: selectedFolder is always null → folder bar showed with empty selectedFolders → nothing worked.
FOLDERS mode: same issue.
Fix
Replaced the if (selectedFolder == null) branch with:

selectedUris computation (via remember) that switches on viewMode:

FILES → selectedVideos URIs directly.
ALL_FOLDERS (no folder open) → all videos inside selectedFolders.
ALL_FOLDERS (inside a folder) → selectedVideos URIs directly.
FOLDERS → union of selectedVideos URIs + all videos inside selectedFolders (deduplicated).
useFolderBar flag: true only when viewMode == ALL_FOLDERS && selectedFolder == null && selectedFolders.isNotEmpty(). This preserves the original
SelectionBottomAppBar
with its Play-All-Folder and Rename-Folder features.

Otherwise,
VideoSelectionBottomAppBar
is rendered with selectedUris passed to onMove, onCopy, and onDelete - making it work identically across FILES, FOLDERS, and the inside-folder ALL_FOLDERS sub-view.

Validation
Static analysis: dart-mcp-server analyze_files returned No errors across all 4 modified files.
Build and on-device testing recommended to verify runtime behavior.

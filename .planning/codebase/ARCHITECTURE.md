# Architecture - NosvedPlayer

High-level architectural design and data flow of the application.

## Architectural Pattern: MVVM (Model-View-ViewModel)
The application follows the decoupled MVVM pattern, separating UI logic from business logic and data management.

### Layer 1: View (Jetpack Compose UI)
- **Screens**: Defined in `ui/screens/` (e.g., `HomeScreen`, `VideoScreen`).
- **Components**: Reusable, atomic UI elements in `ui/components/`.
- **Theme**: Centralized styling in `ui/theme/` (Supports dynamic colors and custom palettes).

### Layer 2: ViewModel
- **State Management**: Uses `StateFlow` and `MutableStateFlow` to expose reactive state to the UI.
- **Async Execution**: Leverages `viewModelScope` for coroutines (e.g., database operations, playback control).
- **Managers Interaction**: Bridges between UI events and the `PlayerManager` or `WorkManager`.

### Layer 3: Domain & Repositories
- **Abstraction**: Repositories (`VideoRepository`, `WatchHistoryRepository`) provide a clean interface for data operations, hiding the complexity of Room and DataStore.
- **Entities**: Data models defined in `model/` (e.g., `Video`, `TrackInfo`).

### Layer 4: Data Sources
- **Persistence**:
  - **Room Database**: Stores structured data like watch history and video metadata.
  - **DataStore**: Stores key-value pairs for user preferences (Theme, Player settings).
- **System Services**: `MediaStore` for video discovery; `DocumentFile` for SAF-based storage access.

## Playback Engine
- **PlayerManager**: A dedicated manager class that encapsulates `ExoPlayer` (Media3).
- **Renderer Factory**: Uses `NextRenderersFactory` from `nextlib` for enhanced codec support (e.g., FFmpeg).
- **Audio Boost**: Implements `LoudnessEnhancer` for audio amplification beyond system limits.

## Data Flow
1. **Event**: User interacts with the UI (e.g., taps "Play").
2. **Action**: UI calls a method on the `ViewModel`.
3. **Processing**: `ViewModel` interacts with `PlayerManager` or `Repository`.
4. **State Update**: `PlayerManager` updates its `StateFlow`; `ViewModel` propagates this to the UI.
5. **Recomposition**: Compose UI observes the `StateFlow` and updates automatically.

# Concerns - NosvedPlayer

Identification of technical debt, architectural risks, and performance bottlenecks.

## Architectural Concerns

- **ViewModel Bloat**: `VideoViewModel.kt` is exceeds 400 lines and handles too many responsibilities (playback state, settings management, history logic, track selection).
- **Large UI Components**: `Modernstyleplayercontrols.kt` and `VideoListScreen.kt` are very large, making maintenance and debugging difficult.
- **Unstable APIs**: Extensive use of `@OptIn(UnstableApi::class)` specifically for Media3, which may break in future library updates.

## Performance & Stability

- **Playback Compatibility**: Recurring `UnrecognizedInputFormatException` and `NoDeclaredBrand` errors in logs indicate issues with certain MP4/Media containers.
- **Hardware Limitations**: Some 10-bit HEVC or high-bitrate files may fail due to hardware decoder limitations, as noted in `PlayerManager.kt`.
- **MediaCodec Stability**: Logs show `MediaCodecVideoRenderer` crashes, suggesting fragility in the interaction with hardware decoders.
- **Memory Pressure**: High JIT compilation allocation (14MB+) observed for complex screens like `VideoScreen`.

## Security & Compliance

- **Permissions**: Use of `MANAGE_EXTERNAL_STORAGE` is a "High Priority" permission that requires strict justification for Play Store distribution.
- **Input Validation**: The app allows loading external subtitles via URI; ensure proper validation to prevent path traversal or malicious file injection.

## Technical Debt

- **Testing Gap**: Significant lack of unit and instrumentation tests beyond boilerplate examples.
- **Manual Dependency Management**: Absence of a DI framework (like Hilt or Koin) leads to manual dependency passing, which will become unmanageable as the app grows.
- **Log Management**: The root `error.log` file is useful but might grow indefinitely; needs a rotation or size-limit strategy.

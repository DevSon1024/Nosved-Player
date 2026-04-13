# Testing - NosvedPlayer

Documentation of the testing strategy, existing suites, and coverage.

## Testing Strategy
- **Unit Testing**: Targeted for business logic, repositories, and ViewModels.
- **Instrumentation Testing**: Focuses on Compose UI components and database integrations.
- **Manual Verification**: Primary method for verifying video playback and gesture controls.

## Existing Test Suites
### Unit Tests (Local)
- Location: `app/src/test/java/com/devson/nosvedplayer/`
- Current implementation: 
  - `ExampleUnitTest.kt`: Boilerplate JUnit 4 test.
- **Gaps**: No unit tests for `VideoRepository`, `PlayerManager`, or logic-heavy ViewModels.

### Instrumentation Tests (On-device)
- Location: `app/src/androidTest/java/com/devson/nosvedplayer/`
- Current implementation:
  - `ExampleInstrumentedTest.kt`: Boilerplate AndroidX test.
- **Gaps**: No automated UI tests for critical flows like video playback, settings changes, or folder navigation.

## Tools & Frameworks
- **JUnit 4**: Standard unit testing framework.
- **Espresso**: Used for instrumentation tests.
- **Compose Test**: Configured in Gradle but not currently leveraged for UI component testing.

## Running Tests
- **All tests**: `.\gradlew test connectedAndroidTest`
- **Unit tests only**: `.\gradlew test`
- **Instrumentation tests only**: `.\gradlew connectedAndroidTest`

## Pending Quality Work
- [ ] Implement unit tests for `WatchHistoryRepository` (Room integrations).
- [ ] Create Compose UI tests for the Player controls.
- [ ] Implement regression tests for playback edge cases (unsupported formats, orientation changes).

# AI Agent Instructions for NVPlayer Development

This document serves as the absolute source of truth for any AI agent or LLM assisting with the development of the **NVPlayer** project. You must strictly adhere to these rules, architectural guidelines, and development philosophies before generating or modifying any code.

## 1. Core Development Philosophy

- **Goal:** NVPlayer is a high-performance, native Android video player application built with Kotlin, Jetpack Compose, Coroutines/Flow, Coil 3, and the MPV Player Engine.
- **Flawless Execution:** The app MUST work smoothly without any bottleneck bugs. Performance regressions, UI lag, and stuttering (especially during list scrolling and thumbnail generation) are unacceptable.
- **Zero Crash Tolerance:** Improve code robustness to ensure the app does not crash under any circumstances. Always prioritize graceful degradation (e.g., showing an error state, fallback UI, or empty list) over throwing unhandled exceptions.
- **No Hallucinations:** Only use existing APIs, classes, and resources within the project. If you are unsure about an existing implementation, ask the developer to fetch the file contents.

## 2. UI / Jetpack Compose Guidelines

- **Framework:** Jetpack Compose is the exclusive UI framework. Avoid legacy XML layouts entirely for UI screens (XML is only permitted for AndroidManifest, drawables, vector assets, and basic values).
- **Design Principle:** Always consider a mobile-first approach to UI styling and layouts, ensuring touch targets are accurate, responsive, and intuitive for native mobile usage.
- **Material Design:** Strictly utilize Material Design 3 (M3) components (`androidx.compose.material3.*`) to ensure a clean, native experience.
- **Composables & Recomposition:** Keep composable functions highly focused and modular. Prevent unnecessary recompositions by ensuring StateFlow updates are targeted.
- **State Hoisting:** Prefer state hoisting for UI components to keep them stateless. **Never** perform heavy calculations, object allocations, I/O, or file operations directly within composable functions.

## 3. Code Quality & Performance Optimization

- **Language:** Kotlin is the exclusive programming language.
- **Asynchronous Operations:** Use Kotlin Coroutines and Flows (`StateFlow`/`SharedFlow`) for all asynchronous programming and state observation.
- **Null Safety & Crash Prevention:** Handle nullable types safely and exhaustively. **Never** use the not-null assertion operator (`!!`). Catch specific exceptions rather than generic `Exception` where possible, and ensure errors are pushed to the UI state rather than silently crashing the app.
- **Eliminate Bottlenecks:**
  - **Disk I/O:** Always dispatch database, MediaStore queries, or file reading/writing operations to `Dispatchers.IO`. Never hit the file system (e.g., `File.lastModified()`) on the Main thread or inside loop iterations that block rendering.
  - **Memory Management:** Be aggressive about optimizing memory. Avoid unnecessary object allocations (like compressing/decompressing Bitmaps back to ByteArrays unnecessarily).
  - **Concurrency:** Ensure tasks like generating thumbnails or parsing media files are properly parallelized using appropriately sized thread pools, avoiding single-thread bottlenecks.

## 4. Documentation & Update Tracking

You must actively maintain the project's changelog. After every completed task, error resolution, or feature addition, you must append an entry to the `update_details.md` file.

**Format and Rules for `update_details.md`:**

- Do NOT read or rewrite the whole file every time. Simply append the new data at the very end of the document.
- Include a Date and Time stamp for the update.
- **Type of Details:** (e.g., Error Solving, New Update, Refactor, Performance Improvement, UI Polish).
- **Description:** A brief, clear summary of what was changed, fixed, or added in the recent interaction.
- After the details of the latest update, you must append exactly `---` on a new line to close out that specific session.
- Do not include any conversational filler in the file.

## 5. Version Control (Git) Protocol

- **Do not commit or push** any changes to the repository until explicitly being asked to do so by the developer.

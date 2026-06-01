# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

Otto Launcher is a minimal Android home screen replacement for quickly searching and launching apps. It includes Trace, a local-only personal evidence layer for low-friction food/drink photo capture, weight logging, sleep confirmation, and weekly coverage summaries.

## Build & Run

```bash
./gradlew build              # Build the project
./gradlew installDebug       # Install debug APK to connected device/emulator
./gradlew test               # Run unit tests
./gradlew connectedTest      # Run instrumented/UI tests
./gradlew clean              # Clean build artifacts
```

Requires Android SDK 34+ and JDK 17. The Gradle wrapper (9.0-milestone-1) is included.

A `.env` file at the repo root must contain `GROQ_API_KEY=...` — Gradle reads it at build time and injects it into `BuildConfig`. Voice features degrade gracefully without it (falls back to fuzzy matching).

## Architecture

Single-activity Kotlin app using Jetpack Compose with Material 3 (dark theme only).

The launcher shell still lives mostly in `app/src/main/java/com/otto/launcher/MainActivity.kt`. This includes:
- `LauncherScreen()` — root composable with all UI state
- App discovery and filtering via `PackageManager` (allowlist for system apps, blacklist for problematic packages, explicit WebAPK inclusion)
- Manual search (case-insensitive substring, 3+ char threshold)
- Gesture system: single/double tap detection via `pointerInput()` with 400ms timeout window
- `VoiceTranscriptionManager` — MediaRecorder → Groq Whisper API
- `VoiceLaunchAgent` — Groq LLaMA chat completion to resolve spoken intent to an app
- Fuzzy matching via Levenshtein distance (fallback when AI agent fails)

Trace lives under `app/src/main/java/com/otto/launcher/trace/`:
- `data/` — Room database, DAO, repository, private media storage, preferences
- `domain/` — trace enums, summaries, next-action rules, sleep-estimate rules
- `ui/` — Compose strip, capture sheet, dialogs, CameraX overlay, ViewModel

**Theme files** in `app/src/main/java/com/otto/launcher/ui/theme/` define the Lilac/Charcoal/Graphite color palette.

## Key Design Decisions

- **Launcher shell stays compact**: app search/policy/updater code remains in the launcher shell; Trace is split into data/domain/UI packages because it has persistence, media, and tests.
- **Launcher shell state stays simple**: existing launcher controls use `mutableStateOf()` and `rememberSaveable()`.
- **Trace uses Room + Flow + ViewModel**: Trace summaries are derived from local Room events and emitted into Compose through `TraceViewModel`.
- **Coroutine-based I/O**: all Groq API calls run on `Dispatchers.IO` via `rememberCoroutineScope()`.
- **Graceful degradation**: voice features require Groq API key; without it, search and fuzzy match still work.
- **App filtering**: system apps need explicit allowlist entry; WebAPKs (Chrome PWAs) are always shown.
- **Trace stays local-first**: food/drink photos are copied into app-private storage; Photo Picker import is used instead of broad media-library permissions.

## Deployment Rules

- **ALWAYS bump the version** when deploying to the phone (`./gradlew installDebug`). Increment `versionCode` by 1 and update `versionName` in `app/build.gradle.kts`.

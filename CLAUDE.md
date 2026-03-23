# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

Otto Launcher is a minimal Android home screen replacement for quickly searching and launching apps. It integrates with Groq AI for voice transcription (Whisper) and intelligent app selection (LLaMA), with fuzzy-match fallback.

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

**Nearly all logic lives in `app/src/main/java/com/otto/launcher/MainActivity.kt`** (~960 lines). This includes:
- `LauncherScreen()` — root composable with all UI state
- App discovery and filtering via `PackageManager` (allowlist for system apps, blacklist for problematic packages, explicit WebAPK inclusion)
- Manual search (case-insensitive substring, 3+ char threshold)
- Gesture system: single/double/triple tap detection via `pointerInput()` with 400ms timeout window
- `VoiceTranscriptionManager` — MediaRecorder → Groq Whisper API
- `VoiceLaunchAgent` — Groq LLaMA chat completion to resolve spoken intent to an app
- Fuzzy matching via Levenshtein distance (fallback when AI agent fails)

**Theme files** in `app/src/main/java/com/otto/launcher/ui/theme/` define the Lilac/Charcoal/Graphite color palette.

## Key Design Decisions

- **Monolithic by design**: co-locating UI, state, and business logic in one file keeps the ~1000-line app simple and state flow transparent. Split if scope grows.
- **Compose state only**: `mutableStateOf()` and `rememberSaveable()` — no LiveData, Flow, or ViewModel.
- **Coroutine-based I/O**: all Groq API calls run on `Dispatchers.IO` via `rememberCoroutineScope()`.
- **Graceful degradation**: voice features require Groq API key; without it, search and fuzzy match still work.
- **App filtering**: system apps need explicit allowlist entry; WebAPKs (Chrome PWAs) are always shown.

## Deployment Rules

- **ALWAYS bump the version** when deploying to the phone (`./gradlew installDebug`). Increment `versionCode` by 1 and update `versionName` in `app/build.gradle.kts`.

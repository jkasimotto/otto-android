# Otto Launcher

A minimal Android home screen replacement focused on quickly searching and launching installed apps, with a launcher-native Trace layer for quiet personal evidence capture.

## Features
- Launcher intent-filter so it can be set as the default home app
- Jetpack Compose UI with Material 3 styling locked to a minimal dark palette
- Manual search filters as-you-type (3+ chars) across labels + package names
- Trace strip on the home screen for sleep, weight, food count, and eating-window state
- Contextual double-tap capture: food camera during meal windows, sleep/weight in the morning, capture sheet otherwise
- Private food/drink photo capture with CameraX, plus Android Photo Picker import without broad media-library access
- Manual weight and sleep logging with coverage-based weekly summaries and neutral copy
- Version label baked into the UI so you can confirm the running build

## Project structure
- `app/src/main/java/com/otto/launcher/MainActivity.kt` – Launcher shell, search logic, app launching, and Trace entrypoints
- `app/src/main/java/com/otto/launcher/trace` – Trace persistence, rules, summaries, and Compose surfaces
- `app/src/main/java/com/otto/launcher/ui/theme` – Simple Material theme
- `app/src/main/res` – Launcher icons, strings, and themes

## Building & running
1. Open the project in Android Studio Flamingo/ newer and let it sync dependencies. Studio will prompt you to generate a Gradle wrapper if one is missing – accept the prompt or run `gradle wrapper` from the terminal if you have Gradle 8.2+ installed.
2. Ensure the `.env` file at the repo root contains `GROQ_API_KEY=...` (already copied from `pad/.env`). Gradle injects it into `BuildConfig` so voice transcription + the Groq agent can run.
3. Use the **app** run configuration to deploy to an emulator or device (Android 8.0 / API 26+ required). Approve camera permission the first time you capture a Trace photo; microphone permission is still required for voice features if they are re-enabled.
4. After installation, press the home button and choose **Otto Launcher** as the default home app to test the experience.

## Releasing
- Run `./scripts/release.sh` to publish the version already set in `app/build.gradle.kts`.
- Run `./scripts/release.sh 1.22` to bump to `versionName = "1.22"`, increment `versionCode`, build `app-debug.apk`, push the current branch, tag `v1.22`, and create the GitHub release.
- The release asset must stay named `app-debug.apk` because the in-app updater downloads that exact filename from the latest GitHub release.

## Next ideas
- Persist custom ordering or pinned apps
- Add keyboard shortcuts / richer agent prompting and context
- Support widgets or frequently used app rows
- Health Connect import for sleep and weight
- UsageStats-based sleep estimates beyond launcher-observed inactive gaps

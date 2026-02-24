# Otto Launcher

A minimal Android home screen replacement focused on quickly searching and launching installed apps. The first build contains a single search field and a filtered list of launcher activities.

## Features
- Launcher intent-filter so it can be set as the default home app
- Jetpack Compose UI with Material 3 styling locked to a minimal dark palette
- Manual search filters as-you-type (3+ chars) across labels + package names
- Double-tap launches your pinned "Pad" WebAPK instantly, while a quick triple tap (double + extra tap) enters hands-free voice search that records, transcribes via Groq Whisper, and asks a Groq agent which app to launch (with fuzzy fallback)
- Version label baked into the UI so you can confirm the running build

## Project structure
- `app/src/main/java/com/otto/launcher/MainActivity.kt` – Compose UI, search logic, and app launching
- `app/src/main/java/com/otto/launcher/ui/theme` – Simple Material theme
- `app/src/main/res` – Launcher icons, strings, and themes

## Building & running
1. Open the project in Android Studio Flamingo/ newer and let it sync dependencies. Studio will prompt you to generate a Gradle wrapper if one is missing – accept the prompt or run `gradle wrapper` from the terminal if you have Gradle 8.2+ installed.
2. Ensure the `.env` file at the repo root contains `GROQ_API_KEY=...` (already copied from `pad/.env`). Gradle injects it into `BuildConfig` so voice transcription + the Groq agent can run.
3. Use the **app** run configuration to deploy to an emulator or device (Android 8.0 / API 26+ required). Approve the microphone permission the first time you double-tap for voice.
4. After installation, press the home button and choose **Otto Launcher** as the default home app to test the experience.

## Next ideas
- Persist custom ordering or pinned apps
- Add keyboard shortcuts / richer agent prompting and context
- Support widgets or frequently used app rows

# Deploying Otto for someone else

Otto is a personal launcher. It runs fine on any stock phone, but its headline blocking features
need device-owner provisioning, and the in-app updater/feedback are tied to one GitHub repo. This
guide covers what a new person needs to run their own copy.

## What you get without any setup

Set Otto as the home app and these work immediately, no permissions or provisioning required:

- App search and launch
- Trace (food/drink photos, weight, sleep, voice memos, weekly summaries)
- The home screen, phone-usage chart, sleep chart, and weather row (weather needs location permission)

Everything in the **blocking layer is a no-op until the app is a device owner** (see below):
app blocking, timed lockdown, Slack/browser after-hours gating, greyscale-by-policy, the website
DNS VPN, and lock-task. None of it crashes when not provisioned; it simply does nothing.

## Optional keys (`.env`)

Copy `.env.example` to `.env`. All values are optional:

| Key | Enables | Without it |
| --- | --- | --- |
| `GROQ_API_KEY` | Voice transcription + voice launch agent | Voice memos stay queued; launch falls back to fuzzy match |
| `GITHUB_FEEDBACK_TOKEN` | In-app "Send feedback" → GitHub issue | Feedback is disabled |
| `OTTO_GITHUB_REPO` | Repo (owner/name) the updater + feedback target | Defaults to `jkasimotto/otto-android` |

> **Security:** every `.env` value is compiled into the APK as plaintext and is extractable from
> any build you hand out. Never attach an APK built with real keys to a public release. Keep keyed
> builds private, or ship public builds with these blank.

## Build requirements

- Android SDK 34+, JDK 17, the bundled Gradle wrapper
- `minSdk` 26, `targetSdk`/`compileSdk` 34

```bash
./gradlew installDebug   # build + install to a connected device
./gradlew test           # unit tests
```

## Becoming a device owner (enables blocking)

Device owner is a one-time, factory-reset-gated step. It is what lets Otto suspend/hide apps, run
lockdown, gate Slack/browsers, and set greyscale.

1. **Factory reset** the phone (device owner can only be set on a device with no added accounts).
2. During setup, **do not add a Google account** (skip sign-in). Connect to Wi-Fi.
3. Install Otto: `adb install app/build/outputs/apk/debug/app-debug.apk`
4. Set it as device owner:
   ```bash
   adb shell dpm set-device-owner com.otto.launcher/.OttoDeviceAdminReceiver
   ```
   This must succeed before any account is added; otherwise it fails with "not allowed to set the
   device owner".
5. Set Otto as the home app (Settings → Default apps → Home, or the home-button chooser).

To undo: `adb shell dpm remove-active-admin com.otto.launcher/.OttoDeviceAdminReceiver`, or factory
reset.

## Releasing / the in-app updater

`scripts/release.sh` builds `app-debug.apk`, pushes the branch, tags `v<version>`, and creates a
GitHub release with the APK attached. The in-app updater downloads that exact `app-debug.apk` from
the latest release of `OTTO_GITHUB_REPO`.

Two caveats for a fork:

- **Signing:** the updater installs whatever APK is on the release. Android refuses to update an app
  with a different signing key, so a self-builder must release builds signed with their own keystore
  consistently. A debug-signed APK as the release asset is fine for personal use but is not a real
  distribution path.
- **Account:** publishing requires write access (`gh`) to `OTTO_GITHUB_REPO`.

## Personalization points (currently source constants)

These encode the original owner's schedule and habits. They are not yet runtime-configurable; a fork
edits them in source. Locate by name, not line number.

- Work hours + after-hours gate: `OttoPolicyController` — `TIME_GATE_START_HOUR_INCLUSIVE`,
  `TIME_GATE_END_HOUR_EXCLUSIVE`, `slackGateRule`, the browser gate, `hardBlockedAppPackages`,
  `lockdownAllowedHints`.
- Website blocklist + news rate-limits + night-YouTube: `OttoDnsVpnService`.
- "Critical people" always-reachable apps: `CriticalPeoplePolicyRepair.CRITICAL_PEOPLE_PACKAGES`.
- Default app-tier hints: `AppPolicyEngine` / `DefaultAppPolicyCatalog`.
- Meal windows: `TraceDomain`.
- Hidden secret-notes app label: `TRIPLE_TAP_APP_LABEL` in `MainActivity`.

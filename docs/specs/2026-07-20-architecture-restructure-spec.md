# Architecture Restructure Spec

Date: 2026-07-20
Status: approved for implementation
Baseline commit: `1ef7ba6` (feat: implement voice-driven quest system), clean working tree.

## 1. Goal

Break up the `MainActivity.kt` god object, consolidate six hand-rolled Groq clients into one, fix the dependency arrows that point the wrong way, and add machine enforcement (Konsist + detekt) plus agent-facing docs so the boundaries stay fixed.

This is a pure structural refactor. **Zero behavior change** except the small, explicitly listed deltas in section 6.4. The app on the phone must work identically before and after.

**Non-goals** (do not touch): resolving the trace V1/V2 migration, restructuring `nag/` into data/domain/ui, converting to a multi-module Gradle build, any new features, any UI changes, any deployment. See section 13.

## 2. Hard safety constraints

Read these before writing any code. Violating any of them can brick the user's phone (this app is the active launcher and device owner on a real device).

1. **Manifest-frozen classes.** Every component declared in `AndroidManifest.xml` keeps its exact fully qualified class name. Moving or renaming any of these is forbidden:
   - `com.otto.launcher.OttoApp`
   - `com.otto.launcher.MainActivity`
   - `com.otto.launcher.voice.QuickVoiceCaptureActivity`
   - `com.otto.launcher.OttoDeviceAdminReceiver` (active device-owner binding; changing its component name cannot be undone without a factory reset)
   - `com.otto.launcher.OttoPolicyEventsReceiver`
   - `com.otto.launcher.OttoDnsVpnService`
   - `com.otto.launcher.OttoPackageInstallReceiver`
   - `com.otto.launcher.nag.NagAnswerActivity`
   - `com.otto.launcher.nag.NagDebugReceiver`
   - `com.otto.launcher.quest.ui.QuestActivity`
2. **Worker classes are frozen.** WorkManager persists worker class names in its own database. Any `Worker`/`CoroutineWorker` subclass keeps its FQCN: `worker/PolicyRestoreWorker`, `nag/NagWorker`, and any worker in `quest/`. Enqueued work must survive the refactor.
3. **Room database:** the database file name `"trace.db"`, all entity/table definitions, all migrations, and all type converters stay byte-identical. Renaming the `TraceDatabase` *class* is allowed (section 7); changing what it builds is not.
4. **SharedPreferences file names and keys are frozen** everywhere (`otto_secret_notes`, nag store, quest focus, policy controller, mode repository, trace preferences, DNS service). Moving the classes that read them is fine; the string constants must not change.
5. **Enum names stored via Room converters must not be renamed.** General rule: the only class rename in this entire spec is `TraceDatabase` to `OttoDatabase`.
6. **Secrets model is untouched.** `BuildConfig` fields, `.env` loading, and the embedded-key tradeoff stay exactly as they are. Do not "fix" this.
7. **Never add any code path that posts transcripts or their summaries to GitHub.** `FeedbackSubmitter` moves but its behavior does not change.
8. **Do not deploy.** No `installDebug`, no version bump, no release. The user deploys.
9. **No dependency version bumps** except adding detekt and Konsist (section 10).
10. Prompts, models, and temperatures of every Groq call stay character-identical unless section 6.4 says otherwise.

## 3. Definition of green

Create `scripts/check.sh` (executable):

```bash
#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew --console=plain detekt testDebugUnitTest assembleDebug
```

Green means `scripts/check.sh` exits 0. Konsist rules run as unit tests, so they are inside `testDebugUnitTest`. Every commit in this refactor must be green. Work until it is.

## 4. Target package layout

```
com/otto/launcher/
  OttoApp.kt, MainActivity.kt                  (frozen manifest classes; MainActivity slimmed)
  OttoDeviceAdminReceiver, OttoPolicyEventsReceiver,
  OttoDnsVpnService, OttoPackageInstallReceiver.kt,
  OttoDiagnostics, ProcessingOverlay           (root stays only because the manifest pins it)
  core/
    config/OttoConfig.kt                       (from MainActivity)
    http/HttpClientProvider.kt                 (from MainActivity)
    llm/GroqClient.kt, GroqResponseParser.kt   (new; replaces all six copies)
    db/OttoDatabase.kt, OttoConverters.kt      (from trace/data/TraceDatabase.kt)
  apps/
    AppInfo.kt, AppCatalog.kt, AppActions.kt,
    FuzzyAppMatcher.kt, VoiceLaunchAgent.kt    (from MainActivity)
  voice/
    VoiceRecorder.kt, QuickVoiceCaptureActivity.kt, AssistantRegistration.kt (existing)
    VoiceTranscriptionManager.kt, AudioChunker.kt,
    ReminderExtractionAgent.kt, UseCaseExtractionAgent.kt (from MainActivity)
  updater/OttoUpdater.kt                       (from MainActivity)
  feedback/FeedbackSubmitter.kt                (from MainActivity)
  notes/SecretNoteStore.kt                     (from MainActivity)
  guard/
    OttoPolicyController.kt                    (from root)
    DeviceOwnerController.kt                   (from device/)
    GreyscaleController.kt                     (extracted from MainActivity)
  domain/                                      (unchanged: pure shared kernel)
  data/                                        (unchanged: goals, health, mode, policy, time, usage, weather)
  trace/  data|domain|ui                       (unchanged shape; loses the database class)
  quest/  data|domain|llm|ui                   (unchanged shape; llm shrinks onto core/llm)
  nag/                                         (unchanged; call sites migrate to core/llm)
  ui/     capture, home, review, settings, theme, time
          + home/LauncherScreen.kt, home/LauncherComponents.kt (from MainActivity)
  worker/                                      (unchanged, frozen)
```

## 5. The boundary rules

These are the rules the Konsist tests in section 10 encode. They are the contract of the codebase:

> Domain decides what is true, in pure Kotlin.
> Data persists it and talks to the outside world.
> Core serves every feature and knows none of them.
> Features own their slice and never touch each other or the shell.
> The shell only wires; nothing imports the shell.

Concretely:

- `domain/**` and `*/domain/**`: no `android.*`, no `okhttp3`, no Room, no Compose. Pure Kotlin plus `java.*`/`kotlin.*`.
- `core/**` imports only `core/**` and stdlib/libraries. **One documented exception:** `core/db` may import `trace.data` and `quest.data` entities, DAOs, and converters, because Room requires the `@Database` class to reference every entity. No other core package may import a feature.
- Feature slices (`trace/`, `quest/`, `nag/`, `apps/`, `voice/`, `updater/`, `feedback/`, `notes/`, `guard/`) may import `core/**`, `domain/**`, `data/**`, and their own slice. They may not import each other or anything in the root package.
- Files containing `@Composable` functions may not import `*.data.*` packages. ViewModels are the bridge and may import data. (Exemption list at introduction time: `LauncherViewModel`, `TraceViewModel`, plus any `*ViewModel.kt`.)
- Nothing outside the root package imports root-package classes. The root is wiring only.

## 6. Workstream A: core extraction and Groq consolidation

### 6.1 Moves

- `OttoConfig` (MainActivity.kt:2666) moves verbatim to `core/config/OttoConfig.kt`, stays `internal object`.
- `HttpClientProvider` (MainActivity.kt:2691) moves to `core/http/HttpClientProvider.kt`, becomes `internal object` (it is currently `private`).

### 6.2 New `core/llm/GroqClient.kt`

```kotlin
internal object GroqClient {
    const val DEFAULT_MODEL = "llama-3.1-8b-instant"

    /**
     * One Groq chat completion returning the reply parsed as a JSON object.
     * Fails (never throws) when the key is missing, the HTTP call fails, or the
     * reply contains no parseable JSON object. Every caller keeps its own fallback.
     */
    suspend fun chatJson(
        system: String,
        user: String,
        model: String = DEFAULT_MODEL,
        temperature: Double = 0.0,
    ): Result<JSONObject>

    /** Uploads one audio file (<= ~20MB) to Whisper. Chunking stays in voice/AudioChunker. */
    suspend fun transcribeChunk(file: File): Result<String>
}
```

Implementation requirements:

- Runs on `Dispatchers.IO`, uses `HttpClientProvider.client`, reads the key from `OttoConfig.groqApiKey`, returns `Result.failure` when blank.
- Always sends `response_format: {"type": "json_object"}`.
- Endpoint constants live here and nowhere else: `https://api.groq.com/openai/v1/chat/completions` and `https://api.groq.com/openai/v1/audio/transcriptions`. Whisper model stays `whisper-large-v3-turbo`, multipart form identical to the current `uploadChunk` (MainActivity.kt:2446).
- Parsing goes through `core/llm/GroqResponseParser.kt`, pure internal functions so they are unit-testable without network:
  - `parseChatContent(body: String): String?` extracts `choices[0].message.content`.
  - `extractJsonObject(content: String): JSONObject?` does the lenient first-`{` to last-`}` extraction the current agents use, then parses.

### 6.3 Call-site migration (all six, no survivors)

After this workstream, `grep -rn "api.groq.com" app/src/main` must return exactly the two constants in `GroqClient.kt`.

| Current site | Migration |
|---|---|
| `VoiceLaunchAgent` (MainActivity.kt:2568) | Moves to `apps/VoiceLaunchAgent.kt`, calls `chatJson(temperature = 0.15)`. Keeps its prompt, `MAX_APP_SAMPLE`, `parsePackageFromAgent`, and the rule that any failure returns the fuzzy-match fallback as `Result.success`. |
| `ReminderExtractionAgent` (MainActivity.kt:2754) | Moves to `voice/ReminderExtractionAgent.kt`, calls `chatJson()`. Keeps prompt and `parseTasks`; any failure still yields `emptyList()`. |
| `UseCaseExtractionAgent` (MainActivity.kt:2836) | Moves to `voice/UseCaseExtractionAgent.kt`, same treatment. `UseCaseCandidate` is defined elsewhere; find its definition and leave it with its owning feature. Only the agent class moves. |
| `nag/NagJudge.kt` | Deletes its private client/URL/model constants, calls `chatJson(system, user)`. `NagVerdict` and its parse stay. |
| `nag/NagSleepRecorder.kt` | Same treatment as NagJudge. |
| `quest/llm/QuestLlm.kt` | Delete the private `GroqQuestClient` object; `QuestClassifier`, `QuestDrafter`, `QuestStepPlanner`, `QuestEndJudge` call `GroqClient.chatJson` directly. All prompts, parse logic, and `fallback()` functions stay identical. Import of `com.otto.launcher.OttoConfig` becomes `com.otto.launcher.core.config.OttoConfig` (or disappears entirely, since the client owns the key check). |
| `VoiceTranscriptionManager` (MainActivity.kt:2404) | Moves to `voice/VoiceTranscriptionManager.kt`. `splitAudioForUpload` (MainActivity.kt:2492) moves to `voice/AudioChunker.kt`. Upload goes through `GroqClient.transcribeChunk`; chunk-size constant, chunk stitching, and delete-after semantics stay identical. Keep the existing doc comments about the 413 limit; they record hard-won knowledge. |

### 6.4 Accepted behavior deltas (the complete list)

1. All chat calls share one OkHttp client with a 60s call timeout. NagJudge was 30s, QuestLlm was 45s. Accepted.
2. `VoiceLaunchAgent` now sends `response_format: json_object` (it previously sent none). Its lenient parse and fuzzy fallback make this safe. Accepted.

Nothing else may change observably. If you find yourself creating a third delta, stop and leave that call site closer to its original form.

### 6.5 Tests (new)

In `app/src/test/.../core/llm/GroqResponseParserTest.kt`: valid body, body with prose around the JSON, malformed JSON, empty choices array, blank content. Also add direct unit tests for `QuestClassifier.fallback`, `QuestDrafter.fallback`, `QuestStepPlanner.fallback` if not already covered. Do not write tests that hit the network, and do not test paths that depend on whether `.env` holds a real key.

## 7. Workstream B: database ownership

- Move `trace/data/TraceDatabase.kt` to `core/db/OttoDatabase.kt` and rename the class `TraceDatabase` to `OttoDatabase`. The builder keeps `"trace.db"` and every migration unchanged.
- Move `TraceConverters` into `core/db` (rename to `OttoConverters` is allowed since converter class names are not persisted; keep it if renaming causes churn).
- Update every `TraceDatabase.get(...)` call site (`TraceRepository`, `TraceV2Repository`, `QuestRepository`, others found by grep).
- Trace DAOs and entities stay in `trace/data`; quest DAOs and entities stay in `quest/data`. `core/db` imports them under the documented exception in section 5.

## 8. Workstream C: MainActivity slimming

Everything below is a mechanical move: cut the code, place it in the target file, fix imports, adjust visibility from `private` to `internal` only where cross-package access now requires it. Do not rewrite logic while moving it.

| What (current location in MainActivity.kt) | Target |
|---|---|
| `OttoUpdater` (:2914), `currentVersionName` (:2554) | `updater/OttoUpdater.kt` |
| `OttoPackageInstallReceiver` (:2978) | Own file `OttoPackageInstallReceiver.kt`, **root package unchanged** (manifest-frozen) |
| `FeedbackSubmitter` (:2704) | `feedback/FeedbackSubmitter.kt` |
| `SecretNoteStore` (:3050) plus its constants | `notes/SecretNoteStore.kt` (prefs name/keys frozen) |
| `AppInfo`, `loadLauncherApps`, `buildLauncherApps`, `queryActivities`, `shouldDisplayApp`, `injectHiddenGatedApps`, `ALLOWED_SYSTEM_PACKAGES` (:3013), `ALLOWED_SYSTEM_LABEL_KEYWORDS` | `apps/AppInfo.kt`, `apps/AppCatalog.kt` |
| `launchApp`, `openAppInfo`, `openSystemSettings` | `apps/AppActions.kt` |
| `fuzzyMatchApps`, `similarityScore`, `levenshteinDistance`, `findPreferredApp` | `apps/FuzzyAppMatcher.kt` |
| Greyscale: `isGreyscaleEnabled`, `setGreyscaleEnabled`, `setGreyscaleViaDevicePolicy`, `setGreyscaleViaSecureSettings`, `hasGreyscaleState`, `openGreyscaleSettings`, daltonizer constants (:3036) | `guard/GreyscaleController.kt` |
| `LauncherScreen` composable (:229) | `ui/home/LauncherScreen.kt` |
| `QuickActionRow`, `QuickActionIcon`, `QuickActionChip`, `AppRow`, `MinimalSearchField`, `VoiceControlChip` | `ui/home/LauncherComponents.kt` |
| Gesture/misc constants (`TRIPLE_TAP_WINDOW_MS`, `DISTRACTION_GATE_*`, `newDistractionGateCode`, `PROCESSING_*`, `formatMinutesHuman`) | With whichever file uses them; distraction-gate helpers to `guard/` if only policy uses them, otherwise alongside their caller |

Also move (separate files, not inside MainActivity.kt today):

- `OttoPolicyController.kt` (root) to `guard/OttoPolicyController.kt`. It is an `object`, not manifest-declared, so the move is safe. Prefs names frozen.
- `device/DeviceOwnerController.kt` to `guard/DeviceOwnerController.kt`; delete the empty `device/` package.
- `OttoDnsVpnService`, `OttoDeviceAdminReceiver`, `OttoPolicyEventsReceiver` **stay in the root package** (manifest-frozen) even though they belong conceptually to `guard/`. Note this in the guard AGENTS.md.

`LauncherScreen` guidance: this is the riskiest move. Keep the composable's body and its 85 state hooks intact while relocating; MainActivity passes callbacks/lambdas exactly as before. Do not attempt a state-holder refactor in this spec. If a child composable extraction forces threading more than ~5 new parameters, leave that child where it is and note it.

End-state size targets: `MainActivity.kt` at most 900 lines and containing only the `MainActivity` class plus private helpers it alone uses. No production file over 800 lines except the grandfathered list in section 10.

## 9. Workstream D: UI layering fixes

`ui/review/InboxReviewScreen.kt`, `ui/review/TranscriptViewerScreen.kt`, and `ui/review/FoodReviewScreen.kt` import `*.data.*` directly from composables. Fix each by the smallest mechanical means: introduce or extend a ViewModel that owns the repository/DAO access and exposes domain types plus callbacks; the composable file loses all `*.data.*` imports. Do not redesign the screens. If a screen currently passes a repository as a composable parameter, move that construction into its ViewModel.

## 10. Workstream E: enforcement

### 10.1 Detekt

- Add `io.gitlab.arturbosch.detekt` version `1.23.6` to the build.
- `config/detekt.yml`: enable defaults; set `LongMethod` threshold 80, `LongParameterList` 7, `LargeClass` 600. Disable formatting-style rules (no whitespace churn in this refactor).
- Generate `config/detekt-baseline.xml` after the moves so existing violations are grandfathered. New code meets the bar. The baseline may only shrink in future commits; say so in AGENTS.md.

### 10.2 Konsist architecture tests

- Add `testImplementation("com.lemonappdev:konsist:0.17.3")`.
- Create `app/src/test/java/com/otto/launcher/architecture/BoundariesTest.kt` encoding every rule in section 5, plus:
  - **File length:** no production `.kt` file over 800 lines, with an explicit grandfathered set (expected after refactor: `OttoPolicyController.kt`, `OttoDnsVpnService.kt`, and whatever else still exceeds it; enumerate exactly what remains). The test asserts non-grandfathered files are under the limit AND that grandfathered files do not grow beyond their recorded line counts.
  - **Root package freeze:** the root package contains only the manifest-frozen classes plus `OttoDiagnostics` and `ProcessingOverlay`. Adding a new root-package file fails the test.
  - **Groq confinement:** no file outside `core/llm` contains the string `api.groq.com` or builds a `Request` against a Groq URL.
- These run in `testDebugUnitTest`, so they are part of green.

### 10.3 Pre-commit hook

- `.githooks/pre-commit` runs `./gradlew --console=plain detekt`.
- `scripts/install-git-hooks.sh` sets `git config core.hooksPath .githooks`.
- Full gate remains `scripts/check.sh`; the hook is just the fast line of defense.

## 11. Workstream F: documentation

### 11.1 Root constitution

- Create root `AGENTS.md` as the source of truth; replace `CLAUDE.md` with a symlink to it (`ln -s AGENTS.md CLAUDE.md`). Merge the useful content of the current CLAUDE.md (build commands, key design decisions, deployment rules) into it.
- Required sections, in order: What This Is; How We Decide (prefer named industry conventions; the bar for bespoke patterns is high); Architecture (package map plus the five-line boundary contract from section 5, verbatim); Designing A Feature (the five ordered gates: what are we building, what already exists, what data, what are the units in that data, what is the minimum place this can live); Commands (`scripts/check.sh` is the done gate); Enforcement (detekt baseline shrinks only, Konsist rules are the law); Deployment Rules (version bump on install, user deploys); Parallel Agents (stage explicit paths only, never `git add -A`, never touch another agent's work).

### 11.2 Per-module guides

Create an `AGENTS.md` (with `CLAUDE.md` symlink) in: `core/`, `apps/`, `voice/`, `guard/`, `trace/`, `quest/`, `nag/`, `domain/`, `ui/`. Each is at most 40 lines: the module's one job, what it may import, and dated provisional decisions. Required provisional-decision notes:

- `trace/`: the V1/V2 duplication is a half-finished migration; decide finish-or-delete separately; do not build new features on V1.
- `core/`: the `core/db` exception (imports feature entities because Room requires it); revisit if a third feature adds tables.
- `guard/`: the VPN service and both receivers live in the root package solely because the manifest and the device-owner binding pin them there.
- `nag/`: flat on purpose for now; split into data/domain/ui when it next grows.

## 12. Sequencing and commits

Execute workstreams in order A, B, C, D, E, F. Each workstream is one to three atomic commits. Every commit passes `scripts/check.sh` (create the script first, in its own commit, before workstream A). Konsist tests land in E but write them against the *target* layout; if you prefer, land them earlier with grandfathered exemptions and burn the exemptions down as you go.

Commit style: short, direct, specific. Prose paragraphs in the body are single long lines, never hard-wrapped. Stage explicit paths only.

Existing unit tests may only receive mechanical import updates. If a test needs a logic change to pass, you broke behavior; go back.

## 13. Out of scope, recorded for later

- Trace V1/V2 migration resolution (needs a product decision).
- `nag/` restructure into data/domain/ui.
- Multi-module Gradle build. Revisit trigger: Konsist rules being fought regularly, or a second contributor.
- `LauncherScreen` state-holder refactor and decomposition of its 85 state hooks.
- `HomeScreenV2` naming (there is no V1; rename is churn until the next real change touches it).
- Backfilling repository tests and the empty `androidTest/` suite.

## 14. Acceptance checklist

- [ ] `scripts/check.sh` green.
- [ ] `grep -rn "api.groq.com" app/src/main` returns exactly the two constants in `core/llm/GroqClient.kt`.
- [ ] `grep -rn "chat/completions" app/src/main` returns exactly one file.
- [ ] `MainActivity.kt` is at most 900 lines and defines only `MainActivity` plus private helpers.
- [ ] Every class listed in section 2.1 and 2.2 is at its original FQCN (`git log --follow` shows no moves for them; verify against the manifest).
- [ ] `AndroidManifest.xml` is unchanged.
- [ ] `quest/` and `nag/` no longer import anything from the root package.
- [ ] The three `ui/review/` screens have no `*.data.*` imports in composable files.
- [ ] Konsist `BoundariesTest` encodes every section 5 rule and passes.
- [ ] `config/detekt-baseline.xml` exists and CI-gate (`scripts/check.sh`) runs detekt.
- [ ] Root `AGENTS.md` + symlinked `CLAUDE.md`, and all nine module guides exist.
- [ ] All pre-existing unit tests pass with at most import-line changes.
- [ ] No version bump, no deploy, no dependency updates beyond detekt and Konsist.

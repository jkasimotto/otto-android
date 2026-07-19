# Quest System — Implementation Spec

Status: ready to build. Audience: the coding agent implementing this. Product rationale lives in the vision doc (tangent: `otto/launcher/2026-07-20-quest-system-product-vision`); this file is self-contained for implementation.

Do not treat class names below as mandatory. They describe responsibilities and data shapes. Match the codebase's existing conventions. File paths are recommendations grounded in the current structure.

---

## 1. What we're building

A summon-only, voice-driven task system that replaces a todo list. The user never sees a backlog list. They speak a request that carries their current constraints ("I've got 15 minutes, I'm at home, give me a quest"), and Otto drafts **three** candidate quests as coloured cards (roguelite draft, Slay-the-Spire style). The user picks one by voice or tap, a smooth animated transition reveals an ordered step-by-step breakdown plus the apps needed, and the phone narrows to just those apps until the quest is finished. Completion and deferral are voice-only; the spoken reason for deferring is stored and used to make future drafts smarter.

Importance is **revealed, not declared**: the user never ranks anything. Obligation-vs-wish classification plus deferral history drive ranking.

This spec covers the quest system only. The novel-domain focus firewall is a separate feature with its own spec.

### Design contract: the boring-phone exception

The launcher is greyscale, dark, minimal on purpose (see `ui/theme/Color.kt`: everything is black/graphite/silver/white, no accent). The quest draft and reveal are the **single deliberate exception**: vivid colour, animation, a dopamine payoff. Keep that contrast sharp. Vivid colour appears **only** inside the quest surface, never leaks into the launcher.

---

## 2. Architecture and placement

New package: `com.otto.launcher.quest`, mirroring how `com.otto.launcher.nag` is organised.

Suggested layout:

```
quest/
  data/        QuestEntity, QuestStepEntity, QuestEventEntity, QuestDao, QuestRepository
  domain/      Quest enums (kind, place, status, event type), ranking + filter rules
  llm/         QuestClassifier, QuestDrafter, QuestStepPlanner (Groq calls)
  ui/          QuestActivity (full-screen, vivid theme), draft screen, in-progress screen, capture entry
  QuestFocus   applies/restores the quest-scoped app lockdown
  QuestKickoff scheduler + worker for the morning nag on-ramp
```

### Reuse map (do not rebuild these)

| Need | Reuse | File |
| --- | --- | --- |
| Room persistence | `TraceDatabase` (single app DB `trace.db`), add entities + a `questDao()`, bump version 5→6 with a `MIGRATION_5_6` | `trace/data/TraceDatabase.kt` |
| Type converters (Instant, LocalTime, enums) | `TraceConverters`; add converters for new quest enums here | `trace/data/TraceDatabase.kt` |
| Voice recording | `VoiceRecorder` (m4a/AAC, `start(cacheDir)`, `stop(): File?`, `discard()`) | `voice/VoiceRecorder.kt` |
| Record + transcribe | `VoiceTranscriptionManager` (`startRecording()`, `stopRecording(): File?`, `suspend transcribe(file, deleteAfter): Result<String>`) — currently `internal` in `MainActivity.kt` line ~2402. If the quest package needs it, either move it to `voice/` or add a thin wrapper. Whisper model is `whisper-large-v3-turbo`. | `MainActivity.kt` |
| Groq key | `OttoConfig.groqApiKey` / `OttoConfig.hasGroqKey` (`internal object`, `MainActivity.kt` line ~2664) | `MainActivity.kt` |
| Structured Groq JSON call pattern | `NagJudge` is the exact template: OkHttp, `response_format: {type: json_object}`, `temperature: 0`, model `llama-3.1-8b-instant`, endpoint `https://api.groq.com/openai/v1/chat/completions`, parse `choices[0].message.content` then `JSONObject(content)` | `nag/NagJudge.kt` |
| App lockdown / suspend / lock-task allowlist / greyscale | `DeviceOwnerController` (`suspendPackages`, `hidePackage`, `setLockTaskAllowlist`, `applyGreyscale`) and `OttoPolicyController` (`startLockdown`, `applyPolicies`, `isBlockedApp`, tiers) | `device/DeviceOwnerController.kt`, `OttoPolicyController.kt` |
| App tiers (always-open set) | `AppTier` enum: `CORE, PEOPLE, UTILITY, WORK, DISTRACTION, BLOCKED, ADMIN`. Emergency/always-open = `CORE` + `PEOPLE` + `ADMIN`. Do not hardcode package names; derive the always-open allowlist from the existing tier assignments. | `domain/policy/AppTier.kt`, policy repos |
| Scheduling that survives reboot/process death | `NagScheduler` pattern (WorkManager `OneTimeWorkRequest`, unique names, `durationUntilNext(dayOfWeek, hour, minute)`) | `nag/NagScheduler.kt` |
| Full-screen alarm-style prompt over lockscreen | `NagNotifier` (full-screen intent, `IMPORTANCE_HIGH`) + `NagAnswerActivity` (`showWhenLocked`, `turnScreenOn`) are the template for the morning quest nag | `nag/NagNotifier.kt`, `nag/NagAnswerActivity.kt` |
| App startup wiring | `OttoApp.onCreate` (`armNags()` pattern) — add quest kickoff arming here | `OttoApp.kt` |

`OttoConfig` and `VoiceTranscriptionManager` are `internal` in `MainActivity.kt`. Since the quest package is in the same module they are reachable, but prefer extracting `OttoConfig` to its own top-level file and moving `VoiceTranscriptionManager` into `voice/` so both the nag and quest packages share them cleanly. The nag package already reaches `OttoConfig`, so a top-level extraction is low-risk.

---

## 3. Data model

Add to `trace.db` (bump to version 6, add `MIGRATION_5_6`, register the entities in `@Database`, add `abstract fun questDao(): QuestDao`).

### `quest` table

| Column | Type | Notes |
| --- | --- | --- |
| id | TEXT PK | uuid |
| title | TEXT | short cleaned title for the card, LLM-generated at capture |
| rawText | TEXT | the original transcript |
| kind | TEXT | `OBLIGATION` or `WISH` |
| tiedTo | TEXT? | person or party the obligation is to (e.g. "Sam"), null for wishes |
| moneyAmount | TEXT? | free text if money is involved (e.g. "$200") |
| deadline | INTEGER? | epoch millis, null if none; may be inferred |
| place | TEXT | `PHONE`, `LAPTOP`, or `PLACE_BOUND` (where it can actually be done) |
| placeNote | TEXT? | for `PLACE_BOUND`, where (e.g. "at the shops", "at home") |
| effortMinutes | INTEGER? | rough estimate; corrected by deferral reasons over time |
| urgent | INTEGER | bool; drives the morning nag |
| status | TEXT | `OPEN`, `ACTIVE`, `DONE`, `DROPPED` |
| suggestedAppsJson | TEXT? | package names chosen for the active quest, set when it goes ACTIVE |
| createdAt | INTEGER | |
| updatedAt | INTEGER | |
| lastSurfacedAt | INTEGER? | last time it appeared in a draft; used to avoid immediate repeats |

Index: `status`, `createdAt`, `deadline`.

### `quest_step` table

Ordered steps for a quest, generated at quest-time when it goes ACTIVE. Persisted so the checklist survives app death.

| Column | Type | Notes |
| --- | --- | --- |
| id | TEXT PK | |
| questId | TEXT | FK-ish |
| orderIndex | INTEGER | 0-based order |
| text | TEXT | e.g. "Log into your bank" |
| done | INTEGER | bool; steps are a read-only guide for v1, but keep the column for later tap-to-check |

Index: `questId`.

### `quest_event` table (the learning log)

Every interaction, so future drafts learn.

| Column | Type | Notes |
| --- | --- | --- |
| id | TEXT PK | |
| questId | TEXT | |
| type | TEXT | `CAPTURED`, `SURFACED`, `PICKED`, `COMPLETED`, `DEFERRED` |
| reasonText | TEXT? | the spoken reason on defer, or the completion utterance |
| createdAt | INTEGER | |

Index: `questId`, `type`.

Enums (`OBLIGATION/WISH`, `PHONE/LAPTOP/PLACE_BOUND`, `OPEN/ACTIVE/DONE/DROPPED`, event types) live in `quest/domain/`. Add `@TypeConverter`s for them in `TraceConverters`.

---

## 4. LLM calls (three, all Groq, all modelled on `NagJudge`)

Model: `llama-3.1-8b-instant`, `temperature: 0`, `response_format: {type: json_object}`, same endpoint/client/parse as `NagJudge`. Each returns a validated data class via `Result<T>`; on failure, degrade gracefully (see §10). Keep each call to one round trip.

### 4a. Classify (at capture)

Input: the raw transcript.
Output JSON:
```json
{
  "title": "Pay Sam back $200",
  "kind": "OBLIGATION",
  "tiedTo": "Sam",
  "moneyAmount": "$200",
  "deadline": null,
  "place": "PHONE",
  "placeNote": null,
  "effortMinutes": 5,
  "urgent": true
}
```
System-prompt rules to encode:
- `kind`: OBLIGATION if someone else, money, or a promised deadline depends on it; otherwise WISH.
- `place`: PHONE if fully doable on a phone; LAPTOP if it realistically needs a computer; PLACE_BOUND if it needs to be somewhere physical (set `placeNote`).
- `urgent`: true if there is a near deadline or another person is blocked/waiting.
- `effortMinutes`: a rough estimate; it is expected to be wrong and gets corrected later.

### 4b. Draft (at summon) — parse constraints AND pick, one call

Input: the raw spoken summon (e.g. "I've got 15 minutes, I'm at home, give me a quest") plus the candidate open quests (id, title, kind, place, effortMinutes, deadline, urgent, tiedTo, lastSurfacedAt, and a short dodge summary per quest).

The model does three things in one call: parse the spoken constraints (available minutes, place, device), filter to quests that fit, and return up to three ranked picks.

Output JSON:
```json
{
  "constraints": { "minutes": 15, "place": "home", "device": "phone" },
  "picks": [
    { "questId": "…", "reason": "owed to a person, dodged 4 times, fits 15 min" },
    { "questId": "…", "reason": "…" },
    { "questId": "…", "reason": "…" }
  ],
  "nearMiss": { "questId": "…", "estimateMinutes": 40, "note": "closest thing, needs ~40 min" }
}
```
Rules to encode:
- Filter out quests that cannot be done in the stated context: LAPTOP quests when the user says phone/away from laptop; PLACE_BOUND quests whose place does not match; quests whose `effortMinutes` clearly exceeds the stated window.
- Rank survivors: obligations before wishes, then nearer deadlines, then longer-dodged (older `lastSurfacedAt` / more `DEFERRED` events).
- Return 1–3 picks. If fewer than three fit, return fewer. **Never invent a quest.**
- If **nothing** fits, `picks` is empty and `nearMiss` names the closest quest with a corrected estimate. Do not force a bad fit.

The app then records a `SURFACED` event and updates `lastSurfacedAt` for each drafted quest.

### 4c. Plan steps (at reveal, when a quest goes ACTIVE)

Input: the picked quest (title, rawText, kind, tiedTo, place) and the list of installed app labels+packages (so it can suggest real apps).
Output JSON:
```json
{
  "steps": ["Open your banking app", "Log in", "New payment to Sam", "Enter $200", "Confirm"],
  "suggestedApps": ["com.brave.browser", "au.com.nab.mobile"]
}
```
Rules:
- Steps are concrete and ordered, as granular as "log into this bank", "log into that bank". Simple imperative sentences.
- `suggestedApps` are package names chosen from the provided installed list; Brave (or the installed browser) is usually included. If it names an app not installed, drop it.
- Persist steps to `quest_step`, store `suggestedApps` on the quest row (`suggestedAppsJson`), set status ACTIVE.

---

## 5. Flows

### 5a. Capture (quests in)

1. User triggers voice capture (see entry points below).
2. Record via `VoiceRecorder`, transcribe via `VoiceTranscriptionManager.transcribe`.
3. Call Classify (4a), build a `QuestEntity` with status OPEN, insert.
4. Insert a `CAPTURED` event.
5. If `urgent`, the morning nag (§7) will pick it up.

Entry points: a mic affordance on the launcher home surface, and reuse of the existing assist-gesture path (`voice/QuickVoiceCaptureActivity.kt`, `voice/AssistantRegistration.kt`) if a spoken capture should work without opening the app. Match how quick voice capture already persists a memo; here it persists a quest instead.

### 5b. Summon (quests out)

1. User opens the quest surface and speaks the summon (constraints + "give me a quest"), or speaks it via the assist gesture.
2. Transcribe, load open quests, call Draft (4b).
3. If `picks` non-empty → show the **three-card draft** (§6). Record `SURFACED` per pick, update `lastSurfacedAt`.
4. If empty → show the near-miss message ("nothing fits 15 minutes; closest is X, ~40 min"). Offer to start it anyway or to re-summon with a different window. Do not auto-start.

### 5c. Pick → reveal

1. User picks a card by voice ("the first one" / the quest name) or tap.
2. Call Plan steps (4c), persist steps + suggested apps, set quest ACTIVE, insert `PICKED` event.
3. Animate from the card into the in-progress screen (§6). Enter quest focus (§8).

### 5d. Doing it

The in-progress screen shows the ordered steps and the suggested apps as launch buttons. The phone is narrowed to the suggested apps plus the always-open emergency set (§8). Most quests finish on the laptop; the phone screen is the ordered checklist you glance at.

### 5e. Completion (voice only)

No done button anywhere.
- "Done" / "finished it" (spoken) → mark DONE, insert `COMPLETED` event, exit quest focus (§8), return to launcher.
- "Not now, because …" (spoken) → mark back to OPEN, insert `DEFERRED` event with the reason, exit quest focus, run the learning update (§9), return to launcher.

Use a spoken-intent judge (small Groq call, or reuse the `NagJudge` shape) to decide whether the utterance is a completion or a deferral-with-reason, and to extract the reason. A bare "done" with no reason is a completion; anything explaining a blocker is a deferral.

---

## 6. The quest surface (UI) — the dopamine exception

Build as a dedicated full-screen Activity in `quest/ui/` (call it e.g. `QuestActivity`), modelled on `NagAnswerActivity` (`showWhenLocked`, `turnScreenOn`, its own theme). A dedicated Activity keeps the vivid theme isolated from the greyscale launcher and lets the morning nag launch straight into it.

Phases within the Activity:

1. **Summon** — a listening state; mic affordance; shows the spoken constraints being understood.
2. **Draft** — three cards. This is the centrepiece:
   - Cards are **vivid and coloured**, roguelite draft feel (Slay the Spire / The Bazaar). Colour/rarity encodes stakes: obligations hotter/brighter, wishes cooler. Pick a small vivid palette that lives only in `quest/ui/` (do not touch `ui/theme/Color.kt`).
   - Each card: title, the one-line pick reason, effort estimate, a stakes tint.
   - Selectable by voice or tap.
   - Entrance animation: cards deal in like drawing cards.
3. **Reveal** — picking a card triggers a **smooth, satisfying animated transition** (the card expands/flips into the full quest). This transition is the payoff and must feel great; invest here. Then the ordered steps stream in and the suggested-app buttons appear.
4. **In-progress** — steps checklist + suggested-app launch buttons + a listening affordance for the voice done/defer.

Animation: use Compose (`animate*AsState`, `AnimatedContent`, `updateTransition`) as the existing UI does. The nag screen already does full-screen Compose with animated colour; follow that.

Empty-draft state: a calm "nothing fits that window" with the near-miss suggestion. Keep it un-nagging.

---

## 7. Morning nag on-ramp (7–10am)

Urgent / people-involving quests get an alarm-style nag in the **07:00–10:00** window to get them done early. This reuses the nag engine's teeth (loud, full-screen, over lockscreen) but its "answer" is engaging with quests, not answering a question.

Implementation:
- A scheduler + worker in `quest/` modelled on `NagScheduler` + `NagWorker`, arming a daily 07:00 open (reuse `durationUntilNext(null, 7, 0)`), re-nagging on an interval until satisfied, and giving up at 10:00 (reuse the give-up pattern) so it never nags past the morning window.
- "Satisfied" = at least one urgent OPEN quest was PICKED or DEFERRED-with-reason this morning. If there are no urgent open quests, the morning nag does not fire at all (a quest with nothing to do is not a quest).
- Fire via `NagNotifier`'s full-screen-intent approach, but the intent opens `QuestActivity` directly into a **Draft filtered to urgent quests**, not `NagAnswerActivity`.
- Arm it in `OttoApp.onCreate` alongside `armNags()`.

Reconcile with the existing `NagNotifier` channel or add a sibling channel; reuse `USE_FULL_SCREEN_INTENT` + `POST_NOTIFICATIONS` already in the manifest. Register `QuestActivity` in the manifest like `NagAnswerActivity` (exported, `showWhenLocked`, `turnScreenOn`, `singleInstance`, `taskAffinity=""`).

---

## 8. Quest focus (app lockdown while ACTIVE)

While a quest is ACTIVE, narrow the phone to: the always-open emergency set (`AppTier.CORE` + `PEOPLE` + `ADMIN`, derived from existing tier data, not hardcoded) plus the quest's `suggestedApps`. Everything else suspended/hidden.

Implementation:
- Reuse `DeviceOwnerController.setLockTaskAllowlist`, `suspendPackages`, and/or `OttoPolicyController` rather than adding a parallel mechanism. The cleanest path is a quest-scoped allowlist applied through the same policy application that `OttoPolicyController.applyPolicies` already performs.
- On quest end (DONE or DEFERRED), restore the prior policy state.
- The base phone is already greyscale/locked (boring-phone). Quest focus **narrows** the allowlist to the quest's apps; it does not need to re-lock from scratch.
- Suggested browser (Brave) opens normally; the novel-domain firewall (separate feature, `OttoDnsVpnService`) still vets sites inside it. No quest-scoped network re-adjudication in v1.
- Provide a hard escape: the emergency set stays open so phone/messages always work; no lockdown state may trap the user.

Coordinate with `OttoPolicyController.isLockdownActive` / `startLockdown` so quest focus and any existing lockdown do not fight. Treat quest focus as a distinct policy layer that composes with the base mode.

---

## 9. The learning loop

On each `DEFERRED` event, run a small metadata update so the next Draft is smarter. Either rules or a tiny Groq extraction over `reasonText`:

- "not at my laptop" / "need my computer" → set `place = LAPTOP`. Draft then suppresses it when the user is on their phone.
- "need more time" / "not enough time" → increase `effortMinutes` (bump toward the stated/again-estimated size). This is the fix for the model getting the estimate wrong: the user's "nah, need more time" carries forward.
- "don't have X yet" / "waiting on …" → mark blocked (a `deadline`/hold or a note) so it is suppressed until likely resolved.
- Repeated defers with time excuses but never "don't want to" → leave OPEN and let ranking surface it in a genuinely free window (long stated minutes). Do not auto-drop.
- Many vague defers and never done → surface a "kill this?" suggestion in a future draft (graduated forgetting). Do not delete without the user's spoken ok.

Ranking (in Draft, §4b) consumes: `kind`, `deadline`, dodge count (`DEFERRED` events), `lastSurfacedAt`.

---

## 10. Degradation and edge cases

- **No Groq key** (`OttoConfig.hasGroqKey == false`): capture still stores the raw transcript as an unclassified quest (kind defaults to WISH, place unknown); Draft falls back to a simple local rank (obligation-first, then oldest) with no constraint parsing; steps are skipped (show the raw quest text). Never crash; mirror the launcher's existing graceful-degradation stance.
- **Nothing fits the window**: never force. Show the near-miss (§5b). "Nah, need more time" corrects the estimate (§9).
- **Re-draft / "give me another"**: re-run Draft; the three-card draft already gives choice, so a re-summon is the re-roll. Exclude the just-declined picks from the next draft.
- **Transcription failure**: reuse whatever the voice memo path does on failure; do not lose the audio (there is an open launcher bug to save recordings on failure — align with its fix).
- **Empty backlog**: no summon result; there is simply no quest.

---

## 11. Build order (milestones)

1. **Data + capture**: quest tables + migration 5→6 + `QuestDao`/`QuestRepository`; voice capture → Classify (4a) → persist OPEN quest + `CAPTURED` event. Verify by capturing a few quests and reading `trace.db` via `adb run-as`.
2. **Summon + draft + reveal**: `QuestActivity` with Summon→Draft→Reveal→In-progress; Draft (4b) and Plan-steps (4c); the three coloured cards and the animated reveal. This is the core loop and the dopamine surface.
3. **Voice completion + learning**: spoken done/defer, `COMPLETED`/`DEFERRED` events, metadata updates (§9), ranking reads dodge history.
4. **Quest focus**: narrow the app allowlist while ACTIVE, restore on end (§8).
5. **Morning nag on-ramp**: 7–10am urgent-quest kickoff via the nag engine (§7).

Ship and test each milestone on the phone before the next. Bump `versionCode`/`versionName` per the repo deployment rule on every `installDebug`.

---

## 12. Non-goals for v1

- No quest-scoped network re-adjudication (the standing firewall is enough).
- No calendar or ambient sensing; the user states constraints by voice.
- No earned unlock / reward window; the phone stays locked down.
- No tap-to-check per step (steps are a read-only guide; the `done` column is reserved for later).
- No auto-detection of idle/wasting time; summon is pull-only.
- The novel-domain focus firewall is a separate feature and spec.

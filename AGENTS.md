# AGENTS.md

## What This Is

Otto is a deliberately quiet Android launcher whose purpose is to reduce phone use. It includes local-first Trace, policy controls, voice capture, nags, and quests. Launcher surfaces stay boring, low-stimulation, and frictional; never add telemetry, gamification, rewards, promotional UI, or visual flourish.

## How We Decide

Prefer established Android conventions: unidirectional data flow, repository boundaries, dependency inversion, and feature slices. The bar for a bespoke pattern is high. Preserve useful friction around distracting apps and settings.

## Architecture

Packages live under `app/src/main/java/com/otto/launcher`: `core` provides shared infrastructure; `domain` is pure Kotlin; `data` owns shared persistence and integrations; feature slices are `apps`, `voice`, `guard`, `trace`, `quest`, `nag`, `updater`, `feedback`, and `notes`; `ui` owns Compose; the root is wiring and manifest-pinned components.

Domain decides what is true, in pure Kotlin.
Data persists it and talks to the outside world.
Core serves every feature and knows none of them.
Features own their slice and never touch each other or the shell.
The shell only wires; nothing imports the shell.

## Designing A Feature

Answer these gates in order:

1. What are we building?
2. What already exists?
3. What data does it need?
4. What are the units in that data?
5. What is the minimum place this can live?

## Commands

Run `scripts/check.sh`; it is the done gate. It executes detekt, unit/architecture tests, and a debug assembly. Android SDK 34+ and JDK 17 are required. Voice degrades gracefully without `GROQ_API_KEY` in `.env`.

## Enforcement

Detekt's baseline only shrinks; never add new findings to it. Konsist architecture rules are the law. Keep launcher surfaces minimal and quiet.

## Deployment Rules

Do not deploy. The user deploys. Any eventual phone install requires incrementing `versionCode` and `versionName` first; this structural refactor must not bump either.

## Parallel Agents

Stage explicit paths only. Never use `git add -A`. Never modify, revert, stage, or overwrite another agent's work.

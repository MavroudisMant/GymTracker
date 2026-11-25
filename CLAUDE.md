# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Single-module Android app (`:app`, package `com.mavros.gymtracker`) for logging gym workouts. Kotlin + Jetpack Compose (Material 3) + Room + Navigation Compose. No DI framework, no network, no Hilt. minSdk 30, compile/targetSdk 37, Java 11. Versions live in `gradle/libs.versions.toml` (AGP 9.x, Kotlin 2.2, KSP for Room).

`versionCode` continues from an earlier recovered build of the app (`com.mavrogenis.gymtracker` 1.6 / code 6) — always bump it, never reset.

## Commands

Run from the repo root:

```bash
./gradlew assembleDebug                 # build debug APK
./gradlew installDebug                  # build + install on connected device/emulator
./gradlew assembleRelease               # R8-optimized release build
./gradlew testDebugUnitTest             # JVM unit tests (app/src/test)
./gradlew testDebugUnitTest --tests "com.mavros.gymtracker.ExampleUnitTest"   # single test class
./gradlew connectedDebugAndroidTest     # instrumented tests (needs device)
./gradlew lint                          # Android lint
```

Tests are currently only the template placeholders; there is no meaningful test suite.

## Architecture

**Layers:** `data/` (Room entities, DAOs, repositories) → `ui/screens/<feature>/` (one `XxxScreen.kt` + `XxxViewModel.kt` per feature) → `ui/navigation/` (routes + NavHost).

**Manual DI:** `GymApplication` holds lazy singletons — `GymDatabase`, `WorkoutRepository`, `TemplateRepository`, `BackupRepository`, `SettingsRepository`, plus an `applicationScope`. `MainActivity` passes them into `GymNavGraph`, which constructs each ViewModel via a `XxxViewModel.factory(...)` companion function (using `viewModelFactory { initializer { } }`). Adding a screen = add route in `Routes.kt`, add `composable` in `GymNavGraph.kt`, give the ViewModel a `factory()`.

**ViewModel pattern:** expose `StateFlow`s — either a `MutableStateFlow<XxxUiState>` data class, or repository `Flow`s mapped to UI models and `stateIn(viewModelScope, WhileSubscribed(5_000), ...)`. UI-only models (`*Ui` data classes) live in the ViewModel file.

**Navigation:** string routes in `Routes.kt` with builder functions (`Routes.logWorkout(id)`, etc.). `log_workout?sessionId=` uses `-1` as "new workout".

### Data model

- `Exercise` — unique by `normalizedName` (trimmed, lowercased via `normalizeExerciseName`). Always create via `Exercise.create(...)` / `WorkoutRepository.getOrCreateExercise` so the normalized name is set. Has optional `TargetArea` enum and `isTimed`.
- Templates: `Template` → `TemplateDay` → `TemplateSection` (optional grouping, `includeInWorkout` flag) and `TemplateExercise` (position, optional `sectionId`, `supersetGroup`, `targetSets/targetReps`).
- Logged workouts: `WorkoutSession` (date as `LocalDate`, optional `templateDayId` with SET_NULL) → `SessionExercise` (position, `supersetGroup`, denormalized `sectionName` string) → `SetEntry` (`weight` nullable, `reps`).
- Exercise FKs from template/session rows are `NO_ACTION`; check `exerciseUsageCount` before deleting an exercise.
- `Relations.kt` holds all `@Embedded`/`@Relation` read models (`TemplateDetail`, `SessionDetail`, `RecentSession`, ...).
- `Converters`: `LocalDate` ↔ epoch day `Long`, `TargetArea` ↔ name.
- Ordering is always by explicit `position` / `setNumber` columns — sort by them when reading; reorder via the repository `reorder*` methods (transactional).

### Key behaviors spanning multiple files

- **Starting from a template:** `WorkoutRepository.startSessionFromTemplateDay` copies the day's exercises (skipping sections with `includeInWorkout = false`, ordered by section then position) into a new session with no sets. `LogWorkoutViewModel.loadSession` then pre-fills empty set rows from the template targets when a session has no sets.
- **Workout auto-save:** `LogWorkoutViewModel` debounces state changes (1.5 s) and calls `saveNow()`, guarded by a `Mutex`. `saveImmediately()` runs on the **app scope** so edits survive navigating away. `WorkoutRepository.saveSession` overwrites an existing session by deleting and re-inserting all its exercises/sets in one transaction — session exercise/set IDs are not stable.
- **Weight unit:** `SettingsRepository` (SharedPreferences, `StateFlow<WeightUnit>`) is provided to the whole UI via `LocalWeightUnit` CompositionLocal in `MainActivity`. Weights are stored as raw numbers; no conversion happens on unit change.
- **Backup/restore** (`BackupRepository` + Settings screen): backup = `VACUUM INTO` a file in `cacheDir/backups/`, shared through the `FileProvider` (`${applicationId}.fileprovider`, `res/xml/file_paths.xml`). Restore validates the file (size, schema version, required tables, `integrity_check`), closes the DB, swaps the file in, deletes WAL/SHM, and the app restarts via `exitProcess`.
- **CSV export:** `WorkoutRepository.exportCsv()`.
- **Drag-to-reorder:** custom implementation in `ui/components/Reorder.kt` (`ReorderState`, `Modifier.reorderItem`, edge auto-scroll), used by the template builder.

## Database migrations (important)

When changing any entity:
1. Bump `version` in `GymDatabase`.
2. Add a `MIGRATION_N_N+1` and register it in `addMigrations(...)` (no destructive fallback is configured — a missing migration crashes on upgrade).
3. **Also bump `DATABASE_VERSION` in `BackupRepository`** (used to reject backups from newer versions) and update `REQUIRED_TABLES` if tables change.
4. Build once so Room exports the new schema JSON to `app/schemas/`, and commit it. Only `6.json` exists; earlier schemas weren't preserved.

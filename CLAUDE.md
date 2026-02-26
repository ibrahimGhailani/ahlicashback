# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with this repository.

## Project Overview

**CashbackCalc** is an Android app that reads AlAhli bank SMS messages, parses Arabic cashback notifications ("استرجاع نقدي"), extracts SAR amounts, and displays monthly cashback summaries.

- **Namespace / App ID:** `space.ibrahim.cashbackcalc`
- **Min SDK:** 29 (Android 10), **Target SDK:** 36 (Android 15)
- **Language:** Kotlin 2.0.21, **UI:** Jetpack Compose + Material3

## Build & Run Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (minified via ProGuard)
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug

# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Run a single unit test class
./gradlew test --tests "space.ibrahim.cashbackcalc.ExampleUnitTest"
```

## Architecture

Single-activity app with all logic in `app/src/main/java/space/ibrahim/cashbackcalc/`:

- **`MainActivity.kt`** — Entry point and entire app logic. Contains:
  - `CashbackTrackerScreen()` — main Composable; handles permission request, triggers SMS read, renders results
  - `MonthlySummaryCard()` — reusable card Composable for per-month display
  - `readSms()` — suspend function (runs on `Dispatchers.IO`) that queries `ContentResolver` for SMS, applies regex to extract Arabic cashback amounts, and returns a list of `MonthlySummary`
  - `MonthlySummary` — data class: `monthYear: String`, `totalAmount: Double`, `transactionCount: Int`

- **`ui/theme/`** — Custom "AlAhli" theme:
  - Primary: AlAhli Green `#006A4E`, Secondary: AlAhli Gold `#FDB813`
  - Always uses the light color scheme regardless of system dark mode

## Key Implementation Details

- **SMS parsing:** Filters messages from AlAhli bank using keyword "استرجاع نقدي" and extracts amounts with a regex for SAR currency format.
- **Permission:** `READ_SMS` is requested at runtime from `CashbackTrackerScreen`. The manifest also declares `android.hardware.telephony` as optional.
- **State management:** Compose `remember`/`mutableStateOf` — no ViewModel or external state library.
- **Async:** Coroutines via `rememberCoroutineScope()` + `Dispatchers.IO` for SMS reading; no Hilt/DI.

## Dependencies

Managed via version catalog (`gradle/libs.versions.toml`):
- Compose BOM `2024.09.00` (ui, material3, tooling)
- `androidx.core:core-ktx:1.10.1`
- `androidx.lifecycle:lifecycle-runtime-ktx:2.6.1`
- `androidx.activity:activity-compose:1.8.0`
- Testing: JUnit 4, Espresso

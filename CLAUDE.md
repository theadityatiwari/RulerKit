# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Publish rulerkit locally (for JitPack verification)
./gradlew :rulerkit:publishToMavenLocal

# Run local unit tests
./gradlew test

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Run tests for a specific module
./gradlew :app:test
./gradlew :rulerkit:test

# Clean build
./gradlew clean

# Install debug APK on connected device
./gradlew installDebug
```

## Project Structure

Two Gradle modules:
- **`:rulerkit`** — Android library (`com.nativeknights.rulerkit`) — the publishable library, distributed via JitPack
- **`:app`** — Sample/demo app (`com.nativeknights.rulerkit.sample`), depends on `:rulerkit`

Dependencies are managed via version catalog at `gradle/libs.versions.toml` (AGP 8.11.1, Kotlin 2.0.21, compileSdk 36, minSdk 24).

### JitPack Publishing
- `jitpack.yml` at root specifies `openjdk17` and runs `:rulerkit:assembleRelease`
- `rulerkit/build.gradle.kts` has `maven-publish` with groupId `com.github.nativeknights`, artifactId `rulerkit`
- To release: push a git tag (e.g. `v1.0.0`) — JitPack builds it automatically

### KMP Migration Path (Phase 4)
The `:rulerkit` module is structured to convert to Kotlin Multiplatform with minimal friction:
- `src/main/` → splits into `src/androidMain/` + `src/commonMain/`
- Files with zero Android imports belong in `commonMain`: `UnitSet.kt`, `InputType.kt`, `RulerConfig.kt`, `ScrollEngine.kt`
- Files using `android.*`, `Canvas`, `View` stay in `androidMain`: `RulerPickerView.kt`, `IndicatorType.kt`

## Architecture

**No MVVM, no ViewModel, no LiveData.** All rendering and interaction logic lives directly in the View subclasses.

### `:rulerkit` library — `com.nativeknights.rulerkit`

| File | Role |
|------|------|
| `RulerPickerView.kt` | Main Android View: Canvas drawing, touch/fling/snap, haptic |
| `ScrollEngine.kt` | All value/step math and scroll state (zero Android imports) |
| `RulerConfig.kt` | Immutable config data class with companion factories (zero Android imports) |
| `InputType.kt` | Sealed class: Weight, Height, Distance, Custom (zero Android imports) |
| `UnitSet.kt` | `RulerUnit` interface + all unit enums with defaults/conversion (zero Android imports) |
| `IndicatorType.kt` | `IndicatorType` and `IndicatorPosition` enums |

**Key design decisions:**
- `ScrollEngine` owns all value-space math; `RulerPickerView` owns all pixel math. The bridge is `pixelsPerStep = tickSpacingDp * density`.
- Colors are ARGB-packed `Int` (KMP-compatible). Dimensions are dp/sp `Float` in config, converted to px only in the View.
- Tick loop uses integer indices (`min + idx * step`) to avoid floating-point accumulation.
- `tickBaseY` is computed dynamically in `computeLayout` so the value label never clips the top edge.
- `requestDisallowInterceptTouchEvent(true)` on `ACTION_DOWN` prevents parent `ScrollView` from stealing horizontal drags.

### `:app` sample — `com.nativeknights.rulerkit.sample`

`MainActivity` demonstrates four `RulerPickerView` instances:
- Weight (kg/lb toggle with `setUnit()`)
- Height (cm/inch toggle)
- Age (custom ruler, 1–120 yrs)
- Distance (flipped vertically)

### Theme

`Theme.Material3.DayNight.NoActionBar` with edge-to-edge layout enabled in `MainActivity`.

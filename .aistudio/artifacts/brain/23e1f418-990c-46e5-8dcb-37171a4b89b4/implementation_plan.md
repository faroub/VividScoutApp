# Implementation Plan - Release Build Minification & ProGuard Keep Rules

## User Intent
Enable `isMinifyEnabled = true` and `isShrinkResources = true` in the release build type and configure comprehensive, documented R8/ProGuard keep rules in `app/proguard-rules.pro` to ensure the release APK is properly obfuscated, minified, and fully functional without runtime reflection or serialization crashes.

---

## User Review Required

> [!IMPORTANT]
> The release build will have R8 minification and resource shrinking enabled. All data models, Room entities, Moshi adapters, Retrofit interfaces, and dynamic reflection lookups will be protected via keep rules.

---

## Proposed Changes

### 1. Build Configuration (`app/build.gradle.kts`)
- In `android.buildTypes.release`:
  - Set `isMinifyEnabled = true`
  - Set `isShrinkResources = true`
  - Keep standard `proguard-rules.pro` reference.

### 2. ProGuard Configuration (`app/proguard-rules.pro`)
Write documented keep rules explaining why each rule is required:
- **Keep Stack Traces & Annotations**: Preserve `SourceFile`, `LineNumberTable`, `*Annotation*`, `Signature`, `InnerClasses`.
- **Moshi Models & Codegen Adapters**:
  - Keep `@com.squareup.moshi.JsonClass` annotated classes and generated `*JsonAdapter` classes.
  - Keep Moshi internal reflection and model fields.
- **Room Database & DAOs**:
  - Keep classes annotated with `@androidx.room.Entity`, `@androidx.room.Dao`, `@androidx.room.Database`.
  - Keep Room generated implementation classes (`*_Impl`).
- **Retrofit & OkHttp Interfaces**:
  - Keep network interfaces and data classes in `com.example.data.network.**`.
  - Keep OkHttp and Retrofit annotations and method signatures.
- **Kotlin Coroutines & Serialization**:
  - Keep coroutines internal continuation handlers and exception handlers.
- **Firebase AI & App Check**:
  - Keep Firebase AI Logic and App Check Recaptcha provider entry points.
- **Reflection & Dynamic Resource Lookups**:
  - Keep `com.example.util.ImageResolver` and `@Keep` annotated classes.
  - Keep `com.example.data.model.**` and `com.example.data.local.**` entity models.

### 3. Resource Shrinking Protection (`app/src/main/res/raw/keep.xml`)
- Create `res/raw/keep.xml` to instruct resource shrinker to keep dynamic drawables accessed via `Resources.getIdentifier(...)` (e.g. `ic_spot_*`, `ic_category_*`, `img_*`).

---

## Verification Plan

### Automated Tests & Build Verification
1. Run `compile_applet` to verify compilation state.
2. Run `run_command(CommandLine="gradle :app:assembleRelease", Cwd=".", WaitMsBeforeAsync=10000)` to verify the minified release APK builds successfully without R8 shrinker errors.
3. Run `run_command(CommandLine="gradle :app:testDebugUnitTest", Cwd=".", WaitMsBeforeAsync=10000)` to ensure all 106 unit tests pass.

# VividScout ProGuard & R8 Optimization and Shrinking Rules
# Documented keep rules protecting data models, database persistence, network clients, and AI APIs.

# --------------------------------------------------------------------------------
# 1. Stack Traces & Debugging Attributes
# Preserve source file names and line numbers for human-readable crash reports.
# --------------------------------------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# --------------------------------------------------------------------------------
# 2. Moshi JSON Codegen & Reflective Data Models
# Preserve Moshi models annotated with @JsonClass and their generated Adapters.
# --------------------------------------------------------------------------------
-keep @com.squareup.moshi.JsonClass class * {
    public <init>(...);
}
-keep class * extends com.squareup.moshi.JsonAdapter {
    public <init>(...);
}
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}

# --------------------------------------------------------------------------------
# 3. Room Database Entities & DAOs
# Preserve Room database entities, DAOs, and KSP generated implementation classes.
# --------------------------------------------------------------------------------
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Database class * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class com.example.data.local.** { *; }
-keep class *Impl extends androidx.room.RoomDatabase { *; }

# --------------------------------------------------------------------------------
# 4. Retrofit & OkHttp Network Interfaces & Data Models
# Keep Retrofit interface methods, response models, and OkHttp reflection targets.
# --------------------------------------------------------------------------------
-keep interface com.example.data.network.** { *; }
-keep class com.example.data.network.** { *; }
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.** <methods>;
}
-keep class okhttp3.** { *; }

# --------------------------------------------------------------------------------
# 5. Kotlin Coroutines & Async State Flow
# Keep coroutines internal continuation handlers and exception mechanisms.
# --------------------------------------------------------------------------------
-keepclassmembers class kotlinx.coroutines.** { *; }
-keep class kotlinx.coroutines.android.** { *; }

# --------------------------------------------------------------------------------
# 6. Firebase AI Logic & App Check
# Protect Firebase AI Studio / Gemini API SDKs and reCAPTCHA App Check attestation.
# --------------------------------------------------------------------------------
-keep class com.google.firebase.ai.** { *; }
-keep class com.google.firebase.appcheck.** { *; }
-keep class com.google.firebase.appcheck.recaptcha.** { *; }

# --------------------------------------------------------------------------------
# 7. Internal Application Data Models & Reflection Utilities
# Preserve app domain models, vector engine data structures, and reflection targets.
# --------------------------------------------------------------------------------
-keep class com.example.data.model.** { *; }
-keep class com.example.engine.** { *; }
-keep class com.example.util.ImageResolver { *; }
-keep class com.example.util.AppCheckInitializerKt { *; }
-keep @androidx.annotation.Keep class * { *; }

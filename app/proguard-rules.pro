# ============================================================
# Anchor Media Server — Optimized R8 / ProGuard Rules
# ============================================================

# ── Entry Points ─────────────────────────────────────────────
-keep class com.example.anchor.AnchorApplication { *; }
-keep class com.example.anchor.MainActivity { *; }
-keep class com.example.anchor.server.service.AnchorServerService { *; }
-keep class com.example.anchor.server.service.AnchorMediaService { *; }

# ── Kotlin Serialization ─────────────────────────────────────
# Keep metadata for serializable classes to prevent data loss
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class com.example.anchor.data.dto.**$$serializer { *; }
-keepclassmembers class com.example.anchor.data.dto.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.anchor.data.dto.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class com.example.anchor.data.dto.** { *; }

# ── Ktor + Netty (Critical for Server) ───────────────────────
# Netty uses a lot of reflection to determine capabilities at runtime
-keep class io.ktor.** { *; }
-keep class io.netty.** { *; }
-dontwarn io.netty.**
-dontwarn io.ktor.**

# Required for Netty's unsafe memory access and platform detection
-keepclassmembers class io.netty.util.internal.PlatformDependent0 {
    static boolean IS_ANDROID_SET_ACCESSIBLE_0;
}
-keepclassmembers class io.netty.util.internal.CleanerJava6 {
    private static long CLEANER_BIT;
}

# ── Koin (Dependency Injection) ──────────────────────────────
# Koin uses reflection to find constructors; keep them for injected classes
-keep class org.koin.** { *; }
-keepclassmembers class * {
    public <init>(...);
}
# Keep ViewModels so Koin can instantiate them via class reference
-keep class * extends androidx.lifecycle.ViewModel { *; }
-dontwarn org.koin.**

# ── Coroutines ────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ── Media3 / ExoPlayer ────────────────────────────────────────
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ── Strip Verbose Logs in Release ─────────────────────────────
# This reduces the size of string constants and slightly improves performance
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static boolean isLoggable(java.lang.String, int);
}

# ── General Optimizations ─────────────────────────────────────
-optimizationpasses 5
-allowaccessmodification
-mergeinterfacesaggressively
-repackageclasses ''

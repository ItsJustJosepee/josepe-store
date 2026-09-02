# ==============================================================================
# Josepe Store Android — ProGuard / R8 Configuration
# ==============================================================================

-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 1. Clases y Modelos del Proyecto Josepe Store
-keep class store.josepe.dev.** { *; }

# 2. Compose Multiplatform & Snapshots
-keep class androidx.compose.runtime.snapshots.** { *; }

# 3. Koin (Inyección de Dependencias)
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# 4. Ktor & Coroutines
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn kotlinx.coroutines.**

# 5. Haze (Glassmorphism)
-keep class dev.chrisbanes.haze.** { *; }

# ==============================================================================
# Josepe Store Desktop — ProGuard Shrink-Only Configuration
# ==============================================================================

# Solo eliminar clases/código muerto (Tree-shaking de dependencias e iconos)
# NO ofuscar nombres para garantizar compatibilidad con JNI, JNA y Serialización
-dontobfuscate
-dontoptimize
-ignorewarnings

# Preservar firmas y metadatos para reflexión y Kotlinx Serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions,SourceFile,LineNumberTable

# 1. Compose Multiplatform & Skiko (UI y Renderizado)
-keep class androidx.compose.** { *; }
-keep class org.jetbrains.compose.** { *; }
-keep class org.jetbrains.skiko.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# 2. Clases del Proyecto Josepe Store
-keep class store.josepe.dev.** { *; }

# 3. Kotlin & Coroutines
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class kotlinx.serialization.** { *; }

# 4. Inyección de Dependencias (Koin)
-keep class org.koin.** { *; }

# 5. Red & Ktor
-keep class io.ktor.** { *; }

# 6. Integraciones Nativas de Ventana y Sistema (JNA)
-keep class com.sun.jna.** { *; }
-keep class store.josepe.dev.WindowsWindowProcSubclass** { *; }

# 7. Haze Glassmorphism
-keep class dev.chrisbanes.haze.** { *; }

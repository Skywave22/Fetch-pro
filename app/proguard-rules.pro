# FetchPro ProGuard Rules - Production

# Keep all app classes (R8 will optimize but we keep DB entities)
-keep class com.fetchpro.downloadmanager.data.local.db.** { *; }
-keep class com.fetchpro.downloadmanager.domain.model.** { *; }
-keep class com.fetchpro.downloadmanager.download.engine.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# OkHttp
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keepclasseswithmembers class * {
    @dagger.hilt.android.AndroidEntryPoint <methods>;
}
-keep @dagger.hilt.android.AndroidEntryPoint class *

# Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers,allowobfuscation class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Compose
-keep class androidx.compose.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Security - don't obfuscate checksum checker
-keep class com.fetchpro.downloadmanager.download.engine.IntegrityChecker { *; }

# Remove logs in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Optimization
-optimizations !code/simplification/arithmetic,!code/allocation/variable
-optimizationpasses 5
-allowaccessmodification

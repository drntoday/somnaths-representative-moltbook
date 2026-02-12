# Keep source/line attributes useful for release crash diagnostics.
-keepattributes SourceFile,LineNumberTable

# OkHttp/Okio may reference optional platform classes.
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlin serialization (only relevant when @Serializable models exist).
-keepclassmembers class **$$serializer { *; }
-keepclassmembers class kotlinx.serialization.internal.** { *; }

# WorkManager workers are instantiated by class name.
-keep class * extends androidx.work.ListenableWorker

# Compose works with default R8 rules; no extra keep rules needed.

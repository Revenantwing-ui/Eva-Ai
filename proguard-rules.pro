# Add project specific ProGuard rules here.

# TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.support.** { *; }

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Keep accessibility service
-keep class com.aiautomation.assistant.service.AutomationAccessibilityService { *; }

# Kotlin
-dontwarn kotlin.**
-dontwarn kotlinx.**

# AndroidX
-dontwarn androidx.**
-keep class androidx.** { *; }

# Keep data classes
-keep class com.aiautomation.assistant.data.** { *; }
-keep class com.aiautomation.assistant.ml.** { *; }

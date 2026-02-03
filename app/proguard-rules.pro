# ProGuard rules for AudioFlow

# Keep Media3 classes
-keep class androidx.media3.** { *; }

# Keep Hilt
-keep class dagger.hilt.** { *; }

# Keep Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Keep data classes
-keep class com.audioflow.player.model.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# General
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# =============================================================================
# Nosved Player ProGuard / R8 Rules
# =============================================================================
# IMPORTANT: Release builds use isMinifyEnabled=true with proguard-android-optimize.txt.
# These rules guard all JNI bridges, native-callback interfaces, and coroutine
# continuations that R8 would otherwise strip or rename.
# =============================================================================


# -----------------------------------------------------------------------------
# 1.  MPV ANDROID LIBRARY  (mpvlib.aar)
# -----------------------------------------------------------------------------
# MPVLib is a JNI bridge. C++ code calls back into Java/Kotlin by exact method
# names via JNI reflection. -keep without allowoptimization is required here
# because allowoptimization lets R8 inline/rename methods, breaking the ABI.
# The conflicting "-keep,allowoptimization" rule below has been removed.
# -----------------------------------------------------------------------------
-keep class is.xyz.mpv.MPVLib { *; }
-keep class is.xyz.mpv.MPVLib$* { *; }
-keep class is.xyz.mpv.MPVNode { *; }
-keep class is.xyz.mpv.MPVNode$* { *; }

# The EventObserver interface is implemented by MPVPlayerEngine.
# Its methods (eventProperty, event) are invoked by the native mpv thread via
# JNI - NOT by any Kotlin caller - so R8 treats them as dead code and removes
# them. -keep prevents that.
-keep interface is.xyz.mpv.MPVLib$EventObserver { *; }
-keepclassmembers class * implements is.xyz.mpv.MPVLib$EventObserver {
    public void eventProperty(java.lang.String);
    public void eventProperty(java.lang.String, long);
    public void eventProperty(java.lang.String, boolean);
    public void eventProperty(java.lang.String, java.lang.String);
    public void eventProperty(java.lang.String, double);
    public void eventProperty(java.lang.String, is.xyz.mpv.MPVNode);
    public void event(int, is.xyz.mpv.MPVNode);
}

# MediaInfo JNI bridge
-keep class net.mediaarea.mediainfo.lib.** { public protected *; }


# -----------------------------------------------------------------------------
# 2.  Nosved PLAYER PLAYER PACKAGE  (JNI + SurfaceHolder callbacks)
# -----------------------------------------------------------------------------
# MPVPlayerEngine implements MPVLib.EventObserver - its JNI callback methods
# must not be renamed or stripped.
# MPVSurfaceView implements SurfaceHolder.Callback - surfaceCreated/Changed/
# Destroyed are called by the Android framework by name.
# -----------------------------------------------------------------------------
-keep class com.devson.nvplayer.player.engine.MPVPlayerEngine { *; }
-keep class com.devson.nvplayer.player.engine.MPVSurfaceView { *; }
-keep class com.devson.nvplayer.player.** { *; }

# Keep all data/enum classes used across JNI boundaries or serialised via
# reflection (TrackInfo, ChapterInfo, PlayerState, DecoderMode, AspectMode…)
-keepclassmembers class com.devson.nvplayer.player.** {
    public protected *;
}


# -----------------------------------------------------------------------------
# 3.  YT-DLP INTEGRATION
# -----------------------------------------------------------------------------
# YtdlpManager uses android.system.Os.setenv() and ProcessBuilder with paths
# derived at runtime - R8 must not rename these classes.
# YtdlpOptionsBuilder is called by name from PlayerViewModel via settings flow.
# -----------------------------------------------------------------------------
-keep class com.devson.nvplayer.player.ytdlp.** { *; }


# -----------------------------------------------------------------------------
# 4.  KOTLIN COROUTINES  (critical for release builds)
# -----------------------------------------------------------------------------
# R8 with proguard-android-optimize.txt can eliminate coroutine continuations
# that appear unreachable via static analysis.  These rules are the standard
# set recommended by JetBrains for coroutine-heavy release apps.
# -----------------------------------------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-keepclassmembers class kotlin.coroutines.jvm.internal.BaseContinuationImpl {
    private final kotlin.coroutines.Continuation completion;
}


# -----------------------------------------------------------------------------
# 5.  VIEWMODEL / LIFECYCLE
# -----------------------------------------------------------------------------
# AndroidViewModel subclasses are instantiated by ViewModelProvider via
# reflection using the constructor signature.
# -----------------------------------------------------------------------------
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }


# -----------------------------------------------------------------------------
# 6.  ROOM DATABASE
# -----------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }


# -----------------------------------------------------------------------------
# 7.  ATTRIBUTES REQUIRED FOR REFLECTION AND STACK TRACES
# -----------------------------------------------------------------------------
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes Exceptions
-keepattributes SourceFile,LineNumberTable


# -----------------------------------------------------------------------------
# 8.  REMOVE AGGRESSIVE OBFUSCATION FLAGS
# -----------------------------------------------------------------------------
# The previous -repackageclasses '' + -allowaccessmodification combination was
# moving all classes into the default package, which broke JNI method lookup
# because libmpv.so encodes the full Java class path in its native method
# registration table.  These flags are intentionally omitted here.
#
# -optimizationpasses 5 was also removed: 5 passes of optimisation can inline
# or reorder methods that the native layer depends on by ABI contract.
# R8 default (proguard-android-optimize.txt) is already aggressive enough.
# -----------------------------------------------------------------------------
# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
# -renamesourcefileattribute SourceFile

# enabling obfuscation would break some self-reflection in the app
-dontobfuscate

# reflection by androidx via theme attr viewInflaterClass
-keep class org.akanework.gramophone.logic.ui.ViewCompatInflater { *; }

# reflection by lyric getter xposed
-keep class androidx.media3.common.util.Util {
    public static void setForegroundServiceNotification(...);
}

# Audio visualizer pipeline — singleton accessed via companion object, fed by TeeAudioProcessor
-keep class org.akanework.gramophone.logic.utils.VisualizerProcessor {
    <fields>;
    <methods>;
}
-keep class org.akanework.gramophone.ui.components.VisualizerView { *; }

# Equalizer audio effects — singleton accessed via companion object
-keep class org.akanework.gramophone.logic.utils.EqEffectWrapper {
    <fields>;
    <methods>;
}
-keep class org.akanework.gramophone.ui.components.EqualizerBottomSheet { *; }

# JNI
-keep class org.nift4.gramophone.hificore.NativeTrack {
    onAudioDeviceUpdate(...);
    onUnderrun(...);
    onMarker(...);
    onNewPos(...);
    onStreamEnd(...);
    onNewIAudioTrack(...);
    onNewTimestamp(...);
    onLoopEnd(...);
    onBufferEnd(...);
    onMoreData(...);
    onCanWriteMoreData(...);
}

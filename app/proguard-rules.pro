# zxing-cpp ships its own consumer rules for the JNI bridge.
# CameraX is pulled in transitively by zxing-cpp but never used here.
-dontwarn androidx.camera.**

# ML Kit discovers its components by class name at runtime; keep them intact.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_common.** { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }

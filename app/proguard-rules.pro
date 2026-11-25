# zxing-cpp ships its own consumer rules for the JNI bridge.
# CameraX is pulled in transitively by zxing-cpp but never used here.
-dontwarn androidx.camera.**

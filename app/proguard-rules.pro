# ASH release rules. R8 full mode is on by default with AGP 9.

# Strip verbose logging from release: logcat never receives debug/info lines.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# SQLCipher uses JNI; its Java side must keep names.
-keep class net.zetetic.database.** { *; }
-keep interface net.zetetic.database.** { *; }

# Tink uses protobuf-lite reflection for key material.
-keep class com.google.crypto.tink.proto.** { *; }
-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite { <fields>; }
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# kotlinx.serialization: backup payload, record extras and navigation routes.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.ash.**$$serializer { *; }

# Keep enum names: Room stores enums by name.
-keepclassmembers enum app.ash.** { *; }

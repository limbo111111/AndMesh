# Keep JNI methods and Native bridge
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class com.andmesh.app.RtlSdrNative { *; }
-keepclassmembers class com.andmesh.app.RtlSdrNative {
    public static void onPacketDecoded(java.lang.String);
}

# Room Rules
-keep class androidx.room.** { *; }
-dontwarn androidx.room.paging.**

# Keep Entities
-keep class com.andmesh.app.data.local.entity.** { *; }
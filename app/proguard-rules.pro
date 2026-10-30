# NO OBFUSCATION — shrinking and optimization stay on. quickjs-kt marshals
# Kotlin values across the JS bridge by reflected type names; every renamed
# class that crosses the bridge dies with "Cannot convert java type 'xy9'"
# (verified twice on device with two different classes). Obfuscation buys
# nothing for a sideloaded personal app, so turn it off wholesale instead of
# chasing keep rules for every type that may ever cross the bridge.
-dontobfuscate

# QuickJS (io.github.dokar3:quickjs-kt) — JNI bridge: native code resolves
# these classes/methods by name, R8 must not rename or strip them.
-keep class com.dokar.quickjs.** { *; }
-keepclasseswithmembers class * {
    native <methods>;
}

# quickjs-kt converts Kotlin objects crossing the JS bridge by reflecting on
# class/property names. Verified on device: with these obfuscated, every
# plugin call dies with "TypeError: Cannot convert java type 'zc3' to a js
# value". Anything passed to or returned from a define()d binding must keep
# its original shape.
-keep class com.malina.seanimetvport.data.plugin.** { *; }
-keep class com.malina.seanimetvport.data.anilist.model.** { *; }

# kotlinx.serialization — recent versions ship consumer rules, these are a
# defensive floor for @Serializable models resolved via serializer() lookups.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.malina.seanimetvport.** {
    *** Companion;
}
-keepclasseswithmembers class com.malina.seanimetvport.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp platform probes reference optional classes not present on Android.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Hush Android — R8/ProGuard rules

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.nickdegs.hush.**$$serializer { *; }
-keepclassmembers class com.nickdegs.hush.** {
    *** Companion;
}
-keepclasseswithmembers class com.nickdegs.hush.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit
-keepattributes Signature, Exceptions
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Compose
-keep class androidx.compose.runtime.** { *; }

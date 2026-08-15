# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.kimi3.client.**$$serializer { *; }
-keepclassmembers class com.kimi3.client.** {
    *** Companion;
}
-keepclasseswithmembers class com.kimi3.client.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

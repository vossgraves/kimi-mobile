# ---- kotlinx.serialization ----
# Without these R8 strips the generated serializers and every API call fails
# at runtime with "Serializer for class X not found".
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class com.kimimobile.**$$serializer { *; }
-keepclassmembers class com.kimimobile.** {
    *** Companion;
}
-keepclasseswithmembers class com.kimimobile.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers @kotlinx.serialization.Serializable class com.kimimobile.** {
    <fields>;
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- OkHttp / Okio ----
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepclassmembers class okhttp3.internal.** { *; }

# ---- WebView JS bridge ----
# The login flow calls Android.onToken() from JavaScript; R8 can't see that
# call site, so the method must be kept explicitly.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ---- Compose ----
-dontwarn androidx.compose.**

# ---- Coil ----
-dontwarn coil.**

# Keep our data models used through reflection-ish paths (JSON parsing).
-keep class com.kimimobile.data.** { *; }

# Line numbers make release crash reports readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

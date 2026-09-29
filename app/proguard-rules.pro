# Keep kotlinx.serialization models
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class com.daydreamin.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.daydreamin.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# NewPipeExtractor resolves YouTube's player through Rhino (a JavaScript engine), which works by
# reflection — shrinking either breaks playback. Size isn't the point of this build; speed is.
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.classfile.**
-dontwarn javax.script.**
-dontwarn java.beans.**
-dontwarn jdk.dynalink.**
-dontwarn org.ietf.jgss.**
-dontwarn com.google.re2j.**

# jsoup (used by the extractor) references optional libraries it doesn't need at runtime.
-keep class org.jsoup.** { *; }
-dontwarn org.jspecify.annotations.**

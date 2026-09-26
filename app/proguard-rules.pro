# Keep kotlinx.serialization models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class com.daydreamin.app.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.daydreamin.app.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

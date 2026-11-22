# Keep the JNI bridge so native code can find the Kotlin callbacks.
-keep class com.saarathi.app.llm.LlamaNative { *; }
-keepclassmembers class com.saarathi.app.llm.LlamaNative { *; }

# PdfBox-Android uses reflection for font and filter loading.
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.bouncycastle.**
-dontwarn javax.**

# ML Kit
-keep class com.google.mlkit.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.saarathi.app.**$$serializer { *; }
-keepclassmembers class com.saarathi.app.** { *** Companion; }
-keepclasseswithmembers class com.saarathi.app.** { kotlinx.serialization.KSerializer serializer(...); }

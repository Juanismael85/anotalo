# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class com.voicenotes.app.data.** {
  *** Companion;
}
-keepclasseswithmembers class com.voicenotes.app.data.** {
  kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.voicenotes.app.data.**$$serializer { *; }

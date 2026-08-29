# kotlinx-serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.wc.workout.**$$serializer { *; }
-keepclassmembers class com.wc.workout.** {
    *** Companion;
}
-keepclasseswithmembers class com.wc.workout.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Room-generated impls (defensive; Room ships consumer rules)
-keep class * extends androidx.room.RoomDatabase { <init>(); }

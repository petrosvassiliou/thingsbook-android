# Add project specific ProGuard rules here.
-keepattributes *Annotation*,InnerClasses,Signature,RuntimeVisibleAnnotations
-keepnames class com.pv.thingsbook.data.remote.dto.** { *; }
-keepclassmembers class com.pv.thingsbook.data.remote.dto.** { *; }

# Keep domain models & enums used with Enum.valueOf and kotlinx.serialization
-keepnames class com.pv.thingsbook.domain.model.** { *; }
-keepclassmembers class com.pv.thingsbook.domain.model.** { *; }
-keepclassmembers enum com.pv.thingsbook.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Gemini AI & Sync kotlinx.serialization DTOs and generated serializers
-keepnames class com.pv.thingsbook.data.ai.** { *; }
-keepclassmembers class com.pv.thingsbook.data.ai.** { *; }
-keepnames class com.pv.thingsbook.data.repository.** { *; }
-keepclassmembers class com.pv.thingsbook.data.repository.** { *; }
-keepnames class com.pv.thingsbook.data.update.** { *; }
-keepclassmembers class com.pv.thingsbook.data.update.** { *; }

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

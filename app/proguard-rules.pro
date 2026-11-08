# Add project specific ProGuard rules here.
-keepattributes *Annotation*,InnerClasses,Signature,RuntimeVisibleAnnotations
-keepnames class com.culturedcode.things3.data.remote.dto.** { *; }
-keepclassmembers class com.culturedcode.things3.data.remote.dto.** { *; }

# Keep domain models & enums used with Enum.valueOf and kotlinx.serialization
-keepnames class com.culturedcode.things3.domain.model.** { *; }
-keepclassmembers class com.culturedcode.things3.domain.model.** { *; }
-keepclassmembers enum com.culturedcode.things3.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Gemini AI & Sync kotlinx.serialization DTOs and generated serializers
-keepnames class com.culturedcode.things3.data.ai.** { *; }
-keepclassmembers class com.culturedcode.things3.data.ai.** { *; }
-keepnames class com.culturedcode.things3.data.repository.** { *; }
-keepclassmembers class com.culturedcode.things3.data.repository.** { *; }

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

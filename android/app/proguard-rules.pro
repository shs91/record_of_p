# kotlinx-serialization: 직렬화 대상 DTO의 serializer 유지
-keepclassmembers class com.recordofp.app.data.poi.** {
    *** Companion;
}
-keepclasseswithmembers class com.recordofp.app.data.poi.** {
    kotlinx.serialization.KSerializer serializer(...);
}

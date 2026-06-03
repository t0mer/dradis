# ---- HiveMQ MQTT client + transitive deps (Netty, RxJava, jctools, dagger) ----
# The client relies heavily on reflection / ServiceLoader internally, so keep it
# and its native/runtime deps intact and silence warnings for optional ones.
-keep class com.hivemq.** { *; }
-keep class io.netty.** { *; }
-keep class org.jctools.** { *; }
-dontwarn com.hivemq.**
-dontwarn io.netty.**
-dontwarn org.jctools.**
-dontwarn org.slf4j.**
-dontwarn org.reactivestreams.**
-dontwarn io.reactivex.**
-dontwarn reactor.**
-dontwarn javax.annotation.**
-dontwarn java.lang.management.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---- kotlinx.serialization: keep generated serializers + annotations ----
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault,InnerClasses
-dontnote kotlinx.serialization.**
# Keep the @Serializable model classes and their generated $$serializer.
-keep class dev.tomerklein.dradis.**$$serializer { *; }
-keepclassmembers class dev.tomerklein.dradis.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class dev.tomerklein.dradis.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- Coroutines (debug agent / service loader) ----
-dontwarn kotlinx.coroutines.**

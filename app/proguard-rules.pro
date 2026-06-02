# HiveMQ MQTT client / Netty: keep reflectively-referenced internals.
-dontwarn io.netty.**
-dontwarn org.slf4j.**
-dontwarn com.hivemq.client.internal.**
-keep class io.netty.** { *; }

# kotlinx.serialization: keep generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class dev.tomerklein.dradis.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Manter Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Manter modelos de dados
-keep class com.antibet.data.local.entity.** { *; }
-keep class com.antibet.domain.model.** { *; }

# Manter Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Manter AccessibilityService
-keep class * extends android.accessibilityservice.AccessibilityService { *; }
-keep class com.antibet.service.accessibility.** { *; }

# Kotlinx Serialization (se usar)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# dnsjava — DNS parsing library used in AntiBetVpnService
-keep class org.xbill.DNS.** { *; }
-dontwarn org.xbill.DNS.**

# pcap4j — IP/UDP packet parsing and building used in AntiBetVpnService
-keep class org.pcap4j.** { *; }
-dontwarn org.pcap4j.**
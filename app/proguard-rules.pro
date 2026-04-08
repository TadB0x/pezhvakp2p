# Pezhvak ProGuard rules

# Keep Nostr event model
-keep class com.pezhvak.p2p.transport.nostr.NostrEvent { *; }
-keep class com.pezhvak.p2p.news.NewsRepository$NewsPayload { *; }
-keep class com.pezhvak.p2p.calls.CallManager$CallSignal { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer { *; }

# BouncyCastle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }

# WebRTC
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

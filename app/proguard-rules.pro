# Keep app models (used via reflection-free JSON binding in some paths)
-keep class com.myiptv.app.data.** { *; }

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep class com.bumptech.glide.load.data.parallax.** { *; }
-dontwarn com.bumptech.glide.**

# Kotlin
-dontwarn kotlin.**
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# Media3 / ExoPlayer (keeps renderer + manifest reflection intact when minifying)
-keep class androidx.media3.** { *; }

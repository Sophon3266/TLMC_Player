# ProGuard rules for TLMC Player

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }

# Media3
-keep class androidx.media3.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }


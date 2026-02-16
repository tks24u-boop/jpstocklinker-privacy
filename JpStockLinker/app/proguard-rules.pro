# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ===== Gson =====
# Gson uses generic type information stored in a class file when working with fields.
# Proguard removes such information by default, so configure it to keep all of it.
-keepattributes Signature

# For using GSON @Expose annotation
-keepattributes *Annotation*

# Gson specific classes
-dontwarn sun.misc.**

# Application classes that will be serialized/deserialized over Gson
-keep class jp.stocklinker.app.StockItem { *; }
-keep class jp.stocklinker.app.Group { *; }
-keep class jp.stocklinker.app.MasterStockItem { *; }
-keep class jp.stocklinker.app.NewsItem { *; }

# Prevent proguard from stripping interface information from TypeAdapter, TypeAdapterFactory,
# JsonSerializer, JsonDeserializer instances (so they can be used in @JsonAdapter)
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ===== RSS Parser =====
-keep class com.prof18.rssparser.** { *; }
-dontwarn com.prof18.rssparser.**

# ===== Widget =====
-keep class jp.stocklinker.app.StockWidgetProvider { *; }
-keep class jp.stocklinker.app.StockWidgetService { *; }
-keep class jp.stocklinker.app.StockRemoteViewsFactory { *; }

# ===== Debugging =====
# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

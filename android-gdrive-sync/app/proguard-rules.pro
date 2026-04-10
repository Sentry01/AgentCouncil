# Google API client
-keepattributes Signature
-keep class com.google.api.** { *; }
-keep class com.google.gson.** { *; }
-dontwarn com.google.api.client.**

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker

# DataStore
-keepclassmembers class * {
    @androidx.datastore.preferences.protobuf.ProtoField <fields>;
}

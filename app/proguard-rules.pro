# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Gson serializes these models by reflection and their field names are part of
# the Controller/Target HTTP contract.
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.mories.control_droid.core.model.** { *; }

# PairedDeviceStore/MacroStore build `object : TypeToken<List<X>>() {}` at
# runtime so Gson can deserialize a List<X>. R8 can merge/strip that anonymous
# subclass even with Signature kept, which drops the generic type information
# Gson needs and crashes with "TypeToken must be created with a type argument"
# the moment getAll() runs in a release build. Keep TypeToken and its
# subclasses so the generic signature survives.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# Android entrypoints referenced by the manifest and the MediaProjection flow.
-keep class com.mories.control_droid.core.control.AccessibilityController { *; }
-keep class com.mories.control_droid.features.target.ScreenCaptureService { *; }
-keep class com.mories.control_droid.features.target.ScreenPermissionActivity { *; }

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

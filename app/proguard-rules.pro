# The app is small: keep our own code untouched so widgets, receivers and tiles
# (which the system / Glance instantiate by class name) can never be stripped or renamed.
-keep class com.shdurazi.todo.** { *; }

# Glance instantiates these by name.
-keep class * extends androidx.glance.appwidget.action.ActionCallback { <init>(); }
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { <init>(); }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { <init>(); }

# Keep line numbers for readable crash reports.
-keepattributes SourceFile,LineNumberTable

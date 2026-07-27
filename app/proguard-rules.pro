# --- Vosk + JNA -------------------------------------------------------------
# Vosk talks to native code through JNA, which resolves classes, methods, and struct
# fields reflectively at runtime. R8 can't see those uses, so they must be kept explicitly
# or transcription crashes with UnsatisfiedLinkError / NoSuchMethodError in release builds.
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { *; }
-keep class * implements com.sun.jna.Library { *; }
-keepclassmembers class * extends com.sun.jna.Structure { *; }
-keep class * extends com.sun.jna.Callback { *; }

# JNA references desktop-JVM classes that don't exist on Android; they are never hit at runtime.
-dontwarn java.awt.**
-dontwarn com.sun.jna.**

# --- App entry points -------------------------------------------------------
# Alarm/boot receivers and the call service are referenced from the manifest / PendingIntents;
# keep their names stable so intents resolve after shrinking.
-keep class com.smartreminder.core.alarm.ReminderAlarmReceiver { *; }
-keep class com.smartreminder.core.alarm.BootReceiver { *; }
-keep class com.smartreminder.feature.call.IncomingCallService { *; }
-keep class com.smartreminder.feature.call.CallActivity { *; }

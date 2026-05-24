# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK tools configuration.

# Paho MQTT
-keep class org.eclipse.paho.** { *; }
-dontwarn org.eclipse.paho.**

# Keep the service
-keep class com.example.mqttpublisher.** { *; }

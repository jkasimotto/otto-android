# Add project specific ProGuard rules here.
# You can control the set of applied rules by editing the proguardFiles
# directive in build.gradle.kts.

# If you keep only Kotlin/Java reflection metadata, shrinkers have
# more context about the code that is actually used when building
# release versions.
-keepattributes *Annotation*
-dontwarn kotlin.reflect.jvm.internal.**

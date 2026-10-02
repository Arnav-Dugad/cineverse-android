# ============================================================
# R8 for CineVerse
#
# The release build is minified and resource-shrunk, which takes the APK from
# ~90 MB to a fraction of it. Three libraries need help, all for the same
# reason: they look types up by name at runtime, and R8 cannot see that.
# ============================================================

# ---------- kotlinx.serialization ----------
# The compiler plugin generates a `Companion.serializer()` for every @Serializable
# class. R8 does not know anything calls it, so without this every DTO loses its
# serializer and every network response fails to parse — in release only, which
# is the worst kind of bug to find.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.cineverse.app.**$$serializer { *; }
-keepclassmembers class com.cineverse.app.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.cineverse.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# The navigation routes are @Serializable objects and data classes; keeping them
# whole is what makes type-safe navigation survive minification.
-keep class com.cineverse.app.nav.Route { *; }
-keep class com.cineverse.app.nav.Route$* { *; }

# ---------- Retrofit ----------
-keepattributes Signature, RuntimeVisibleAnnotations, AnnotationDefault
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>

# ---------- OkHttp ----------
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---------- Firebase ----------
# Firestore maps documents onto classes reflectively.
-keepattributes *Annotation*
-keepclassmembers class com.cineverse.app.data.model.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ---------- Glance ----------
# The widget receiver and its action callbacks are only ever named in XML or
# resolved by class name, so nothing in code refers to them.
-keep class com.cineverse.app.widget.** { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-keep class * implements androidx.glance.appwidget.action.ActionCallback { *; }

# ---------- The installer's callback ----------
-keep class com.cineverse.app.update.InstallReceiver { *; }

# Line numbers in a crash report are worth the few kilobytes.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

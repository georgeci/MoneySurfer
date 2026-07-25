# Shared R8/ProGuard rules for :androidApp and :androidApp-offline release builds.

# --- Kermit PII muting (issue #154) ---
# We deliberately do NOT strip Kermit v/d/i via `-assumenosideeffects`: Kermit's
# public logging API is inline (`log.i { ... }`), so the message lambda is inlined
# into the caller and R8 can't reliably elide it. Instead the release min-severity
# is raised to Warn at startup (domain/.../logging/LoggingConfig.configureLogging,
# wired through di/initKoin), which short-circuits Verbose/Debug/Info *before* the
# message lambda runs — so the PII string is never even built. Redaction
# (domain/.../logging/PiiRedaction) is the second layer at the call sites.

# --- Crash deobfuscation: keep line numbers, hide original source file name ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- kotlinx.serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
# Scoped to @Serializable types only — official kotlinx.serialization R8 rules.
# Keep the Companion of @Serializable classes.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
# Keep the companion's serializer() for @Serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
# Keep INSTANCE + serializer() of @Serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Room ---
# `Room.databaseBuilder<T>(context, path)` without a factory lambda (see
# data-local/src/androidMain DatabaseBuilder.android.kt) locates the generated
# `<T>_Impl` on Android via Class.forName, so the impl class must survive both
# shrinking and renaming. room-runtime ships this exact consumer rule, but the
# release build must not silently regress if that artifact ever stops shipping
# it — duplicated here on purpose.
-keep class * extends androidx.room.RoomDatabase { void <init>(); }
# BundledSQLiteDriver calls native code; JNI resolves callbacks by name.
# sqlite-bundled ships consumer rules — this only silences optional-dependency
# warnings if they ever surface in a minified build.
-dontwarn androidx.sqlite.**

# --- Koin ---
# Koin 4.x resolution is compile-time (koin-annotations codegen + reified
# inline get()) — no reflection over constructors, so definitions and
# ViewModels need no keep rules. The single reflective path in koin-android,
# KoinFragmentFactory (Class.forName over Fragment names), is unused: the app
# is Compose-only with no Fragments. Suppress spurious missing-class warnings
# from Koin's optional interop paths (mirrors Koin's own consumer rules).
-dontwarn org.koin.**

# --- Firebase component discovery ---
# firebase-common reads the `com.google.firebase.components:<FQCN>` meta-data
# entries the library manifests contribute and instantiates each registrar
# reflectively via its no-arg constructor (ComponentDiscovery.instantiate ->
# Class.forName + getDeclaredConstructor()). R8 keeps the *class* (the name is
# in the manifest) but has no reference to the constructor, so full mode shrinks
# it away: discovery then fails with
#   ComponentDiscovery: Could not instantiate …CrashlyticsRegistrar
#   Caused by: NoSuchMethodException: …CrashlyticsRegistrar.<init> []
# and every API backed by that registrar is missing at runtime — for Crashlytics
# that means `FirebaseCrashlytics.getInstance()` throws "FirebaseCrashlytics
# component is not present.", killing the app in Application.onCreate (the
# reporter is a Koin single resolved by initKoin) and, being pre-init, never
# reporting the crash. Release-only, so it only ever showed up in the Play /
# App Distribution builds.
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}

# --- Enums (often serialized by name) ---
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

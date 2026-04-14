# Keep Kotlin metadata and generic signatures used by Gson/Room/generated code.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# UniFFI / JNA bridge classes are loaded and invoked across native boundaries.
-keep class uniffi.rust_core.** { *; }
-keep class com.sun.jna.** { *; }
-keep class com.sun.jna.ptr.** { *; }
-keep class * extends com.sun.jna.Structure { *; }
-keep class * extends com.sun.jna.IntegerType { *; }
-keep class * implements com.sun.jna.Callback { *; }
-keepclassmembers class * {
    native <methods>;
}
-dontwarn com.sun.jna.**

# Room database/DAO generated implementations.
-keep class erl.webdavtoon.AppDatabase { *; }
-keep class erl.webdavtoon.AppDatabase_Impl { *; }
-keep class erl.webdavtoon.FavoritePhotoDao { *; }
-keep class erl.webdavtoon.FavoritePhotoDao_Impl { *; }
-keep class erl.webdavtoon.FavoritePhotoEntity { *; }

# Glide generated API surface.
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl { *; }
-keep class erl.webdavtoon.GlideApp { *; }
-keep class erl.webdavtoon.GlideOptions { *; }
-keep class erl.webdavtoon.GlideRequest { *; }
-keep class erl.webdavtoon.GlideRequests { *; }

# Gson-backed persisted models must retain stable field names.
-keep class erl.webdavtoon.Photo { *; }
-keep class erl.webdavtoon.WebDavSlotConfig { *; }
-keep class erl.webdavtoon.LegacyWebDavSlotConfig { *; }

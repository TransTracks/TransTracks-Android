-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Kotlin
-dontwarn kotlin.**

# Legacy HTTP https://issuetracker.google.com/issues/37070898
-dontnote android.net.http.*
-dontnote org.apache.commons.codec.**
-dontnote org.apache.http.**

# Misc
-dontnote **
-dontwarn org.jaxen.**
-dontwarn org.w3c.dom.Node
-dontwarn sun.misc.Unsafe

# Picasso
-dontwarn com.squareup.okhttp.**
-dontwarn okhttp3.internal.platform.*

# Realm
-keep class com.gettotallyrad.pictapgo.data.** { *; }
-keep class io.realm.annotations.RealmModule
-keep @io.realm.annotations.RealmModule class *
-keep class io.realm.internal.Keep
-keep @io.realm.internal.Keep class *
-dontwarn javax.**
-dontwarn io.realm.**

# The WebView bridge is reached by name from JavaScript; R8 must not rename or strip it.
-keepclassmembers class com.meridian.shell.MainActivity$Deck {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.meridian.shell.MainActivity$Deck { *; }

# The Termux result receiver is instantiated by the system from the manifest.
-keep class com.meridian.shell.TermuxResult { *; }

# SystemProperties is reached through reflection with a getprop fallback; keep the call site honest.
-dontwarn android.os.SystemProperties

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# R8 rules for Memora's release build.
#
# Activities, receivers, the widget and the FileProvider are named in the manifest, so R8
# keeps them on its own. Backups use org.json from Android itself.

# Navigation routes (ui/Route.kt) are found through their kotlinx.serialization serializers.
-keep class com.smnexstudio.memora.ui.Route { *; }
-keep class com.smnexstudio.memora.ui.Route$* { *; }

# Readable stack traces in crash reports: keep line numbers, hide the source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

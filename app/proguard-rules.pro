# R8 rules for Memora's release build.
#
# Activities, receivers, the widget and the FileProvider are named in the manifest, so R8
# keeps them on its own. Memora uses no reflection or serialisation libraries (backups use
# org.json from Android itself), so no extra keep rules are needed.

# Readable stack traces in crash reports: keep line numbers, hide the source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

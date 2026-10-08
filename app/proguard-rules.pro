# R8 rules for release builds. Room, kotlinx-serialization, Compose and Navigation ship their own consumer rules.
# Activities, services and receivers named in the manifest are kept automatically.

# Readable stack traces in Play Console (the bundle's mapping.txt decodes the names).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

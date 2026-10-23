# R8 configuration for the release build.
#
# The app has no third-party dependencies and uses no reflection, so the default rules
# generated from the manifest and resources already keep everything that is entered from
# outside the app (activities, the application class, views inflated from XML).

# Keep the line numbers of a crash readable while still renaming classes. The mapping file
# is written to app/build/outputs/mapping/release/mapping.txt — keep it for any build that
# is handed to someone else, it is the only way to read a stack trace afterwards.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# org.json ships with the Android framework, so nothing to keep for it here.

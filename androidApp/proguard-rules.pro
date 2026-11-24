# The libraries (MapLibre, Compose) already ship their own rules.
# Readable error messages in beta crashes.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
# No obfuscation: the code is open source and Play Protect distrusts obfuscated apps
# that install updates. Shrinking stays on.
-dontobfuscate
# secp256k1-kmp finds its native loader by reflection (Nostr signatures, #3).
-keep class fr.acinq.secp256k1.** { *; }

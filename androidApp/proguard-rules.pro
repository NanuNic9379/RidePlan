# Le librerie (MapLibre, Compose) portano gia' le loro regole.
# Messaggi d'errore leggibili nei crash delle beta.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
# Niente offuscamento: il codice e' open source e Play Protect diffida delle app offuscate
# che installano aggiornamenti. Il rimpicciolimento resta attivo.
-dontobfuscate

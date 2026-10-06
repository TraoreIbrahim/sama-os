# Les fonctions du système appelées par réflexion (Wi-Fi, Bluetooth…) passent par des interfaces cachées
# implémentées avec java.lang.reflect.Proxy : rien à garder de notre côté.

# Jakarta Mail : ses protocoles (IMAP, SMTP) et ses types MIME sont chargés par leur nom.
-keep class com.sun.mail.** { *; }
-keep class javax.mail.** { *; }
-keep class javax.activation.** { *; }
-keep class myjava.awt.datatransfer.** { *; }
-dontwarn java.awt.**
-dontwarn java.beans.**
-dontwarn javax.security.**
-dontwarn com.sun.mail.**
-dontwarn javax.activation.**
-dontwarn javax.mail.**
-dontwarn myjava.awt.datatransfer.**

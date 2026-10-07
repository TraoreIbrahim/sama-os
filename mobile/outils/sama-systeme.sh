#!/bin/sh
# Fait d'un émulateur « Android Open Source » (sans Google) un aperçu de Sama OS :
# animation de démarrage, Accueil installé comme appli système, français de Côte d'Ivoire,
# accès du Pouls accordés comme le ferait le système.
#
# L'émulateur doit tourner avec un système modifiable :
#   emulator -avd Sama -writable-system -no-snapshot-load
# puis :
#   outils/sama-systeme.sh            (ANDROID_SERIAL=emulator-5554 s'il y a plusieurs appareils)
set -e

ADB=${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}
MOBILE=$(cd "$(dirname "$0")/.." && pwd)
APK=$MOBILE/accueil/build/outputs/apk/release/accueil-release.apk
APK_REGLAGES=$MOBILE/reglages/build/outputs/apk/release/reglages-release.apk
APK_APPAREIL=$MOBILE/appareil/build/outputs/apk/release/appareil-release.apk
APK_SUGU=$MOBILE/sugu/build/outputs/apk/release/sugu-release.apk
ANIM=$MOBILE/systeme/build/bootanimation.zip
PAQUET=africa.samaos.accueil

attendre_demarrage() {
    $ADB wait-for-device
    until [ "$($ADB shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
}

redemarrer() {
    $ADB reboot
    sleep 5
    attendre_demarrage
    $ADB root >/dev/null
    sleep 2
    $ADB wait-for-device
}

echo "• Construction de l'Accueil et de l'animation"
python3 "$MOBILE/outils/droits-reglages.py" >/dev/null
(cd "$MOBILE" && ./gradlew -q :accueil:assembleRelease :reglages:assembleRelease :telephone:assembleRelease :contacts:assembleRelease :messages:assembleRelease :horloge:assembleRelease :calculatrice:assembleRelease :notes:assembleRelease :agenda:assembleRelease :fichiers:assembleRelease :photos:assembleRelease :dictaphone:assembleRelease :lecteur:assembleRelease :griot:assembleRelease :appareil:assembleRelease :sugu:assembleRelease :mail:assembleRelease :clavier:assembleRelease)
[ -f "$ANIM" ] || python3 "$MOBILE/systeme/animation/fabriquer.py"
"$MOBILE/systeme/surcouche-android/fabriquer.sh" >/dev/null
"$MOBILE/systeme/surcouche-systemui/fabriquer.sh" >/dev/null

echo "• Accès au système"
attendre_demarrage
$ADB root >/dev/null
sleep 2
$ADB wait-for-device
# La première fois, Android lève la vérification du système et demande un redémarrage.
if $ADB remount 2>&1 | grep -q "reboot"; then
    echo "  (premier passage : redémarrage)"
    redemarrer
    $ADB remount >/dev/null
fi

echo "• Sama dans le système"
# Une ancienne installation ordinaire masquerait la version système : on la retire.
$ADB uninstall "$PAQUET" >/dev/null 2>&1 || true
$ADB shell mkdir -p /system/priv-app/SamaAccueil
$ADB push "$APK" /system/priv-app/SamaAccueil/SamaAccueil.apk >/dev/null
# Les Réglages de Sama, signés avec la clé de la plateforme : ils passent avant les Paramètres d'Android.
$ADB uninstall africa.samaos.reglages >/dev/null 2>&1 || true
$ADB shell mkdir -p /system/priv-app/SamaReglages
$ADB push "$APK_REGLAGES" /system/priv-app/SamaReglages/SamaReglages.apk >/dev/null
# L'Appareil photo de Sama : appli système, car Android ne laisse que l'appareil du système répondre
# aux autres applis qui demandent une photo ou une vidéo (IMAGE_CAPTURE, VIDEO_CAPTURE).
$ADB uninstall africa.samaos.appareil >/dev/null 2>&1 || true
$ADB shell mkdir -p /system/app/SamaAppareil
$ADB push "$APK_APPAREIL" /system/app/SamaAppareil/SamaAppareil.apk >/dev/null
# Sugu, le magasin de Sama : il installe sans seconde question les applis qu'il a vérifiées (INSTALL_PACKAGES).
$ADB uninstall africa.samaos.sugu >/dev/null 2>&1 || true
$ADB shell mkdir -p /system/priv-app/SamaSugu
$ADB push "$APK_SUGU" /system/priv-app/SamaSugu/SamaSugu.apk >/dev/null
$ADB push "$MOBILE/systeme/privapp-permissions-samaos.xml" /system/etc/permissions/ >/dev/null
$ADB shell mkdir -p /system/etc/default-permissions
$ADB push "$MOBILE/systeme/default-permissions-samaos.xml" /system/etc/default-permissions/ >/dev/null
$ADB push "$ANIM" /product/media/bootanimation.zip >/dev/null
# Les réglages de constructeur de Sama : pas d'écran « Passage à … » d'Android, Pouls dans chaque Espace,
# loupe du texte ronde.
$ADB push "$MOBILE/systeme/build/SamaAndroid.apk" /product/overlay/SamaAndroid.apk >/dev/null
$ADB shell chmod 644 /product/overlay/SamaAndroid.apk
# Cinq appuis sur le bouton marche : SystemUI lance le SOS des Réglages de Sama.
$ADB push "$MOBILE/systeme/build/SamaSystemUI.apk" /product/overlay/SamaSystemUI.apk >/dev/null
$ADB shell chmod 644 /product/overlay/SamaSystemUI.apk
$ADB shell setprop persist.sys.locale fr-CI
$ADB shell setprop persist.sys.timezone Africa/Abidjan
$ADB shell settings put global device_name "Sama"
# Les profils (Espaces) passent par des fonctions du système hors SDK public : on les autorise sur l'émulateur.
# Dans Sama OS compilé, l'Accueil sera signé avec la clé du système et n'en aura pas besoin.
$ADB shell settings put global hidden_api_policy 1
# Les réglages partagés du bouclier et des soldes existent dès le départ (vides : tout est actif), pour que
# chaque appli lise tout de suite ce que la personne change ensuite dans les Réglages.
for cle in sama_bouclier sama_soldes_alertes; do
    [ "$($ADB shell settings get global $cle | tr -d '\r')" = "null" ] && $ADB shell "settings put global $cle '{}'"
done
# Navigation par gestes, comme dans les maquettes : un trait en bas, pas de boutons.
$ADB shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.gestural

echo "• Redémarrage sur Sama"
redemarrer

echo "• Réglages d'après démarrage"
$ADB shell cmd role add-role-holder android.app.role.HOME "$PAQUET" 0
$ADB shell cmd notification allow_listener "$PAQUET/$PAQUET.Ecouteur"
$ADB shell appops set "$PAQUET" WRITE_SETTINGS allow
# Sur un émulateur déjà configuré, les droits par défaut ne sont pas rejoués : on les accorde à la main.
$ADB shell pm grant "$PAQUET" android.permission.READ_PHONE_STATE
$ADB shell pm grant "$PAQUET" android.permission.READ_CONTACTS
$ADB shell pm grant "$PAQUET" android.permission.READ_SMS
$ADB shell pm grant "$PAQUET" android.permission.POST_NOTIFICATIONS
# La recherche de la Cour lit le nom des fichiers (accès accordé ici comme le ferait le système de Sama).
$ADB shell appops set "$PAQUET" MANAGE_EXTERNAL_STORAGE allow
for droit in READ_PHONE_STATE ACCESS_FINE_LOCATION BLUETOOTH_CONNECT BLUETOOTH_SCAN SEND_SMS POST_NOTIFICATIONS READ_SMS CALL_PHONE; do
    $ADB shell pm grant africa.samaos.reglages android.permission.$droit
done
# Sugu : la notification « Ouvert à vos proches » et les applis que les proches envoient.
$ADB shell pm grant africa.samaos.sugu android.permission.POST_NOTIFICATIONS

echo "• Applis de Sama : Téléphone, Contacts, Messages, Horloge, Calculatrice, Notes"
# Signées avec la clé de la plateforme, installées comme des applis ordinaires ; Android leur confie
# ensuite les appels (rôle DIALER) et les SMS (rôle SMS).
for appli in telephone contacts messages horloge calculatrice notes agenda fichiers photos dictaphone lecteur griot mail clavier; do
    $ADB install -r "$MOBILE/$appli/build/outputs/apk/release/$appli-release.apk" >/dev/null
done
for droit in CALL_PHONE READ_CALL_LOG WRITE_CALL_LOG READ_CONTACTS READ_PHONE_STATE READ_PHONE_NUMBERS POST_NOTIFICATIONS; do
    $ADB shell pm grant africa.samaos.telephone android.permission.$droit
done
for droit in READ_CONTACTS WRITE_CONTACTS READ_PHONE_STATE CALL_PHONE; do
    $ADB shell pm grant africa.samaos.contacts android.permission.$droit
done
# Le clavier de Sama devient le clavier du téléphone. Sur l'émulateur, le clavier du Mac compte comme un
# clavier physique : on demande à Android de montrer quand même le clavier à l'écran.
$ADB shell ime enable africa.samaos.clavier/.Clavier >/dev/null
$ADB shell ime set africa.samaos.clavier/.Clavier >/dev/null
# Un seul clavier : celui d'Android reste installé (« ime enable » le remet), mais n'ajoute plus le globe sous le clavier.
$ADB shell ime disable com.android.inputmethod.latin/.LatinIME >/dev/null 2>&1 || true
$ADB shell settings put secure show_ime_with_hard_keyboard 1
# Mail : reconnaître les expéditeurs connus, prévenir des nouveaux mails.
for droit in READ_CONTACTS POST_NOTIFICATIONS; do
    $ADB shell pm grant africa.samaos.mail android.permission.$droit
done
for droit in SEND_SMS RECEIVE_SMS READ_SMS RECEIVE_MMS READ_CONTACTS READ_PHONE_STATE POST_NOTIFICATIONS CALL_PHONE; do
    $ADB shell pm grant africa.samaos.messages android.permission.$droit
done
$ADB shell cmd role add-role-holder android.app.role.DIALER africa.samaos.telephone 0
$ADB shell cmd role add-role-holder android.app.role.SMS africa.samaos.messages 0
$ADB shell cmd role add-role-holder android.app.role.BROWSER africa.samaos.griot 0
$ADB shell pm grant africa.samaos.horloge android.permission.POST_NOTIFICATIONS
$ADB shell pm grant africa.samaos.notes android.permission.POST_NOTIFICATIONS
for droit in READ_CALENDAR WRITE_CALENDAR POST_NOTIFICATIONS; do
    $ADB shell pm grant africa.samaos.agenda android.permission.$droit
done
# Fichiers voit tout le stockage partagé (« accès à tous les fichiers »). Les Fichiers d'Android restent :
# ils servent aussi de sélecteur aux autres applis ; l'Accueil cache seulement leur icône.
$ADB shell appops set africa.samaos.fichiers MANAGE_EXTERNAL_STORAGE allow
for droit in READ_MEDIA_IMAGES READ_MEDIA_VIDEO ACCESS_MEDIA_LOCATION; do
    $ADB shell pm grant africa.samaos.photos android.permission.$droit
done
$ADB shell appops set africa.samaos.photos MANAGE_EXTERNAL_STORAGE allow
for droit in CAMERA RECORD_AUDIO; do
    $ADB shell pm grant africa.samaos.appareil android.permission.$droit
done
for droit in RECORD_AUDIO READ_MEDIA_AUDIO POST_NOTIFICATIONS; do
    $ADB shell pm grant africa.samaos.dictaphone android.permission.$droit
done
for droit in READ_MEDIA_AUDIO READ_MEDIA_VIDEO POST_NOTIFICATIONS; do
    $ADB shell pm grant africa.samaos.lecteur android.permission.$droit
done
# Les applis d'Android que Sama remplace passeraient avant (choix demandé à chaque fois) : on les met de côté,
# sans les effacer (pm enable les remet). Le Téléphone d'Android reste pour les appels d'urgence.
# L'Agenda de Sama reçoit aussi les rappels (EVENT_REMINDER) : celui d'Android peut donc s'effacer.
# Les informations d'urgence de l'écran verrouillé sont celles des Réglages de Sama (fiche, contacts, 185/180/170).
for paquet in com.android.contacts com.android.deskclock com.android.calculator2 com.android.calendar com.android.gallery3d com.android.camera2 com.android.emergency; do
    $ADB shell pm disable-user --user 0 $paquet >/dev/null 2>&1 || true
done

$ADB shell input keyevent KEYCODE_HOME
echo "Sama est installé dans le système."

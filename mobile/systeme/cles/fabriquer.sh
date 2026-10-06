#!/bin/sh
# Les clés « platform » publiques d'AOSP (build/make/target/product/security), celles des images
# « test-keys » de l'émulateur. Signer Sama avec elles lui donne, sur l'émulateur seulement, les droits
# du système (Wi-Fi, Bluetooth, SIM…), comme le vrai Sama OS signé avec ses propres clés.
# Elles sont publiques : JAMAIS pour un vrai téléphone ni une version distribuée.
# Elles ne sont pas versionnées : la première construction les télécharge chez AOSP et vérifie le certificat.
set -e
cd "$(dirname "$0")"
[ -f plateforme.p12 ] && exit 0
AOSP=https://android.googlesource.com/platform/build/+/refs/heads/main/target/product/security
EMPREINTE=C8:A2:E9:BC:CF:59:7C:2F:B6:DC:66:BE:E2:93:FC:13:F2:FC:47:EC:77:BC:6B:2B:0D:52:C1:1F:51:19:2A:B8
for f in platform.pk8 platform.x509.pem; do
    [ -f "$f" ] || curl -fsSL "$AOSP/$f?format=TEXT" | base64 -d > "$f"
done
if [ "$(openssl x509 -in platform.x509.pem -noout -fingerprint -sha256 | cut -d= -f2)" != "$EMPREINTE" ]; then
    echo "Le certificat téléchargé n'est pas celui des clés de test d'AOSP : arrêt." >&2
    rm -f platform.pk8 platform.x509.pem
    exit 1
fi
openssl pkcs8 -inform DER -nocrypt -in platform.pk8 -out platform.key
openssl pkcs12 -export -legacy -in platform.x509.pem -inkey platform.key -name plateforme \
    -out plateforme.p12 -passout pass:android 2>/dev/null || \
openssl pkcs12 -export -in platform.x509.pem -inkey platform.key -name plateforme \
    -out plateforme.p12 -passout pass:android
rm -f platform.key

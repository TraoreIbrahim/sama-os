#!/bin/sh
# Construit la surcouche SystemUI de Sama (build/SamaSystemUI.apk), signée avec la clé de débogage.
set -e
ICI=$(cd "$(dirname "$0")" && pwd)
SDK=${ANDROID_HOME:-$HOME/Library/Android/sdk}
OUTILS=$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)
SORTIE=$ICI/../build
mkdir -p "$SORTIE/surcouche-systemui"
"$OUTILS/aapt2" compile --dir "$ICI/res" -o "$SORTIE/surcouche-systemui/res.zip"
"$OUTILS/aapt2" link -o "$SORTIE/surcouche-systemui/non-signee.apk" -I "$SDK/platforms/android-36/android.jar" \
    --manifest "$ICI/AndroidManifest.xml" --min-sdk-version 31 --target-sdk-version 36 \
    "$SORTIE/surcouche-systemui/res.zip"
"$OUTILS/apksigner" sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android --key-pass pass:android \
    --out "$SORTIE/SamaSystemUI.apk" "$SORTIE/surcouche-systemui/non-signee.apk"
echo "$SORTIE/SamaSystemUI.apk"

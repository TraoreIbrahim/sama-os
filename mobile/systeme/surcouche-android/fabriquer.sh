#!/bin/sh
# Construit la surcouche Android de Sama (build/SamaAndroid.apk), signée avec la clé de débogage.
set -e
ICI=$(cd "$(dirname "$0")" && pwd)
SDK=${ANDROID_HOME:-$HOME/Library/Android/sdk}
OUTILS=$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)
SORTIE=$ICI/../build
mkdir -p "$SORTIE/surcouche"
# Le paysage Banco devient le fond d'écran par défaut d'Android.
python3 "$ICI/fond.py" "$SORTIE/surcouche/res/drawable-nodpi/default_wallpaper.png"
"$OUTILS/aapt2" compile --dir "$ICI/res" -o "$SORTIE/surcouche/res.zip"
"$OUTILS/aapt2" compile --dir "$SORTIE/surcouche/res" -o "$SORTIE/surcouche/fond.zip"
"$OUTILS/aapt2" link -o "$SORTIE/surcouche/non-signee.apk" -I "$SDK/platforms/android-36/android.jar" \
    --manifest "$ICI/AndroidManifest.xml" --min-sdk-version 31 --target-sdk-version 36 \
    "$SORTIE/surcouche/res.zip" "$SORTIE/surcouche/fond.zip"
"$OUTILS/apksigner" sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android --key-pass pass:android \
    --out "$SORTIE/SamaAndroid.apk" "$SORTIE/surcouche/non-signee.apk"
echo "$SORTIE/SamaAndroid.apk"

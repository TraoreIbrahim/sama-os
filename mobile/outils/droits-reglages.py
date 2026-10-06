#!/usr/bin/env python3
"""Recopie les droits protégés du manifeste des Réglages dans la liste privapp du système.
Android refuse de démarrer si une appli de /system/priv-app demande un droit privilégié absent de la liste :
on la tient à jour à chaque construction.
Toute permission « signature|privileged » du manifeste (SCHEDULE_EXACT_ALARM, CALL_PRIVILEGED…) doit porter
tools:ignore="ProtectedPermissions" pour être recopiée ici : sinon l'émulateur ne démarre plus."""
import re, os
racine = os.path.join(os.path.dirname(__file__), '..')
man = open(os.path.join(racine, 'reglages/src/main/AndroidManifest.xml')).read()
droits = re.findall(r'<uses-permission android:name="([^"]+)"\s*\n?\s*tools:ignore="ProtectedPermissions"', man)
chemin = os.path.join(racine, 'systeme/privapp-permissions-samaos.xml')
s = open(chemin).read()
bloc = ''.join(f'        <permission name="{d}" />\n' for d in droits)
s = re.sub(r'(<privapp-permissions package="africa\.samaos\.reglages">\n)(.*?)(    </privapp-permissions>)', lambda m: m.group(1) + bloc + m.group(3), s, flags=re.S)
open(chemin, 'w').write(s)
print(len(droits), 'droits pour les Réglages')

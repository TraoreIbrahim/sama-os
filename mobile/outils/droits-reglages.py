#!/usr/bin/env python3
"""Recopie les droits protégés du manifeste des applis système de Sama (Réglages, Sugu) dans la liste privapp.
Android refuse de démarrer si une appli de /system/priv-app demande un droit privilégié absent de la liste :
on la tient à jour à chaque construction.
Toute permission « signature|privileged » du manifeste (SCHEDULE_EXACT_ALARM, INSTALL_PACKAGES…) doit porter
tools:ignore="ProtectedPermissions" pour être recopiée ici : sinon l'émulateur ne démarre plus."""
import re, os
racine = os.path.join(os.path.dirname(__file__), '..')
APPLIS = {'reglages': 'africa.samaos.reglages', 'sugu': 'africa.samaos.sugu'}
chemin = os.path.join(racine, 'systeme/privapp-permissions-samaos.xml')
s = open(chemin).read()
for module, paquet in APPLIS.items():
    man = open(os.path.join(racine, module, 'src/main/AndroidManifest.xml')).read()
    droits = re.findall(r'<uses-permission android:name="([^"]+)"\s*\n?\s*tools:ignore="ProtectedPermissions"', man)
    bloc = ''.join(f'        <permission name="{d}" />\n' for d in droits)
    motif = r'(<privapp-permissions package="' + re.escape(paquet) + r'">\n)(.*?)(    </privapp-permissions>)'
    if re.search(motif, s, flags=re.S):
        s = re.sub(motif, lambda m: m.group(1) + bloc + m.group(3), s, flags=re.S)
    else:
        s = s.replace('</permissions>', f'    <privapp-permissions package="{paquet}">\n{bloc}    </privapp-permissions>\n</permissions>')
    print(len(droits), 'droits pour', module)
open(chemin, 'w').write(s)

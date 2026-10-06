# Proche en proche — protocole, version 1

Recevoir sans data les applis et mises à jour de Sama et de Sugu, depuis un **point Sama** (ordinateur Sama
d'une école, d'une mairie, d'un cybercafé) et, bientôt, depuis le téléphone d'un contact.

## Le principe de sécurité

Le transport n'est pas protégé et n'a pas besoin de l'être : ce qui fait foi, c'est **la fiche signée** de
l'éditeur. Un fichier peut passer par n'importe qui ; s'il ne correspond pas exactement à sa fiche, il est
supprimé sans rien installer.

Avant d'installer, le téléphone vérifie, dans l'ordre :

1. **la signature de la fiche**, par une clé d'éditeur connue du système (`assets/editeurs/<nom>.pem` de la
   bibliothèque `proches` ; personne ne peut en ajouter) ;
2. **la version** : une appli absente ou plus récente que celle du téléphone, jamais une plus ancienne ;
3. **le fichier entier** : même taille, même empreinte SHA-256 que la fiche ;
4. **l'appli** : le paquet, la version et le certificat de signature de l'APK sont ceux de la fiche.

## La fiche

```json
{"type": "appli", "paquet": "africa.samaos.kalan", "nom": "Kalan", "version": 12, "versionNom": "1.3",
 "taille": 12582912, "sha256": "…", "certificat": "…", "editeur": "sugu", "signature": "…"}
```

`certificat` est l'empreinte SHA-256 du certificat qui signe l'APK. `signature` est une signature ECDSA P-256
(SHA-256), en base64, des champs suivants joints par des retours à la ligne, dans cet ordre :

```
sama-proches-1, type, paquet, nom, version, versionNom, taille, sha256, certificat, editeur
```

## Le point Sama

Du HTTP/1.1 tout simple sur le réseau local, port 8765, annoncé en mDNS (`_samapoint._tcp`, attribut `nom`) :

- `GET /proches/v1/catalogue` → `{"point": {"nom": "Lycée du quartier"}, "paquets": [fiches]}` ;
- `GET /proches/v1/fichier/<sha256>` → le fichier ; avec `Range: bytes=N-`, la suite (réponse 206), pour
  reprendre où la réception s'était arrêtée.

Un point ne fait que fournir : il ne peut rien pousser ni installer. Le téléphone écarte les fiches mal signées
et peut bloquer un point qui a servi un fichier refusé. Quand le réseau de l'école n'annonce pas le point,
on l'ajoute par son adresse (Réglages › Proche en proche).

Point de référence pour les essais : `outils/point-sama.py` (avec `--debit` pour simuler une liaison lente).
Fiches de test : `outils/signer-paquet.py`, avec la clé d'éditeur de test (`systeme/cles-proches`,
émulateur seulement). Les vraies clés de Sama et de Sugu seront gardées dans un HSM, à deux personnes.

## Pas encore fait

- **Entre téléphones** (maquettes i6-envoyer-appli, i6-emetteur, i6-maj-voisin, i6-sugu-proches) : Wi-Fi Direct,
  le même protocole une fois le groupe formé. La réception s'ouvre 10 minutes à la demande de la personne ; seuls
  les contacts enregistrés des deux côtés se voient, sans diffuser de nom. Reste à choisir comment deux téléphones
  se reconnaissent sans qu'un inconnu qui connaît votre numéro puisse se faire passer pour un contact.
- **Mises à jour du système** : elles viendront avec Sama compilé (paquets OTA d'AOSP, mêmes vérifications).
- **Le point Sama du bureau** : servir ce protocole depuis un ordinateur Sama (annonce mDNS comprise).

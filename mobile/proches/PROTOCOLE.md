# Proche en proche — protocole, version 1

Recevoir sans data les applis et mises à jour de Sama et de Sugu, depuis un **point Sama** (ordinateur Sama
d'une école, d'une mairie, d'un cybercafé) ou depuis le téléphone d'un proche reconnu.

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

## La vitrine

Ce que Sugu montre d'une appli est signé lui aussi, par le même éditeur que la fiche, et joint à elle dans le
catalogue (`"vitrine": {…}`) :

```json
{"paquet": "africa.samaos.dictaphone", "version": 1, "editeurNom": "Sama", "resume": "…", "description": "…",
 "categorie": "ecole", "horsLigne": true, "sansTraceur": true, "icone": "<sha256 de l'image>",
 "droits": ["Le micro, pour enregistrer"], "editeur": "test-emulateur", "signature": "…"}
```

Champs signés, joints par des retours à la ligne :

```
sugu-vitrine-1, paquet, version, editeurNom, resume, description, categorie, horsLigne (1/0),
sansTraceur (1/0), icone, droits joints par « | », editeur
```

Une vitrine mal signée, ou qui ne correspond pas au paquet et à la version de sa fiche, est ignorée : l'appli
reste proposée, sans description. L'icône est servie comme un fichier (`/proches/v1/fichier/<sha256>`) et
vérifiée par son empreinte.

## Le point Sama

Du HTTP/1.1 tout simple sur le réseau local, port 8765, annoncé en mDNS (`_samapoint._tcp`, attribut `nom`) :

- `GET /proches/v1/catalogue` → `{"point": {"nom": "Lycée du quartier"}, "paquets": [fiches]}` ;
- `GET /proches/v1/fichier/<sha256>` → le fichier ; avec `Range: bytes=N-`, la suite (réponse 206), pour
  reprendre où la réception s'était arrêtée.

Un point ne fait que fournir : il ne peut rien pousser ni installer. Le téléphone écarte les fiches mal signées
et peut bloquer un point qui a servi un fichier refusé. Quand le réseau de l'école n'annonce pas le point,
on l'ajoute par son adresse (Sugu › Autour, ou Réglages › Proche en proche).

Point de référence pour les essais : `outils/point-sama.py` (avec `--debit` pour simuler une liaison lente).
Fiches de test : `outils/signer-paquet.py`, avec la clé d'éditeur de test (`systeme/cles-proches`,
émulateur seulement). Les vraies clés de Sama et de Sugu seront gardées dans un HSM, à deux personnes.

## Se reconnaître entre proches (appairage par code QR)

Deux téléphones ne se voient que s'ils se sont **reconnus l'un l'autre, en personne** : chacun scanne le code
QR de l'autre (Réglages › Proche en proche › « Reconnaître un proche », ou l'Appareil photo).

- **L'identité du téléphone** : une clé EC P-256 créée une fois dans la puce de sécurité (Android Keystore),
  qui n'en sort jamais.
- **Le code QR** : `samaos:proche?v=1&n=<prénom>&k=<clé publique X.509, base64url>`. Ni numéro, ni compte.
- **Le secret partagé** : SHA-256 de l'accord ECDH entre la clé du téléphone et celle du proche. Les deux
  téléphones calculent le même ; personne d'autre ne le peut, même en ayant photographié les deux codes.
  C'est lui qui servira à se reconnaître au moment de l'échange, sans rien diffuser.
- **Le code de vérification** : 8 chiffres, les mêmes sur les deux téléphones (SHA-256 des deux clés publiques,
  dans l'ordre de leur écriture base64), qu'on compare à voix haute. S'ils diffèrent, quelqu'un a glissé un autre
  code : on annule.
- **Prudence** : un code reçu en photo ou par message ne se scanne jamais ; les écrans le disent.

## Entre téléphones de proches

Un téléphone ne se montre à ses proches que quand la personne l'ouvre (Sugu › Autour › « Ouvrir à mes proches »,
ou en envoyant une appli), pour **10 minutes**, avec une notification qui permet de fermer. Seuls les proches
reconnus **des deux côtés** se voient. Les secrets ne quittent jamais les Réglages : Sugu leur demande les
étiquettes et les preuves (fournisseur `africa.samaos.reglages.proches`, réservé aux applis signées comme le
système).

- **L'annonce** : mDNS `_samaproche._tcp`, sous un nom de service au hasard (`sama-xxxxxxxx`) et un port au
  hasard. `GET /proches/v1/annonce`, la seule requête publique, répond `{"v": 1, "n": "<nombre>", "e": [étiquettes]}` :
  un nombre au hasard de 128 bits, neuf à chaque ouverture, et une étiquette par proche, dans le désordre :
  `HMAC-SHA256(secret, "sama-proches-1|annonce|" + n)`, 16 octets en base64url. Celui qui n'est pas un proche ne
  voit qu'un nombre au hasard et des étiquettes qu'il ne peut ni comprendre ni relier d'une ouverture à l'autre.
- **Se trouver** : le téléphone qui voit l'annonce calcule, pour ce nombre, l'étiquette de chacun de ses proches ;
  s'il en retrouve une, c'est ce proche.
- **La preuve** : toutes les autres requêtes portent `Sama-Proche: <m>.<preuve>`, où `m` est un nombre neuf et
  `preuve = HMAC-SHA256(secret, "sama-proches-1|acces|" + n + "|" + m + "|" + "<MÉTHODE> <chemin>")`, 16 octets.
  Le téléphone qui répond retrouve le proche dont le secret donne cette preuve ; il refuse une preuve absente,
  fausse ou déjà servie (`403`).
- **Catalogue et fichiers** : les mêmes que ceux d'un point Sama. Un téléphone ne donne que les applis dont le
  fichier installé est exactement celui d'une fiche signée qu'il a reçue (d'un point ou d'un proche) : il garde
  la fiche et la vitrine à l'installation. Les réglages « Donner » s'appliquent (partage permis, seulement
  branché avec la batterie au-dessus de 50 %, limite par jour), sauf pour ce qu'on envoie soi-même à un proche.
- **La proposition** : `POST /proches/v1/proposition` `{"sha256": …, "port": …}` (« je t'envoie ce fichier »).
  Le téléphone qui la reçoit, seulement s'il est ouvert, va lire la fiche chez l'envoyeur (son adresse, le port
  donné) avec sa propre preuve ; il ne la montre que si elle est signée et plus récente que ce qu'il a, et la
  personne choisit. S'il l'a déjà, ou si la personne refuse : `POST /proches/v1/reponse`
  `{"sha256": …, "reponse": "deja" | "non"}`.
- **Ce qui n'est pas caché** : sur un Wi-Fi partagé, le contenu n'est pas chiffré ; un autre appareil du réseau
  peut voir quelles applis passent (jamais un nom ni un numéro). Le chiffrement par le secret partagé viendra avec
  Wi-Fi Direct.

## Les alertes du bouclier entre proches

Le secret partagé sert aussi à « Un proche veille sur vous » : la personne choisit, sur son propre téléphone,
un proche reconnu à prévenir par SMS quand son bouclier arrête une arnaque grave. Le SMS reste lisible
partout et finit par `#SA1.<t>.<code>.<preuve>` : `t` l'heure d'envoi (secondes, base 36), `code` le type
d'arnaque (S, M, A, P, L, I, F ; E pour un essai), `preuve = HMAC-SHA256(secret, "sama-proches-1|acces|alerte|" + t + "|" + code)`,
16 octets en base64url. Le téléphone du proche vérifie la preuve (fournisseur des Réglages, méthode `verifier`),
refuse une alerte déjà reçue (même preuve) sans se fier à l'heure de l'expéditeur, et signale une imitation.
Seul le type part : jamais le contenu des messages, les numéros ni le solde. Le texte n'utilise que l'alphabet
des SMS (GSM 7 bits) : un seul SMS.

## Pas encore fait

- **Wi-Fi Direct** (sans routeur) : le même protocole une fois le groupe formé ; à essayer sur deux vrais
  téléphones.
- **Mises à jour du système** : elles viendront avec Sama compilé (paquets OTA d'AOSP, mêmes vérifications).
- **Le point Sama du bureau** : servir ce protocole depuis un ordinateur Sama (annonce mDNS comprise).

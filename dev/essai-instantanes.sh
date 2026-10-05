#!/bin/sh
# Essai des instantanés (/usr/libexec/samaos/instantanes.py) dans la session d'essai de la machine virtuelle, sur un petit
# disque Btrfs fictif (image en mémoire) : instantané, mise à jour coupée net, restauration, rangement au démarrage.
# scripts/mode-direct.sh commande "cat > /tmp/e.sh" < dev/essai-instantanes.sh && scripts/mode-direct.sh commande "sudo sh /tmp/e.sh"
set -u
E=/tmp/essai-btrfs; T=/usr/libexec/samaos/instantanes.py
umount -R $E/racine 2>/dev/null; umount $E/haut 2>/dev/null; losetup -D 2>/dev/null; rm -rf $E; mkdir -p $E/haut $E/racine
truncate -s 600M $E/disque.img
L=$(losetup --show -f $E/disque.img)
mkfs.btrfs -q -f $L
mount $L $E/haut
for v in @ @home @instantanes; do btrfs -q subvolume create $E/haut/$v; done
R=$E/haut/@
mkdir -p $R/etc/NetworkManager/system-connections $R/var/lib/samaos $R/boot $R/usr/bin $R/.instantanes $R/home
echo "sama:x:1000:1000::/home/sama:/bin/bash" > $R/etc/passwd
echo 'VERSION_ID="0.2"' > $R/etc/os-release
echo "ancien" > $R/usr/bin/programme
echo "wifi-bureau" > $R/etc/NetworkManager/system-connections/Bureau.nmconnection
chmod 600 $R/etc/NetworkManager/system-connections/Bureau.nmconnection
echo noyau > $R/boot/vmlinuz-6.12.0-essai; echo initrd > $R/boot/initrd.img-6.12.0-essai
ln -s boot/vmlinuz-6.12.0-essai $R/vmlinuz; ln -s boot/initrd.img-6.12.0-essai $R/initrd.img
umount $E/haut
mount -o subvol=@ $L $E/racine
mount -o subvol=@instantanes $L $E/racine/.instantanes
export SAMA_RACINE_SYSTEME=$E/racine SAMA_INSTANTANES=$E/racine/.instantanes SAMA_DISQUE=$L SAMA_HAUT=$E/haut \
       SAMA_UUID=$(blkid -s UUID -o value $L)
echo "== 1. Instantané manuel"
python3 $T creer manuel "avant examens"
echo "== 2. Avant une mise à jour de 3 paquets (marque posée)"
python3 $T avant-maj 3
cat $E/racine/var/lib/samaos/maj-en-cours.cfg
echo "== 3. La mise à jour commence… et le courant saute"
echo "nouveau (à moitié installé)" > $E/racine/usr/bin/programme
echo 'VERSION_ID="0.3"' > $E/racine/etc/os-release
echo "aminata:x:1001:1001::/home/aminata:/bin/bash" >> $E/racine/etc/passwd
echo "wifi-maison" > $E/racine/etc/NetworkManager/system-connections/Maison.nmconnection
echo "== Menu de démarrage"
cat $E/racine/.instantanes/grub.cfg
grub-script-check $E/racine/.instantanes/grub.cfg && echo "(syntaxe GRUB correcte)"
ID=$(sed -n 's/set sama_instantane="\(.*\)"/\1/p' $E/racine/var/lib/samaos/maj-en-cours.cfg)
echo "== 4. Restauration (comme restauration-init) vers $ID"
python3 $T restaurer $ID maj-interrompue && echo "(restauré)"
mount -o subvolid=5 $L $E/haut
ls $E/haut
echo "-- programme : $(cat $E/haut/@/usr/bin/programme)  ·  version : $(cat $E/haut/@/etc/os-release)"
echo "-- comptes :"; cat $E/haut/@/etc/passwd
echo "-- Wi-Fi :"; ls -l $E/haut/@/etc/NetworkManager/system-connections/
echo "-- marques : $(ls $E/haut/@/var/lib/samaos/)"; cat $E/haut/@/var/lib/samaos/restauration.json
umount $E/haut
echo "== 5. Démarrage suivant : rangement"
python3 $T demarrage
mount -o subvolid=5 $L $E/haut; ls $E/haut; umount $E/haut
echo "== 6. État vu par Réglages"
python3 $T etat | python3 -m json.tool

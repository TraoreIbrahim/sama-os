# Clés de test de la plateforme (émulateur seulement)

`platform.pk8` et `platform.x509.pem` sont les clés **publiques** de test d'AOSP
(`build/make/target/product/security`), celles qui signent les images « test-keys » de l'émulateur
(empreinte SHA-256 `C8:A2:E9:BC:…:2A:B8`). Elles ne sont pas dans le dépôt : à la première construction,
`fabriquer.sh` les télécharge chez AOSP, vérifie l'empreinte du certificat et en fait `plateforme.p12` pour Gradle.

Signées avec elles, l'Accueil et les Réglages de Sama ont sur l'émulateur les droits du système,
comme le vrai Sama OS les aura avec ses propres clés. N'importe qui peut signer avec ces clés :
ne jamais les utiliser pour un vrai téléphone ni pour une version distribuée.

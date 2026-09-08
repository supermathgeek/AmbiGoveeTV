# Confidentialité

AmbiGovee est conçu pour fonctionner principalement en réseau local.

## Données locales

L'application conserve localement sur la TV :

- l'adresse locale et les identifiants d'association JointSpace de la Philips ;
- la liste des appareils Govee configurés (IP locale, identifiant LAN/SKU, position) ;
- les réglages de l'app ;
- les états de lumière nécessaires pour restaurer chaque lampe après une synchro.

Ces informations ne sont pas envoyées à un serveur AmbiGovee.

## Flux TV

AmbiGovee ne capture ni image ni vidéo. Il lit uniquement les valeurs RGB calculées par l'Ambilight via l'API locale Philips.

## Govee

La synchronisation utilise le protocole LAN Govee et ne nécessite pas de clé cloud Govee.

## GitHub

Les builds officiels peuvent contacter l'API publique GitHub Releases au lancement afin de vérifier s'il existe une version plus récente. Cette requête est une vérification de version et ne contient pas les identifiants Philips/Govee de l'utilisateur.

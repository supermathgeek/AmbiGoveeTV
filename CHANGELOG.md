# Changelog

## 1.5.0

- Nouvelle interface TV plus simple et plus propre.
- Suppression du réglage manuel de luminosité : elle suit automatiquement l’Ambilight.
- Connexion / déconnexion individuelle des appareils Govee sans les supprimer.
- Suppression définitive séparée d’un appareil.
- Déconnexion / réassociation de la TV Philips sans perdre les lampes Govee.
- Bouton de vérification manuelle des mises à jour.
- Vérification GitHub automatique à chaque lancement pour les builds officiels.
- Assets GitHub stables `AmbiGoveeTV.apk` et `AmbiGoveeTV-Windows.zip` pour un téléchargement direct.
- Navigation D-pad simplifiée sur l’écran principal.

## 1.5.0

- support de plusieurs appareils Govee LAN en même temps ;
- ajout/suppression de lampes depuis l'assistant ;
- position par lampe : pièce/plafond, gauche, droite, au-dessus ou sous la TV ;
- chaque lampe reçoit la zone Ambilight correspondant à sa position ;
- migration automatique de l'ancienne configuration mono-lampe ;
- restauration indépendante de la couleur/luminosité normale de chaque lampe ;
- scan Govee multi-appareils ;
- test de compatibilité de toutes les lampes configurées ;
- vérification automatique des GitHub Releases à chaque lancement des builds officiels ;
- téléchargement d'une nouvelle APK depuis l'app puis passage par l'installateur Android ;
- l'installation d'une mise à jour reste soumise aux autorisations/confirmations Android ;
- compteur de lampes actives/synchronisées sur le dashboard.

## 1.3.0

- nouvelle interface TV ;
- navigation D-pad explicite ;
- focus visible et animation TV ;
- suppression du slider au profit de boutons +/- adaptés à la télécommande ;
- assistant d'installation en 4 étapes ;
- association Philips par PIN dans l'app ;
- scan Govee LAN depuis l'app ;
- fallback de scan local si le multicast est bloqué ;
- test de compatibilité Philips/Govee ;
- aperçu de la couleur Ambilight en direct sur le dashboard ;
- structure préparée pour GitHub Actions et releases signées.
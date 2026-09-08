# AmbiGovee TV

AmbiGovee TV synchronise en local les couleurs **Philips Ambilight** avec un ou plusieurs appareils **Govee compatibles LAN**.

Le but : installer l'app directement sur une Philips Android TV / Google TV compatible, configurer les lumières une fois, puis ne plus avoir besoin d'un PC pour la synchro.

## Ce que fait l'app

- lit les couleurs Ambilight via l'API locale Philips JointSpace ;
- pilote plusieurs Govee en parallèle via le protocole LAN UDP ;
- permet d'assigner chaque lampe à une position : pièce/plafond, gauche, haut, droite ou bas ;
- chaque lampe suit la zone Ambilight correspondant à sa position ;
- ne passe pas par le cloud Govee pour la synchro ;
- ne capture pas la vidéo de la TV ;
- n'allume jamais une lampe toute seule ;
- mémorise séparément la couleur/luminosité normale de chaque lampe ;
- restaure les lumières normales quand la TV passe en veille ;
- propose Direct / Cinéma / Doux et une intensité maximale ;
- interface Android TV navigable à la télécommande ;
- vérifie les **GitHub Releases** au lancement des builds officiels et propose les nouvelles versions.

## Installation simple

### Pour un utilisateur normal

Dans **GitHub Releases**, télécharger `AmbiGovee-vX.Y.Z-Windows.zip`, décompresser puis lancer :

```text
INSTALLER_WINDOWS.bat
```

L'installateur télécharge Android Platform Tools, se connecte en ADB et installe l'APK déjà signée.

Avant cela, sur la TV :

1. Paramètres > À propos > **Build Android TV** : appuyer 7 fois.
2. Ouvrir **Options pour les développeurs**.
3. Activer **Débogage USB / ADB**.
4. Noter l'adresse IP de la TV.

Une fois AmbiGovee installé, ADB peut être désactivé : il n'est pas nécessaire pour la synchro quotidienne.

### Ensuite, directement dans AmbiGovee

L'assistant fait le reste :

1. association Philips par PIN ;
2. activation demandée de **Contrôle LAN** dans Govee Home ;
3. scan automatique des Govee du réseau ;
4. choix d'une première lampe ;
5. choix de sa position ;
6. ajout éventuel d'autres lampes ;
7. test de compatibilité de la TV et de toutes les lampes.

Aucune clé Govee cloud n'est nécessaire.

## Positions des lumières

- **Pièce / plafond** : suit une couleur globale calculée depuis toutes les zones Ambilight ;
- **Gauche** : suit le côté gauche de la TV ;
- **Au-dessus** : suit le haut de la TV ;
- **Droite** : suit le côté droit ;
- **Sous la TV** : suit le bas lorsqu'il existe, sinon repasse automatiquement en couleur globale.

Cette logique permet par exemple d'utiliser un plafonnier global + deux lampes latérales qui réagissent différemment.

## Compatibilité

AmbiGovee ne promet pas « tous les modèles Philips/Govee ». L'app teste les fonctions dont elle a réellement besoin.

### Philips

Requis :

- Android TV / Google TV capable d'exécuter l'APK ;
- API JointSpace accessible ;
- endpoint Ambilight `measured` utilisable.

### Govee

Requis pour chaque appareil :

- option **Contrôle LAN** disponible et activée ;
- appareil détectable par le protocole LAN ;
- contrôle local RGB et luminosité utilisable.

Voir [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).

## Mises à jour intégrées

Les APK construites par le workflow GitHub reçoivent automatiquement le nom du dépôt (`owner/repo`) au build.

Au lancement, AmbiGovee interroge :

```text
GitHub Releases -> latest
```

Si la Release est plus récente que `BuildConfig.VERSION_NAME`, l'app propose **METTRE À JOUR**.

L'APK peut être téléchargée depuis l'app. Android garde cependant le dernier mot : selon la version/les réglages de la TV, l'utilisateur doit autoriser AmbiGovee comme source d'installation et confirmer l'installation. Il n'y a pas de mise à jour silencieuse forcée.

Pour que les mises à jour remplacent les anciennes sans perdre la configuration, **toutes les releases doivent être signées avec la même clé**. Voir [docs/PUBLISH_GITHUB.md](docs/PUBLISH_GITHUB.md).

## Build

```bash
gradle :app:assembleDebug -PAMBIGOVEE_REPO="owner/AmbiGoveeTV"
```

APK :

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Confidentialité

Le contrôle Philips/Govee utilise le réseau local. Les identifiants d'association Philips sont stockés dans les préférences privées Android de l'application.

La seule requête Internet ajoutée en 1.4 est la vérification de version GitHub lorsque l'APK a été construite avec un dépôt de mise à jour configuré.

Voir [PRIVACY.md](PRIVACY.md).

## Licence / crédits

Le protocole Philips JointSpace et son appairage ont été documentés par la communauté, notamment le projet `suborb/philips_android_tv`. AmbiGovee TV est distribué sous **GPL-2.0-only**.

## Statut

Version actuelle : **1.4.0**.

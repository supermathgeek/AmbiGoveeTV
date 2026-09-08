AmbiGoveeTV
> Synchronise l'Ambilight de téléviseurs Philips compatibles avec des éclairages Govee compatibles **LAN**, directement depuis Android TV / Google TV.
AmbiGoveeTV est un projet indépendant créé et maintenu par supermathgeek.  
Il n'est affilié ni à Philips, ni à Govee.
---
✨ À quoi sert AmbiGoveeTV ?
AmbiGoveeTV récupère les couleurs calculées par l'Ambilight d'un téléviseur Philips compatible et les transmet en temps réel à des éclairages Govee compatibles avec le contrôle LAN.
L'objectif est simple : profiter d'un éclairage de pièce cohérent avec l'Ambilight sans caméra supplémentaire ni boîtier HDMI dédié.
Exemple :
TV Philips Ambilight
plafonnier Govee au plafond
lampe Govee à gauche de la TV
lampe Govee à droite de la TV
Chaque lumière peut recevoir une couleur adaptée à sa position.
---
🚀 Fonctionnalités
Synchronisation Ambilight → Govee en réseau local
Fonctionnement directement sur Android TV / Google TV
Pas besoin de laisser un PC allumé après l'installation
Association Philips par PIN
Détection des appareils Govee compatibles sur le réseau
Support de plusieurs lampes
Position configurable pour chaque lumière :
pièce entière / plafond
gauche
haut
droite
bas
Profils de rendu :
Direct
Cinéma
Doux
Réglage de l'intensité maximale
Restauration de la couleur normale des lampes quand la TV passe en veille
AmbiGoveeTV n'allume jamais automatiquement une lampe qui était éteinte
Vérification des nouvelles versions via GitHub Releases
Interface conçue pour être utilisée à la télécommande
---
📺 Compatibilité
AmbiGoveeTV ne promet pas une compatibilité avec tous les téléviseurs Philips ni avec tous les produits Govee.
Philips
La TV doit notamment disposer de :
Ambilight
Android TV ou Google TV
API Philips JointSpace compatible
accès réseau local à l'API Ambilight
Govee
L'éclairage doit notamment disposer de :
couleurs RGB / RGBIC utilisables via le protocole LAN
option Contrôle LAN disponible dans Govee Home
connexion au même réseau local que la TV
L'application essaie de détecter les fonctions nécessaires au lieu de se baser uniquement sur une liste fixe de modèles.
> Un modèle non encore testé peut fonctionner.  
> Si tu testes un nouveau téléviseur ou un nouvel appareil Govee, ouvre une Issue pour partager le résultat.
---
🛠️ Installation
1. Préparer la TV Philips
Sur la TV :
Ouvre Paramètres
Va dans À propos
Appuie plusieurs fois sur Build Android TV jusqu'à l'activation des options développeur
Ouvre Options pour les développeurs
Active Débogage USB / ADB
La TV et le PC utilisés pour l'installation doivent être sur le même réseau local.
---
2. Préparer Govee
Dans l'application Govee Home :
Ouvre l'appareil à utiliser
Va dans ses paramètres
Active Contrôle LAN
Tous les appareils Govee utilisés doivent être sur le même réseau local que la TV.
---
3. Installer AmbiGoveeTV
Télécharge la dernière version dans :
GitHub → Releases → Latest
Puis utilise l'installateur Windows fourni avec la Release, ou installe directement l'APK avec ADB.
Après l'installation, AmbiGoveeTV se lance sur la TV et guide l'utilisateur étape par étape.
---
🧭 Première configuration
AmbiGoveeTV doit pouvoir guider l'utilisateur de cette manière :
détection de la TV Philips
demande d'association à la TV
affichage d'un PIN sur la Philips
saisie du PIN dans AmbiGoveeTV
activation demandée du contrôle LAN Govee
scan automatique des appareils Govee
sélection des lumières
choix de leur position
test de synchronisation
terminé
Aucune clé Philips ne doit être récupérée manuellement par l'utilisateur dans le fonctionnement normal de l'application.
---
💡 Plusieurs lampes
AmbiGoveeTV permet d'utiliser plusieurs éclairages en même temps.
Exemple :
```text
                  Lampe haute
                      ↑

Lampe gauche  ←   PHILIPS   →  Lampe droite

                 Plafonnier
              ambiance globale
```
Chaque appareil peut être associé à une zone différente de l'Ambilight.
---
🎬 Modes
⚡ Direct
Réaction rapide aux changements de scène.
Idéal pour :
jeux vidéo
contenus dynamiques
utilisateurs qui veulent un effet très proche de l'Ambilight
🎥 Cinéma
Transitions plus progressives et lumière moins agressive.
Idéal pour :
films
séries
visionnage dans le noir
🌙 Doux
Effet d'ambiance discret avec transitions lentes.
---
🔄 Mises à jour
AmbiGoveeTV peut vérifier les nouvelles versions publiées dans les GitHub Releases.
Lorsqu'une nouvelle version est disponible, l'application peut proposer son téléchargement.
Selon la version d'Android TV / Google TV, Android peut demander une confirmation avant l'installation d'une nouvelle APK.
Les versions officielles doivent conserver la même signature Android afin que les mises à jour puissent être installées sans supprimer l'application ni perdre sa configuration.
---
🔐 Confidentialité
AmbiGoveeTV est pensé pour fonctionner principalement sur le réseau local.
Les informations nécessaires à l'association Philips et aux appareils Govee sont stockées localement sur l'appareil.
Aucune donnée privée ne doit être ajoutée au dépôt GitHub.
Voir également : `PRIVACY.md`
---
⚠️ Avertissement
AmbiGoveeTV utilise des interfaces réseau disponibles sur des appareils Philips et Govee compatibles.
Ce projet :
n'est pas officiel
n'est pas affilié à Philips
n'est pas affilié à Govee
peut ne pas fonctionner sur certains firmwares ou certains modèles
peut nécessiter des adaptations après une mise à jour du fabricant
Utilise le projet à tes propres risques.
---
🐛 Signaler un bug
Ouvre une Issue et indique si possible :
modèle exact de la TV Philips
version Android TV / Google TV
modèle exact de l'appareil Govee
version AmbiGoveeTV
étape où le problème apparaît
comportement attendu
comportement obtenu
capture d'écran ou logs si disponibles
Merci d'éviter de publier :
clés Philips
mots de passe
adresses MAC
informations personnelles
données privées de ton réseau
---
💡 Proposer une amélioration
Les idées sont les bienvenues.
Tu peux ouvrir une Issue pour proposer :
un nouveau mode d'éclairage
une amélioration de l'interface
un nouveau type de position
la compatibilité avec un appareil supplémentaire
une amélioration de l'installation
une fonction de diagnostic
une optimisation des performances
Explique simplement ce que tu aimerais voir ajouté et pourquoi.
---
🤝 Contributions
AmbiGoveeTV est un projet créé et maintenu par supermathgeek.
Le projet reste sous ma direction, mais les retours, rapports de bugs, idées et contributions sont les bienvenus.
Tu peux :
ouvrir une Issue
proposer une Pull Request
tester AmbiGoveeTV sur de nouveaux appareils
améliorer la documentation
Les Pull Requests peuvent être discutées, modifiées ou refusées afin de garder le projet cohérent, stable et simple à utiliser.
---
⭐ Soutenir le projet
Si AmbiGoveeTV t'est utile :
mets une ⭐ au dépôt
signale les appareils compatibles
partage tes retours
propose des améliorations
Ça aide beaucoup le projet à évoluer.
---
📄 Licence
Copyright © 2026 supermathgeek
AmbiGoveeTV est distribué sous la licence présente dans le fichier `LICENSE`.
Certaines parties du projet peuvent s'appuyer sur des recherches ou projets communautaires tiers. Les mentions correspondantes sont regroupées dans `NOTICE.md`.
---
🧪 État du projet
AmbiGoveeTV est encore en développement.
La compatibilité, l'interface, l'installation et les performances vont continuer à évoluer au fil des tests sur différents téléviseurs et appareils Govee.
Les retours sont donc particulièrement utiles.

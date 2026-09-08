<div align="center">

# 🌈 AmbiGoveeTV

### Philips Ambilight × Govee, directement depuis ta TV.

Synchronise les couleurs de ton **Philips Ambilight** avec tes éclairages **Govee compatibles LAN**, sans caméra supplémentaire et sans laisser un PC allumé.

<br>

![Android TV](https://img.shields.io/badge/Android%20TV-compatible-3DDC84?logo=android&logoColor=white)
![Google TV](https://img.shields.io/badge/Google%20TV-compatible-4285F4?logo=google&logoColor=white)
![Local Network](https://img.shields.io/badge/Sync-Local%20Network-7C3AED)
![License](https://img.shields.io/github/license/supermathgeek/AmbiGoveeTV)
![Release](https://img.shields.io/github/v/release/supermathgeek/AmbiGoveeTV)

<br>

[📥 Télécharger Windows](https://github.com/supermathgeek/AmbiGoveeTV/releases/latest/download/AmbiGoveeTV-Windows.zip)
&nbsp;•&nbsp;
[📦 APK direct](https://github.com/supermathgeek/AmbiGoveeTV/releases/latest/download/AmbiGoveeTV.apk)
&nbsp;•&nbsp;
[🐛 Signaler un bug](https://github.com/supermathgeek/AmbiGoveeTV/issues)
&nbsp;•&nbsp;
[💡 Proposer une idée](https://github.com/supermathgeek/AmbiGoveeTV/issues)

</div>

---

## ✨ C'est quoi AmbiGoveeTV ?

AmbiGoveeTV récupère les couleurs calculées par l'Ambilight d'un téléviseur Philips compatible et les envoie en temps réel à des éclairages Govee sur le réseau local.

```text
📺 Philips Ambilight
        ↓
   AmbiGoveeTV
        ↓
💡 Govee LAN
```

Une fois installé sur la TV, **plus besoin de laisser un PC allumé**.

---

## 🚀 Fonctionnalités

| Fonction | Description |
|---|---|
| ⚡ **Synchronisation en direct** | Les couleurs Govee suivent l'Ambilight de la TV |
| 💡 **Plusieurs lampes** | Ajoute, connecte, déconnecte ou supprime chaque Govee indépendamment |
| 📍 **Positionnement** | Plafond, pièce entière, gauche, haut, droite ou bas |
| 🎬 **3 modes** | Direct, Cinéma et Doux |
| ✨ **Lumière adaptative** | Le rendu lumineux suit automatiquement l'Ambilight, sans réglage manuel |
| 🏠 **Réseau local** | La synchronisation principale ne dépend pas du cloud |
| 💾 **Restauration** | Les lampes retrouvent leur couleur normale quand la TV s'arrête |
| 🔒 **Respect de l'état des lampes** | AmbiGoveeTV n'allume pas une lampe que tu avais éteinte |
| 🔄 **Mises à jour** | Vérification des nouvelles versions via GitHub Releases |
| 🎮 **Interface TV** | Navigation pensée pour une télécommande |

---

## 💡 Plusieurs lampes, plusieurs zones

Chaque lampe peut suivre une partie différente de l'Ambilight.

```text
                 💡 Lampe haute
                       ↑

💡 Lampe gauche ←  📺 TV  → 💡 Lampe droite

                  💡 Plafonnier
                ambiance globale
```

Exemple :

- un plafonnier → **Pièce entière / Plafond**
- une lampe à gauche → **Gauche**
- une lampe à droite → **Droite**
- un bandeau au-dessus → **Haut**

---


## 🔌 Connecter / déconnecter un appareil

Dans **Gérer les appareils**, chaque Govee peut être :

- **connecté** à la synchronisation ;
- **déconnecté** temporairement sans perdre sa position ;
- déplacé vers une autre zone Ambilight ;
- supprimé définitivement d’AmbiGoveeTV.

La TV Philips peut également être réassociée ou déconnectée sans supprimer les appareils Govee enregistrés.

---

## 🎬 Modes d'éclairage

### ⚡ Direct
Réaction rapide aux changements de scène.

Idéal pour :
- jeux vidéo
- contenus dynamiques
- effet proche de l'Ambilight

### 🎥 Cinéma
Transitions plus fluides et moins agressives.

Idéal pour :
- films
- séries
- visionnage dans le noir

### 🌙 Doux
Effet discret avec transitions lentes.

Idéal pour :
- ambiance
- soirée
- lumière d'accompagnement

---

## 📺 Compatibilité

AmbiGoveeTV vise les appareils qui disposent réellement des fonctions nécessaires.

### Philips

La TV doit notamment avoir :

- **Ambilight**
- **Android TV ou Google TV**
- une API **Philips JointSpace** compatible
- l'accès réseau local aux données Ambilight

### Govee

L'appareil doit notamment avoir :

- un éclairage RGB/RGBIC compatible avec le **contrôle LAN**
- l'option **Contrôle LAN** disponible dans Govee Home
- une connexion au même réseau local que la TV

> Un modèle non encore listé peut fonctionner.  
> Si tu testes AmbiGoveeTV sur un nouvel appareil, ouvre une Issue pour partager le résultat.

---

## 🛠️ Installation

### 1. Préparer la TV Philips

Sur la TV :

1. ouvre **Paramètres**
2. va dans **À propos**
3. appuie plusieurs fois sur **Build Android TV** pour activer les options développeur
4. ouvre **Options pour les développeurs**
5. active **Débogage USB / ADB**

---

### 2. Préparer Govee

Dans **Govee Home** :

1. ouvre l'appareil à utiliser
2. ouvre ses paramètres
3. active **Contrôle LAN**

---

### 3. Installer AmbiGoveeTV

Télécharge la dernière version ici :

👉 **[GitHub Releases](https://github.com/supermathgeek/AmbiGoveeTV/releases/latest)**

Puis :

- **Windows :** clique sur le bouton Télécharger Windows en haut de cette page ;
- **APK :** utilise APK direct si tu veux installer manuellement avec ADB.

---

## 🧭 Première configuration

L'application est pensée pour guider l'utilisateur étape par étape :

1. détection de la TV Philips
2. association à la TV
3. affichage d'un PIN sur la Philips
4. saisie du PIN dans AmbiGoveeTV
5. demande d'activation du contrôle LAN Govee
6. scan automatique du réseau
7. sélection des appareils Govee
8. choix de leur position
9. test de synchronisation
10. terminé

L'objectif est que l'utilisateur **n'ait pas à récupérer manuellement une clé Philips ou un Device ID**.

---

## 🔄 Mises à jour

AmbiGoveeTV peut vérifier les nouvelles versions disponibles sur GitHub.

Quand une nouvelle version est publiée :

```text
Version installée : 1.5
Nouvelle version : 1.6
```

l'application peut proposer la mise à jour.

Selon Android TV / Google TV, une confirmation peut être demandée avant l'installation.

---

## 🔐 Confidentialité

AmbiGoveeTV est conçu pour fonctionner principalement sur le **réseau local**.

Les informations nécessaires à l'association Philips et aux appareils Govee sont stockées localement sur la TV.

Aucune donnée personnelle ne doit être publiée dans ce dépôt.

Voir aussi : [`PRIVACY.md`](PRIVACY.md)

---

## 🐛 Signaler un bug

Ouvre une **Issue** en indiquant si possible :

- modèle exact de la TV Philips
- version Android TV / Google TV
- modèle exact du Govee
- version d'AmbiGoveeTV
- ce qui devait se passer
- ce qui s'est réellement passé
- captures d'écran ou logs utiles

Merci de ne jamais publier :

- clés Philips
- mots de passe
- adresses MAC
- informations privées de ton réseau

👉 **[Créer une Issue](https://github.com/supermathgeek/AmbiGoveeTV/issues/new)**

---

## 💡 Une idée d'amélioration ?

Les idées sont les bienvenues.

Tu peux proposer :

- de nouveaux modes
- une meilleure interface
- de nouvelles positions de lampes
- la compatibilité avec de nouveaux appareils
- des optimisations
- une installation plus simple
- de nouvelles automatisations

👉 **[Proposer une idée](https://github.com/supermathgeek/AmbiGoveeTV/issues/new)**

---

## 🤝 Contributions

**AmbiGoveeTV est un projet créé et maintenu par [supermathgeek](https://github.com/supermathgeek).**

Le projet reste sous ma direction, mais les idées, retours, rapports de bugs et contributions sont les bienvenus.

Tu peux :

- ouvrir une **Issue**
- proposer une **Pull Request**
- tester l'application sur de nouveaux appareils
- améliorer la documentation

Les Pull Requests peuvent être discutées, modifiées ou refusées afin de garder le projet cohérent, stable et simple à utiliser.

---

## ⭐ Soutenir le projet

Si AmbiGoveeTV t'est utile :

- mets une ⭐ au dépôt
- partage tes tests de compatibilité
- signale les bugs
- propose tes idées

Ça aide énormément le projet à évoluer.

---

## ⚠️ Projet indépendant

AmbiGoveeTV est un projet communautaire indépendant.

Il n'est **ni affilié, ni sponsorisé, ni approuvé par Philips ou Govee**.

Le fonctionnement peut varier selon les modèles, firmwares et mises à jour des fabricants.

---

## 📄 Licence

Copyright © 2026 **supermathgeek**

AmbiGoveeTV est distribué sous la licence disponible dans [`LICENSE`](LICENSE).

Les éventuelles mentions de projets ou travaux tiers sont regroupées dans [`NOTICE.md`](NOTICE.md).

---

<div align="center">

### 🌈 AmbiGoveeTV

**Ton Ambilight. Toute ta pièce.**

</div>

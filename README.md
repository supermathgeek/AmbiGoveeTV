<div align="center">

# 🌈 AmbiGoveeTV

### Ton Ambilight Philips, dans toute ta pièce.

AmbiGoveeTV synchronise les couleurs de l'**Ambilight Philips** avec des éclairages **Govee compatibles LAN**, directement depuis Android TV / Google TV.

**Pas de caméra. Pas de PC à laisser allumé. Pas de compte AmbiGovee.**

<br>

[![Android TV](https://img.shields.io/badge/Android%20TV-compatible-3DDC84?logo=android&logoColor=white)](#compatibilite)
[![Google TV](https://img.shields.io/badge/Google%20TV-compatible-4285F4?logo=google&logoColor=white)](#compatibilite)
[![Release](https://img.shields.io/github/v/release/supermathgeek/AmbiGoveeTV)](https://github.com/supermathgeek/AmbiGoveeTV/releases/latest)
[![License](https://img.shields.io/github/license/supermathgeek/AmbiGoveeTV)](LICENSE)

<br>

## [🪟 Installer sur Windows](https://github.com/supermathgeek/AmbiGoveeTV/releases/latest/download/INSTALLER_AMBIGOVEE_WINDOWS.bat)

[📦 APK direct](https://github.com/supermathgeek/AmbiGoveeTV/releases/latest/download/AmbiGoveeTV.apk)
&nbsp;•&nbsp;
[📁 Pack Windows](https://github.com/supermathgeek/AmbiGoveeTV/releases/latest/download/AmbiGoveeTV-Windows.zip)
&nbsp;•&nbsp;
[🐛 Signaler un bug](https://github.com/supermathgeek/AmbiGoveeTV/issues)

</div>

---

## ✨ Ce que fait AmbiGoveeTV

```text
📺 Philips Ambilight
        ↓
   AmbiGoveeTV
        ↓
💡 Govee sur le réseau local
```

AmbiGoveeTV lit les couleurs calculées par l'Ambilight de la TV puis les transmet aux lumières Govee compatibles via le réseau local.

Une fois l'installation terminée, **le PC ne sert plus**.

### Fonctionnalités

- ⚡ synchronisation Ambilight → Govee en temps réel ;
- 💡 plusieurs appareils Govee ;
- 📍 zones **Pièce entière / Gauche / Haut / Droite / Bas** ;
- 🎬 modes **Direct**, **Cinéma** et **Doux** ;
- 🔒 une lampe éteinte par l'utilisateur **n'est jamais rallumée par AmbiGoveeTV** ;
- 💾 restauration de la couleur et de la luminosité normales après la synchro ;
- 🏠 synchronisation principale en **réseau local** ;
- 🎮 interface pensée pour une télécommande Android TV ;
- 🔄 vérification des mises à jour via GitHub Releases.

---

# 🚀 Installation

## Méthode recommandée — Windows

Télécharge simplement :

### 👉 [INSTALLER_AMBIGOVEE_WINDOWS.bat](https://github.com/supermathgeek/AmbiGoveeTV/releases/latest/download/INSTALLER_AMBIGOVEE_WINDOWS.bat)

Puis double-clique dessus.

L'installateur :

1. télécharge automatiquement la dernière APK officielle ;
2. télécharge Android Platform Tools si nécessaire ;
3. te demande l'adresse IP de la TV ;
4. se connecte à Android TV ;
5. installe ou met à jour AmbiGoveeTV ;
6. détecte le **profil Android réellement utilisé par la TV** ;
7. active l'application sur ce profil ;
8. lance AmbiGoveeTV.

Tu n'as **aucune commande ADB ou PowerShell à taper**.

> Windows peut afficher un avertissement pour un script téléchargé depuis Internet. Le fichier est lisible directement dans ce dépôt et ne contient aucune clé privée.

---

## Avant l'installation

Sur la TV Philips :

1. ouvre **Paramètres** ;
2. va dans **À propos** ;
3. appuie 7 fois sur **Build Android TV / Numéro de build** ;
4. ouvre **Options pour les développeurs** ;
5. active **Débogage USB / ADB** ;
6. vérifie que le PC et la TV sont sur le même réseau.

L'installateur demandera ensuite seulement l'**adresse IP locale de la TV**.

Après l'installation, le débogage ADB peut être désactivé : **AmbiGoveeTV n'en a pas besoin pour fonctionner**.

---

# 🧭 Première configuration

L'installation dans AmbiGovee est volontairement courte.

## 1 — Philips

AmbiGovee affiche un **QR code** sur la TV.

1. scanne-le avec ton téléphone ;
2. appuie sur **Démarrer l'association** ;
3. Philips affiche un PIN sur la TV ;
4. **laisse la fenêtre Philips ouverte** ;
5. entre le PIN sur ton téléphone.

L'association est ensuite conservée localement sur la TV. Le téléphone n'est plus nécessaire.

## 2 — Govee

Dans l'application **Govee Home**, active **Contrôle LAN** pour chaque lumière compatible.

Dans AmbiGovee :

1. choisis **Rechercher une lumière** ;
2. sélectionne l'appareil ;
3. choisis sa position ;
4. répète si tu as plusieurs lumières.

## 3 — Terminé

Lance la synchro et choisis ton mode :

- **Direct** — rapide et réactif ;
- **Cinéma** — plus fluide ;
- **Doux** — transitions plus lentes.

---

# 💡 Comportement des lumières

AmbiGoveeTV est conçu pour ne pas prendre le contrôle de ta pièce de façon agressive.

- une lumière déjà éteinte reste éteinte ;
- si tu éteins une lumière pendant un film, AmbiGovee ne la rallume pas ;
- la couleur et la luminosité normales sont mémorisées avant la synchro ;
- lorsque la TV se met en veille ou que tu quittes la synchro, AmbiGovee restaure l'état lumineux normal lorsqu'il peut le faire sans rallumer une lampe éteinte.

---

# 📺 Compatibilité

## Philips

Il faut notamment :

- une TV **Philips Ambilight** ;
- **Android TV** ou **Google TV** ;
- une API **JointSpace** compatible ;
- l'accès local aux couleurs Ambilight.

## Govee

Il faut notamment :

- un appareil couleur compatible avec le **contrôle LAN Govee** ;
- l'option **Contrôle LAN** disponible dans Govee Home ;
- la TV et le Govee sur le même réseau local.

La compatibilité est basée sur les capacités de l'appareil, pas sur une liste fermée de modèles.

Si un appareil fonctionne ou ne fonctionne pas, tu peux ouvrir une [Issue](https://github.com/supermathgeek/AmbiGoveeTV/issues).

---

# 🔄 Mises à jour

AmbiGoveeTV vérifie les nouvelles **GitHub Releases**.

Pour qu'une mise à jour soit reconnue :

- la release doit avoir une version supérieure ;
- l'APK doit garder le package `fr.ambigovee.tv` ;
- toutes les versions doivent être signées avec la **même clé Android** ;
- la release doit contenir l'asset stable `AmbiGoveeTV.apk`.

Android TV / Google TV peut demander une confirmation d'installation ou l'autorisation d'installer depuis cette source. AmbiGovee ne contourne pas les protections Android.

---

# 🔐 Confidentialité

La synchronisation Philips ↔ AmbiGovee ↔ Govee fonctionne principalement sur le **réseau local**.

Les identifiants nécessaires à l'association Philips et la configuration Govee sont stockés localement sur la TV.

Ne publie jamais dans une Issue :

- une clé d'association Philips ;
- un mot de passe ;
- une adresse MAC ;
- des informations privées de ton réseau.

Voir [`PRIVACY.md`](PRIVACY.md).

---

# 🐛 Bug / idée / contribution

Pour un bug, indique si possible :

- modèle de la TV ;
- version Android TV / Google TV ;
- modèle du Govee ;
- version d'AmbiGoveeTV ;
- comportement attendu ;
- comportement observé ;
- capture ou log utile sans donnée privée.

👉 [Ouvrir une Issue](https://github.com/supermathgeek/AmbiGoveeTV/issues/new)

Les Pull Requests sont les bienvenues, mais le projet reste maintenu et dirigé par **supermathgeek** afin de garder une expérience cohérente et simple.

---

# ⚠️ Projet indépendant

AmbiGoveeTV est un projet communautaire indépendant.

Il n'est **ni affilié, ni sponsorisé, ni approuvé par Philips ou Govee**.

Les noms et marques appartiennent à leurs propriétaires respectifs.

---

# 📄 Licence

Copyright © 2026 **supermathgeek**

Voir [`LICENSE`](LICENSE) et [`NOTICE.md`](NOTICE.md).

---

<div align="center">

## 🌈 AmbiGoveeTV

**Ton Ambilight. Toute ta pièce.**

</div>

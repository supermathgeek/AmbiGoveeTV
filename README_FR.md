<div align="center">

# 🌈 AmbiGoveeTV

### Philips Ambilight → Govee, directement sur Android TV / Google TV

**Transforme des lumières Govee LAN compatibles en extension de l'Ambilight de ta TV Philips — sans caméra, boîtier HDMI, Home Assistant, Raspberry Pi ou PC allumé en permanence.**

[![Android TV](https://img.shields.io/badge/Android%20TV-compatible-3DDC84?logo=android&logoColor=white)](#compatibilité)
[![Google TV](https://img.shields.io/badge/Google%20TV-compatible-4285F4?logo=google&logoColor=white)](#compatibilité)
[![Dernière version](https://img.shields.io/github/v/release/supermathgeek/AmbiGoveeTV?label=latest)](https://github.com/supermathgeek/AmbiGoveeTV/releases/latest)
[![Licence](https://img.shields.io/github/license/supermathgeek/AmbiGoveeTV)](LICENSE)

[English](README.md) · **Français**

<br>

### Télécharger

[**🪟 Installateur Windows**](https://github.com/supermathgeek/AmbiGoveeTV/releases/latest/download/AmbiGovee-Installer-Windows.exe)
&nbsp;&nbsp;
[**🍎 macOS — Apple Silicon**](https://github.com/supermathgeek/AmbiGoveeTV/releases/latest/download/AmbiGovee-Installer-macOS-Apple-Silicon.zip)
&nbsp;&nbsp;
[**🍎 macOS — Intel**](https://github.com/supermathgeek/AmbiGoveeTV/releases/latest/download/AmbiGovee-Installer-macOS-Intel.zip)
&nbsp;&nbsp;
[**🐧 Linux**](https://github.com/supermathgeek/AmbiGoveeTV/releases/latest/download/AmbiGovee-Installer-Linux-x86_64.tar.gz)

[**📦 Télécharger directement l'APK**](https://github.com/supermathgeek/AmbiGoveeTV/releases/latest/download/AmbiGoveeTV.apk)

</div>

---

## Peut-on synchroniser des Govee avec l'Ambilight d'une TV Philips ?

**Oui, avec des appareils compatibles.** AmbiGoveeTV récupère les couleurs déjà calculées par l'Ambilight d'une TV Philips Android TV / Google TV compatible via l'interface locale Philips JointSpace, puis envoie ces couleurs aux lumières Govee compatibles grâce au contrôle LAN Govee.

La TV Philips reste donc la source de l'effet Ambilight. AmbiGoveeTV l'étend simplement dans la pièce.

```text
TV Philips Ambilight
        │
        │ couleurs Ambilight locales
        ▼
   AmbiGoveeTV
        │
        │ réseau local
        ▼
 Lumières Govee compatibles
```

### Pourquoi c'est différent

- **Pas de caméra devant l'écran**
- **Pas de boîtier HDMI**
- **Pas de PC à laisser allumé**
- **Pas besoin de Home Assistant**
- **Pas besoin de Raspberry Pi**
- **Pas de compte AmbiGovee**
- La synchronisation principale Philips → Govee reste sur le **réseau local**
- Interface pensée pour une **télécommande Android TV**

---

# 🚀 Installation facile

AmbiGoveeTV possède maintenant un installateur guidé avec une vraie interface visuelle.

Il télécharge automatiquement Android Platform Tools et la dernière APK officielle, se connecte à la TV, gère les profils Android TV, installe l'application puis la lance.

## Windows

1. Télécharge [**AmbiGovee-Installer-Windows.exe**](https://github.com/supermathgeek/AmbiGoveeTV/releases/latest/download/AmbiGovee-Installer-Windows.exe).
2. Ouvre-le.
3. Suis les instructions affichées.
4. Entre l'adresse IP locale de ta TV Philips.

> L'installateur n'est pas encore signé numériquement. Windows SmartScreen peut donc afficher un avertissement. Le code source est visible dans [`installer/ambigovee_installer.py`](installer/ambigovee_installer.py).

## macOS

Choisis la version de ton Mac :

- **Apple Silicon (M1/M2/M3/M4/...)** : [télécharger](https://github.com/supermathgeek/AmbiGoveeTV/releases/latest/download/AmbiGovee-Installer-macOS-Apple-Silicon.zip)
- **Mac Intel** : [télécharger](https://github.com/supermathgeek/AmbiGoveeTV/releases/latest/download/AmbiGovee-Installer-macOS-Intel.zip)

Décompresse le ZIP puis ouvre **AmbiGovee Installer**.

> L'application macOS n'est pas encore notariée par Apple. Si macOS bloque le premier lancement, fais clic droit sur l'application puis **Ouvrir**.

## Linux

Télécharge [**AmbiGovee-Installer-Linux-x86_64.tar.gz**](https://github.com/supermathgeek/AmbiGoveeTV/releases/latest/download/AmbiGovee-Installer-Linux-x86_64.tar.gz), décompresse-le puis lance :

```bash
./AmbiGovee-Installer
```

L'installateur est fourni comme exécutable autonome : Python n'est **pas** nécessaire sur la machine de l'utilisateur.

---

# 📺 Avant l'installation

Sur la TV Philips :

1. Ouvre **Paramètres → À propos**.
2. Appuie 7 fois sur **Build / Numéro de build Android TV**.
3. Ouvre **Options pour les développeurs**.
4. Active **Débogage USB / Débogage ADB**.
5. Vérifie que l'ordinateur et la TV sont sur le même réseau local.
6. Repère l'adresse IP locale de la TV dans les réglages réseau.

Après l'installation, tu peux désactiver ADB. Il sert uniquement à installer ou mettre à jour l'application depuis un ordinateur.

---

# 📱 Association Philips : QR code + téléphone

La première association Philips utilise le téléphone comme second écran temporaire.

Cela permet de **laisser la fenêtre du PIN Philips ouverte sur la TV**.

1. AmbiGoveeTV affiche un QR code sur la TV.
2. Scanne-le avec ton téléphone connecté au même Wi-Fi/LAN.
3. Appuie sur **Démarrer l'association**.
4. Philips affiche un PIN sur la TV.
5. **Laisse la fenêtre du PIN Philips ouverte.**
6. Entre le PIN sur le téléphone.
7. AmbiGovee affiche : **TV connectée !**
8. Continue sur la TV et ajoute tes lumières Govee.

La page est servie directement par la TV sur le réseau local. Les identifiants Philips sont stockés sur la TV et ne sont pas renvoyés au téléphone.

---

# 💡 Configuration Govee

Pour chaque lumière Govee compatible :

1. Ouvre **Govee Home**.
2. Active **Contrôle LAN**.
3. Dans AmbiGoveeTV, choisis **Ajouter / Rechercher une lumière**.
4. Choisis sa position :
   - Pièce entière / plafond
   - Gauche
   - Haut
   - Droite
   - Bas

Tu peux ajouter plusieurs lumières et mettre un appareil en pause sans le supprimer.

---

# 🎬 Modes de synchronisation

| Mode | Idéal pour | Comportement |
|---|---|---|
| **Direct** | Jeux / contenu réactif | Réaction rapide |
| **Cinéma** | Films / séries | Transitions plus fluides |
| **Doux** | Ambiance | Changements plus calmes |

---

# 🛡️ Respect de l'état des lumières

AmbiGoveeTV est conçu pour respecter l'état réel de tes lampes.

- Une Govee éteinte n'est **pas rallumée** juste pour synchroniser.
- Si tu éteins une lumière pendant un film, AmbiGoveeTV ne la rallume pas.
- Avant la synchro, l'application peut mémoriser couleur et luminosité normales.
- Quand la synchro s'arrête, cet état est restauré seulement si cela ne force pas le rallumage d'une lumière volontairement éteinte.

---

# ✅ Compatibilité

## TV Philips

Il faut notamment une TV compatible avec :

- **Philips Ambilight**
- Android TV ou Google TV
- l'API locale **Philips JointSpace**
- l'accès aux couleurs Ambilight

La compatibilité peut varier selon la génération et le firmware.

## Lumières Govee

La lumière doit exposer des fonctions compatibles avec **Govee LAN Control**.

AmbiGoveeTV cherche les capacités de l'appareil plutôt que de se limiter à un seul modèle codé en dur.

Si ton matériel fonctionne — ou ne fonctionne pas — ouvre une [Issue](https://github.com/supermathgeek/AmbiGoveeTV/issues) avec le modèle de TV, le firmware, le modèle Govee et la version d'AmbiGoveeTV. Ne publie jamais de clés privées d'association.

---

# ❓ FAQ

### Peut-on utiliser des Govee avec Philips Ambilight sans caméra ?

Oui, lorsque la TV Philips et la Govee sont compatibles. AmbiGoveeTV utilise les couleurs Ambilight calculées par la TV elle-même : aucune caméra n'a besoin de filmer l'écran.

### Le PC doit-il rester allumé pendant un film ?

Non. Windows, macOS ou Linux servent uniquement à installer l'application Android TV. Ensuite, AmbiGoveeTV tourne directement sur la TV.

### Home Assistant est-il obligatoire ?

Non.

### Faut-il un Raspberry Pi ?

Non.

### Est-ce compatible avec Netflix, les HDMI et les applications intégrées ?

AmbiGoveeTV suit les informations Ambilight exposées par la TV. La disponibilité de ces couleurs dépend de la TV Philips, du firmware et de la source utilisée.

### La synchronisation passe-t-elle par le cloud ?

La synchronisation principale Philips → Govee est conçue pour fonctionner en local sur le LAN. GitHub sert pour les releases et la vérification des mises à jour.

### AmbiGoveeTV peut-il rallumer une lampe que j'ai volontairement éteinte ?

Non. La logique de synchronisation est conçue pour ne pas forcer le rallumage d'une Govee éteinte.

### Est-ce une application officielle Philips ou Govee ?

Non. AmbiGoveeTV est un projet open source indépendant, non affilié, sponsorisé ou approuvé par Philips ou Govee.

---

# 🔄 Mises à jour

AmbiGoveeTV vérifie les nouvelles versions via GitHub Releases.

Une application Android TV installée manuellement ne peut pas garantir une installation totalement silencieuse sur toutes les TV. Android peut demander une autorisation ou une confirmation.

Pour conserver les mises à jour en place :

- garder le package `fr.ambigovee.tv` ;
- signer toutes les versions avec le même certificat Android ;
- augmenter le `versionCode` ;
- conserver l'asset stable `AmbiGoveeTV.apk`.

L'installateur de bureau télécharge toujours la dernière APK stable et peut donc également servir à réparer ou mettre à jour l'installation.

---

# 🔐 Confidentialité

AmbiGoveeTV est construit autour du réseau local.

Ne publie jamais dans une Issue :

- identifiant ou clé d'association Philips ;
- mot de passe ;
- identifiant privé du réseau ;
- token privé.

Voir [PRIVACY.md](PRIVACY.md).

---

# 🐛 Bugs, compatibilité et contributions

Un appareil fonctionne ? Un bug apparaît ?

👉 [Ouvrir une Issue](https://github.com/supermathgeek/AmbiGoveeTV/issues/new)

Informations utiles :

- modèle de TV Philips ;
- version Android TV / Google TV ;
- firmware Philips ;
- modèle Govee ;
- version d'AmbiGoveeTV ;
- comportement attendu ;
- comportement observé ;
- logs nettoyés de toute donnée privée.

Les Pull Requests sont les bienvenues. Le projet est maintenu par **supermathgeek**.

---

---

# 📄 Licence

Copyright © 2026 **supermathgeek**

Voir [LICENSE](LICENSE) et [NOTICE.md](NOTICE.md).

<div align="center">

### 🌈 AmbiGoveeTV

**Ton Ambilight. Toute ta pièce.**

</div>

# Publier AmbiGovee sur GitHub

## Pourquoi une cle de signature stable est obligatoire

Android n'accepte une mise a jour par-dessus une ancienne APK que si la nouvelle APK est signee avec la meme cle.

Ne publie donc pas des APK debug generees avec des cles differentes a chaque version.

## 1. Creer une cle release une seule fois

Exemple Windows avec Java :

```powershell
keytool -genkeypair -v -keystore ambigovee-release.jks -alias ambigovee -keyalg RSA -keysize 4096 -validity 10000
```

Garde ce fichier hors du depot public et fais-en une sauvegarde privee.

## 2. Ajouter les secrets GitHub Actions

Dans le depot : Settings > Secrets and variables > Actions.

Ajouter :

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Pour obtenir `KEYSTORE_BASE64` sous PowerShell :

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("ambigovee-release.jks")) | Set-Clipboard
```

## 3. Creer une release

Le workflow `.github/workflows/release.yml` se declenche sur un tag de type :

```bash
git tag v1.4.0
git push origin v1.4.0
```

Il compile alors `app-release.apk`, signe avec ta cle GitHub Secret et joint l'APK a la GitHub Release.

## 4. Version suivante

Avant chaque release :

- augmenter `versionCode` ;
- changer `versionName` ;
- completer `CHANGELOG.md`.

Tant que `applicationId` et la cle de signature restent identiques, les utilisateurs peuvent mettre a jour sans refaire l'association Philips/Govee.

## Vérification de mise à jour intégrée

Le workflow de build passe automatiquement `${GITHUB_REPOSITORY}` à Gradle via `-PAMBIGOVEE_REPO`.

L'APK officielle connaît donc son propre dépôt GitHub sans nom d'utilisateur codé en dur. À chaque lancement, elle consulte la dernière GitHub Release publique. Si un tag plus récent est trouvé et qu'une APK release est jointe, AmbiGovee propose son téléchargement et lance l'installateur Android.

Android peut demander à l'utilisateur d'autoriser AmbiGovee comme source d'installation puis de confirmer la mise à jour. Cette confirmation n'est pas contournée.

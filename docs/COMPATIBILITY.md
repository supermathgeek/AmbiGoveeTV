# Compatibilité

## Principe

AmbiGovee privilégie la détection réelle au runtime plutôt qu'une liste marketing de modèles.

Une combinaison est considérée compatible lorsque :

1. la TV accepte l'association JointSpace ;
2. `/6/ambilight/measured` renvoie une couche Ambilight exploitable ;
3. chaque Govee choisi est découvert en LAN ;
4. chaque Govee répond à une requête d'état locale et expose une couleur RGB.

## Philips

Cible principale : Philips Android TV / Google TV avec Ambilight et API JointSpace compatible.

Des firmwares peuvent exposer `processed` avec uniquement des zéros alors que `measured` fonctionne ; AmbiGovee utilise donc `measured`.

Une TV Philips non-Android ne peut pas exécuter directement l'APK. Un fonctionnement via un boîtier Android externe pourrait être ajouté plus tard.

## Govee

L'option **Contrôle LAN** doit être visible et activée dans Govee Home pour chaque lampe.

Le simple fait qu'un produit Govee existe ne garantit pas qu'il expose les commandes LAN RGB nécessaires. L'assistant scanne et teste les appareils disponibles localement.

AmbiGovee 1.4 accepte plusieurs lampes simultanément. Une lampe peut être associée à :

- **Pièce / plafond** : couleur globale Ambilight ;
- **Gauche** : LEDs Ambilight du côté gauche ;
- **Au-dessus** : LEDs du haut ;
- **Droite** : LEDs du côté droit ;
- **Sous la TV** : LEDs du bas si la TV en possède ; sinon fallback vers la couleur globale.

## Réseau

TV et Govee doivent se voir sur le même réseau local. Les réseaux invités, l'isolation Wi-Fi/AP isolation, certains VLAN et certains routeurs peuvent bloquer UDP multicast.

AmbiGovee tente :

- multicast Govee `239.255.255.250:4001` ;
- réponses locales sur UDP 4002 ;
- contrôle sur UDP 4003 ;
- fallback de scan direct sur le sous-réseau local /24 pendant l'assistant.

# Bracelet Connecté Android

Application Android Kotlin (Jetpack Compose) avec trois fonctions :

1. Connexion BLE GATT au bracelet ESP32.
2. Retransmission en direct des mesures biométriques vers le backend Django (`webapp-backend`),
   via `POST /api/datas`.
3. Frontend connecté au backend (auth, dashboard temps réel, historique/statistiques), sur le
   même modèle que `webapp-frontend`.

## Architecture

```
BraceletBleClient (scan/connexion/notify BLE)
        │  Flow<BraceletEvent>
        ▼
DashboardViewModel ──► POST /api/datas   (retransmission temps réel)
        │
        └─ affichage direct de la dernière mesure (faible latence)

MeasurementsRepository ──poll 15s──► GET /api/datas/{userId}, GET /api/statistics/{userId}
        │
        ▼
Écrans Dashboard (cartes + résumé) / Stats (graphique + table)
```

Il n'y a plus de dépendance MQTT : la retransmission se fait directement en HTTP vers le backend.

## Ce qui est en place

- `BLE` via `domain/BraceletBleClient.kt` (scan/connexion/notifications) et
  `domain/BraceletMeasurementCodec.kt` (décodage JSON / texte délimité / binaire).
- Authentification (login/register/session persistée) via `data/auth/AuthRepository.kt`
  (DataStore Preferences, mêmes clés que le frontend web : `pdg.token` / `pdg.user` /
  `pdg.apiBaseUrl`).
- Client réseau Retrofit + kotlinx.serialization vers l'API Django (`data/api/`).
- Pairing bracelet↔compte avec confirmation explicite (`data/bracelet/BraceletRepository.kt`).
- Historique/statistiques avec poll REST toutes les 15s (`data/measurements/MeasurementsRepository.kt`).
- Écrans Compose : Login, Register, Dashboard (BLE + métriques + pairing), Stats (graphique
  Canvas fait main + table), navigation via Navigation-Compose.

## Ce qu'il faut adapter

- Si le bracelet expose des UUID GATT connus, les placer dans `BraceletBleConfig`
  (`domain/AppConfig.kt`) — sinon l'app utilise par défaut la première caractéristique
  NOTIFY/INDICATE trouvée.
- Si le bracelet publie un format binaire spécifique, ajuster `BraceletMeasurementCodec`.
- L'URL du backend est éditable directement dans l'écran Login (utile pour cibler l'IP LAN du
  serveur depuis un téléphone physique, `10.0.2.2` n'étant valide que depuis l'émulateur).

## Tester sans matériel

`infra-orchestrator/fake-emitter` poste des mesures simulées vers `POST /api/datas` toutes les
5s (device_uid `11111111-1111-1111-1111-111111111111`) — pratique pour peupler Dashboard/Stats
sans bracelet physique. Il suffit de pairer ce bracelet de test au compte utilisé.

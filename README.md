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
- Pas de pairing/appairage : `POST /api/datas` attache chaque mesure au compte connecté (le
  Bearer token suffit), et le lien BLE ne parle qu'à un seul bracelet à la fois — rien à
  appairer ni désappairer, un compte peut recevoir des mesures de bracelets physiques différents.
- Historique/statistiques avec poll REST toutes les 15s (`data/measurements/MeasurementsRepository.kt`).
- Écrans Compose : Login, Register, Dashboard (BLE + métriques), Stats (graphique
  Canvas fait main + table), navigation via Navigation-Compose.

## Ce qu'il faut adapter

- Si le bracelet expose des UUID GATT connus, les placer dans `BraceletBleConfig`
  (`domain/AppConfig.kt`) — sinon l'app utilise par défaut la première caractéristique
  NOTIFY/INDICATE trouvée.
- Si le bracelet publie un format binaire spécifique, ajuster `BraceletMeasurementCodec`.
- L'URL du backend est éditable directement dans l'écran Login (utile pour cibler l'IP LAN du
  serveur depuis un téléphone physique, `10.0.2.2` n'étant valide que depuis l'émulateur).

## Tester sans matériel

Pas de bracelet sous la main : poster une mesure à la main avec le token d'un compte de test.

```sh
TOKEN=$(curl -s -X POST http://localhost:8000/api/login \
  -H "Content-Type: application/json" \
  -d '{"email":"ryad@example.com","password":"Demo-1234"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')

curl -X POST http://localhost:8000/api/datas \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"device_uid":"test-device","serial_number":"TEST-001","heart_rate_bpm":72,"spo2_percent":98,"step_count":100}'
```

Connecte-toi avec ce même compte (`infra-db/initdb/002-seed.sql`) dans l'app pour voir la mesure
apparaître dans Dashboard/Stats.

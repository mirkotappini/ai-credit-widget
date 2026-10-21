# Crediti AI

App Android per tenere d'occhio quanto resta e quanto consumi sui provider AI che usi.
Nessun backend, nessun account, nessun analytics: le chiavi API restano cifrate sul
dispositivo e i dati non escono da lì.

## Cosa mostra

| Provider | Dato principale | Fonte |
|---|---|---|
| DeepSeek | saldo residuo (totale, omaggio, ricariche) e speso di oggi | `GET /user/balance` |
| OpenAI | speso di oggi, 7 giorni e mese corrente | `GET /v1/organization/costs` (Admin key) |

OpenAI non espone il saldo residuo tramite API: per quel provider l'app mostra solo i costi
e richiede una **Admin key** (`sk-admin-…`) con permesso di sola lettura. Una chiave di
progetto (`sk-proj-…`) riceve un 403 e l'app lo dice esplicitamente.

## Installazione

Requisiti: Android 8.0 o superiore (minSdk 26).

Scarica l'APK dall'ultima release e installalo. Per ricevere gli aggiornamenti senza
riscaricare il file ogni volta, usa [Obtainium](https://github.com/ImranR98/Obtainium):

1. In Obtainium, *Add App*
2. Incolla `https://github.com/mirkotappini/ai-credit-widget`
3. Ottieni gli aggiornamenti dalle release GitHub

## Privacy

- Le chiavi API sono cifrate con AES/GCM usando una chiave generata nell'Android Keystore
  del dispositivo: non sono esportabili e non vengono mai scritte in chiaro né loggate.
- `allowBackup=false`: i dati non finiscono nei backup cloud.
- Unica rete in uscita: HTTPS verso `api.deepseek.com` e `api.openai.com`.
- Nessun SDK di analytics, crash reporting o pubblicità.

## Build

```bash
export JAVA_HOME=<jdk>
export ANDROID_HOME=<sdk>
./gradlew assembleRelease
```

La firma release legge `signing.properties` (non versionato). Il progetto è Kotlin con
Views XML: niente Compose, niente Room, niente OkHttp.

## Font

IBM Plex Sans e IBM Plex Mono, licenza SIL Open Font License 1.1
(`app/src/main/assets/licenses/OFL-IBM-Plex.txt`).

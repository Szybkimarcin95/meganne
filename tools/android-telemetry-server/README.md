# CSDP-RM3 — drugi telefon Android jako serwer pomocniczy

Ten katalog pozwala wykorzystać drugi telefon z Androidem jako lokalny serwer pomocniczy dla `CSDP-RM3 / Megane Overlord`.

## Rola drugiego telefonu

Rekomendowany układ:

`SAMOCHÓD → ELM327 Bluetooth → GŁÓWNY TELEFON z aplikacją → Wi‑Fi/LAN → DRUGI TELEFON / SERVER`

Drugi telefon **nie łączy się równocześnie z ELM327** i nie udaje ECU. Jego zadanie to:

- odbieranie kopii telemetrii już odczytanej przez aplikację,
- zapisywanie jej w lokalnym SQLite,
- przechowywanie historii niezależnie od Room na głównym telefonie,
- udostępnianie prostego API LAN,
- opcjonalny SSH do administracji Termuksem,
- późniejsza możliwość hostowania dashboardu/statusu lub synchronizacji logów.

To jest warstwa opcjonalna. Room w aplikacji pozostaje lokalnym źródłem danych aplikacji; serwer jest mirror/backup, a nie zamiennikiem.

## Dlaczego tak

Klasyczny Bluetooth ELM327 zwykle powinien mieć jednego aktywnego klienta. Główny telefon pozostaje klientem ELM327, a drugi telefon komunikuje się z aplikacją przez sieć IP. Dzięki temu nie komplikujemy toru OBD i nie ryzykujemy dwóch klientów walczących o adapter.

## Wymagania

Na drugim telefonie:

- Android,
- Termux,
- sieć Wi‑Fi/hotspot wspólna z głównym telefonem,
- opcjonalnie Termux:Boot do autostartu.

Serwer używa wyłącznie standardowej biblioteki Pythona. Nie wymaga FastAPI/Flask ani pip.

## Instalacja w Termux

Po sklonowaniu repozytorium:

```bash
git clone https://github.com/Szybkimarcin95/meganne.git
cd meganne/tools/android-telemetry-server
chmod +x install.sh run.sh stop.sh install-boot.sh
./install.sh
```

`install.sh`:

- instaluje `python`, `openssh`, `git`,
- tworzy katalog `data/`,
- generuje losowy token API,
- zapisuje token w `~/.csdp-server.env`,
- ustawia prawa `600` do pliku z tokenem.

## Start

```bash
cd ~/meganne/tools/android-telemetry-server
./run.sh
```

Sprawdzenie lokalne:

```bash
curl http://127.0.0.1:8765/health
```

Przykładowa odpowiedź:

```json
{"status":"ok","service":"csdp-rm3-android-telemetry-server","version":"0.1.0","uptimeMs":12345,"rows":0}
```

## Adres IP drugiego telefonu

Telefon A i telefon B muszą być w tej samej sieci/hotspocie.

W Termux możesz sprawdzić interfejsy np.:

```bash
ip -4 addr
```

Załóżmy, że drugi telefon ma adres:

```text
192.168.1.50
```

Wtedy health z głównego telefonu/komputera:

```text
http://192.168.1.50:8765/health
```

## Token API

Token jest zapisany na drugim telefonie:

```bash
cat ~/.csdp-server.env
```

Nie commituj tego pliku do GitHub.

Chronione endpointy wymagają nagłówka:

```text
Authorization: Bearer <TOKEN>
```

## Wysłanie przykładowej telemetrii

Na drugim telefonie lub innym urządzeniu w LAN:

```bash
source ~/.csdp-server.env

curl -X POST http://127.0.0.1:8765/api/v1/telemetry \
  -H "Authorization: Bearer $CSDP_SERVER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "timestampMs": 1789041600000,
    "vehicleId": "megane-x95-k9k636",
    "deviceId": "android-primary",
    "source": "SIMULATED",
    "isSimulated": true,
    "verificationStatus": "SIMULATION",
    "metrics": {
      "rpm": 830,
      "speedKmH": 0,
      "coolantTempC": 86
    }
  }'
```

Dane w przykładzie są jawnie oznaczone jako symulacja. Serwer nie interpretuje ich jako pomiar fizyczny.

## Odczyt najnowszych rekordów

```bash
source ~/.csdp-server.env
curl \
  -H "Authorization: Bearer $CSDP_SERVER_TOKEN" \
  "http://127.0.0.1:8765/api/v1/telemetry/latest?limit=20"
```

## Eksport NDJSON

```bash
source ~/.csdp-server.env
curl \
  -H "Authorization: Bearer $CSDP_SERVER_TOKEN" \
  http://127.0.0.1:8765/api/v1/export.ndjson \
  -o telemetry.ndjson
```

Aktualnie endpoint eksportuje do 500 najnowszych rekordów. To jest świadome ograniczenie wersji `0.1.0`.

## Stop

```bash
./stop.sh
```

## Autostart po restarcie telefonu

Zainstaluj Termux:Boot z tego samego źródła/podpisu co Termux, uruchom jego ikonę jeden raz, a następnie:

```bash
cd ~/meganne/tools/android-telemetry-server
./install-boot.sh
```

Skrypt tworzy:

```text
~/.termux/boot/20-csdp-rm3-server
```

który wykonuje `termux-wake-lock` i uruchamia serwer.

Android nadal może ograniczać procesy w tle. Dla telefonu-serwera ustaw Termux na brak optymalizacji/ograniczeń baterii i testuj działanie po zablokowaniu ekranu.

## SSH do drugiego telefonu

Opcjonalnie:

```bash
passwd
sshd
```

Termux `sshd` standardowo używa portu `8022`.

Łącz się tylko w zaufanej sieci/VPN. Nie wystawiaj portu SSH ani API bezpośrednio do Internetu przez przekierowanie portów routera.

## API v0.1

### `GET /health`

Bez tokena. Zwraca status procesu i liczbę rekordów.

### `POST /api/v1/telemetry`

Wymaga Bearer tokena. Przyjmuje pojedynczy obiekt JSON do 128 KiB.

Zalecany payload:

```json
{
  "timestampMs": 0,
  "vehicleId": "megane-x95-k9k636",
  "deviceId": "android-primary",
  "source": "MEASURED | SIMULATED | DATABASE | UNKNOWN",
  "isSimulated": false,
  "verificationStatus": "...",
  "metrics": {}
}
```

Serwer przechowuje cały payload JSON. Nie wymyśla wartości, jednostek ani źródeł.

### `GET /api/v1/telemetry/latest?limit=N`

Wymaga tokena. `N` jest ograniczone do 1–500.

### `GET /api/v1/export.ndjson`

Wymaga tokena. Eksportuje zapisane rekordy w NDJSON.

## Baza danych

Domyślnie:

```text
tools/android-telemetry-server/data/telemetry.db
```

SQLite działa w trybie WAL.

`data/` powinno pozostać lokalne i nie powinno być commitowane.

## Ważne granice bezpieczeństwa projektu

Ten serwer:

- NIE wysyła poleceń do ECU,
- NIE komunikuje się z ELM327,
- NIE implementuje Renault/SID307 DID,
- NIE wykonuje Mode 04,
- NIE wykonuje resetów, actuator tests ani konfiguracji,
- NIE zmienia Room aplikacji,
- NIE zastępuje `DiagnosticTransport`.

Przyszła integracja aplikacji powinna być osobnym checkpointem i dodać wyłącznie opcjonalny `TelemetryMirrorClient`, wysyłający kopię już istniejącej telemetrii do serwera.

## Następny bezpieczny etap

Po uruchomieniu tego serwera na drugim telefonie należy najpierw ręcznie zweryfikować:

1. `/health` działa lokalnie,
2. `/health` działa z głównego telefonu po LAN,
3. POST testowego payloadu działa,
4. rekord pojawia się w `/api/v1/telemetry/latest`,
5. serwer działa po zablokowaniu ekranu,
6. serwer wraca po restarcie telefonu z Termux:Boot.

Dopiero potem warto integrować klienta HTTP z aplikacją Android.

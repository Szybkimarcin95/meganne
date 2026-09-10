# CSDP-RM3 / MEGANE OVERLORD — CURRENT PROJECT STATE

> Aktualny kod repozytorium ma pierwszeństwo nad wcześniejszymi raportami AI.

## Stable snapshot
Bazowy snapshot po synchronizacji Google AI Studio → GitHub:
- branch: `main`
- checkpoint 6: zakończony
- Bluetooth connection indicator: zachowany
- fake ELM327 fuse/circuit test: usunięty
- błędne sensor trend mappings: oczyszczone
- Mode 04 wording: zneutralizowane
- testy jednostkowe: PASS wg raportu AI Studio

## Accepted checkpoints
### Checkpoint 1
Usunięto syntetyczne seedy trendów/historii DTC z produkcji.

### Checkpoint 2
Audyt Room 2→3 wykrył ryzyko destructive fallback bez jawnej migracji.

### Checkpoint 3
Dodano i przetestowano `MIGRATION_2_3`.

### Checkpoint 4/5
Audyt Source of Truth / Digital Twin / OBD / ELM327 / SID307. Najnowszy audyt wskazał płaskie `EngineComponent` i `FuseItem`, brak strukturalnego graph modelu oraz niepotwierdzoną gotowość fizycznego ELM327.

### Checkpoint 6
Wykonano cleanup w dwóch krokach:
- `DigitalTwinScreen.kt`: usunięto hardcoded fake ELM327 circuit continuity/resistance test.
- `OverlordViewModel.kt`: unsupported `fuel_filter_primer`, `dpf_differential`, `glow_plugs` nie zapisują już fałszywych trendów; poprawiono komunikat Mode 04.

## Auxiliary second-Android server
Status: `IMPLEMENTED IN REPO / PHYSICAL VALIDATION PENDING`.

Dodano izolowany katalog:
`tools/android-telemetry-server/`

Rola:
`CAR → ELM327 → primary Android app → LAN/Wi-Fi → secondary Android telemetry server`

Serwer:
- działa w Termux,
- używa Python standard library,
- zapisuje telemetrię do SQLite WAL,
- ma token Bearer dla chronionych endpointów,
- ma health/latest/NDJSON export,
- ma install/run/stop/Termux:Boot scripts,
- nie komunikuje się bezpośrednio z ELM327 ani ECU,
- nie zastępuje Room ani `DiagnosticTransport`.

GitHub CI dla serwera: PASS po dodaniu funkcjonalności.

Do czasu testu na drugim telefonie nie oznaczać go jako physically validated.

## Current verified-by-code facts
`OverlordViewModel.logCurrentSensorSample()` obsługuje obecnie:
- `turbocharger → boostBar`
- `map_sensor → mapPressureKpa`
- `hp_fuel_pump → railPressureBar`
- `piezo_injectors → injector1Correction`
- `egr_valve → egrPositionPercent`
- wszystkie inne identyfikatory → `return` / NO SAMPLE

Mode 04 success message wymaga ponownego skanu zamiast deklarowania braku aktywnych usterek.

## Known risks / open questions
- physical ELM327 compatibility nie została potwierdzona na samochodzie,
- drugi telefon-serwer nie został jeszcze fizycznie zweryfikowany po LAN / lock screen / reboot,
- dane fuse/wire/OEM wymagają provenance,
- dokładny SID307 candidate nie jest potwierdzony,
- wcześniejsze raporty AI były sprzeczne w kwestii Digital Twin graph models,
- unit tests ≠ physical hardware validation.

## External data candidates
SID307 JSON candidates:
- `SID307_00F7_550_V05_20130313T104520`
- `SID307_00FD_A00_V01_20140522T150659`
- `SID307_00FD_A40_V1.0_20181106T171634`
- `SID307_FC_500_F8_600_F7_560_V01_20140429T151710`

Known XML counterparts exist for all above except A40 in the current database index.

## Current gate
Do not start Renault-specific WRITE/RESET/CONFIGURATION/ACTUATOR work.
Do not assume exact SID307 definition match before file-content/ECU-identification evidence.
Do not integrate the Android app with the second-phone server until the standalone server passes physical LAN validation.

## Next safe work lane
1. physical setup/test of `tools/android-telemetry-server` on the second phone,
2. independent Codex read-only review of current snapshot,
3. after both checks choose one implementation checkpoint only.

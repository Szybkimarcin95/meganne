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

## Next safe work lane
1. keep GitHub as synchronization source,
2. independent Codex read-only review of current snapshot,
3. after review choose one implementation checkpoint only.

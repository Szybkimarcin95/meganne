# DATA PROVENANCE POLICY

## Goal
Każda techniczna informacja używana przez CSDP-RM3 powinna mieć znane pochodzenie i status weryfikacji.

## Required conceptual metadata
Każdy przyszły importowany element diagnostyczny powinien wspierać:
- `sourceType`
- `sourceFile`
- `sourceFormat`
- `sourceVersion`
- `sourceIdentifier`
- `verificationStatus`
- `vehicleMatchStatus`
- `operationType`
- `notes`

## Verification states
Preferowane klasyfikacje:
- `VERIFIED_STANDARD`
- `VERIFIED_DATABASE`
- `VERIFIED_FROM_FILE`
- `CANDIDATE`
- `VEHICLE_MATCH_UNCONFIRMED`
- `UNVERIFIED`
- `SOURCE_NOT_FOUND`
- `UNKNOWN`
- `SIMULATION`

## Operation classes
- `READ`
- `WRITE`
- `ACTUATOR TEST`
- `RESET`
- `CONFIGURATION`
- `UNKNOWN`

## External datasets
### ecu.zip
Format znany z indeksu:
- `.json`
- `.json.layout`
- graphics

Known SID307 candidates:
- `SID307_00F7_550_V05_20130313T104520`
- `SID307_00FD_A00_V01_20140522T150659`
- `SID307_00FD_A40_V1.0_20181106T171634`
- `SID307_FC_500_F8_600_F7_560_V01_20140429T151710`

### database.zip
Format znany z indeksu:
- ECU `.xml`
- vehicle data
- graphics
- scripts
- small `.hex` resources

Known SID307 XML candidates:
- `SID307_00F7_550_V05_20130313T104520`
- `SID307_00FD_A00_V01_20140522T150659`
- `SID307_FC_500_F8_600_F7_560_V01_20140429T151710`

`SID307_00FD_A40...` ma status JSON-only w bieżącym indeksie; nie zgadywać przyczyny.

## Correlation rule
Filename similarity = candidate only.

JSON↔XML należy porównywać po:
1. metadata,
2. ECU identification,
3. requests/responses,
4. data definitions/scaling,
5. identifiers,
6. vehicle mappings,
7. operation type.

## Vehicle match rule
Dla każdego kandydata wymagaj:
- TARGET PLATFORM MATCH
- ENGINE FAMILY MATCH
- K9K636 MATCH
- MODEL YEAR COMPATIBILITY
- ECU IDENTIFICATION REQUIREMENTS
- CONFIDENCE
- EVIDENCE

Nie inferuj K9K636 z samego tokenu `K9K` i nie inferuj applicability z samego `X95` lub `SID307`.

## App import policy
Nie shipować całej zewnętrznej bazy w APK. Docelowo importować minimalny, zweryfikowany, vehicle-specific subset.

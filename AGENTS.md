# CSDP-RM3 / MEGANE OVERLORD — AGENT RULES

## Source of truth
- Aktualny kod repozytorium jest nadrzędnym źródłem prawdy.
- Gdy raport AI przeczy kodowi, ufaj kodowi.
- Nie odtwarzaj brakującej implementacji z pamięci ani założeń.
- Niepotwierdzone dane oznaczaj jako `UNVERIFIED`, `UNKNOWN`, `SOURCE NOT FOUND`, `CANDIDATE` lub `VEHICLE MATCH UNCONFIRMED`.

## Safety
**BETTER JOB STOP THAN DAMAGE WORKING PROJECT.**

Nie wykonuj masowych refaktoryzacji, zmian niezwiązanych z checkpointem, zmian Room bez migracji/testu ani technicznych placeholderów bez źródła.

## Workflow
Dla większego zadania: `INSPECT → SOURCE OF TRUTH → ANALYZE → PLAN → IMPLEMENT ONLY APPROVED SCOPE → TEST → REVIEW DIFF → REPORT → STOP`.

Dla `AUDIT / READ ONLY / ZERO EDITION` obowiązuje:
- `FILES MODIFIED: 0`
- `FILES CREATED: 0`
- `FILES DELETED: 0`

## Project target
Docelowy tor diagnostyczny:
`SAMOCHÓD → ECU → OBD-II → fizyczny ELM327 Bluetooth → Android → DiagnosticTransport → parser → Repository/ViewModel → UI → Room/history`.

Nigdy nie przedstawiaj danych hardcoded/symulowanych jako rzeczywistego pomiaru.

## Architecture
Zachowuj istniejący stack, jeśli checkpoint nie wymaga inaczej:
- Kotlin
- Android
- Jetpack Compose
- MVVM
- Repository
- StateFlow
- coroutines
- Room

## OBD / ELM327 / ECU
Nie wymyślaj Renault-specific PID/DID, ECU/CAN addresses, request/response bytes, skalowania, masek bitowych, security access, actuator/reset/config/coding, pinów, bezpieczników, przewodów, napięć, rezystancji, OEM ani momentów dokręcania.

Kolejność Renault-specific:
1. ECU identification
2. verified READ-only
3. live values
4. DTC read/description
5. osobny audyt WRITE/RESET/CONFIGURATION/ACTUATOR

Mode 04 traktuj jako akcję modyfikującą stan diagnostyczny.

## Real vs simulation
Rozróżniaj: `REAL DEVICE DATA`, `DATABASE DATA`, `STATIC REFERENCE DATA`, `SIMULATION`, `MOCK`, `TEST FIXTURE`, `HARDCODED`, `UNKNOWN`.

Unit test nie dowodzi działania z fizycznym autem.

## Digital Twin
Docelowy graf:
`fuse → circuit → relay → power → harness → connector → pin → component/module`
`ground → harness → component`
`symptom → cause → component → sensor/wiring/power → fuse → ground → module`

Preferuj jedno źródło prawdy dla relacji.

## Room
Przy zmianie DB: sprawdź poprzedni schemat, określ diff, dodaj Migration, zachowaj dane, uruchom test migracji, zweryfikuj schemat i destructive fallback.

## External ECU datasets
Nie kopiuj całej bazy do APK. Pracuj na minimalnym, zweryfikowanym, vehicle-specific subset. Każdy importowany element powinien mieć provenance i operation class.

## UI
Docelowo: minimalistyczny, profesjonalny, czytelny, intuicyjny. Nie wykonuj redesignu bez checkpointu UI.

## Final report
Na końcu implementacji raportuj:
- `STATUS: PASS / PASS WITH WARNINGS / JOB STOP`
- `CHANGED`
- `CREATED`
- `DELETED`
- `WHAT WAS DONE`
- `TESTS`
- `RISKS / UNVERIFIED`
- `NEXT SAFE STEP`

Po wykonaniu zakresu: **STOP**.

# CP3A — RAPORT: KOREKTA WIERNOŚCI ŹRÓDŁOWEJ FIREWALLA READ-ONLY

- Data UTC: 2026-09-23T23:36:45Z
- Status: **PASS WITH WARNINGS**
- Cel: Ograniczenie białej listy wykonywalnych komend diagnostycznych wyłącznie do komend popartych bieżącym kodem produkcyjnym i standardem OBD-II (Least Privilege). Bezwzględne zablokowanie komend proprietary SID307/Renault (status `BLOCKED_PENDING_SOURCE_VERIFICATION`) do czasu weryfikacji fizycznych plików CP2. Zablokowanie Mode 04 w polityce READ_ONLY. Zachowanie i przetestowanie serializacji transakcji.

## 1. Wykonane modyfikacje

### A. Usunięcie niezweryfikowanych komend proprietary SID307 / Renault z białej listy
- Z listy wykonywalnych usunięto:
  - `ATSP6`
  - `ATCRA 7E8`
  - `ATSH 7E0`
  - Serwis Renault/SID307 UDS 0x19 (`1902FF`, itp.)
  - Serwis Renault/SID307 UDS 0x22 (`222001`, `222002`, `222028`, itp.)
- Komendy te zaklasyfikowano w `CommandFirewall` jako:
  `BLOCKED_PENDING_SOURCE_VERIFICATION: ... zablokowane do czasu CP2`
- Wynik: 0 komend proprietary SID307/Renault jest wykonywalnych.

### B. Minimalna standardowa biała lista (Least Privilege)
- **Komendy AT (inicjalizacja adaptera):**
  - Wyłącznie: `ATZ`, `ATE0`, `ATL0`, `ATS0`, `ATSP0`
  - Wszelkie inne komendy AT są odrzucane z kodem Least Privilege.
- **Odczyty standardowe OBD-II Mode 01 (telemetria na żywo):**
  - Wyłącznie PID-y odpytywane w bieżącym kodzie produkcyjnym:
    `010C` (RPM), `010D` (Speed), `0105` (Coolant Temp), `010F` (Intake Air Temp),
    `0110` (MAF), `0104` (Engine Load), `0111` (Throttle Position), `010B` (MAP)
- **Odczyty błędów DTC:**
  - `03` (Mode 03 - Stored DTCs)
  - `07` (Mode 07 - Pending DTCs)
- Nieużywane w bieżącej produkcji odczyty (np. Mode 02, Mode 09, inne PID-y Mode 01) pozostają zablokowane do czasu ich uzasadnionego wprowadzenia w kolejnych checkpointach.

### C. Polityka Mode 04 (Clear DTC)
- Mode 04 nie jest operacją read-only (modyfikuje stan diagnostyczny ECU).
- W polityce READ_ONLY komenda `04` jest bezwzględnie blokowana przez `CommandFirewall`.
- Usunięto flagę `allowMode04Clear`. Żaden fizyczny bajt Mode 04 nie opuszcza aplikacji do transportu fizycznego.
- W testach jednostkowych Mode 04 weryfikowane jest wyłącznie pod kątem blokowania przez zaporę (0 wywołań transportu).

### D. Twarde blokowanie niebezpiecznych serwisów
- `11` (ECUReset)
- `27` (SecurityAccess)
- `2E` (WriteDataByIdentifier)
- `2F` / `30` (InputOutputControl / ActuatorTest)
- `31` (RoutineControl)
- `34` / `36` / `37` (Download / TransferData / Flash)
- `3D` (WriteMemoryByAddress)

### E. Gwarancje SafeDiagnosticSession
1. Pojedyncza transakcja na magistrali naraz (`transactionMutex.withLock`).
2. Brak przeplatania bajtów i komend przy współbieżności.
3. Walidacja `CommandFirewall.validate()` następuje PRZED jakimkolwiek wywołaniem transportu.
4. Zablokowana komenda powoduje dokładnie 0 wywołań transportu (`sentCommands.size == 0`).
5. Timeout i Cancellation zwalniają blokadę transakcji (gwarantowane przez `Mutex.withLock`).
6. Zmiana generacji sesji (`sessionGeneration`) odrzuca zaległe odpowiedzi z poprzednich sesji.

### F. Klasyfikacja błędów adaptera
- Jawnie zdefiniowane odpowiedzi adaptera:
  - `NO DATA` -> `AdapterError("NO_DATA")`
  - `BUFFER FULL` -> `AdapterError("BUFFER_FULL")`
  - `CAN ERROR` -> `AdapterError("CAN_ERROR")`
  - `UNABLE TO CONNECT` -> `AdapterError("UNABLE_TO_CONNECT")`
  - `STOPPED` -> `AdapterError("STOPPED")`
  - `ERROR` -> `AdapterError("ERROR")`
  - `TIMEOUT` -> `TransportError("TIMEOUT")`
- Błąd transportu/adaptera nie tworzy fałszywych kodów DTC.

### G. Integracja z ObdManager
- Wszystkie standardowe zapytania telemetryczne (Mode 01) oraz odczyt błędów (Mode 03 i Mode 07) przechodzą przez `SafeDiagnosticSession`.

### H. Audyt inicjalizacji ELM327 (Ostrzeżenie / Warning)
- **AUDIT FINDING (HIGH WARNING):** Sekwencja inicjalizacji adaptera (`ATZ`, `ATE0`, `ATL0`, `ATS0`, `ATSP0`) w `ObdManager.kt` jest wykonywana przez `SafeDiagnosticSession`, jednak przepływ łączenia nie przerywa procedury przed wejściem w stan `ObdConnectionState.READING`, jeśli adapter zwróci błąd na `ATZ` lub `ATSP0`. Ze względu na rygorystyczny zakaz niekontrolowanego poszerzania zakresu CP3/CP3A i zakaz masowych refaktoryzacji, zachowano dotychczasowy flow, oznaczając go jako HIGH WARNING do bezpiecznego utwardzenia przy weryfikacji fizycznej adaptera.

---

## 2. Zestawienie zmian w plikach

### Pliki zmodyfikowane w CP3A:
- `app/src/main/java/com/example/data/obd/CommandFirewall.kt`
- `app/src/main/java/com/example/data/obd/SafeDiagnosticSession.kt`
- `app/src/main/java/com/example/data/obd/ObdManager.kt`
- `app/src/test/java/com/example/data/obd/CommandFirewallAndSessionTest.kt`
- `app/src/test/java/com/example/data/obd/DiagnosticTransportTest.kt`
- `automation/megane/journal.jsonl`

### Pliki utworzone w CP3A:
- `automation/megane/checkpoints/CP3A_REPORT.md`

### Pliki usunięte:
- 0

---

## 3. Testy jednostkowe i kompilacja
- **Testy jednostkowe:**
  - Polecenie: `gradle :app:testDebugUnitTest`
  - Kod wyjścia: `0`
  - Wynik: **BUILD SUCCESSFUL** (33 actionable tasks: 7 executed, 26 up-to-date)
  - 22 dedykowane testy zapory i sesji zakończone sukcesem (w tym wszystkie 20 wymaganych punktów testowych).
- **Kompilacja aplikacji:**
  - Polecenie: `compile_applet`
  - Wynik: **Build succeeded - the applet is compiled**

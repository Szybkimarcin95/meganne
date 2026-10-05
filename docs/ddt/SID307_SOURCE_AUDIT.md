# SID307 source audit — target vehicle

Status: exact physical ECU identity is available; runtime execution remains read-only gated.

## Physical ECU evidence

The supplied OBD export identifies the engine controller as:

- ISO 15765-4 CAN, 11-bit, 500 kbaud
- ECU response address: 7E8
- diagnostic version: 129
- supplier: 4BE
- software: 00F7
- version: 5500

The project intentionally does not persist the VIN in the DDT matcher.

## Database evidence

The supplied database candidate inventory contains:

- `SID307_00F7_550_V05_20130313T104520.xml`
- `SID307_00FD_A00_V01_20140522T150659.xml`
- `SID307_FC_500_F8_600_F7_560_V01_20140429T151710.xml`

The supplied ecu candidate inventory contains the JSON counterpart:

- `SID307_00F7_550_V05_20130313T104520.json`
- matching `.json.layout`

It also contains other SID307 variants, including 00FD/A00, 00FD/A40 and FC/F8/F7 variants.

## Project decision

The current target is:

`SID307_00F7_550_V05_20130313T104520.json`

Selection is based on the physical identity tuple, not filename similarity alone:

`diag=129 / supplier=4BE / software=00F7 / version=5500`

The Android code now parses DDT AutoIdent metadata and requires an exact four-field AutoIdent match before a database definition can be marked vehicle-matched.

## Execution policy

Even after an exact match:

- database requests default to `executable=false`
- the read-only candidate catalog contains only requests classified as READ
- WRITE / CONFIGURATION / ACTUATOR_TEST / RESET / SECURITY_ACCESS remain excluded
- existing `CommandFirewall` and `SafeDiagnosticSession` remain authoritative runtime gates

No whole DDT database archive is committed to the APK.

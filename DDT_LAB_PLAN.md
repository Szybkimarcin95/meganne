# DDT ECU Lab — test branch

Branch: `feature/ddt-capability-browser`

## Goal

Turn the project from a generic OBD-II reader into a vehicle-specific Renault ECU laboratory driven by verified DDT database content.

The current checkpoint remains read-only at runtime.

## Completed in this branch

- DDT4All JSON schema indexing for ECU definition files from `ecu.zip`.
- ECU metadata extraction:
  - protocol
  - CAN send/receive IDs
  - functional address
  - baud rate
  - endian
  - AutoIdent metadata
- Request inventory and request input/output data-name inventory.
- Conservative operation classification:
  - READ
  - WRITE
  - ACTUATOR_TEST
  - RESET
  - CONFIGURATION
  - SECURITY_ACCESS
  - SESSION_CONTROL
  - UNKNOWN
- Every imported capability defaults to `executable=false`.
- Physical ECU AutoIdent matcher.
- Vehicle-specific SID307 target profile.
- Read-only candidate catalog that stays empty until an exact four-field AutoIdent match succeeds.
- No command is sent to the vehicle by these DDT indexing/matching modules.

## Physical ECU match

The supplied vehicle diagnostic export identifies:

- CAN 11-bit / 500 kbaud
- ECU response address 7E8
- diag version 129
- supplier 4BE
- software 00F7
- version 5500

The supplied DDT inventories contain the matching definition:

`SID307_00F7_550_V05_20130313T104520`

The match decision is based on AutoIdent data, not filename similarity.

## Electrical continuity scope

The application must distinguish:

- **ECU-reported circuit status**: open circuit, short to ground, short to battery, implausible signal, voltage/state flags.
- **Derived plausibility check**: compare multiple ECU observations and operating conditions.
- **Physical continuity/resistance measurement**: requires a multimeter or dedicated hardware and must never be fabricated from ELM327 data.

## Next checkpoint

1. Import only the matched SID307 JSON definition (and only required referenced metadata), not the whole DDT archive.
2. Generate a request-level capability manifest with provenance and operation class.
3. Compare classified READ requests against the existing `CommandFirewall`.
4. Add a database-aware READ gate for requests that are source-backed, exact-ECU-matched and explicitly allowed.
5. Build the ECU Lab capability browser UI.
6. Validate physical READ transport on the real ELM327.
7. Keep WRITE / ACTUATOR / RESET / CONFIGURATION / SECURITY_ACCESS disabled until their separate audit and confirmation policy is implemented.

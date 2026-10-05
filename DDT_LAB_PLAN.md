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
- Transport-aware vehicle match:
  - response CAN ID
  - protocol
  - baud rate
- Vehicle-specific SID307 target profile.
- Read-only candidate catalog that stays empty until the ECU definition exactly matches the physical ECU.
- Capability audit summary with operation counts and guarded-operation counts.
- Database-aware DDT READ policy:
  - command must exist in the matched definition,
  - request must be classified READ,
  - unknown commands remain blocked,
  - state-changing service families remain hard-blocked.
- No command is sent to the vehicle by these DDT indexing/matching/policy modules.

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

The match decision is based on the physical identity tuple plus transport metadata, not filename similarity.

## Runtime safety boundary

The existing production `CommandFirewall` remains unchanged and still blocks Renault-specific `0x19` / `0x22` traffic.

The new `DdtReadOnlyCommandPolicy` is a separate dynamic allowlist and does not bypass `SafeDiagnosticSession`. It is deliberately not wired to physical transport yet.

This separation lets the project fully index and audit the matched SID307 definition before the first Renault-specific physical read is enabled.

## Electrical continuity scope

The application must distinguish:

- **ECU-reported circuit status**: open circuit, short to ground, short to battery, implausible signal, voltage/state flags.
- **Derived plausibility check**: compare multiple ECU observations and operating conditions.
- **Physical continuity/resistance measurement**: requires a multimeter or dedicated hardware and must never be fabricated from ELM327 data.

## Next checkpoint

1. Extract the actual matched `SID307_00F7_550_V05_20130313T104520.json` from the supplied archive.
2. Generate a request-level capability manifest with provenance and operation class from the real file.
3. Review UNKNOWN and ambiguous operation classifications manually.
4. Build the ECU Lab capability browser UI from that manifest.
5. Add a controlled integration path from `SafeDiagnosticSession` to `DdtReadOnlyCommandPolicy`.
6. Validate only selected READ requests on the real ELM327.
7. Keep WRITE / ACTUATOR / RESET / CONFIGURATION / SECURITY_ACCESS disabled until their separate audit and confirmation policy is implemented.

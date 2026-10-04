# DDT ECU Lab — test branch

Branch: `feature/ddt-capability-browser`

## Goal

Turn the project from a generic OBD-II reader into a vehicle-specific Renault ECU laboratory driven by verified DDT database content.

The first checkpoint is intentionally read-only at runtime.

## What this checkpoint adds

- DDT4All JSON schema indexing for ECU definition files from `ecu.zip`.
- ECU metadata extraction:
  - protocol
  - CAN send/receive IDs
  - functional address
  - baud rate
  - endian
  - AutoIdent count
- Request inventory.
- Request input/output data-name inventory.
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
- No command is sent to the vehicle by this module.

## Why this matters

Instead of exposing generic PIDs, the app can build a capability screen from the exact ECU definition selected for the car. This is the basis for:

1. ECU identification and exact file matching.
2. Renault-specific live values.
3. ECU-reported electrical diagnostics and plausibility checks.
4. Connector/sensor/actuator troubleshooting based on real ECU signals.
5. Later guarded actuator tests and configuration changes.

## Electrical continuity scope

The application must distinguish:

- **ECU-reported circuit status**: open circuit, short to ground, short to battery, implausible signal, voltage/state flags.
- **Derived plausibility check**: compare multiple ECU observations and operating conditions.
- **Physical continuity/resistance measurement**: requires a multimeter or dedicated hardware and must never be fabricated from ELM327 data.

## Next checkpoint

1. Locate/import the user's actual `ecu.zip` / DDT database.
2. Index the SID307 candidates already listed in project provenance.
3. Match AutoIdent against the physical ECU.
4. Generate a vehicle-specific capability catalog.
5. Unlock only verified READ requests through a database-aware extension to CommandFirewall.
6. Keep WRITE/ACTUATOR/RESET/CONFIGURATION disabled until each request has explicit source + ECU match + confirmation policy.

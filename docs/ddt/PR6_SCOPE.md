# PR #6 — SID307 Manifest + Decoder + ECU Lab

## Scope

This PR starts from the merged PR #5 checkpoint and keeps the existing safety boundary intact.

### 1. Manifest generator
Use the existing `tools/ddt/build_sid307_manifest.py` from `main`.

Target input:
`SID307_00F7_550_V05_20130313T104520.xml`

Target output:
`sid307_read_manifest.json`

Expected source-backed READ inventory:
- 912 × service 0x22
- 68 × service 0x19
- 4 × service 0x21
- 984 READ candidates total before runtime filtering

The generated manifest must preserve:
- request name
- sent bytes
- expected reply metadata
- minimum response length
- decoded data definitions
- source/provenance
- transport metadata
- execution status = false by default

### 2. Decoder
Implement source-backed response decoding only.

Required cases include:
- 16-bit scaled values
- signed values
- divideBy / step / offset metadata
- enum/list values
- ASCII/byte fields
- bit and multi-bit fields
- endianness metadata
- minimum response length checks
- positive response validation

Negative responses must be first-class results:
- NRC 0x12
- NRC 0x22
- NRC 0x31
- other NRC values preserved as raw code + description where known

No NRC may be misclassified as a normal RAW success.

### 3. Session prerequisites
No implicit session changes.

Any request needing a non-default diagnostic session must carry an explicit prerequisite in the manifest/runtime model. Session-changing services stay outside the normal READ execution path unless explicitly modeled and approved.

### 4. Scheduler
Do not fire all 984 requests in one burst.

Required controls:
- minimum inter-request delay
- per-request timeout
- grouped request execution
- cancellation
- retry policy bounded per request
- no automatic 984-request scan at app startup
- restart-safe execution state

### 5. ECU Lab UI
Expose capability status clearly.

Required counters:
- SUPPORTED
- NRC
- TIMEOUT
- SESSION REQUIRED
- NOT TESTED

Useful filters:
- Live
- DTC
- Electrical
- DPF
- Air
- Fuel
- Identification

The UI must distinguish:
- source-backed capability
- physically confirmed response
- NRC
- timeout/no response
- simulated/demo data

### Safety boundary — unchanged
Hard-block and keep non-executable:
- WRITE
- CONFIGURATION
- ACTUATOR
- RESET
- SECURITY

Explicit hard blocks remain:
- `14FFFFFF` — ClearDiagnosticInformation.All
- `320000` — StopRoutineByLocalIdentifier

No physical Renault-specific request becomes executable merely because it is present in the DDT XML or generated manifest.

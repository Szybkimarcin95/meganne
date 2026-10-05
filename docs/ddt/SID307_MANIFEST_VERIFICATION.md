# SID307 manifest generator verification

Source XML:

`SID307_00F7_550_V05_20130313T104520.xml`

Generator:

`tools/ddt/build_sid307_manifest.py`

Executed against the actual SID307 DDT2000 XML extracted from the user's database.

## Result

Generator CLI uses positional arguments:

```bash
python3 tools/ddt/build_sid307_manifest.py <xml> <output>
```

Observed output:

- read requests: **984**
- service 0x22: **912**
- service 0x19: **68**
- service 0x21: **4**
- send CAN ID: **7E0**
- receive CAN ID: **7E8**
- baud rate: **500000**
- data definitions: **1935**
- generated JSON size: **972,790 bytes**
- executable=true records: **0**

Full service inventory from the same XML:

- 0x10: 4
- 0x14: 1
- 0x19: 68
- 0x21: 4
- 0x22: 912
- 0x2E: 359
- 0x30: 1
- 0x31: 2
- 0x32: 1
- 0x3E: 1

## Decoder metadata spot checks

### 222496 — particulate filter soot mass

- reply template: `6224960000`
- minimum response bytes: 5
- first byte: 4
- width: 16 bits
- unsigned
- unit: `g`
- divideBy: `100`

### 222401 — boost pressure

- reply template: `6224010000`
- minimum response bytes: 5
- first byte: 4
- width: 16 bits
- unsigned
- unit: `mbar`

### 222801 — rail pressure

- reply template: `6228010000`
- minimum response bytes: 5
- first byte: 4
- width: 16 bits
- unsigned
- unit: `bar`

### 222026 — brake pedal open-active switch state

- reply template: `62202600`
- minimum response bytes: 4
- first byte: 4
- bit offset: 6
- width: 2 bits
- enum:
  - 0 = reserved
  - 1 = not pressed
  - 2 = pressed

### 2181 — VIN

- reply prefix: `6181`
- minimum response bytes: 21
- VIN starts at byte 3
- VIN length: 17 bytes
- ASCII = true
- CRC starts at byte 20 and uses 2 bytes

## Important gaps found

The current generated manifest does **not yet** include explicit fields for:

- NRC handling / known NRC mapping
- required diagnostic session
- session prerequisites
- denied sessions / `deny_sds`
- per-request provenance line/source pointer

The generator currently includes all source-backed services in `READ_SERVICES = {19,21,22}` and does not filter requests by session prerequisite metadata.

This is therefore a successful **source extraction verification**, but not yet the final PR #6 runtime manifest schema.

## Safety

The generated records remain `executable=false`.

No WRITE / CONFIGURATION / ACTUATOR / RESET / SECURITY operation was enabled or executed.

`14FFFFFF` and `320000` remain outside the READ manifest.


## Runtime metadata pass — schema v2

The generator was extended and rerun against the same actual XML.

Observed:

- schemaVersion: **2**
- READ count remains **984**
- sourceLine present: **984 / 984**
- sessionPrerequisite present: **2 / 984**
- accessConstraints = `NoSDS`: **2 / 984**
- explicit securityRequired=true: **0**
- securityRequired unknown/not declared by source: **984**
- excludedFromRead=true at generation time: **0**
- executable=true: **0**

The two source-backed session/access constrained reads are:

- `21F0` — DataRead.History.Ident.0 — source line **15803**
- `21F1` — DataRead.History.Reprog.0 — source line **15826**

Both contain the XML constraint:

`<DenyAccess><NoSDS/></DenyAccess>`

The source does **not** declare which exact diagnostic session must be selected for those two reads, therefore `requiredSession` remains `null` instead of guessing default/extended.

### NRC policy

Every READ record now carries runtime NRC policy metadata for:

- `0x12` — subFunctionNotSupported
- `0x13` — incorrectMessageLengthOrInvalidFormat
- `0x22` — conditionsNotCorrect
- `0x31` — requestOutOfRange
- `0x33` — securityAccessDenied
- `0x78` — responsePending

NRCs are runtime ECU responses. The generator does **not** pre-mark a request as excluded merely because an NRC is possible. Runtime may mark a request unsupported/blocked after the physical ECU actually returns the relevant NRC.

### Source fidelity notes

The actual XML contains session-control requests:

- `1081` — StartDiagnosticSession.Default
- `1085` — StartDiagnosticSession.Programming
- `10C0` — StartDiagnosticSession.ExtendedDiagnostic

However, individual 0x22 requests are not annotated in the source with a reliable per-request default-vs-extended requirement. PR #6 therefore preserves `requiredSession=null` unless a prerequisite is directly source-backed.

The XML also contains no explicit `SecurityAccess` / seed-key declaration for the READ requests. `DenyAccess/NoSDS` is preserved as an access/session constraint and is **not** re-labeled as SecurityAccess without evidence.

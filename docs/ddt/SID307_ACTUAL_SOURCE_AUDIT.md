# SID307 actual-source audit — 00F7 / 5500

Source examined directly from the user's DDT database:

`ecus/SID307_00F7_550_V05_20130313T104520.xml`

The XML was extracted from the user's `database.rar`. No filename-only inference is used below.

## Target identity

The XML target declares:

- target: `SID307_00F7_550_V05`
- application: `X95 ph2`
- engine: `K9K 636`
- ECU: `SID_307`
- software family: `F7_550`
- function: Injection
- CAN baud rate: 500000
- database generator: DDT2000 XML library 2.0.0.1

The source contains 10 AutoIdent tuples. One of them is exactly:

`DiagVersion=129 / Supplier=4BE / Soft=00F7 / Version=5500`

This exactly matches the physical ECU identification captured from the target vehicle.

## Request inventory

The actual XML contains **1353 requests**.

| Service | Count | Observed role in this definition |
|---|---:|---|
| 0x22 | 912 | DataRead |
| 0x2E | 359 | DataWrite |
| 0x19 | 68 | diagnostic/IUPR information reads |
| 0x10 | 4 | diagnostic session control |
| 0x21 | 4 | identification/history reads |
| 0x31 | 2 | start routine |
| 0x14 | 1 | clear diagnostic information |
| 0x3E | 1 | tester present |
| 0x30 | 1 | output control |
| 0x32 | 1 | stop routine |

The source-backed READ candidate set is therefore **984 requests** from services 0x19, 0x21 and 0x22 before any further semantic filtering.

## Safety findings from the real source

Two state-changing services were present that were not covered by the first heuristic-only implementation:

- `14FFFFFF` — `ClearDiagnosticInformation.All`
- `320000` — `StopRoutineByLocalIdentifier`

Both are now explicitly classified as non-READ and hard-blocked by the DDT read-only policy.

Other observed state-changing/control families remain guarded:

- 0x2E DataWrite
- 0x30 Output Control
- 0x31 StartRoutineByLocalIdentifier
- 0x10 session control
- 0x3E tester present

No database request is made executable merely because it exists in the source.

## Confirmed high-value READ examples

The real SID307 source contains, among many others:

| Request | Database name |
|---|---|
| `222005` | Battery voltage |
| `22200F` | Brake pedal - switches consolidation state |
| `222014` | Camshaft/crankshaft synchronization state |
| `22201A` | State of glow plug control actuator relay |
| `222021` | Sensors supply voltage 1 |
| `222022` | Sensors supply voltage 2 |
| `222023` | Sensors supply voltage 3 |
| `222025` | Brake pedal - close active switch state |
| `222026` | Brake pedal - open active switch state |
| `222045` | Clutch pedal - maximum travel switch state |
| `222046` | Starter status |
| `222048` | Cranking autorisation status |
| `222058` | Water detection in fuel state |
| `222059` | Clutch pedal - minimum travel switch state - wire |
| `222401` | Boost pressure |
| `222402` | Boost pressure setpoint |
| `222403` | Boost pressure PWM command |
| `222407` | EGR valve position |
| `222408` | EGR valve position setpoint |
| `222409` | EGR valve PWM command |
| `22240B` | Intake manifold pressure |
| `222417` | Inlet throttle position sensor voltage |
| `22241E` | Boost regulation state |
| `222424` | EGR valve position sensor voltage |
| `222435` | CSF differential pressure before after-sales regeneration |
| `222436` | CSF differential pressure after after-sales regeneration |
| `222446` | CSF upstream pressure sensor voltage |
| `222496` | CSF - Particulate filter soot mass |
| `222801` | Rail pressure |
| `222802` | Rail pressure setpoint |
| `222806` | Total fuel quantity |
| `2181` | VIN |

## Decoder evidence

The source does not merely name parameters. It also defines how response bytes map to values.

Examples verified directly:

- `222401` Boost pressure: 16-bit scaled value, unit mbar, first response data byte 4, minimum response 5 bytes.
- `222496` particulate-filter soot mass: 16-bit scaled value, divide by 100, unit g, first data byte 4.
- `222801` rail pressure: 16-bit scaled value, unit bar, first data byte 4.
- `222005` battery voltage: 16-bit scaled value, divide by 100, unit V, first data byte 4.
- `222021` sensor supply voltage 1: 16-bit scaled value, unit mV.
- `222026` brake-pedal open-active switch: 2-bit enumerated value at byte 4 / bit offset 6, with database states including not pressed and pressed.

This is sufficient to build a source-backed decoder instead of displaying raw hexadecimal responses.

## Electrical-diagnostic boundary

The source supports a useful ECU-view electrical layer: sensor supply voltages, switch states, relay commands, actuator states, synchronization states and diagnostic status information.

It does **not** turn an ELM327 into an ohmmeter. Physical wire resistance or continuity in ohms must not be synthesized. The application should label these values as ECU-reported state/voltage or database-derived interpretation.

## Runtime decision

The production `CommandFirewall` remains authoritative and unchanged for the existing generic OBD path.

The DDT path is being built as a second, definition-backed gate. A Renault request can only become eligible for read execution after:

1. exact physical ECU identity match,
2. transport match,
3. exact request membership in this source,
4. READ classification,
5. response decoder provenance,
6. explicit runtime integration through `SafeDiagnosticSession`.

WRITE / RESET / ROUTINE / OUTPUT CONTROL / CONFIGURATION remain outside the executable checkpoint.

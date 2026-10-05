# SID307 Runtime Decoder Verification

PR #6 runtime decoder checkpoint.

## Implemented

- manifest v2 loader + schema validation
- positive-response prefix and minimum-length validation
- UDS negative-response parser
- DDT2000 field extraction for bits/bytes
- DDT2000 scaled-value formula
- enum decoding
- ASCII decoding
- signed two's-complement handling
- DDT-style little-endian field extraction
- VIN CRC-16/X-25 validation
- non-sending session-context eligibility helper

## Source-grounded decoder semantics

DDT4All source was inspected before implementing scaling and bit geometry.

Observed DDT4All scaling formula:

`physical = (raw * step + offset) / divideBy`

For ordinary big-endian bit fields, DDT4All treats `BitOffset` as an offset into the MSB-first binary representation of the response bytes.

Therefore the actual SID307 brake switch definition:

- request: `222026`
- FirstByte: 4
- BitOffset: 6
- bitCount: 2
- enum:
  - 0 = reserved
  - 1 = not pressed
  - 2 = pressed

is decoded from the final two bits of response byte 4. The enum mapping is taken from the XML; it is not inferred.

## Runtime test vectors

### 222496 — particulate filter soot mass

Response:

`62 24 96 04 D2`

Raw = 1234, divideBy = 100.

Expected decoded value: **12.34 g**.

### 222401 — boost pressure

Response:

`62 24 01 03 E8`

Expected decoded value: **1000 mbar**.

### 222801 — rail pressure

Response:

`62 28 01 01 F4`

Expected decoded value: **500 bar**.

### 222026 — brake switch

Responses:

- `62 20 26 01` -> **not pressed**
- `62 20 26 02` -> **pressed**
- `62 20 26 00` -> **reserved**

### 2181 — VIN

VIN test value:

`VF1KZ140647630778`

CRC-16/X-25 as used by DDT4All VIN helper:

`65E6` in response byte order.

The runtime decoder validates VIN ASCII and CRC separately.

## NRC behavior

Runtime parser recognizes UDS negative response:

`7F <original service> <NRC>`

Current UI classifications include:

- 0x12 -> UNSUPPORTED
- 0x13 -> FORMAT_ERROR
- 0x22 -> CONDITIONS_NOT_CORRECT
- 0x31 -> UNSUPPORTED
- 0x33 -> SECURITY_REQUIRED
- 0x78 -> RESPONSE_PENDING
- unknown code -> NRC

NRC 0x13 is treated as `incorrectMessageLengthOrInvalidFormat`, not as a session prerequisite.

## Test result

Local authorized environment:

- 13 unit tests
- 13 passed
- 0 failed

The regenerated actual SID307 manifest also passes schema v2 validation:

- schemaVersion = 2
- readRequestCount = 984

## Scope boundary

No scheduler or UI implementation is included in this checkpoint.

No physical ECU request was sent.

All generated capabilities remain `executable=false`.
WRITE / RESET / ACTUATOR / CONFIGURATION / SECURITY hard blocks remain unchanged.

## Clarification about the 1935 definitions

The 1935 figure is the total number of DDT data definitions in the ECU XML. It does not by itself mean that every response is a multi-DID response. The runtime decoder therefore uses the manifest's explicit output fields and byte/bit offsets rather than assuming a generic multi-DID framing scheme.

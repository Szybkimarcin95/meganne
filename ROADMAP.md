# ROADMAP — CSDP-RM3 / MEGANE OVERLORD

## Phase 0 — Stabilization
Status: mostly completed
- remove synthetic production seed data
- protect Room data with explicit 2→3 migration
- confirm tests

## Phase 1 — Source of Truth
Status: completed for current snapshot
- audit repository
- classify real/simulated/hardcoded behavior
- audit OBD/ELM327 path
- audit Digital Twin
- audit SID307 candidates
- remove known fake user-facing circuit test
- sanitize invalid sensor trend mappings

## Phase 2 — Independent review / synchronization
Status: active
- use GitHub as shared source of truth
- maintain AGENTS/PROJECT_STATE/ROADMAP/PROVENANCE handoff files
- Codex read-only review of current snapshot
- compare review against actual code
- choose one safe implementation checkpoint

## Optional lane A — Second Android telemetry server
Status: implemented in repository, physical validation pending.

Location:
`tools/android-telemetry-server/`

Purpose:
- second Android phone as local Termux server,
- telemetry mirror/backup over LAN,
- SQLite WAL history,
- simple authenticated API,
- optional SSH and Termux:Boot autostart.

Required physical validation before app integration:
1. local `/health`,
2. LAN `/health` from primary phone,
3. authenticated telemetry POST,
4. latest-record readback,
5. operation while screen is locked,
6. operation after reboot/autostart.

The server must remain separate from ELM327/ECU transport. Do not make the second phone a competing Bluetooth client to the same ELM327.

Future app-side integration, if approved, should be an optional telemetry mirror client and must not replace Room or `DiagnosticTransport`.

## Phase 3 — Digital Twin foundation
Only after review:
- verify missing relation models in actual source
- introduce relation graph only if justified
- single source of truth
- provenance/status support
- no fabricated electrical data

## Phase 4 — Physical ELM327 readiness
- runtime Bluetooth permission handling
- paired-device flow
- RFCOMM/SPP robustness
- prompt buffering/timeouts/disconnect/reconnect
- real-adapter validation checklist
- no assumption that mock/unit tests prove hardware readiness

## Phase 5 — Standard OBD-II physical validation
- Mode 01
- Mode 02
- Mode 03
- Mode 07
- Mode 04 only as explicit protected action

## Phase 6 — SID307 identification
READ-only identification first.
- correlate candidate definitions
- establish ECU-identification criteria from verified sources/files
- match physical ECU
- record provenance/confidence

## Phase 7 — Renault/SID307 READ-only subset
- only verified requests
- safe live parameters
- DTC mappings
- provenance
- no actuator/reset/configuration

## Phase 8 — Digital Twin expansion
Potential entities:
- fuse
- relay
- circuit
- ground
- harness
- connector
- pin
- module
- component passport
- symptom/fault relations

## Phase 9 — UI simplification
Future direction:
- minimalist
- professional
- readable
- intuitive
- optimized for Android use in vehicle

Do not redesign before transport/data correctness is stable.

## Phase 10 — Write / actuator / reset / configuration
High-risk separate phase.
Default status: `LOCKED`.
Requires verified source, explicit safety review, user confirmation and physical validation plan.

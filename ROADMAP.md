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
Status: next
- use GitHub as shared source of truth
- Codex read-only review of current snapshot
- compare review against actual code
- choose one safe implementation checkpoint

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

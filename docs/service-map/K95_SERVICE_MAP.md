# K95 SERVICE MAP — Renault Mégane III Grandtour Bose

## Scope
Vehicle-specific service map for VIN/profile already defined in `VEHICLE_PROFILE.md`.

Evidence classes:
- `VEHICLE_CONFIRMED` — physically observed/measured on this car.
- `DATABASE_VERIFIED` — Renault/VISU-derived connector or circuit data corroborated by public service references.
- `PROJECT_DATABASE` — DDT/PyRen data already indexed in this project.
- `INFERENCE` — plausible but not yet physically matched.
- `UNVERIFIED` — not established.

## 1. BOSE woofer path

### Database-verified
Amplifier component: `1917 AUDIO AMPLIFIER`.

Woofer component: `1987 WOOFER (BASS)`.

Amplifier F081:
- pin 6 — 2.5 mm² — `34MX` — woofer audio +
- pin 2 — 2.5 mm² — `34MY` — woofer audio -
- pin 8 — 2.0 mm² — `MV` — audio earth
- pin 4 — 2.0 mm² — `BP1P` or `BP2F` depending option set — protected battery feed

Woofer CA23C:
- pin 1 — 2.5 mm² — `34MX` — woofer +
- pin 2 — 2.5 mm² — `34MY` — woofer -

Source status: `DATABASE_VERIFIED`.

### Vehicle-confirmed
- Factory BOSE amplifier label: `28063 9878R`.
- Physical 8-position connector at amplifier has been photographed.
- Wire-side orientation used during service work, latch at bottom:
  - top row: `8 7 6 5`
  - bottom row: `4 3 2 1`
- Current physical identification:
  - pin 6 = thick green/turquoise woofer lead
  - pin 2 = thick light/white-pink woofer lead
  - pin 4 = thick red feed
  - pin 8 = thick dark grey/black earth
- Actual woofer cable routing in this car:
  - amplifier/glovebox zone → side under dash → right side/sill area → 2-pin intermediate connector → short cable → woofer
  - it does NOT route along the passenger-seat rail.
- Two 25 A fuses high behind the glovebox were physically checked by removal; each interruption stops audio and reinsertion restores it.

### Current fault state
- Original symptom: bass intermittently returned when manipulating the 2-pin woofer connector.
- Connector was later bypassed.
- New speaker cable has now been installed from the internal woofer/speaker connection outward to approximately floor-exit level.
- Resistance test with probes shorted: ~0.1 ohm.
- Across woofer circuit the meter initially reads OL.
- Moving the external leads did not change OL.
- Accidental pressure/contact on the metal speaker basket produced unstable low readings roughly 0.8 → 0.6 → ... → 0.1 ohm.
- This is NOT yet proof of a healthy voice coil. Direct speaker terminal-to-terminal and terminal-to-basket measurements are still required.

### Next physical BOSE tests
1. Speaker disconnected from amplifier.
2. Measure direct speaker terminal + ↔ terminal -.
3. Measure terminal + ↔ metal basket; expected OL.
4. Measure terminal - ↔ metal basket; expected OL.
5. If direct speaker terminal resistance is stable and finite, continue to amplifier output test.
6. At amplifier, verify pin 6 and pin 2 terminal retention.
7. With a bass test signal, measure AC only BETWEEN pin 6 and pin 2; never reference either speaker output to chassis ground and never short the outputs together.

## 2. Rear camera path

### R2 — Dashboard / Left Rear Connection
Database-verified camera circuits:
- A8 — 0.35 mm² — `TB34` — shield
- A9 — 0.35 mm² — `34KL` — video
- A10 — 0.35 mm² — `34KK` — camera +
- A11 — 0.35 mm² — `34KJ` — camera -

Status: `DATABASE_VERIFIED`.

The large corroded multi-pin connector shown by the owner may be R2, but housing identity and A8-A11 have NOT yet been physically matched.
Status: `INFERENCE`.

### R372 — Rear / Fascia Band Connection
For K95:
- pin 1 — 0.35 mm² — `TB34` — shield
- pin 2 — 0.35 mm² — `34KL` — video
- pin 3 — 0.35 mm² — `34KJ` — camera -
- pin 4 — 0.35 mm² — `34KK` — camera +

Harness variant: CQ0A or LQ0A depending configuration.
Status: `DATABASE_VERIFIED`.

### Camera component
Component `1778 REAR CAMERA` exists in Megane III connector data; exact K95 terminal mapping remains to be added from the matching K95 variant before any repair conclusion.

## 3. Corroded dashboard/rear connector
Vehicle evidence shows severe turquoise/green copper corrosion in a large grey multi-row connector with a green secondary lock.

Repair rule:
- battery disconnected,
- photograph both halves and orientation,
- identify housing/pin numbering before depinning,
- service one terminal at a time,
- replace terminals/pigtails where corrosion has migrated under insulation,
- do not treat contact cleaner as a permanent repair.

Status of connector identity: `UNVERIFIED`.

## 4. HVAC / water-ingress zone
Vehicle evidence shows water tracks high on the passenger-side bulkhead and corrosion in electrical connections.

Priority leak zones:
1. scuttle/plenum drains,
2. fresh-air intake/filter housing,
3. HVAC case/firewall sealing,
4. evaporator condensate drain,
5. windscreen bonding/right A-pillar,
6. firewall grommets.

Relevant documented electrical points/components in this zone include `R99 Dashboard/Heating Connection`, `Mau`, `Nam`, evaporator sensor `408`, recirculation motor `475`, fan output module `1023`.

Leak-source ranking above is diagnostic inference from vehicle evidence, not a VISU source statement.

## 5. Data-source architecture
Target source merge:
- VISU/service wiring → circuits, connectors, pins, grounds, component locations
- DDT/PyRen → ECU identification, requests, live data, DTC/READ capabilities
- owner photos/measurements → physical location and exact vehicle evidence
- app Digital Twin → vehicle-specific graph with provenance

Target relation graph:
`fuse → circuit → relay → power → harness → connector → pin → component/module`
`ground → harness → component`
`symptom → cause → component → sensor/wiring/power → fuse → ground → module`

No unsupported electrical fact may be promoted to verified status.

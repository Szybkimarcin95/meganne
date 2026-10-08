# K95 Multi-ECU Inventory v1 — source browser checkpoint

Base: work/k95-service-map-ui-20261007, 9d9913f.

Implemented: ECU navigation tab, source catalog, independent ViewModel, identification/live/DTC/installation/source panels, BOSE circuit diagram bound to Service Map, catalog regression tests.

The only current Service Map module candidate is amplifier 1917, with UNVERIFIED bus presence and no diagnostic address. Passive subwoofer 1987 is not an ECU. R372 and R2 are camera-related connectors, not amplifier diagnostic endpoints. Existing DATABASE_VERIFIED labels are inherited metadata, not independently validated here.

Import contract: place only a reviewed, vehicle-specific DDT JSON subset under app/src/main/assets/ddt/k95/. The existing DdtCapabilityIndexer supplies addresses verbatim. Missing sources and malformed definitions are displayed. No full DDT dataset is packaged. No module aliases or TX+8 addresses are inferred. Source-file definitions remain separate until physical identification establishes equivalence.

Not implemented: physical discovery, addressed transport, Room persistence of observations. SafeDiagnosticSession currently exposes executeCommand(String), not sendRaw or readDataByIdentifier. CommandFirewall still blocks addressed CAN setup and UDS. The supplied fixed 7E* module list is not used. Bluetooth and firewall are unchanged.

No synthetic measurements, fault codes, voltages or wire colours. No Room schema change. No capability is advertised as physically supported.

Validation: git diff --check passes locally. Android compilation and Kotlin tests require CI; no Gradle/Android SDK available locally. Workflow compiles the app and runs catalog tests on a PR targeting main. No physical vehicle validation performed.

Next: obtain actual K95 DDT subset plus approved addressed-read transport implementation; design atomic adapter setup/request/restore against live-polling interleaving before enabling discovery. Persist real observations only after migration design and test.

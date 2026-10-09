# Room v4 + gated addressed-transaction checkpoint

Scope: complete K95Ecu persistence and addressed transaction scaffold, not a physical discoverer.

Room: k95_ecu stores every domain field and nested DdtEcuDescriptor as Moshi JSON. MIGRATION_3_4 adds only the new table; MIGRATION_2_3 remains registered. Removed destructive fallback. Source refresh uses INSERT IGNORE so it cannot overwrite a stored observation. Mapper rejects malformed JSON/status rather than silently replacing it with empty data. Existing database tables are preserved.

Session: extracted a private, already-locked command executor. executeAddressedTransaction validates the entire setup/read/restore plan before transmission and owns the mutex throughout. RX must be explicit. No tx+8 rule, fixed Renault module addresses or generic identification DIDs. Caller must supply an explicit reviewed restore plan; no automatic snapshot of adapter configuration exists yet. Restoration is attempted under NonCancellable, and failure invalidates and closes the transport. Adapter setup responses are separated from ECU read responses.

GLOBAL FIREWALL UNCHANGED. Addressed plans currently return BlockedByFirewall without touching transport. This is an intentionally gated API, not an enabled vehicle scanner. Setup/read/restore execution branches require dedicated fixture tests before future activation; current tests prove rejection and ordinary session regression only.

DdtDiscoveryCommandPolicy is an isolated proposal: a single DID READ in a source definition, an unambiguous READ classification, no request input parameters, expected reply metadata, and exact previously measured physical identity. No WRITE/RESET/ACTUATOR services. It is not wired to transport and does not solve initial identification. Bootstrap identity reads require a separately reviewed K95 source subset and response decoder/routing validation. AT OK, empty payloads and negative responses never establish ECU presence.

Tests: Room 2→4 preservation, Room 3→4 schema validation, full nested model round trip after DB reopen, candidate refresh preserving observations, corrupt JSON rejection, zero-transmission addressed preflight, existing session/firewall regressions, scoped policy rejection tests. Android CI runs these checks; no physical validation.

Next: reviewed K95 DDT subset, trustworthy adapter configuration tracking, fixture coverage of active setup/read/restore and cancellation, protocol frame attribution/negative-response parsing, then enable an explicit scoped READ capability and implement physical discovery.

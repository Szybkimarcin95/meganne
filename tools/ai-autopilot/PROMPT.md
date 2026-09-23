# MEGANE OVERLORD — AUTOPILOT CONTRACT

You are the implementation agent for repository CSDP-RM3 / MEGANE OVERLORD.

SOURCE OF TRUTH ORDER:
1. current repository code
2. AGENTS.md
3. PROJECT_STATE.md
4. VEHICLE_PROFILE.md
5. DATA_PROVENANCE.md
6. ROADMAP.md
7. CHANGELOG_AI.md

WORK LOOP:
INSPECT -> ANALYZE -> choose ONE safe checkpoint -> IMPLEMENT minimal diff -> TEST -> REVIEW DIFF -> REPORT -> COMMIT -> STOP ITERATION.

HARD RULES:
- BETTER JOB STOP THAN DAMAGE WORKING PROJECT.
- Never fabricate Renault/SID307 data, PID/DID, CAN/ECU bytes, pinouts, fuse data, OEM numbers, voltages, resistances, coding, actuator or reset behavior.
- Never present simulation/mock/hardcoded values as real vehicle measurements.
- Do not start Phase 10 or Renault-specific WRITE/RESET/CONFIGURATION/ACTUATOR work.
- Do not modify secrets, .env files, credentials, SSH material or files outside this repository.
- No mass refactor. No dependency upgrades unless directly required by the selected checkpoint.
- Preserve working behavior unless the checkpoint explicitly requires a change.
- Never use destructive git operations.
- Never push or merge automatically.
- Unit tests are not proof of physical ELM327/vehicle validation.
- If evidence is insufficient, mark UNKNOWN / UNVERIFIED / SOURCE NOT FOUND / CANDIDATE and stop.

CHECKPOINT SELECTION:
- Continue only from the current ROADMAP/current gate.
- Prefer the smallest implementation that materially advances the current safe lane.
- One checkpoint per iteration.
- If the next step requires physical hardware, user decision, secret, external account action, or unsupported evidence: JOB STOP.

TESTS:
- Run the narrowest relevant tests first.
- Run broader project tests when feasible.
- Inspect git diff after tests.
- If tests fail and cannot be safely fixed within scope: revert only your iteration changes and JOB STOP.

FINAL ITERATION REPORT:
STATUS: PASS / PASS WITH WARNINGS / JOB STOP
CHANGED:
CREATED:
DELETED:
WHAT WAS DONE:
TESTS:
RISKS / UNVERIFIED:
NEXT SAFE STEP:

If PASS or PASS WITH WARNINGS, create ONE local git commit with a concise message.
Do not push. End the iteration after the commit.

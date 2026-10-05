# SID307 READ-only Scheduler Verification

PR #6 scheduler checkpoint.

## Implemented

The scheduler is a pure READ-only runtime component over the manifest and an abstract async transport.

It can emit only services:

- 0x19
- 0x21
- 0x22

It explicitly rejects non-READ services.

No session-control request is emitted in this checkpoint.

## Configuration defaults

Current configurable defaults:

- P2 wait: 25 ms
- P2* wait after NRC 0x78: 5000 ms
- inter-request gap: 75 ms
- max request rate: 20 requests/s
- max response-pending frames: 3
- block size: 20
- inter-block gap: 75 ms

These are scheduler policy defaults. They are not claimed as timing values extracted from the SID307 XML.

Because the 75 ms inter-request gap is stricter than a 20 req/s limiter, the effective maximum continuous rate with defaults is about 13.3 req/s.

## Queue order

The scheduler sorts:

1. 0x22 reads carrying an explicit/unknown session prerequisite
2. ordinary 0x22 reads
3. 0x19 reads
4. 0x21 reads

Requests that require a session which the source does not safely resolve are marked SESSION_REQUIRED and are not sent.

The current scheduler does not send 0x10 01 / 0x10 03.

## NRC 0x78 behavior

UDS NRC 0x78 means the ECU accepted the request and is still processing it.

The scheduler therefore:

1. sends the READ request once,
2. waits P2 for the first response,
3. if 0x78 arrives, waits again using P2*,
4. does not retransmit the original request,
5. accepts up to the configured pending-frame limit,
6. returns NRC_TIMEOUT after that limit.

This avoids duplicating a request while the ECU is already processing it.

## Cancellation

Cancellation is modeled with an asyncio.Event.

After cancellation:

- the current request is allowed to finish its response handling,
- no new request is sent,
- all remaining queued requests become CANCELLED.

## Safety boundary

The scheduler does not emit:

- 0x10 session control
- 0x11 reset
- 0x14 clear diagnostics
- 0x27 security access
- 0x2E writes
- 0x2F / 0x30 controls
- 0x31 / 0x32 routines
- 0x34 / 0x36 / 0x37 transfer/programming
- 0x3D write memory

It does not communicate with a physical ECU in the test suite.

## Tests

Local unit test result:

- 20 tests total
- 20 passed
- 0 failed

Scheduler-specific coverage includes:

- synthetic 984-request queue sends only 0x19/0x21/0x22
- 0x78 waits without retransmitting the original request
- pending limit becomes NRC_TIMEOUT
- required-session request becomes SESSION_REQUIRED and sends nothing
- NoSDS/unknown session prerequisite becomes SESSION_REQUIRED
- cancellation marks remaining requests CANCELLED
- rate limiter never exceeds configured maximum

GitHub Actions:

- DDT Runtime Tests run #4: SUCCESS

CodeQL run #42 was still in progress at the time of this verification note.

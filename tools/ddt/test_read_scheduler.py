from __future__ import annotations

import asyncio
import copy
import unittest

from tools.ddt.runtime.scheduler import ReadScheduler, SchedulerConfig


def make_request(sent: str, *, session=None, prerequisite=None) -> dict:
    service = sent[:2]
    reply = {
        "22": "62" + sent[2:] + "00",
        "19": "59" + sent[2:] + "00",
        "21": "61" + sent[2:] + "00",
    }[service]
    return {
        "name": sent,
        "service": service,
        "sentBytes": sent,
        "replyBytes": reply,
        "minimumResponseBytes": len(bytes.fromhex(reply)),
        "manualSend": False,
        "inputs": [],
        "outputs": [{
            "name": "value",
            "firstByte": len(bytes.fromhex(reply)),
            "definition": {
                "name": "value",
                "encoding": "bits",
                "bitCount": 8,
                "signed": False,
                "valueType": "raw",
            },
        }],
        "nrcPolicy": {
            "78": {"name": "responsePending", "runtimeAction": "extend_timeout"},
            "31": {"name": "requestOutOfRange", "runtimeAction": "mark_unsupported_for_this_ecu"},
        },
        "requiredSession": session,
        "sessionPrerequisite": prerequisite,
        "accessConstraints": [],
        "securityRequired": None,
        "sourceLine": 1,
        "excludedFromRead": False,
        "executable": False,
    }


class FakeTransport:
    def __init__(self, responses):
        self.responses = list(responses)
        self.sent = []
        self.receive_timeouts = []

    async def send(self, payload: bytes) -> None:
        self.sent.append(payload)

    async def receive(self, timeout: float) -> bytes:
        self.receive_timeouts.append(timeout)
        if not self.responses:
            raise TimeoutError()
        response = self.responses.pop(0)
        if isinstance(response, BaseException):
            raise response
        return response


class FakeClock:
    def __init__(self):
        self.now = 0.0
        self.sleeps = []

    def monotonic(self):
        return self.now

    async def sleep(self, seconds: float):
        self.sleeps.append(seconds)
        self.now += seconds


class SchedulerTests(unittest.IsolatedAsyncioTestCase):
    async def test_queue_never_sends_forbidden_service(self):
        requests = []
        for i in range(912):
            did = 0x2000 + i
            requests.append(make_request(f"22{did:04X}"))
        for i in range(68):
            requests.append(make_request(f"1902{i:02X}"))
        for i in range(4):
            requests.append(make_request(f"21{0x80+i:02X}"))

        manifest = {"requests": requests}
        responses = []
        for r in requests:
            if r["service"] == "22":
                responses.append(bytes.fromhex("62" + r["sentBytes"][2:] + "00"))
            elif r["service"] == "19":
                responses.append(bytes.fromhex("59" + r["sentBytes"][2:] + "00"))
            else:
                responses.append(bytes.fromhex("61" + r["sentBytes"][2:] + "00"))

        transport = FakeTransport(responses)
        clock = FakeClock()
        scheduler = ReadScheduler(
            transport,
            manifest,
            config=SchedulerConfig(inter_request_gap_seconds=0.0, max_requests_per_second=1_000_000, block_size=0),
            sleep=clock.sleep,
            monotonic=clock.monotonic,
        )
        results = await scheduler.run()

        self.assertEqual(len(results), 984)
        self.assertEqual(len(transport.sent), 984)
        self.assertTrue(all(payload[0] in (0x19, 0x21, 0x22) for payload in transport.sent))

    async def test_response_pending_waits_without_retransmitting(self):
        request = make_request("222496")
        transport = FakeTransport([
            bytes.fromhex("7F2278"),
            bytes.fromhex("7F2278"),
            bytes.fromhex("62249600"),
        ])
        clock = FakeClock()
        scheduler = ReadScheduler(
            transport,
            {"requests": [request]},
            config=SchedulerConfig(p2_seconds=0.025, p2_star_seconds=5.0, max_response_pending=3, block_size=0),
            sleep=clock.sleep,
            monotonic=clock.monotonic,
        )

        results = await scheduler.run()

        self.assertEqual(len(transport.sent), 1)
        self.assertEqual(results[0]["uiStatus"], "SUPPORTED")
        self.assertEqual(transport.receive_timeouts, [0.025, 5.0, 5.0])

    async def test_too_many_pending_frames_becomes_nrc_timeout(self):
        request = make_request("222496")
        transport = FakeTransport([bytes.fromhex("7F2278")] * 4)
        scheduler = ReadScheduler(
            transport,
            {"requests": [request]},
            config=SchedulerConfig(max_response_pending=3, block_size=0),
        )

        results = await scheduler.run()

        self.assertEqual(len(transport.sent), 1)
        self.assertEqual(results[0]["uiStatus"], "NRC_TIMEOUT")
        self.assertEqual(results[0]["pendingCount"], 4)

    async def test_session_prerequisite_is_skipped_and_no_0x10_is_sent(self):
        constrained = make_request(
            "222496",
            session="extended",
        )
        transport = FakeTransport([])
        scheduler = ReadScheduler(transport, {"requests": [constrained]})

        results = await scheduler.run()

        self.assertEqual(results[0]["uiStatus"], "SESSION_REQUIRED")
        self.assertEqual(transport.sent, [])

    async def test_unknown_source_session_constraint_is_skipped(self):
        constrained = make_request(
            "21F0",
            prerequisite={
                "kind": "diagnosticSessionRequired",
                "source": "DenyAccess/NoSDS",
                "requiredSession": None,
            },
        )
        transport = FakeTransport([])
        scheduler = ReadScheduler(transport, {"requests": [constrained]})

        results = await scheduler.run()

        self.assertEqual(results[0]["uiStatus"], "SESSION_REQUIRED")
        self.assertEqual(transport.sent, [])

    async def test_cancellation_marks_remaining_queue_cancelled(self):
        requests = [make_request("222401"), make_request("222496"), make_request("222801")]
        event = asyncio.Event()

        class CancelAfterFirst(FakeTransport):
            async def send(self, payload: bytes) -> None:
                await super().send(payload)
                if len(self.sent) == 1:
                    event.set()

        transport = CancelAfterFirst([bytes.fromhex("62240100")])
        scheduler = ReadScheduler(
            transport,
            {"requests": requests},
            config=SchedulerConfig(inter_request_gap_seconds=0.0, max_requests_per_second=1_000_000, block_size=0),
        )

        results = await scheduler.run(cancel_event=event)

        self.assertEqual(len(transport.sent), 1)
        self.assertEqual(results[0]["uiStatus"], "SUPPORTED")
        self.assertEqual([r["uiStatus"] for r in results[1:]], ["CANCELLED", "CANCELLED"])

    async def test_rate_limit_never_exceeds_20_per_second(self):
        requests = [make_request("222401"), make_request("222496"), make_request("222801")]
        responses = [
            bytes.fromhex("62240100"),
            bytes.fromhex("62249600"),
            bytes.fromhex("62280100"),
        ]
        transport = FakeTransport(responses)
        clock = FakeClock()
        scheduler = ReadScheduler(
            transport,
            {"requests": requests},
            config=SchedulerConfig(
                inter_request_gap_seconds=0.0,
                max_requests_per_second=20.0,
                block_size=0,
            ),
            sleep=clock.sleep,
            monotonic=clock.monotonic,
        )

        await scheduler.run()

        self.assertEqual(len(transport.sent), 3)
        self.assertGreaterEqual(sum(clock.sleeps), 0.10)


if __name__ == "__main__":
    unittest.main()

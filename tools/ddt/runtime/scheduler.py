"""Deterministic READ-only scheduler for manifest-backed SID307 requests.

This module never changes diagnostic sessions and never emits mutating UDS services.
It schedules only source-backed READ services 0x19, 0x21 and 0x22.
"""

from __future__ import annotations

import asyncio
import time
from dataclasses import dataclass
from typing import Any, Awaitable, Callable, Protocol

from .decoder import decode_response, validate_request_context

READ_SERVICES = {"19", "21", "22"}
FORBIDDEN_SERVICES = {"10", "11", "14", "27", "2E", "2F", "30", "31", "32", "34", "36", "37", "3D"}


class AsyncReadTransport(Protocol):
    async def send(self, payload: bytes) -> None: ...

    async def receive(self, timeout: float) -> bytes: ...


@dataclass(frozen=True)
class SchedulerConfig:
    p2_seconds: float = 0.025
    p2_star_seconds: float = 5.0
    inter_request_gap_seconds: float = 0.075
    max_requests_per_second: float = 20.0
    max_response_pending: int = 3
    block_size: int = 20
    inter_block_gap_seconds: float = 0.075


class ReadScheduler:
    def __init__(
        self,
        transport: AsyncReadTransport,
        manifest: dict[str, Any],
        *,
        config: SchedulerConfig | None = None,
        sleep: Callable[[float], Awaitable[None]] = asyncio.sleep,
        monotonic: Callable[[], float] = time.monotonic,
    ) -> None:
        self.transport = transport
        self.manifest = manifest
        self.config = config or SchedulerConfig()
        self._sleep = sleep
        self._monotonic = monotonic

    def build_queue(self) -> list[dict[str, Any]]:
        requests = [
            r for r in self.manifest.get("requests", [])
            if r.get("service") in READ_SERVICES
            and not r.get("excludedFromRead", False)
        ]

        def priority(request: dict[str, Any]) -> tuple[int, str]:
            service = request.get("service")
            required = request.get("requiredSession")
            prerequisite = request.get("sessionPrerequisite")

            # Session-dependent reads are kept visible but will be skipped locally
            # until an explicitly approved session-control layer exists.
            if service == "22" and (required is not None or prerequisite is not None):
                bucket = 0
            elif service == "22":
                bucket = 1
            elif service == "19":
                bucket = 2
            else:  # 0x21
                bucket = 3
            return bucket, request.get("sentBytes", "")

        return sorted(requests, key=priority)

    @staticmethod
    def _payload_for(request: dict[str, Any]) -> bytes:
        service = request.get("service")
        if service not in READ_SERVICES:
            raise ValueError(f"non-READ service rejected by scheduler: {service}")
        payload = bytes.fromhex(request["sentBytes"])
        if not payload or f"{payload[0]:02X}" != service:
            raise ValueError("manifest service does not match sentBytes")
        if f"{payload[0]:02X}" in FORBIDDEN_SERVICES:
            raise ValueError("forbidden service rejected by scheduler")
        return payload

    async def _respect_rate_limit(self, last_sent_at: float | None) -> None:
        if last_sent_at is None:
            return
        min_rate_gap = 1.0 / self.config.max_requests_per_second
        required_gap = max(self.config.inter_request_gap_seconds, min_rate_gap)
        elapsed = self._monotonic() - last_sent_at
        if elapsed < required_gap:
            await self._sleep(required_gap - elapsed)

    async def _receive_until_terminal(
        self,
        request: dict[str, Any],
    ) -> dict[str, Any]:
        pending_count = 0
        timeout = self.config.p2_seconds

        while True:
            try:
                raw = await self.transport.receive(timeout)
            except TimeoutError:
                return {
                    "kind": "timeout",
                    "uiStatus": "TIMEOUT",
                    "request": request.get("sentBytes"),
                    "pendingCount": pending_count,
                }
            except asyncio.TimeoutError:
                return {
                    "kind": "timeout",
                    "uiStatus": "TIMEOUT",
                    "request": request.get("sentBytes"),
                    "pendingCount": pending_count,
                }

            decoded = decode_response(request, raw)
            if decoded.get("kind") == "nrc" and decoded.get("code") == "78":
                pending_count += 1
                if pending_count > self.config.max_response_pending:
                    return {
                        "kind": "timeout",
                        "uiStatus": "NRC_TIMEOUT",
                        "request": request.get("sentBytes"),
                        "pendingCount": pending_count,
                        "lastNrc": decoded,
                    }
                # UDS 0x78 means "request correctly received, response pending".
                # Do not retransmit the request; wait for the final response.
                timeout = self.config.p2_star_seconds
                continue
            return decoded

    async def run(self, cancel_event: asyncio.Event | None = None) -> list[dict[str, Any]]:
        queue = self.build_queue()
        results: list[dict[str, Any]] = []
        last_sent_at: float | None = None

        for index, request in enumerate(queue):
            if cancel_event is not None and cancel_event.is_set():
                results.extend(
                    {
                        "kind": "cancelled",
                        "uiStatus": "CANCELLED",
                        "request": pending.get("sentBytes"),
                    }
                    for pending in queue[index:]
                )
                break

            context = validate_request_context(request, current_session=None)
            if not context["eligible"]:
                results.append(
                    {
                        "kind": "skipped",
                        "uiStatus": context["uiStatus"],
                        "request": request.get("sentBytes"),
                        "reason": context.get("reason"),
                        "requiredSession": context.get("requiredSession"),
                    }
                )
                continue

            await self._respect_rate_limit(last_sent_at)
            payload = self._payload_for(request)
            await self.transport.send(payload)
            last_sent_at = self._monotonic()

            result = await self._receive_until_terminal(request)
            results.append(result)

            if (
                self.config.block_size > 0
                and (index + 1) % self.config.block_size == 0
                and index + 1 < len(queue)
            ):
                await self._sleep(self.config.inter_block_gap_seconds)

        return results

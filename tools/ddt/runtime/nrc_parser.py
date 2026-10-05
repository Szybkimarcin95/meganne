"""UDS negative-response parsing for the read-only DDT runtime."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

DEFAULT_NRC = {
    0x12: ("subFunctionNotSupported", "UNSUPPORTED"),
    0x13: ("incorrectMessageLengthOrInvalidFormat", "FORMAT_ERROR"),
    0x22: ("conditionsNotCorrect", "CONDITIONS_NOT_CORRECT"),
    0x31: ("requestOutOfRange", "UNSUPPORTED"),
    0x33: ("securityAccessDenied", "SECURITY_REQUIRED"),
    0x78: ("responsePending", "RESPONSE_PENDING"),
}


@dataclass(frozen=True)
class NegativeResponse:
    service: int
    code: int
    meaning: str
    ui_status: str
    runtime_action: str | None
    raw_hex: str

    def as_dict(self) -> dict[str, Any]:
        return {
            "kind": "nrc",
            "service": f"{self.service:02X}",
            "code": f"{self.code:02X}",
            "meaning": self.meaning,
            "uiStatus": self.ui_status,
            "runtimeAction": self.runtime_action,
            "rawHex": self.raw_hex,
        }


def _to_bytes(raw: bytes | bytearray | str) -> bytes:
    if isinstance(raw, (bytes, bytearray)):
        return bytes(raw)
    compact = "".join(raw.split())
    if len(compact) % 2:
        raise ValueError("hex response must contain whole bytes")
    try:
        return bytes.fromhex(compact)
    except ValueError as exc:
        raise ValueError("response is not valid hexadecimal") from exc


def parse_negative_response(
    raw: bytes | bytearray | str,
    request: dict[str, Any] | None = None,
) -> NegativeResponse | None:
    """Parse UDS `7F <service> <NRC>`; return None for a non-NRC response."""

    payload = _to_bytes(raw)
    if not payload or payload[0] != 0x7F:
        return None
    if len(payload) < 3:
        raise ValueError("truncated negative response; expected 7F <service> <NRC>")

    service = payload[1]
    code = payload[2]

    if request is not None:
        expected_service = int(str(request.get("service", "")), 16)
        if service != expected_service:
            raise ValueError(
                f"NRC service mismatch: response={service:02X}, request={expected_service:02X}"
            )

    meaning, ui_status = DEFAULT_NRC.get(code, ("unknownNegativeResponseCode", "NRC"))
    runtime_action = None

    if request is not None:
        policy = request.get("nrcPolicy") or {}
        entry = policy.get(f"{code:02X}") or policy.get(f"{code:02x}")
        if isinstance(entry, dict):
            meaning = entry.get("name") or meaning
            runtime_action = entry.get("runtimeAction")

    return NegativeResponse(
        service=service,
        code=code,
        meaning=meaning,
        ui_status=ui_status,
        runtime_action=runtime_action,
        raw_hex=payload.hex().upper(),
    )

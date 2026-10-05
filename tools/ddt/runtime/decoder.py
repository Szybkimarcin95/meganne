"""Manifest-driven, read-only response decoding for SID307 DDT requests."""

from __future__ import annotations

import json
import math
from pathlib import Path
from typing import Any

from .manifest_schema import validate_manifest_v2
from .nrc_parser import parse_negative_response


class ResponseValidationError(ValueError):
    pass


def _to_bytes(raw: bytes | bytearray | str) -> bytes:
    if isinstance(raw, (bytes, bytearray)):
        return bytes(raw)
    compact = "".join(raw.split())
    if len(compact) % 2:
        raise ResponseValidationError("hex response must contain whole bytes")
    try:
        return bytes.fromhex(compact)
    except ValueError as exc:
        raise ResponseValidationError("response is not valid hexadecimal") from exc


def load_manifest(path: str | Path) -> dict[str, Any]:
    manifest = json.loads(Path(path).read_text(encoding="utf-8"))
    return validate_manifest_v2(manifest)


def find_request(manifest: dict[str, Any], sent_bytes: str) -> dict[str, Any]:
    normalized = "".join(sent_bytes.split()).upper()
    matches = [r for r in manifest["requests"] if r.get("sentBytes") == normalized]
    if not matches:
        raise KeyError(f"request not present in manifest: {normalized}")
    if len(matches) > 1:
        raise KeyError(f"request is ambiguous in manifest: {normalized}")
    return matches[0]


def validate_request_context(
    request: dict[str, Any],
    current_session: str | None,
) -> dict[str, Any]:
    """Return a non-sending eligibility decision for a READ request."""

    if request.get("executable") is not False:
        return {"eligible": False, "uiStatus": "BLOCKED", "reason": "manifest_not_locked"}

    required = request.get("requiredSession")
    prerequisite = request.get("sessionPrerequisite")

    if isinstance(required, str) and required:
        if current_session != required:
            return {
                "eligible": False,
                "uiStatus": "SESSION_REQUIRED",
                "reason": "required_session_mismatch",
                "requiredSession": required,
            }

    if prerequisite and not required:
        return {
            "eligible": False,
            "uiStatus": "SESSION_REQUIRED",
            "reason": "source_declares_session_constraint_but_exact_session_is_unknown",
            "requiredSession": None,
        }

    return {"eligible": True, "uiStatus": "READY"}


def _positive_prefix(request: dict[str, Any]) -> bytes:
    reply_hex = request.get("replyBytes")
    if not reply_hex:
        return b""

    reply = bytes.fromhex(reply_hex)
    outputs = request.get("outputs") or []
    first_data_byte = min(
        (int(item["firstByte"]) for item in outputs if item.get("firstByte")),
        default=len(reply) + 1,
    )

    # DDT FirstByte is 1-based. Bytes before the first output are static
    # service/DID/local-identifier bytes in the source reply template.
    prefix_len = max(0, first_data_byte - 1)
    return reply[:prefix_len]


def validate_positive_response(
    request: dict[str, Any],
    raw: bytes | bytearray | str,
) -> bytes:
    payload = _to_bytes(raw)

    minimum = request.get("minimumResponseBytes")
    if isinstance(minimum, int) and len(payload) < minimum:
        raise ResponseValidationError(
            f"response too short: got {len(payload)} bytes, expected at least {minimum}"
        )

    prefix = _positive_prefix(request)
    if prefix and not payload.startswith(prefix):
        raise ResponseValidationError(
            f"positive response prefix mismatch: got={payload[:len(prefix)].hex().upper()} "
            f"expected={prefix.hex().upper()}"
        )

    if not payload:
        raise ResponseValidationError("empty response")
    if payload[0] == 0x7F:
        raise ResponseValidationError("negative response passed to positive validator")

    return payload


def _extract_big_endian_bits(
    payload: bytes,
    first_byte: int,
    bit_offset: int,
    bit_count: int,
) -> int:
    start = first_byte - 1
    needed = math.ceil((bit_offset + bit_count) / 8)
    chunk = payload[start:start + needed]
    if len(chunk) != needed:
        raise ResponseValidationError("response does not contain enough bytes for bit field")

    bit_string = "".join(f"{byte:08b}" for byte in chunk)
    selected = bit_string[bit_offset:bit_offset + bit_count]
    if len(selected) != bit_count:
        raise ResponseValidationError("response does not contain enough bits for field")
    return int(selected, 2)


def _extract_little_endian_bits_ddt(
    payload: bytes,
    first_byte: int,
    bit_offset: int,
    bit_count: int,
) -> int:
    """Mirror DDT4All's DDT2000 little-endian field extraction."""

    start = first_byte - 1
    needed = math.ceil((bit_count + bit_offset) / 8)
    chunk = payload[start:start + needed]
    if len(chunk) != needed:
        raise ResponseValidationError("response does not contain enough bytes for little-endian field")

    bits = "".join(f"{byte:08b}" for byte in chunk)
    remaining = bit_count

    last_bit = 7 - bit_offset + 1
    first_bit = max(0, last_bit - bit_count)
    selected = bits[first_bit:last_bit]
    remaining -= last_bit - first_bit

    if remaining > 8:
        offset1 = 8
        offset2 = offset1 + ((needed - 2) * 8)
        selected += bits[offset1:offset2]
        remaining -= offset2 - offset1

    if remaining > 0:
        offset1 = (needed - 1) * 8
        offset2 = offset1 - remaining
        selected += bits[offset2:offset1]
        remaining -= offset1 - offset2

    if remaining != 0:
        raise ResponseValidationError("unable to resolve little-endian field geometry")
    return int(selected, 2)


def _twos_complement(value: int, bit_count: int) -> int:
    sign_bit = 1 << (bit_count - 1)
    return value - (1 << bit_count) if value & sign_bit else value


def _decode_definition(raw_value: int | bytes, definition: dict[str, Any]) -> Any:
    value_type = definition.get("valueType")

    if value_type == "ascii":
        if not isinstance(raw_value, bytes):
            raise ResponseValidationError("ASCII decoder expected bytes")
        return raw_value.decode("ascii", errors="strict").rstrip("\x00")

    if isinstance(raw_value, bytes):
        raw_int = int.from_bytes(raw_value, byteorder="big", signed=False)
    else:
        raw_int = raw_value

    bit_count = definition.get("bitCount")
    if definition.get("signed") and isinstance(bit_count, int) and bit_count > 0:
        raw_int = _twos_complement(raw_int, bit_count)

    if value_type == "enum":
        for item in definition.get("items") or []:
            if item.get("value") == raw_int:
                return item.get("text")
        return raw_int

    if value_type == "scaled":
        step = definition.get("step", 1)
        offset = definition.get("offset", 0)
        divide_by = definition.get("divideBy", 1)
        if divide_by == 0:
            raise ResponseValidationError("scaled definition has divideBy=0")
        return (float(raw_int) * float(step) + float(offset)) / float(divide_by)

    if value_type == "raw":
        return raw_int

    raise ResponseValidationError(f"unsupported valueType: {value_type!r}")


def decode_field(payload: bytes, output: dict[str, Any]) -> dict[str, Any]:
    definition = output["definition"]
    first_byte = int(output["firstByte"])
    encoding = definition["encoding"]

    if encoding == "bytes":
        count = int(definition["byteCount"])
        start = first_byte - 1
        raw_bytes = payload[start:start + count]
        if len(raw_bytes) != count:
            raise ResponseValidationError(
                f"response too short for {output.get('name')}: need {count} bytes at {first_byte}"
            )
        raw_for_decode: int | bytes = raw_bytes
        raw_hex = raw_bytes.hex().upper()

    elif encoding == "bits":
        bit_count = int(definition["bitCount"])
        bit_offset = int(output.get("bitOffset", 0))
        endian = output.get("endian", "Big")
        if endian == "Little":
            raw_int = _extract_little_endian_bits_ddt(
                payload, first_byte, bit_offset, bit_count
            )
        else:
            raw_int = _extract_big_endian_bits(
                payload, first_byte, bit_offset, bit_count
            )
        raw_for_decode = raw_int
        raw_hex = f"{raw_int:0{math.ceil(bit_count / 4)}X}"

    else:
        raise ResponseValidationError(f"unsupported field encoding: {encoding!r}")

    decoded = _decode_definition(raw_for_decode, definition)
    return {
        "name": output.get("name"),
        "rawHex": raw_hex,
        "value": decoded,
        "unit": definition.get("unit"),
        "valueType": definition.get("valueType"),
        "sourceDefinition": definition.get("mnemonic") or definition.get("name"),
    }


def crc16_x25_little_endian(data: bytes) -> bytes:
    """Return Renault/DDT VIN CRC bytes as used by DDT4All's X-25 helper."""

    crc = 0xFFFF
    for byte in data:
        crc ^= byte
        for _ in range(8):
            crc = (crc >> 1) ^ 0x8408 if crc & 1 else crc >> 1
    crc ^= 0xFFFF
    return crc.to_bytes(2, byteorder="little")


def _vin_crc_result(values: list[dict[str, Any]]) -> dict[str, Any] | None:
    by_name = {item.get("name"): item for item in values}
    vin = by_name.get("VIN")
    vin_crc = by_name.get("VINcrc")
    if not vin or not vin_crc or not isinstance(vin.get("value"), str):
        return None

    expected = crc16_x25_little_endian(vin["value"].encode("ascii")).hex().upper()
    actual = vin_crc.get("rawHex")
    return {
        "algorithm": "CRC-16/X-25",
        "byteOrder": "little",
        "expectedHex": expected,
        "actualHex": actual,
        "valid": actual == expected,
    }


def decode_response(
    request: dict[str, Any],
    raw: bytes | bytearray | str,
) -> dict[str, Any]:
    """Decode one already-received ECU response without sending anything."""

    nrc = parse_negative_response(raw, request=request)
    if nrc is not None:
        return nrc.as_dict()

    payload = validate_positive_response(request, raw)
    values = [decode_field(payload, output) for output in request["outputs"]]

    result: dict[str, Any] = {
        "kind": "positive",
        "uiStatus": "SUPPORTED",
        "request": request.get("sentBytes"),
        "response": payload.hex().upper(),
        "sourceLine": request.get("sourceLine"),
        "values": values,
    }

    vin_crc = _vin_crc_result(values)
    if vin_crc is not None:
        result["vinCrc"] = vin_crc

    return result

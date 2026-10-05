#!/usr/bin/env python3
"""Build a read-only vehicle-specific manifest from one DDT2000 ECU XML file.

This tool is intentionally conservative:
- includes only services 0x19, 0x21 and 0x22,
- preserves source request/response metadata,
- resolves DDT Data definitions into decoder metadata,
- never communicates with a vehicle.
"""

from __future__ import annotations

import argparse
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any

READ_SERVICES = {"19", "21", "22"}


def local(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def child(element: ET.Element | None, name: str) -> ET.Element | None:
    if element is None:
        return None
    return next((x for x in list(element) if local(x.tag) == name), None)


def text(element: ET.Element | None) -> str:
    return (element.text or "").strip() if element is not None else ""


def normalize_hex(value: str) -> str:
    return re.sub(r"[^0-9A-Fa-f]", "", value or "").upper()


def number(value: str | None) -> int | float | None:
    if value is None:
        return None
    value = value.strip()
    if not value:
        return None
    try:
        parsed = float(value)
        return int(parsed) if parsed.is_integer() else parsed
    except ValueError:
        return None


def first_descendant(root: ET.Element, name: str) -> ET.Element | None:
    return next((x for x in root.iter() if local(x.tag) == name), None)


def parse_can_id(container: ET.Element | None) -> str | None:
    if container is None:
        return None
    can_id = next((x for x in container.iter() if local(x.tag) == "CANId"), None)
    if can_id is None:
        return None
    raw = can_id.attrib.get("Value")
    try:
        return f"{int(raw):X}" if raw is not None else None
    except ValueError:
        return raw


def parse_data_definition(element: ET.Element) -> dict[str, Any]:
    result: dict[str, Any] = {
        "name": element.attrib.get("Name"),
        "comment": text(child(element, "Comment")) or None,
        "mnemonic": text(child(element, "Mnemonic")) or None,
    }

    bits = child(element, "Bits")
    byte_data = child(element, "Bytes")

    if bits is not None:
        result["encoding"] = "bits"
        result["bitCount"] = int(bits.attrib.get("count", "0"))
        result["signed"] = bits.attrib.get("signed") == "1"

        scaled = child(bits, "Scaled")
        enum_list = child(bits, "List")
        if scaled is not None:
            result["valueType"] = "scaled"
            result["unit"] = scaled.attrib.get("Unit")
            result["step"] = number(scaled.attrib.get("Step"))
            result["offset"] = number(scaled.attrib.get("Offset"))
            result["divideBy"] = number(scaled.attrib.get("DivideBy"))
        elif enum_list is not None:
            result["valueType"] = "enum"
            result["items"] = [
                {
                    "value": number(item.attrib.get("Value")),
                    "text": item.attrib.get("Text"),
                }
                for item in list(enum_list)
                if local(item.tag) == "Item"
            ]
        else:
            result["valueType"] = "raw"

    elif byte_data is not None:
        result["encoding"] = "bytes"
        result["byteCount"] = int(byte_data.attrib.get("count", "0"))
        result["ascii"] = byte_data.attrib.get("ascii") == "1"
        result["valueType"] = "ascii" if result["ascii"] else "raw"
    else:
        result["encoding"] = "unknown"
        result["valueType"] = "unknown"

    return {k: v for k, v in result.items() if v is not None}


def parse_data_item(
    item: ET.Element,
    definitions: dict[str, dict[str, Any]],
) -> dict[str, Any]:
    name = item.attrib.get("Name", "")
    result: dict[str, Any] = {
        "name": name,
        "firstByte": int(item.attrib["FirstByte"]) if item.attrib.get("FirstByte") else None,
        "bitOffset": int(item.attrib["BitOffset"]) if item.attrib.get("BitOffset") else None,
        "endian": item.attrib.get("Endian"),
        "definition": definitions.get(name),
    }
    return {k: v for k, v in result.items() if v is not None}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("xml", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    root = ET.parse(args.xml).getroot()
    target = first_descendant(root, "Target")
    can = first_descendant(root, "CAN")
    send_id = parse_can_id(child(can, "SendId"))
    receive_id = parse_can_id(child(can, "ReceiveId"))

    definitions = {
        e.attrib["Name"]: parse_data_definition(e)
        for e in root.iter()
        if local(e.tag) == "Data" and e.attrib.get("Name")
    }

    autoidents = [
        {
            "diagVersion": e.attrib.get("DiagVersion"),
            "supplier": e.attrib.get("Supplier"),
            "software": e.attrib.get("Soft"),
            "version": e.attrib.get("Version"),
        }
        for e in root.iter()
        if local(e.tag) == "AutoIdent"
    ]

    requests: list[dict[str, Any]] = []
    all_service_counts: dict[str, int] = {}

    for request in root.iter():
        if local(request.tag) != "Request":
            continue

        sent = child(request, "Sent")
        received = child(request, "Received")
        sent_bytes = normalize_hex(text(child(sent, "SentBytes")))
        service = sent_bytes[:2]
        all_service_counts[service] = all_service_counts.get(service, 0) + 1

        if service not in READ_SERVICES:
            continue

        inputs = [
            parse_data_item(x, definitions)
            for x in (sent.iter() if sent is not None else [])
            if local(x.tag) == "DataItem"
        ]
        outputs = [
            parse_data_item(x, definitions)
            for x in (received.iter() if received is not None else [])
            if local(x.tag) == "DataItem"
        ]

        minimum_bytes = None
        if received is not None and received.attrib.get("MinBytes"):
            minimum_bytes = int(received.attrib["MinBytes"])

        requests.append(
            {
                "name": request.attrib.get("Name", ""),
                "service": service,
                "sentBytes": sent_bytes,
                "replyBytes": normalize_hex(text(child(received, "ReplyBytes"))) or None,
                "minimumResponseBytes": minimum_bytes,
                "manualSend": child(request, "ManuelSend") is not None,
                "inputs": inputs,
                "outputs": outputs,
                "executable": False,
            }
        )

    description = text(child(target, "Description")) if target is not None else ""

    manifest = {
        "schemaVersion": 1,
        "sourceType": "DDT2000_XML",
        "sourceFile": args.xml.name,
        "target": target.attrib.get("Name") if target is not None else None,
        "sourceDescription": description,
        "transport": {
            "protocol": "CAN",
            "baudRate": int(can.attrib["BaudRate"]) if can is not None and can.attrib.get("BaudRate") else None,
            "sendId": send_id,
            "receiveId": receive_id,
        },
        "autoIdents": autoidents,
        "readServices": sorted(READ_SERVICES),
        "allServiceCounts": dict(sorted(all_service_counts.items())),
        "readRequestCount": len(requests),
        "requests": requests,
    }

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(manifest, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )

    print(f"wrote={args.output}")
    print(f"read_requests={len(requests)}")
    print(f"send_id={send_id} receive_id={receive_id}")
    print(f"data_definitions={len(definitions)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

from __future__ import annotations

import copy
import unittest

from tools.ddt.runtime.decoder import (
    ResponseValidationError,
    crc16_x25_little_endian,
    decode_response,
    validate_positive_response,
)
from tools.ddt.runtime.manifest_schema import (
    ManifestValidationError,
    validate_manifest_v2,
)
from tools.ddt.runtime.nrc_parser import parse_negative_response


NRC_POLICY = {
    "12": {"name": "subFunctionNotSupported", "runtimeAction": "mark_unsupported"},
    "13": {"name": "incorrectMessageLengthOrInvalidFormat", "runtimeAction": "do_not_retry_without_request_fix"},
    "22": {"name": "conditionsNotCorrect", "runtimeAction": "bounded_retry_or_skip"},
    "31": {"name": "requestOutOfRange", "runtimeAction": "mark_unsupported_for_this_ecu"},
    "33": {"name": "securityAccessDenied", "runtimeAction": "security_blocked"},
    "78": {"name": "responsePending", "runtimeAction": "extend_timeout"},
}


def request(
    sent: str,
    reply: str,
    minimum: int,
    output: dict,
    source_line: int = 1,
) -> dict:
    return {
        "name": f"test.{sent}",
        "service": sent[:2],
        "sentBytes": sent,
        "replyBytes": reply,
        "minimumResponseBytes": minimum,
        "manualSend": False,
        "inputs": [],
        "outputs": [output],
        "nrcPolicy": copy.deepcopy(NRC_POLICY),
        "requiredSession": None,
        "sessionPrerequisite": None,
        "accessConstraints": [],
        "securityRequired": None,
        "sourceLine": source_line,
        "excludedFromRead": False,
        "executable": False,
    }


SOOT = request(
    "222496",
    "6224960000",
    5,
    {
        "name": "CSF - Particulate filter soot mass",
        "firstByte": 4,
        "definition": {
            "name": "CSF - Particulate filter soot mass",
            "mnemonic": "DID_$2496",
            "encoding": "bits",
            "bitCount": 16,
            "signed": False,
            "valueType": "scaled",
            "unit": "g",
            "divideBy": 100,
        },
    },
    19220,
)

BOOST = request(
    "222401",
    "6224010000",
    5,
    {
        "name": "Boost pressure",
        "firstByte": 4,
        "definition": {
            "name": "Boost pressure",
            "mnemonic": "DID_$2401",
            "encoding": "bits",
            "bitCount": 16,
            "signed": False,
            "valueType": "scaled",
            "unit": "mbar",
        },
    },
)

RAIL = request(
    "222801",
    "6228010000",
    5,
    {
        "name": "Rail pressure",
        "firstByte": 4,
        "definition": {
            "name": "Rail pressure",
            "mnemonic": "DID_$2801",
            "encoding": "bits",
            "bitCount": 16,
            "signed": False,
            "valueType": "scaled",
            "unit": "bar",
        },
    },
)

BRAKE = request(
    "222026",
    "62202600",
    4,
    {
        "name": "Brake pedal - open active switch state",
        "firstByte": 4,
        "bitOffset": 6,
        "definition": {
            "name": "Brake pedal - open active switch state",
            "mnemonic": "DID_$2026",
            "encoding": "bits",
            "bitCount": 2,
            "signed": False,
            "valueType": "enum",
            "items": [
                {"value": 0, "text": "reserved"},
                {"value": 1, "text": "not pressed"},
                {"value": 2, "text": "pressed"},
            ],
        },
    },
)

VIN = {
    "name": "DataRead.VIN",
    "service": "21",
    "sentBytes": "2181",
    "replyBytes": "6181",
    "minimumResponseBytes": 21,
    "manualSend": False,
    "inputs": [],
    "outputs": [
        {
            "name": "VINcrc",
            "firstByte": 20,
            "definition": {
                "name": "VINcrc",
                "mnemonic": "VINCRC",
                "encoding": "bytes",
                "byteCount": 2,
                "ascii": False,
                "valueType": "raw",
            },
        },
        {
            "name": "VIN",
            "firstByte": 3,
            "definition": {
                "name": "VIN",
                "mnemonic": "VIN",
                "encoding": "bytes",
                "byteCount": 17,
                "ascii": True,
                "valueType": "ascii",
            },
        },
    ],
    "nrcPolicy": copy.deepcopy(NRC_POLICY),
    "requiredSession": None,
    "sessionPrerequisite": None,
    "accessConstraints": [],
    "securityRequired": None,
    "sourceLine": 1,
    "excludedFromRead": False,
    "executable": False,
}


class DecoderTests(unittest.TestCase):
    def test_scaled_soot_mass_uses_ddt_formula(self) -> None:
        decoded = decode_response(SOOT, "62 24 96 04 D2")
        value = decoded["values"][0]
        self.assertEqual(value["unit"], "g")
        self.assertAlmostEqual(value["value"], 12.34)

    def test_boost_pressure(self) -> None:
        decoded = decode_response(BOOST, "62 24 01 03 E8")
        self.assertEqual(decoded["values"][0]["value"], 1000.0)
        self.assertEqual(decoded["values"][0]["unit"], "mbar")

    def test_rail_pressure(self) -> None:
        decoded = decode_response(RAIL, "62 28 01 01 F4")
        self.assertEqual(decoded["values"][0]["value"], 500.0)
        self.assertEqual(decoded["values"][0]["unit"], "bar")

    def test_two_bit_brake_enum_is_msb_indexed_like_ddt4all(self) -> None:
        not_pressed = decode_response(BRAKE, "62 20 26 01")
        pressed = decode_response(BRAKE, "62 20 26 02")
        reserved = decode_response(BRAKE, "62 20 26 00")
        self.assertEqual(not_pressed["values"][0]["value"], "not pressed")
        self.assertEqual(pressed["values"][0]["value"], "pressed")
        self.assertEqual(reserved["values"][0]["value"], "reserved")

    def test_vin_ascii_and_x25_crc(self) -> None:
        vin = "VF1KZ140647630778"
        crc = crc16_x25_little_endian(vin.encode("ascii"))
        self.assertEqual(crc.hex().upper(), "65E6")
        raw = b"\x61\x81" + vin.encode("ascii") + crc
        decoded = decode_response(VIN, raw)
        values = {v["name"]: v for v in decoded["values"]}
        self.assertEqual(values["VIN"]["value"], vin)
        self.assertTrue(decoded["vinCrc"]["valid"])

    def test_wrong_positive_prefix_is_rejected(self) -> None:
        with self.assertRaises(ResponseValidationError):
            validate_positive_response(SOOT, "62 24 95 04 D2")

    def test_short_response_is_rejected(self) -> None:
        with self.assertRaises(ResponseValidationError):
            validate_positive_response(SOOT, "62 24 96 04")

    def test_nrc_31_maps_to_unsupported(self) -> None:
        nrc = parse_negative_response("7F 22 31", SOOT)
        self.assertIsNotNone(nrc)
        self.assertEqual(nrc.ui_status, "UNSUPPORTED")
        self.assertEqual(nrc.meaning, "requestOutOfRange")

    def test_nrc_13_is_format_error_not_session_required(self) -> None:
        decoded = decode_response(SOOT, "7F 22 13")
        self.assertEqual(decoded["uiStatus"], "FORMAT_ERROR")
        self.assertEqual(decoded["meaning"], "incorrectMessageLengthOrInvalidFormat")

    def test_nrc_33_maps_to_security_required(self) -> None:
        decoded = decode_response(SOOT, "7F 22 33")
        self.assertEqual(decoded["uiStatus"], "SECURITY_REQUIRED")

    def test_nrc_78_maps_to_response_pending(self) -> None:
        decoded = decode_response(SOOT, "7F 22 78")
        self.assertEqual(decoded["uiStatus"], "RESPONSE_PENDING")


class ManifestSchemaTests(unittest.TestCase):
    def test_minimal_manifest_v2_accepts_source_backed_record(self) -> None:
        manifest = {
            "schemaVersion": 2,
            "transport": {"sendId": "7E0", "receiveId": "7E8", "baudRate": 500000},
            "readRequestCount": 1,
            "requests": [copy.deepcopy(SOOT)],
        }
        self.assertIs(validate_manifest_v2(manifest), manifest)

    def test_manifest_rejects_executable_read(self) -> None:
        unsafe = copy.deepcopy(SOOT)
        unsafe["executable"] = True
        manifest = {
            "schemaVersion": 2,
            "transport": {"sendId": "7E0", "receiveId": "7E8", "baudRate": 500000},
            "readRequestCount": 1,
            "requests": [unsafe],
        }
        with self.assertRaises(ManifestValidationError):
            validate_manifest_v2(manifest)


if __name__ == "__main__":
    unittest.main()

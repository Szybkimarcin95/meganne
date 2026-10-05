"""Strict-enough validation for the generated SID307 read manifest."""

from __future__ import annotations

from typing import Any

READ_SERVICES = {"19", "21", "22"}


class ManifestValidationError(ValueError):
    pass


def _fail(message: str) -> None:
    raise ManifestValidationError(message)


def validate_manifest_v2(manifest: dict[str, Any]) -> dict[str, Any]:
    if not isinstance(manifest, dict):
        _fail("manifest must be a JSON object")
    if manifest.get("schemaVersion") != 2:
        _fail("schemaVersion must be 2")

    transport = manifest.get("transport")
    if not isinstance(transport, dict):
        _fail("transport must be an object")
    for key in ("sendId", "receiveId", "baudRate"):
        if transport.get(key) in (None, ""):
            _fail(f"transport.{key} is required")

    requests = manifest.get("requests")
    if not isinstance(requests, list):
        _fail("requests must be a list")
    if manifest.get("readRequestCount") != len(requests):
        _fail("readRequestCount does not match requests length")

    for index, request in enumerate(requests):
        prefix = f"requests[{index}]"
        if not isinstance(request, dict):
            _fail(f"{prefix} must be an object")

        service = request.get("service")
        if service not in READ_SERVICES:
            _fail(f"{prefix}.service={service!r} is not READ-eligible")

        sent = request.get("sentBytes")
        if not isinstance(sent, str) or not sent or len(sent) % 2:
            _fail(f"{prefix}.sentBytes must be even-length hex text")

        if not isinstance(request.get("sourceLine"), int) or request["sourceLine"] <= 0:
            _fail(f"{prefix}.sourceLine must be a positive integer")

        session = request.get("requiredSession")
        if session is not None and not isinstance(session, str):
            _fail(f"{prefix}.requiredSession must be null or string")

        prerequisite = request.get("sessionPrerequisite")
        if prerequisite is not None and not isinstance(prerequisite, dict):
            _fail(f"{prefix}.sessionPrerequisite must be null or object")

        constraints = request.get("accessConstraints")
        if not isinstance(constraints, list) or not all(isinstance(x, str) for x in constraints):
            _fail(f"{prefix}.accessConstraints must be a list of strings")

        security = request.get("securityRequired")
        if security is not None and not isinstance(security, bool):
            _fail(f"{prefix}.securityRequired must be null or bool")

        if not isinstance(request.get("excludedFromRead"), bool):
            _fail(f"{prefix}.excludedFromRead must be bool")
        if request.get("executable") is not False:
            _fail(f"{prefix}.executable must remain false")

        policy = request.get("nrcPolicy")
        if not isinstance(policy, dict):
            _fail(f"{prefix}.nrcPolicy must be an object")

        outputs = request.get("outputs")
        if not isinstance(outputs, list) or not outputs:
            _fail(f"{prefix}.outputs must be a non-empty list")
        for out_index, output in enumerate(outputs):
            op = f"{prefix}.outputs[{out_index}]"
            if not isinstance(output, dict):
                _fail(f"{op} must be an object")
            if not isinstance(output.get("firstByte"), int) or output["firstByte"] <= 0:
                _fail(f"{op}.firstByte must be a positive integer")
            definition = output.get("definition")
            if not isinstance(definition, dict):
                _fail(f"{op}.definition is required")
            if definition.get("encoding") not in {"bits", "bytes"}:
                _fail(f"{op}.definition.encoding must be bits or bytes")
            if definition.get("valueType") not in {"scaled", "enum", "raw", "ascii"}:
                _fail(f"{op}.definition.valueType is unsupported")

    return manifest

#!/usr/bin/env python3
"""CSDP-RM3 auxiliary telemetry server for a second Android phone.

Runs in Termux using only Python's standard library.
It is intentionally independent from the Android app and never talks to ELM327/ECU.
The primary phone may mirror already-read telemetry over LAN to this server.
"""

from __future__ import annotations

import argparse
import json
import logging
from logging.handlers import RotatingFileHandler
import os
from pathlib import Path
import sqlite3
import threading
import time
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

APP_VERSION = "0.1.0"
MAX_BODY_BYTES = 128 * 1024
DEFAULT_HOST = "0.0.0.0"
DEFAULT_PORT = 8765
DEFAULT_DB = "data/telemetry.db"
DEFAULT_LOG = "data/server.log"

STARTED_AT_MS = int(time.time() * 1000)
DB_LOCK = threading.Lock()
DB_PATH: Path
AUTH_TOKEN: str
LOGGER = logging.getLogger("csdp-server")


def configure_logging(log_path: Path) -> None:
    log_path.parent.mkdir(parents=True, exist_ok=True)
    LOGGER.setLevel(logging.INFO)
    handler = RotatingFileHandler(log_path, maxBytes=2_000_000, backupCount=3, encoding="utf-8")
    handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(message)s"))
    LOGGER.handlers.clear()
    LOGGER.addHandler(handler)
    LOGGER.addHandler(logging.StreamHandler())


def connect_db() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH, timeout=10)
    conn.row_factory = sqlite3.Row
    return conn


def init_db() -> None:
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    with DB_LOCK, connect_db() as conn:
        conn.execute("PRAGMA journal_mode=WAL")
        conn.execute("PRAGMA synchronous=NORMAL")
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS telemetry (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                received_at_ms INTEGER NOT NULL,
                timestamp_ms INTEGER,
                vehicle_id TEXT,
                device_id TEXT,
                source TEXT,
                is_simulated INTEGER NOT NULL DEFAULT 0,
                verification_status TEXT,
                payload_json TEXT NOT NULL
            )
            """
        )
        conn.execute("CREATE INDEX IF NOT EXISTS idx_telemetry_received ON telemetry(received_at_ms DESC)")
        conn.commit()


def require_token() -> str:
    token = os.environ.get("CSDP_SERVER_TOKEN", "").strip()
    if len(token) < 24:
        raise SystemExit(
            "CSDP_SERVER_TOKEN is missing or too short. Run ./install.sh or export a random token >=24 chars."
        )
    return token


def normalize_payload(payload: dict) -> dict:
    metrics = payload.get("metrics")
    if metrics is not None and not isinstance(metrics, dict):
        raise ValueError("metrics must be a JSON object when present")

    source = payload.get("source")
    if source is not None and not isinstance(source, str):
        raise ValueError("source must be a string when present")

    normalized = {
        "timestamp_ms": payload.get("timestampMs"),
        "vehicle_id": payload.get("vehicleId"),
        "device_id": payload.get("deviceId"),
        "source": source,
        "is_simulated": bool(payload.get("isSimulated", False)),
        "verification_status": payload.get("verificationStatus"),
        "payload": payload,
    }
    return normalized


def insert_telemetry(payload: dict) -> int:
    row = normalize_payload(payload)
    received_at_ms = int(time.time() * 1000)
    with DB_LOCK, connect_db() as conn:
        cur = conn.execute(
            """
            INSERT INTO telemetry(
                received_at_ms, timestamp_ms, vehicle_id, device_id,
                source, is_simulated, verification_status, payload_json
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                received_at_ms,
                row["timestamp_ms"],
                row["vehicle_id"],
                row["device_id"],
                row["source"],
                1 if row["is_simulated"] else 0,
                row["verification_status"],
                json.dumps(row["payload"], ensure_ascii=False, separators=(",", ":")),
            ),
        )
        conn.commit()
        return int(cur.lastrowid)


def latest_rows(limit: int) -> list[dict]:
    limit = max(1, min(limit, 500))
    with DB_LOCK, connect_db() as conn:
        rows = conn.execute(
            """
            SELECT id, received_at_ms, timestamp_ms, vehicle_id, device_id,
                   source, is_simulated, verification_status, payload_json
            FROM telemetry
            ORDER BY id DESC
            LIMIT ?
            """,
            (limit,),
        ).fetchall()

    out: list[dict] = []
    for row in rows:
        item = dict(row)
        item["is_simulated"] = bool(item["is_simulated"])
        item["payload"] = json.loads(item.pop("payload_json"))
        out.append(item)
    return out


def row_count() -> int:
    with DB_LOCK, connect_db() as conn:
        return int(conn.execute("SELECT COUNT(*) FROM telemetry").fetchone()[0])


class Handler(BaseHTTPRequestHandler):
    server_version = f"CSDPRM3/{APP_VERSION}"

    def log_message(self, fmt: str, *args) -> None:
        LOGGER.info("%s %s", self.client_address[0], fmt % args)

    def send_json(self, status: int, payload: dict | list) -> None:
        raw = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(raw)

    def is_authorized(self) -> bool:
        expected = f"Bearer {AUTH_TOKEN}"
        supplied = self.headers.get("Authorization", "")
        return supplied == expected

    def require_auth(self) -> bool:
        if self.is_authorized():
            return True
        self.send_json(HTTPStatus.UNAUTHORIZED, {"error": "unauthorized"})
        return False

    def read_json_body(self) -> dict:
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError as exc:
            raise ValueError("invalid Content-Length") from exc
        if length <= 0:
            raise ValueError("empty request body")
        if length > MAX_BODY_BYTES:
            raise OverflowError("request body too large")
        raw = self.rfile.read(length)
        try:
            payload = json.loads(raw.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise ValueError("invalid JSON") from exc
        if not isinstance(payload, dict):
            raise ValueError("JSON root must be an object")
        return payload

    def do_GET(self) -> None:
        parsed = urlparse(self.path)

        if parsed.path == "/health":
            self.send_json(
                HTTPStatus.OK,
                {
                    "status": "ok",
                    "service": "csdp-rm3-android-telemetry-server",
                    "version": APP_VERSION,
                    "uptimeMs": int(time.time() * 1000) - STARTED_AT_MS,
                    "rows": row_count(),
                },
            )
            return

        if parsed.path == "/api/v1/telemetry/latest":
            if not self.require_auth():
                return
            qs = parse_qs(parsed.query)
            try:
                limit = int(qs.get("limit", ["50"])[0])
            except ValueError:
                self.send_json(HTTPStatus.BAD_REQUEST, {"error": "invalid limit"})
                return
            self.send_json(HTTPStatus.OK, {"items": latest_rows(limit)})
            return

        if parsed.path == "/api/v1/export.ndjson":
            if not self.require_auth():
                return
            rows = latest_rows(500)
            raw = b"".join(
                (json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf-8")
                for row in reversed(rows)
            )
            self.send_response(HTTPStatus.OK)
            self.send_header("Content-Type", "application/x-ndjson; charset=utf-8")
            self.send_header("Content-Length", str(len(raw)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(raw)
            return

        self.send_json(HTTPStatus.NOT_FOUND, {"error": "not_found"})

    def do_POST(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path != "/api/v1/telemetry":
            self.send_json(HTTPStatus.NOT_FOUND, {"error": "not_found"})
            return
        if not self.require_auth():
            return

        try:
            payload = self.read_json_body()
            row_id = insert_telemetry(payload)
        except OverflowError as exc:
            self.send_json(HTTPStatus.REQUEST_ENTITY_TOO_LARGE, {"error": str(exc)})
            return
        except ValueError as exc:
            self.send_json(HTTPStatus.BAD_REQUEST, {"error": str(exc)})
            return
        except Exception:
            LOGGER.exception("telemetry insert failed")
            self.send_json(HTTPStatus.INTERNAL_SERVER_ERROR, {"error": "storage_failure"})
            return

        self.send_json(HTTPStatus.CREATED, {"accepted": True, "id": row_id})


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="CSDP-RM3 Android telemetry mirror server")
    parser.add_argument("--host", default=os.environ.get("CSDP_SERVER_HOST", DEFAULT_HOST))
    parser.add_argument("--port", type=int, default=int(os.environ.get("CSDP_SERVER_PORT", DEFAULT_PORT)))
    parser.add_argument("--db", default=os.environ.get("CSDP_SERVER_DB", DEFAULT_DB))
    parser.add_argument("--log", default=os.environ.get("CSDP_SERVER_LOG", DEFAULT_LOG))
    return parser.parse_args()


def main() -> None:
    global AUTH_TOKEN, DB_PATH
    args = parse_args()
    AUTH_TOKEN = require_token()
    DB_PATH = Path(args.db).expanduser().resolve()
    configure_logging(Path(args.log).expanduser().resolve())
    init_db()

    httpd = ThreadingHTTPServer((args.host, args.port), Handler)
    LOGGER.info("CSDP-RM3 telemetry server %s listening on %s:%s", APP_VERSION, args.host, args.port)
    LOGGER.info("database=%s", DB_PATH)
    try:
        httpd.serve_forever(poll_interval=0.5)
    except KeyboardInterrupt:
        pass
    finally:
        httpd.server_close()
        LOGGER.info("server stopped")


if __name__ == "__main__":
    main()

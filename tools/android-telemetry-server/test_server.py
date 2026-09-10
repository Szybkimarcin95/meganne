#!/usr/bin/env python3

import tempfile
import unittest
from pathlib import Path

import server


class TelemetryStorageTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        server.DB_PATH = Path(self.tmp.name) / "telemetry.db"
        server.init_db()

    def tearDown(self):
        self.tmp.cleanup()

    def test_insert_and_read_latest(self):
        row_id = server.insert_telemetry(
            {
                "timestampMs": 123456789,
                "vehicleId": "megane-x95-k9k636",
                "deviceId": "android-primary",
                "source": "SIMULATED",
                "isSimulated": True,
                "verificationStatus": "SIMULATION",
                "metrics": {"rpm": 830},
            }
        )
        self.assertGreater(row_id, 0)
        rows = server.latest_rows(10)
        self.assertEqual(1, len(rows))
        self.assertTrue(rows[0]["is_simulated"])
        self.assertEqual("SIMULATED", rows[0]["source"])
        self.assertEqual(830, rows[0]["payload"]["metrics"]["rpm"])

    def test_unsupported_payload_shape_is_rejected(self):
        with self.assertRaises(ValueError):
            server.insert_telemetry({"metrics": [1, 2, 3]})

    def test_limit_is_capped(self):
        for i in range(3):
            server.insert_telemetry({"metrics": {"rpm": i}})
        self.assertEqual(3, len(server.latest_rows(9999)))


if __name__ == "__main__":
    unittest.main()

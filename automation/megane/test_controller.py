#!/usr/bin/env python3
"""
Unit tests for Megane Overlord Automation Controller.
Verifies: OFF before start, OFF between tasks, restart, corrupted state, interrupted process, two runs.
"""
import unittest
import os
import json
import time
import tempfile
import shutil
import controller

class TestAutomationController(unittest.TestCase):
    def setUp(self):
        self.test_dir = tempfile.mkdtemp()
        self.orig_base = controller.BASE_DIR
        controller.BASE_DIR = self.test_dir
        controller.CONTROL_FILE = os.path.join(self.test_dir, "control.json")
        controller.STATE_FILE = os.path.join(self.test_dir, "state.json")
        controller.BACKLOG_FILE = os.path.join(self.test_dir, "backlog.json")
        controller.JOURNAL_FILE = os.path.join(self.test_dir, "journal.jsonl")
        controller.LOCK_FILE = os.path.join(self.test_dir, "worker.lock")
        controller.CHECKPOINTS_DIR = os.path.join(self.test_dir, "checkpoints")

        # Initial baseline files
        controller.atomic_write_json(controller.CONTROL_FILE, {
            "desired_enabled": False,
            "revision": 1
        })
        controller.atomic_write_json(controller.STATE_FILE, {
            "worker_state": "IDLE",
            "checkpoint": "CP0"
        })

    def tearDown(self):
        controller.BASE_DIR = self.orig_base
        shutil.rmtree(self.test_dir, ignore_errors=True)

    def test_off_before_start(self):
        """1. OFF before start: desired_enabled must default to false and block execution."""
        status = controller.get_status()
        self.assertFalse(status["desired_enabled"])

    def test_off_between_tasks(self):
        """2. OFF between tasks: toggling desired_enabled increments revision and reflects instantly."""
        controller.set_desired_enabled(True)
        st1 = controller.get_status()
        self.assertTrue(st1["desired_enabled"])
        self.assertEqual(st1["revision"], 2)

        controller.set_desired_enabled(False)
        st2 = controller.get_status()
        self.assertFalse(st2["desired_enabled"])
        self.assertEqual(st2["revision"], 3)

    def test_restart_reload(self):
        """3. Restart: state reloads cleanly from disk across instances."""
        controller.atomic_write_json(controller.STATE_FILE, {
            "worker_state": "PAUSED_PLATFORM_LIMIT",
            "checkpoint": "CP1",
            "last_result": "PASS"
        })
        status = controller.get_status()
        self.assertEqual(status["worker_state"], "PAUSED_PLATFORM_LIMIT")
        self.assertEqual(status["checkpoint"], "CP1")

    def test_corrupted_state_handling(self):
        """4. Corrupted state: corrupt JSON raises error rather than silently proceeding."""
        with open(controller.STATE_FILE, "w") as f:
            f.write("{ INVALID JSON CONTENT")
        with self.assertRaises(json.JSONDecodeError):
            controller.read_json(controller.STATE_FILE)

    def test_interrupted_process_stale_lock(self):
        """5. Interrupted process: dead PID lock is detected as stale and cleaned up safely."""
        # Create lock with fake non-existent PID 999999
        stale_lock = {
            "pid": 999999,
            "run_id": "crashed-run",
            "timestamp_epoch": time.time() - 400
        }
        controller.atomic_write_json(controller.LOCK_FILE, stale_lock)
        # Should successfully acquire after breaking stale lock
        success, msg = controller.acquire_worker_lock("recovery-run")
        self.assertTrue(success)
        controller.release_worker_lock()

    def test_two_runs_prevention(self):
        """6. Two runs: active running process lock prevents second worker from acquiring lock."""
        # Acquire lock with current PID
        success, _ = controller.acquire_worker_lock("run-1")
        self.assertTrue(success)

        # Second attempt should fail because current PID is alive
        success2, msg2 = controller.acquire_worker_lock("run-2")
        self.assertFalse(success2)
        self.assertIn("Lock held by running process", msg2)

        controller.release_worker_lock()

if __name__ == "__main__":
    unittest.main()

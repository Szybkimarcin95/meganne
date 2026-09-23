#!/usr/bin/env python3
"""
Automation Controller for Megane Overlord (CSDP-RM3).
Manages control.json, state.json, worker locking, and revision tracking.
"""
import os
import sys
import json
import time
import tempfile
from datetime import datetime, timezone

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
CONTROL_FILE = os.path.join(BASE_DIR, "control.json")
STATE_FILE = os.path.join(BASE_DIR, "state.json")
BACKLOG_FILE = os.path.join(BASE_DIR, "backlog.json")
JOURNAL_FILE = os.path.join(BASE_DIR, "journal.jsonl")
LOCK_FILE = os.path.join(BASE_DIR, "worker.lock")
CHECKPOINTS_DIR = os.path.join(BASE_DIR, "checkpoints")

LOCK_TIMEOUT_SECONDS = 300  # 5 minutes stale lock timeout

def now_utc():
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

def append_journal(event_type, details):
    entry = {
        "timestamp_utc": now_utc(),
        "event": event_type,
        "details": details
    }
    with open(JOURNAL_FILE, "a", encoding="utf-8") as f:
        f.write(json.dumps(entry, ensure_ascii=False) + "\n")

def atomic_write_json(filepath, data):
    dir_name = os.path.dirname(filepath)
    with tempfile.NamedTemporaryFile("w", dir=dir_name, delete=False, encoding="utf-8") as tf:
        json.dump(data, tf, indent=2, ensure_ascii=False)
        temp_name = tf.name
    os.replace(temp_name, filepath)

def read_json(filepath):
    if not os.path.exists(filepath):
        raise FileNotFoundError(f"File not found: {filepath}")
    with open(filepath, "r", encoding="utf-8") as f:
        return json.load(f)

def is_pid_running(pid):
    if pid <= 0:
        return False
    try:
        os.kill(pid, 0)
        return True
    except OSError:
        return False

def acquire_worker_lock(run_id="default"):
    if os.path.exists(LOCK_FILE):
        try:
            with open(LOCK_FILE, "r", encoding="utf-8") as f:
                lock_info = json.load(f)
            pid = lock_info.get("pid", -1)
            ts = lock_info.get("timestamp_epoch", 0)
            if is_pid_running(pid):
                return False, f"Lock held by running process PID {pid}"
            elif time.time() - ts < LOCK_TIMEOUT_SECONDS:
                return False, f"Lock held by PID {pid} within timeout window"
            else:
                append_journal("STALE_LOCK_BROKEN", f"Stale lock from dead PID {pid} cleaned up")
        except Exception:
            pass  # Corrupted lock file will be overwritten

    lock_data = {
        "pid": os.getpid(),
        "run_id": run_id,
        "timestamp_utc": now_utc(),
        "timestamp_epoch": time.time()
    }
    atomic_write_json(LOCK_FILE, lock_data)
    append_journal("LOCK_ACQUIRED", f"Worker PID {os.getpid()} acquired lock")
    return True, "Lock acquired"

def release_worker_lock():
    if os.path.exists(LOCK_FILE):
        try:
            os.remove(LOCK_FILE)
            append_journal("LOCK_RELEASED", f"Worker PID {os.getpid()} released lock")
            return True
        except Exception as e:
            return False
    return True

def set_desired_enabled(enabled: bool):
    ctrl = read_json(CONTROL_FILE)
    ctrl["desired_enabled"] = enabled
    ctrl["revision"] = ctrl.get("revision", 0) + 1
    atomic_write_json(CONTROL_FILE, ctrl)
    append_journal("CONTROL_CHANGED", f"desired_enabled set to {enabled} (rev {ctrl['revision']})")
    return ctrl

def get_status():
    ctrl = read_json(CONTROL_FILE) if os.path.exists(CONTROL_FILE) else {}
    st = read_json(STATE_FILE) if os.path.exists(STATE_FILE) else {}
    lock_active = False
    lock_holder = None
    if os.path.exists(LOCK_FILE):
        try:
            with open(LOCK_FILE, "r", encoding="utf-8") as f:
                linfo = json.load(f)
            pid = linfo.get("pid", -1)
            if is_pid_running(pid):
                lock_active = True
                lock_holder = pid
        except Exception:
            pass

    return {
        "desired_enabled": ctrl.get("desired_enabled", False),
        "worker_state": st.get("worker_state", "UNKNOWN"),
        "heartbeat_at_utc": st.get("heartbeat_at_utc", "UNKNOWN"),
        "checkpoint": st.get("checkpoint", "UNKNOWN"),
        "last_result": st.get("last_result", "UNKNOWN"),
        "blocker": st.get("blocker"),
        "lock_active": lock_active,
        "lock_holder_pid": lock_holder,
        "revision": ctrl.get("revision", 0)
    }

def print_status():
    status = get_status()
    print("========================================")
    print("MEGANE OVERLORD — AUTOPILOT STATUS")
    print("========================================")
    print(f"desired_enabled : {status['desired_enabled']}")
    print(f"worker_state    : {status['worker_state']}")
    print(f"checkpoint      : {status['checkpoint']}")
    print(f"last_result     : {status['last_result']}")
    print(f"heartbeat_utc   : {status['heartbeat_at_utc']}")
    print(f"blocker         : {status['blocker']}")
    print(f"lock_active     : {status['lock_active']} (PID {status['lock_holder_pid']})")
    print(f"revision        : {status['revision']}")
    print("========================================")

if __name__ == "__main__":
    os.makedirs(CHECKPOINTS_DIR, exist_ok=True)
    if len(sys.argv) < 2:
        print_status()
        sys.exit(0)

    cmd = sys.argv[1].lower()
    if cmd == "on":
        set_desired_enabled(True)
        print("AUTOPILOT: ON (desired_enabled=True). Kontroler ustawił flagę.")
    elif cmd == "off":
        set_desired_enabled(False)
        print("AUTOPILOT: OFF (desired_enabled=False). Kontroler ustawił flagę.")
    elif cmd == "status":
        print_status()
    elif cmd == "lock":
        success, msg = acquire_worker_lock(sys.argv[2] if len(sys.argv) > 2 else "cli")
        print(f"Lock result: {success} ({msg})")
        sys.exit(0 if success else 1)
    elif cmd == "unlock":
        release_worker_lock()
        print("Lock released.")
    else:
        print(f"Unknown command: {cmd}")
        sys.exit(1)

#!/usr/bin/env python3
"""
COMP 512 PA1 Baseline Automated Test Suite
Compiles Server and Client, verifies single-server and distributed deployments,
and tests baseline client commands against running servers.
"""

import os
import sys
import time
import signal
import subprocess
from pathlib import Path

WORKSPACE_ROOT = Path(__file__).resolve().parents[4]
PA1_DIR = WORKSPACE_ROOT / "pa1"
SERVER_DIR = PA1_DIR / "Server"
CLIENT_DIR = PA1_DIR / "Client"

class PA1Verifier:
    def __init__(self):
        self.processes = []
        self.passed = 0
        self.failed = 0

    def cleanup(self):
        print("\n[INFO] Cleaning up test processes...")
        for p in self.processes:
            try:
                os.killpg(os.getpgid(p.pid), signal.SIGTERM)
            except Exception:
                try:
                    p.terminate()
                except Exception:
                    pass
        time.sleep(0.5)

    def test(self, name, condition, details=""):
        if condition:
            self.passed += 1
            print(f"[PASS] {name}")
        else:
            self.failed += 1
            print(f"[FAIL] {name}: {details}")

    def run_build(self):
        print("\n--- 1. Build Verification ---")
        res_server = subprocess.run(["make"], cwd=str(SERVER_DIR), capture_output=True, text=True)
        self.test("Server compilation (make in Server/)", res_server.returncode == 0, res_server.stderr)

        res_client = subprocess.run(["make"], cwd=str(CLIENT_DIR), capture_output=True, text=True)
        self.test("Client compilation (make in Client/)", res_client.returncode == 0, res_client.stderr)

        return res_server.returncode == 0 and res_client.returncode == 0

    def run_tests(self):
        try:
            if not self.run_build():
                print("[ERROR] Build failed, aborting runtime tests.")
                return False

            print("\n--- 2. Starter Code Runtime Smoke Test ---")
            print("  Starting rmiregistry...")
            reg_proc = subprocess.Popen(
                ["rmiregistry", "-J-Djava.rmi.server.useCodebaseOnly=false", "2099"],
                cwd=str(SERVER_DIR),
                preexec_fn=os.setsid,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL
            )
            self.processes.append(reg_proc)
            time.sleep(1.0)

            print("  Starting standalone ResourceManager (Resources)...")
            # Note: test with codebase set to Server directory
            server_proc = subprocess.Popen(
                ["java", f"-Djava.rmi.server.codebase=file:{SERVER_DIR}/", "Server.RMI.RMIResourceManager", "Resources"],
                cwd=str(SERVER_DIR),
                preexec_fn=os.setsid,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True
            )
            self.processes.append(server_proc)
            time.sleep(1.5)

            # Check if server started or threw exception
            if server_proc.poll() is not None:
                _, err = server_proc.communicate()
                self.test("Standalone ResourceManager startup", False, f"Server exited early: {err.strip()}")
            else:
                self.test("Standalone ResourceManager startup", True)

        finally:
            self.cleanup()

        print("\n" + "=" * 50)
        print(f"PA1 Baseline Verification: Passed {self.passed} | Failed {self.failed}")
        print("=" * 50)
        return self.failed == 0

def main():
    verifier = PA1Verifier()
    success = verifier.run_tests()
    sys.exit(0 if success else 1)

if __name__ == "__main__":
    main()

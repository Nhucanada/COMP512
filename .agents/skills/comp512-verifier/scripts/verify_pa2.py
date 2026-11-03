#!/usr/bin/env python3
"""
COMP 512 PA2: Automated Specification & Adherence Verifier
Verifies that PA2 (Total Order Using Paxos / Treasure Island):
1. Workspace structure & jar files are present (pa2, p2-students, comp512p2.jar)
2. Compiles cleanly using comp512p2.jar
3. Strict Package Visibility Invariant:
   - ONLY Paxos.java and its specified methods are public.
   - Any helper classes in comp512st.paxos are package-private.
4. GCL Communication Invariant:
   - All IPC goes through GCL. No RMI or raw Sockets.
5. FailCheck Instrumentation:
   - Verifies hooks for all 5 required FailureTypes:
     RECEIVEPROPOSE, AFTERSENDVOTE, AFTERSENDPROPOSE, AFTERBECOMINGLEADER, AFTERVALUEACCEPT
6. Required scripts and runner configurations exist.
"""

import os
import re
import sys
import subprocess
from pathlib import Path

WORKSPACE_ROOT = Path(__file__).resolve().parents[4]
PA2_DIR = WORKSPACE_ROOT / "pa2"
if (PA2_DIR / "comp512p2").exists():
    PA2_DIR = PA2_DIR / "comp512p2"
P2_STUDENTS_DIR = WORKSPACE_ROOT / "p2-students"
JAR_PATH = PA2_DIR / "comp512p2.jar"
PAXOS_DIR = PA2_DIR / "comp512st" / "paxos"
PAXOS_JAVA = PAXOS_DIR / "Paxos.java"

REQUIRED_FAILURE_TYPES = [
    "RECEIVEPROPOSE",
    "AFTERSENDVOTE",
    "AFTERSENDPROPOSE",
    "AFTERBECOMINGLEADER",
    "AFTERVALUEACCEPT"
]

ALLOWED_PUBLIC_METHODS = {
    "Paxos",
    "broadcastTOMsg",
    "acceptTOMsg",
    "shutdownPaxos"
}

PROHIBITED_IMPORTS = [
    "java.rmi",
    "java.net.Socket",
    "java.net.ServerSocket",
    "java.nio.channels.SocketChannel",
    "java.nio.channels.ServerSocketChannel"
]

class PA2Verifier:
    def __init__(self):
        self.passed = 0
        self.failed = 0
        self.warnings = 0
        self.results = []

    def log(self, test_name, success, details=""):
        if success:
            self.passed += 1
            print(f"[PASS] {test_name}")
            self.results.append((test_name, "PASS", ""))
        else:
            self.failed += 1
            print(f"[FAIL] {test_name}: {details}")
            self.results.append((test_name, "FAIL", details))

    def warn(self, test_name, details=""):
        self.warnings += 1
        print(f"[WARN] {test_name}: {details}")
        self.results.append((test_name, "WARN", details))

    def check_workspace_layout(self):
        print("\n--- 1. Workspace Layout & Artifacts ---")
        self.log("pa2 directory exists", PA2_DIR.exists(), f"{PA2_DIR} missing")
        self.log("p2-students directory exists", P2_STUDENTS_DIR.exists(), f"{P2_STUDENTS_DIR} missing")
        self.log("comp512p2.jar exists in pa2", JAR_PATH.exists(), f"{JAR_PATH} missing")
        self.log("Paxos.java exists", PAXOS_JAVA.exists(), f"{PAXOS_JAVA} missing")

        build_script = PA2_DIR / "build_tiapp.sh"
        self.log("build_tiapp.sh exists and is executable", build_script.exists() and os.access(build_script, os.X_OK),
                 f"{build_script} missing or not executable")

        app_java = PA2_DIR / "comp512st" / "tiapp" / "TreasureIslandApp.java"
        self.log("TreasureIslandApp.java exists", app_java.exists(), f"{app_java} missing")

        auto_java = PA2_DIR / "comp512st" / "tests" / "TreasureIslandAppAuto.java"
        self.log("TreasureIslandAppAuto.java exists", auto_java.exists(), f"{auto_java} missing")

    def check_compilation(self):
        print("\n--- 2. Compilation Audit ---")
        if not JAR_PATH.exists() or not PAXOS_JAVA.exists():
            self.log("Java Compilation", False, "Prerequisite files missing")
            return

        cmd = [
            "javac",
            "-cp", f"{JAR_PATH}:{PA2_DIR}",
            str(PAXOS_JAVA),
            str(PA2_DIR / "comp512st" / "tiapp" / "TreasureIslandApp.java"),
            str(PA2_DIR / "comp512st" / "tests" / "TreasureIslandAppAuto.java")
        ]
        # Include any additional helper java files in paxos
        for f in PAXOS_DIR.glob("*.java"):
            if str(f) not in cmd:
                cmd.append(str(f))

        res = subprocess.run(cmd, cwd=str(PA2_DIR), capture_output=True, text=True)
        self.log("Compile pa2 classes with javac", res.returncode == 0, res.stderr.strip() if res.stderr else "Compilation failed")

    def check_visibility_rules(self):
        print("\n--- 3. Package Visibility Invariant Audit ---")
        if not PAXOS_DIR.exists():
            return

        # Check all .java files in comp512st.paxos
        java_files = list(PAXOS_DIR.glob("*.java"))
        for jf in java_files:
            content = jf.read_text(errors="ignore")
            # Strip comments
            stripped = re.sub(r'//.*', '', content)
            stripped = re.sub(r'/\*.*?\*/', '', stripped, flags=re.DOTALL)

            if jf.name == "Paxos.java":
                # Check class is public
                has_public_class = bool(re.search(r'public\s+class\s+Paxos\b', stripped))
                self.log("Paxos.java declares 'public class Paxos'", has_public_class, "Paxos must be a public class")

                # Check methods: ONLY allowed methods can be public
                public_methods = re.findall(r'public\s+[\w<>\[\]]+\s+([a-zA-Z0-9_]+)\s*\(', stripped)
                for pm in public_methods:
                    if pm not in ALLOWED_PUBLIC_METHODS:
                        self.log(f"Paxos.java public method check: {pm}", False,
                                 f"Method '{pm}' cannot be public. Only {ALLOWED_PUBLIC_METHODS} allowed.")
                    else:
                        self.log(f"Paxos.java allowed public method: {pm}", True)
            else:
                # Any other class MUST NOT be public
                public_decl = re.findall(r'public\s+(?:class|interface|enum)\s+([a-zA-Z0-9_]+)', stripped)
                if public_decl:
                    self.log(f"Visibility rule for {jf.name}", False,
                             f"File declares public types: {public_decl}. Helper types in comp512st.paxos must be package-private!")
                else:
                    self.log(f"Visibility rule for {jf.name} (package-private)", True)

    def check_gcl_ipc_invariant(self):
        print("\n--- 4. GCL IPC Invariant Audit ---")
        if not PAXOS_DIR.exists():
            return

        for jf in PAXOS_DIR.glob("*.java"):
            content = jf.read_text(errors="ignore")
            for banned in PROHIBITED_IMPORTS:
                if banned in content:
                    self.log(f"No banned IPC ({banned}) in {jf.name}", False,
                             f"Found banned IPC reference '{banned}' in {jf.name}. ALL IPC must use GCL!")
                else:
                    self.log(f"No banned IPC ({banned}) in {jf.name}", True)

    def check_failure_points(self):
        print("\n--- 5. FailCheck Failure Points Audit ---")
        if not PAXOS_DIR.exists():
            return

        combined_code = ""
        for jf in PAXOS_DIR.glob("*.java"):
            combined_code += jf.read_text(errors="ignore") + "\n"

        for ft in REQUIRED_FAILURE_TYPES:
            has_ft = f"FailCheck.FailureType.{ft}" in combined_code or f"FailureType.{ft}" in combined_code or ft in combined_code
            if has_ft:
                self.log(f"FailCheck point: {ft} instrumented", True)
            else:
                self.warn(f"FailCheck point: {ft} instrumented",
                          f"Reference to {ft} not found in comp512st.paxos. Must call failCheck.checkFailure(FailCheck.FailureType.{ft})")

    def print_summary(self):
        print("\n" + "=" * 65)
        print("COMP 512 PA2 (PAXOS & TREASURE ISLAND) VERIFICATION SUMMARY")
        print("=" * 65)
        for name, status, msg in self.results:
            detail = f" -> {msg}" if msg else ""
            print(f"[{status}] {name}{detail}")
        print("=" * 65)
        print(f"Total Checks: {self.passed + self.failed} | Passed: {self.passed} | Failed: {self.failed} | Warnings: {self.warnings}")
        print("=" * 65)
        return self.failed == 0

if __name__ == "__main__":
    verifier = PA2Verifier()
    verifier.check_workspace_layout()
    verifier.check_compilation()
    verifier.check_visibility_rules()
    verifier.check_gcl_ipc_invariant()
    verifier.check_failure_points()
    success = verifier.print_summary()
    sys.exit(0 if success else 1)

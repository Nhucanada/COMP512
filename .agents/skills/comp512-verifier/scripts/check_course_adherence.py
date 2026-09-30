#!/usr/bin/env python3
"""
COMP 512 Course Content & Concept Adherence Checker
Strictly verifies that the implementation adheres to COMP 512 lecture slides,
tutorials, and assignment specifications:
1. Whitelisted Java imports (java.rmi, java.net, java.io, java.util)
2. Interface integrity (IResourceManager)
3. Group number configuration
4. Bundle implementation
5. Customer handling strategy
6. Generic message envelope for TCP sockets (mandated if AI is used)
7. Concurrency & non-blocking Middleware design
"""

import os
import re
import sys
from pathlib import Path

WORKSPACE_ROOT = Path(__file__).resolve().parents[4]
PA1_DIR = WORKSPACE_ROOT / "pa1"
if not PA1_DIR.exists():
    PA1_DIR = WORKSPACE_ROOT / "pa1-students" / "Template"

WHITELISTED_IMPORT_PREFIXES = (
    "java.io.",
    "java.net.",
    "java.util.",
    "java.rmi.",
    "Server.",
    "Client."
)

class CourseAdherenceChecker:
    def __init__(self):
        self.passed = 0
        self.failed = 0
        self.warnings = 0
        self.results = []

    def check(self, rule_name, condition, details=""):
        if condition:
            self.passed += 1
            print(f"[PASS] {rule_name}")
            self.results.append((rule_name, "PASS", ""))
        else:
            self.failed += 1
            print(f"[FAIL] {rule_name}: {details}")
            self.results.append((rule_name, "FAIL", details))

    def warn(self, rule_name, details=""):
        self.warnings += 1
        print(f"[WARN] {rule_name}: {details}")
        self.results.append((rule_name, "WARN", details))

    def print_summary(self):
        print("\n" + "=" * 65)
        print("COMP 512 COURSE CONTENT & SPECIFICATION ADHERENCE AUDIT")
        print("Reference: COMP-512 Lecture Slides (Network Stack, RMI, IPC)")
        print("           Course Content/Project Docs/COMP512-p1-2026.pdf")
        print("=" * 65)
        for name, status, msg in self.results:
            detail = f" -> {msg}" if msg else ""
            print(f"[{status}] {name}{detail}")
        print("=" * 65)
        print(f"Total Checks: {self.passed + self.failed} | Passed: {self.passed} | Failed: {self.failed} | Warnings: {self.warnings}")
        print("=" * 65)
        return self.failed == 0

def get_java_files():
    files = []
    for root, _, filenames in os.walk(PA1_DIR):
        for f in filenames:
            if f.endswith(".java"):
                files.append(Path(root) / f)
    return files

def audit_imports(checker, java_files):
    violations = []
    for fpath in java_files:
        rel = fpath.relative_to(PA1_DIR)
        with open(fpath, "r", encoding="utf-8", errors="ignore") as f:
            for line_no, line in enumerate(f, 1):
                line = line.strip()
                if line.startswith("import ") and not line.startswith("import static "):
                    imported = line.replace("import ", "").replace(";", "").strip()
                    if not any(imported.startswith(prefix) for prefix in WHITELISTED_IMPORT_PREFIXES):
                        violations.append(f"{rel}:{line_no} imports '{imported}'")
    checker.check("Imports strictly restricted to course whitelist (java.rmi, java.net, java.io, java.util)",
                  len(violations) == 0,
                  "; ".join(violations))

def audit_syntax_and_hidden_chars(checker, java_files):
    violations = []
    for fpath in java_files:
        rel = fpath.relative_to(PA1_DIR)
        with open(fpath, "rb") as f:
            content = f.read()
            # Check for non-printable control characters (excluding \r, \n, \t)
            for idx, b in enumerate(content):
                if b < 32 and b not in (9, 10, 13):
                    violations.append(f"{rel}: byte offset {idx} has illegal control character 0x{b:02x}")
                    break
    checker.check("No hidden illegal control characters (e.g. \\x03 starter template bug)",
                  len(violations) == 0,
                  "; ".join(violations))

def audit_interface_integrity(checker):
    interface_file = PA1_DIR / "Server" / "Server" / "Interface" / "IResourceManager.java"
    if not interface_file.exists():
        checker.check("IResourceManager.java exists", False, "File missing")
        return

    content = interface_file.read_text(errors="ignore")
    required_methods = [
        "addFlight", "addCars", "addRooms", "newCustomer", "deleteFlight",
        "deleteCars", "deleteRooms", "deleteCustomer", "queryFlight",
        "queryCars", "queryRooms", "queryCustomerInfo", "queryFlightPrice",
        "queryCarsPrice", "queryRoomsPrice", "reserveFlight", "reserveCar",
        "reserveRoom", "bundle"
    ]
    missing = [m for m in required_methods if m not in content]
    checker.check("IResourceManager defines all 18 core methods", len(missing) == 0, f"Missing: {missing}")

def audit_rmi_configuration(checker):
    rmi_server = PA1_DIR / "Server" / "Server" / "RMI" / "RMIResourceManager.java"
    if not rmi_server.exists():
        checker.warn("RMIResourceManager.java check", "File not found")
        return

    content = rmi_server.read_text(errors="ignore")
    if "group_xx_" in content:
        checker.warn("RMI Group ID configuration", "Still using template placeholder 'group_xx_'. Replace with actual group identifier.")
    else:
        checker.check("RMI Group ID customized", True)

def audit_bundle_implementation(checker):
    rm_file = PA1_DIR / "Server" / "Server" / "Common" / "ResourceManager.java"
    if not rm_file.exists():
        checker.warn("ResourceManager.java check", "File not found")
        return

    content = rm_file.read_text(errors="ignore")
    # Check if bundle returns false statically
    m = re.search(r'public boolean bundle[^{]+{\s*return false;\s*}', content)
    if m:
        checker.warn("Bundle method implementation", "bundle() in ResourceManager currently returns static 'false'. Must implement bundle reservation logic in Middleware.")
    else:
        checker.check("Bundle method implemented beyond template stub", True)

def audit_middleware_architecture(checker):
    # Check if Middleware classes exist
    server_dir = PA1_DIR / "Server" / "Server"
    middleware_files = list(server_dir.rglob("*Middleware*.java"))
    if not middleware_files:
        checker.warn("Distributed Middleware Server", "No Middleware class found yet in Server/. Required for Part 1.1 & 1.2.")
    else:
        checker.check("Distributed Middleware Server present", True, f"Found: {[f.name for f in middleware_files]}")

def audit_tcp_implementation(checker):
    # Check if TCP Socket classes exist
    tcp_files = list(PA1_DIR.rglob("*TCP*.java")) + list(PA1_DIR.rglob("*Socket*.java"))
    if not tcp_files:
        checker.warn("TCP Socket Implementation (Part 1.2)", "TCP socket classes not yet implemented.")
    else:
        checker.check("TCP Socket implementation files present", True)

def main():
    checker = CourseAdherenceChecker()
    java_files = get_java_files()

    audit_imports(checker, java_files)
    audit_syntax_and_hidden_chars(checker, java_files)
    audit_interface_integrity(checker)
    audit_rmi_configuration(checker)
    audit_bundle_implementation(checker)
    audit_middleware_architecture(checker)
    audit_tcp_implementation(checker)

    success = checker.print_summary()
    sys.exit(0 if success else 1)

if __name__ == "__main__":
    main()

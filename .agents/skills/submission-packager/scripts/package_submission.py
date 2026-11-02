#!/usr/bin/env python3
"""
Universal Assignment Submission Packager & Verifier for COMP 512
Dynamically packages clean deliverables for MyCourses.
Strictly excludes AI guidelines, course materials, IDE configurations, OS junk, and build artifacts.
Extracts archive into an isolated sandbox to verify clean 'make' build.
"""

import os
import sys
import re
import zipfile
import shutil
import subprocess
import argparse
import tempfile
from pathlib import Path

FORBIDDEN_DIR_NAMES = {
    ".git", ".agents", ".gemini", ".claude", "target", "out", "build", "bin", "dist",
    ".idea", ".vscode", ".settings", ".gradle", "__macosx", "__pycache__", "ai-logging-examples"
}

FORBIDDEN_EXTENSIONS = {
    ".iml", ".ipr", ".iws", ".pdf", ".class", ".jar", ".war", ".zip", ".tar", ".gz",
    ".pyc", ".o", ".obj", ".docx", ".doc", ".pptx"
}

FORBIDDEN_NAME_KEYWORDS = [
    "gemini", "claude", "agent", "copilot", "chatgpt"
]

def find_project_root():
    current = Path.cwd().resolve()
    for parent in [current] + list(current.parents):
        if (parent / "pa1").exists() or (parent / "Course Content").exists() or (parent / ".git").exists():
            return parent
    return current

def is_forbidden(rel_path_str):
    normalized = rel_path_str.replace("\\", "/").lower()
    parts = normalized.split("/")

    for part in parts[:-1]:
        if part in FORBIDDEN_DIR_NAMES or (part.startswith(".") and part != "."):
            return True

    filename = parts[-1]
    if filename.startswith(".") or filename.startswith("._") or filename == ".ds_store":
        return True

    _, ext = os.path.splitext(filename)
    if ext in FORBIDDEN_EXTENSIONS:
        return True

    for kw in FORBIDDEN_NAME_KEYWORDS:
        if kw in filename:
            return True

    return False

def package_submission(output_zip=None, assignment="PA1"):
    assignment_upper = assignment.upper()
    root_dir = find_project_root()
    pa_dir = root_dir / assignment.lower()
    if not pa_dir.exists():
        if assignment_upper == "PA1":
            pa_dir = root_dir / "pa1-students" / "Template"
        elif assignment_upper == "PA2":
            pa_dir = root_dir / "p2-students" / "comp512p2"
        if not pa_dir.exists():
            pa_dir = root_dir

    if not output_zip:
        output_zip = root_dir / f"COMP512_{assignment_upper}_Submission.zip"
    else:
        output_zip = Path(output_zip).resolve()

    print(f"[INFO] Packaging COMP 512 {assignment_upper} Submission...")
    print(f"       Source Directory: {pa_dir}")
    print(f"       Target Archive:   {output_zip}")

    included_files = []

    if assignment_upper == "PA2":
        # Package PA2 artifacts: comp512p2.jar, build_tiapp.sh, comp512st, reports, docs
        for root, _, files in os.walk(pa_dir):
            for f in files:
                file_path = Path(root) / f
                rel_to_pa = file_path.relative_to(pa_dir)
                rel_str = str(rel_to_pa)
                if not is_forbidden(rel_str):
                    included_files.append((file_path, rel_str))
    else:
        # PA1: Package Server/ and Client/ and README.md
        for base_folder in ["Server", "Client"]:
            folder_path = pa_dir / base_folder
            if folder_path.exists():
                for root, _, files in os.walk(folder_path):
                    for f in files:
                        file_path = Path(root) / f
                        rel_to_pa = file_path.relative_to(pa_dir)
                        if not is_forbidden(str(rel_to_pa)):
                            included_files.append((file_path, str(rel_to_pa)))

        # Check for README files
        for rname in ["README.md", "README_SUBMISSION.md", "README.txt"]:
            rpath = pa_dir / rname
            if rpath.exists() and not is_forbidden(rname):
                included_files.append((rpath, "README.md"))
                break
            elif (root_dir / rname).exists() and not is_forbidden(rname):
                included_files.append((root_dir / rname, "README.md"))
                break

    if not included_files:
        print("[ERROR] No valid source files found to package!")
        sys.exit(1)

    # Write zip file
    if output_zip.exists():
        output_zip.unlink()

    with zipfile.ZipFile(output_zip, "w", zipfile.ZIP_DEFLATED) as zf:
        for abs_p, arcname in included_files:
            zinfo = zipfile.ZipInfo.from_file(abs_p, arcname)
            if arcname.endswith(".sh"):
                zinfo.external_attr = 0o100755 << 16
            else:
                zinfo.external_attr = 0o100644 << 16
            with open(abs_p, "rb") as f:
                zf.writestr(zinfo, f.read(), compress_type=zipfile.ZIP_DEFLATED)
            print(f"  + Added: {arcname}")

    print(f"\n[SUCCESS] Created archive with {len(included_files)} files: {output_zip}")
    print(f"          Archive size: {output_zip.stat().st_size:,} bytes")

    # Run sandbox build test
    verify_sandbox(output_zip, assignment_upper)

def verify_sandbox(zip_path, assignment="PA1"):
    print("\n[INFO] Running isolated sandbox compilation audit...")
    with tempfile.TemporaryDirectory() as tmpdir:
        tmp_path = Path(tmpdir)
        with zipfile.ZipFile(zip_path, "r") as zf:
            zf.extractall(tmp_path)

        if assignment == "PA2":
            jar_file = tmp_path / "comp512p2.jar"
            comp_dir = tmp_path / "comp512st"
            if jar_file.exists() and comp_dir.exists():
                print("  Building PA2 Java sources via javac...")
                java_sources = [str(p) for p in comp_dir.rglob("*.java")]
                cmd = ["javac", "-cp", f"{jar_file}:{tmp_path}"] + java_sources
                res_pa2 = subprocess.run(cmd, cwd=str(tmp_path), capture_output=True, text=True)
                if res_pa2.returncode != 0:
                    print(f"[FAIL] Sandbox PA2 compilation failed:\n{res_pa2.stderr}")
                    return False
                print("  [PASS] PA2 compiled cleanly.")
        else:
            # Try building Server and Client
            server_dir = tmp_path / "Server"
            client_dir = tmp_path / "Client"

            if server_dir.exists() and (server_dir / "Makefile").exists():
                print("  Building Server via make...")
                res_server = subprocess.run(["make"], cwd=str(server_dir), capture_output=True, text=True)
                if res_server.returncode != 0:
                    print(f"[FAIL] Sandbox Server build failed:\n{res_server.stderr}")
                    return False
                print("  [PASS] Server compiled cleanly.")

            if client_dir.exists() and (client_dir / "Makefile").exists():
                print("  Building Client via make...")
                res_client = subprocess.run(["make"], cwd=str(client_dir), capture_output=True, text=True)
                if res_client.returncode != 0:
                    print(f"[FAIL] Sandbox Client build failed:\n{res_client.stderr}")
                    return False
                print("  [PASS] Client compiled cleanly.")

    print("[PASS] Sandbox compilation audit passed completely!\n")
    return True

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="COMP 512 Clean Submission Packager")
    parser.add_argument("--assignment", default="PA1", help="Assignment milestone (default: PA1)")
    parser.add_argument("--output", help="Custom output zip path")
    args = parser.parse_args()

    package_submission(args.output, args.assignment)

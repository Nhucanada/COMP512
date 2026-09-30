#!/usr/bin/env python3
"""
COMP 512 Course Content Monitor & Change Detector
Recursively scans 'Course Content/' for any added, modified, or deleted files.
Maintains a state manifest in .agents/skills/course-content-monitor/manifest.json.
When changes are detected:
- Flags new/modified PDFs (slides, tutorials, project specs)
- Extracts text/metadata from new documents
- Flags needed updates to gemini.md, verification skills, and test scripts
"""

import os
import sys
import json
import hashlib
import argparse

WORKSPACE_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../../.."))
COURSE_CONTENT_DIR = os.path.join(WORKSPACE_ROOT, "Course Content")
MANIFEST_PATH = os.path.join(os.path.dirname(__file__), "..", "manifest.json")

def compute_hash(filepath):
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(8192):
            h.update(chunk)
    return h.hexdigest()

def scan_course_content():
    current_state = {}
    if not os.path.exists(COURSE_CONTENT_DIR):
        return current_state

    for root, _, files in os.walk(COURSE_CONTENT_DIR):
        for f in files:
            if f == ".DS_Store" or f.startswith("._"):
                continue
            full_path = os.path.join(root, f)
            rel_path = os.path.relpath(full_path, WORKSPACE_ROOT)
            file_stat = os.stat(full_path)
            current_state[rel_path] = {
                "sha256": compute_hash(full_path),
                "size": file_stat.st_size,
                "mtime": file_stat.st_mtime
            }
    return current_state

def load_manifest():
    if os.path.exists(MANIFEST_PATH):
        try:
            with open(MANIFEST_PATH, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception as e:
            print(f"[WARN] Error reading manifest: {e}")
    return None

def save_manifest(state):
    os.makedirs(os.path.dirname(MANIFEST_PATH), exist_ok=True)
    with open(MANIFEST_PATH, "w", encoding="utf-8") as f:
        json.dump(state, f, indent=2)
    print(f"[INFO] Manifest saved to {os.path.relpath(MANIFEST_PATH, WORKSPACE_ROOT)}")

def extract_pdf_preview(filepath):
    try:
        import pypdf
        reader = pypdf.PdfReader(filepath)
        num_pages = len(reader.pages)
        first_page_text = reader.pages[0].extract_text()[:300].strip() if num_pages > 0 else ""
        return f"Pages: {num_pages} | Preview: {first_page_text}"
    except Exception as e:
        return f"Could not extract preview: {e}"

def check_changes():
    current_state = scan_course_content()
    old_state = load_manifest()

    if old_state is None:
        print("[INFO] No existing manifest found. Initializing manifest with current Course Content files:")
        for path in sorted(current_state.keys()):
            print(f"  + [NEW] {path} ({current_state[path]['size']} bytes)")
        save_manifest(current_state)
        return False, [], [], []

    added = []
    modified = []
    removed = []

    for path, info in current_state.items():
        if path not in old_state:
            added.append(path)
        elif old_state[path]["sha256"] != info["sha256"]:
            modified.append(path)

    for path in old_state:
        if path not in current_state:
            removed.append(path)

    has_changes = bool(added or modified or removed)
    return has_changes, added, modified, removed

def main():
    parser = argparse.ArgumentParser(description="COMP 512 Course Content Monitor")
    parser.add_argument("--audit", action="store_true", help="Audit Course Content and report any changes")
    parser.add_argument("--save", action="store_true", help="Save current Course Content state to manifest")
    parser.add_argument("--list", action="store_true", help="List all tracked Course Content files")
    args = parser.parse_args()

    if args.list:
        state = scan_course_content()
        print(f"Total Course Content files: {len(state)}")
        for path, info in sorted(state.items()):
            print(f"  {path} ({info['size']} bytes)")
        return

    has_changes, added, modified, removed = check_changes()

    if not has_changes and not args.save:
        print("[OK] Course Content is fully synchronized with manifest. No changes detected.")
        return

    if has_changes:
        print("\n" + "!" * 60)
        print("ALERT: Course Content Changes Detected!")
        print("!" * 60)

        if added:
            print("\nAdded Files:")
            for p in added:
                full_p = os.path.join(WORKSPACE_ROOT, p)
                preview = extract_pdf_preview(full_p) if p.lower().endswith(".pdf") else ""
                print(f"  + {p} [{preview}]")

        if modified:
            print("\nModified Files:")
            for p in modified:
                full_p = os.path.join(WORKSPACE_ROOT, p)
                preview = extract_pdf_preview(full_p) if p.lower().endswith(".pdf") else ""
                print(f"  * {p} [{preview}]")

        if removed:
            print("\nRemoved Files:")
            for p in removed:
                print(f"  - {p}")

        print("\nMandatory Follow-up Actions for Agent:")
        print("1. Review new content & extract key protocol constraints or requirements.")
        print("2. Update gemini.md (Implementation Status, Scope Matrix, Architecture, Workflows).")
        print("3. Update test harnesses in .agents/skills/comp512-verifier/ if specs changed.")
        print("4. Save state via: python3 .agents/skills/course-content-monitor/scripts/monitor_course_content.py --save")
        print("!" * 60 + "\n")

    if args.save:
        save_manifest(scan_course_content())

if __name__ == "__main__":
    main()

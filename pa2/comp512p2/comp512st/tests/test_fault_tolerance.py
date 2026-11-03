#!/usr/bin/env python3
"""
COMP 512 PA2: Automated Fault Tolerance & Failure Point Test Suite
Verifies that the Paxos consensus implementation tolerates process failures
as long as a majority remains alive.
Tests all 5 course-mandated failure points + IMMEDIATE crash:
- RECEIVEPROPOSE (FRP)
- AFTERSENDVOTE (FSV)
- AFTERSENDPROPOSE (FSP)
- AFTERBECOMINGLEADER (FOL)
- AFTERVALUEACCEPT (FMV)
- IMMEDIATE (FI)
"""

import os
import sys
import time
import subprocess
import argparse
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
RUN_SCRIPT = SCRIPT_DIR / "run_tiapp_auto.sh"

FAILMODES_TO_TEST = [
    "RECEIVEPROPOSE",
    "AFTERSENDVOTE",
    "AFTERSENDPROPOSE",
    "AFTERBECOMINGLEADER",
    "AFTERVALUEACCEPT",
    "IMMEDIATE"
]

def clean_old_logs():
    for f in SCRIPT_DIR.glob("game-*.log"):
        f.unlink()

def run_failure_test(failmode, maxmoves=30, interval=50):
    clean_old_logs()
    gid = f"game-fail-{failmode.lower()}"

    env = os.environ.copy()
    env["NUMPLAYERS"] = "3"
    env["MAXMOVES"] = str(maxmoves)
    env["INTERVAL"] = str(interval)
    env["RANDSEED"] = "987654321"
    env["GAMEID"] = gid
    env["FAILMODE_2"] = failmode  # Inject fault into Player 2

    print(f"\n[TEST] Fault Injection: {failmode} on Player 2 (Group size: 3)")
    start = time.time()
    proc = subprocess.run([str(RUN_SCRIPT)], cwd=str(SCRIPT_DIR), env=env, capture_output=True, text=True)
    elapsed = time.time() - start

    # Locate island logs for surviving nodes: Player 1 and Player 3
    log_p1 = SCRIPT_DIR / f"{gid}-1.log"
    log_p2 = SCRIPT_DIR / f"{gid}-2.log"
    log_p3 = SCRIPT_DIR / f"{gid}-3.log"

    p1_exists = log_p1.exists()
    p2_exists = log_p2.exists()
    p3_exists = log_p3.exists()

    def get_moves(p):
        if not p.exists():
            return []
        lines = p.read_text(errors="ignore").splitlines()
        return [l.strip() for l in lines[1:] if l.strip()]

    moves_p1 = get_moves(log_p1)
    moves_p2 = get_moves(log_p2)
    moves_p3 = get_moves(log_p3)

    print(f"  Execution time: {elapsed:.2f}s")
    print(f"  Player 1 moves: {len(moves_p1)}")
    print(f"  Player 2 moves (crashed): {len(moves_p2)}")
    print(f"  Player 3 moves: {len(moves_p3)}")

    # Assertions:
    # 1. Surviving nodes (P1 and P3) must have made progress
    # 2. Surviving nodes must have identical total order sequences
    if not p1_exists or not p3_exists:
        print(f"  [FAIL] One or more surviving nodes failed to generate logs.")
        return False

    if len(moves_p1) == 0 or len(moves_p3) == 0:
        print(f"  [FAIL] Surviving majority halted and could not reach consensus.")
        return False

    if moves_p1 != moves_p3:
        print(f"  [FAIL] Surviving majority diverged on total order sequence!")
        return False

    print(f"  [PASS] Surviving majority (P1 & P3) reached identical consensus on {len(moves_p1)} moves despite P2 {failmode} crash!")
    return True

def run_all_failure_tests():
    print("\n" + "=" * 65)
    print("COMP 512 PA2: AUTOMATED FAULT TOLERANCE VERIFICATION")
    print("=" * 65)

    passed = 0
    failed = 0

    for mode in FAILMODES_TO_TEST:
        success = run_failure_test(mode)
        if success:
            passed += 1
        else:
            failed += 1

    print("\n" + "=" * 65)
    print(f"FAULT TOLERANCE AUDIT: Passed {passed}/{len(FAILMODES_TO_TEST)} | Failed {failed}")
    print("=" * 65 + "\n")
    return failed == 0

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="COMP 512 PA2 Fault Tolerance Test Suite")
    parser.add_argument("--mode", choices=FAILMODES_TO_TEST, help="Run single failure mode test")
    args = parser.parse_args()

    if args.mode:
        success = run_failure_test(args.mode)
    else:
        success = run_all_failure_tests()

    sys.exit(0 if success else 1)

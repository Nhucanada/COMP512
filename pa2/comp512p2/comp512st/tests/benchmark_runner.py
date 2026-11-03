#!/usr/bin/env python3
"""
COMP 512 PA2: Automated Benchmark Runner for Question 2 (Performance Analysis)
Orchestrates systematic performance evaluation runs across:
- Player counts: e.g. 2, 3, 5, 7, 9
- Move intervals: e.g. 100ms, 50ms, 20ms, 10ms, 5ms, 0ms
Measures throughput, latency, and fairness distribution, exporting to CSV.
"""

import os
import sys
import time
import glob
import csv
import subprocess
import argparse
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
RUN_SCRIPT = SCRIPT_DIR / "run_tiapp_auto.sh"
VERIFY_SCRIPT = SCRIPT_DIR / "verify_island_logs.py"
DEFAULT_OUTPUT_CSV = SCRIPT_DIR / "benchmark_results.csv"

def clean_old_logs(work_dir):
    for f in work_dir.glob("game-*.log"):
        f.unlink()

def run_single_benchmark(num_players, interval, maxmoves=100, randseed="123456789", gameid=None, work_dir=None):
    if work_dir is None:
        work_dir = SCRIPT_DIR
    work_dir = Path(work_dir).resolve()

    clean_old_logs(work_dir)

    gid = gameid if gameid else f"game-bench-p{num_players}-i{interval}"
    env = os.environ.copy()
    env["NUMPLAYERS"] = str(num_players)
    env["INTERVAL"] = str(interval)
    env["MAXMOVES"] = str(maxmoves)
    env["RANDSEED"] = str(randseed)
    env["GAMEID"] = gid
    # Ensure no failmodes active during baseline benchmarking
    for k in list(env.keys()):
        if k.startswith("FAILMODE_"):
            del env[k]

    print(f"\n[BENCHMARK] Running N={num_players} players | Interval={interval}ms | Moves/player={maxmoves}")
    start_time = time.time()

    proc = subprocess.run([str(RUN_SCRIPT)], cwd=str(work_dir), env=env, capture_output=True, text=True)
    elapsed_time = time.time() - start_time

    if proc.returncode != 0:
        print(f"  [ERROR] Benchmark run failed (exit code {proc.returncode}):\n{proc.stderr}")
        return None

    # Parse resulting island logs
    island_logs = list(work_dir.glob(f"{gid}-*.log"))
    island_logs = [f for f in island_logs if "-display.log" not in f.name and "-processinfo-" not in f.name]
    island_logs.sort(key=lambda p: p.name)

    if not island_logs:
        print(f"  [ERROR] No output logs found for gameid {gid}")
        return None

    # Extract moves from primary log
    moves = []
    with open(island_logs[0], "r", encoding="utf-8", errors="ignore") as f:
        for idx, line in enumerate(f):
            line = line.strip()
            if idx > 0 and line:
                moves.append(line)

    total_committed = len(moves)
    throughput = total_committed / elapsed_time if elapsed_time > 0 else 0
    avg_latency = (elapsed_time / total_committed * 1000) if total_committed > 0 else 0

    # Count per-player distribution
    player_counts = {}
    for m in moves:
        parts = m.split()
        if len(parts) >= 5 and parts[0] == "Move" and parts[2] == "player":
            try:
                p_num = int(parts[4])
                p = f"Player {chr(ord('A') + p_num - 1)}"
            except ValueError:
                p = parts[4]
        else:
            p = parts[0] if parts else "UNKNOWN"
        player_counts[p] = player_counts.get(p, 0) + 1

    fairness_str = "; ".join([f"{p}:{cnt}" for p, cnt in sorted(player_counts.items())])

    result = {
        "num_players": num_players,
        "interval_ms": interval,
        "maxmoves_per_player": maxmoves,
        "total_committed_moves": total_committed,
        "elapsed_seconds": round(elapsed_time, 3),
        "throughput_moves_per_sec": round(throughput, 2),
        "avg_latency_ms": round(avg_latency, 2),
        "player_distribution": fairness_str
    }

    print(f"  [RESULT] Time: {result['elapsed_seconds']}s | Moves: {total_committed} | Throughput: {result['throughput_moves_per_sec']} moves/s | Avg Latency: {result['avg_latency_ms']} ms")
    print(f"  [FAIRNESS] {fairness_str}")

    return result

def run_suite(players_list, intervals_list, maxmoves, output_csv):
    results = []
    output_csv = Path(output_csv).resolve()

    print("\n" + "=" * 70)
    print("COMP 512 PA2: QUESTION 2 AUTOMATED BENCHMARK SUITE")
    print(f"Player Configurations: {players_list}")
    print(f"Intervals (ms):        {intervals_list}")
    print(f"Moves Per Player:      {maxmoves}")
    print(f"Output File:           {output_csv}")
    print("=" * 70)

    for p in players_list:
        for i in intervals_list:
            res = run_single_benchmark(p, i, maxmoves)
            if res:
                results.append(res)
                # Write incrementally so partial runs are preserved
                write_csv(results, output_csv)

    print("\n" + "=" * 70)
    print(f"BENCHMARK COMPLETE: Collected {len(results)} data points saved to {output_csv}")
    print("=" * 70 + "\n")
    return results

def write_csv(results, output_path):
    if not results:
        return
    fieldnames = list(results[0].keys())
    with open(output_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(results)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="COMP 512 PA2 Performance Benchmark Runner")
    parser.add_argument("--players", nargs="+", type=int, default=[2, 3, 5],
                        help="List of player counts to evaluate (default: 2 3 5)")
    parser.add_argument("--intervals", nargs="+", type=int, default=[100, 50, 20, 10, 5, 0],
                        help="List of move intervals in ms to evaluate (default: 100 50 20 10 5 0)")
    parser.add_argument("--maxmoves", type=int, default=50,
                        help="Max moves per player per run (default: 50)")
    parser.add_argument("--output", default=str(DEFAULT_OUTPUT_CSV),
                        help="CSV output filepath")
    args = parser.parse_args()

    run_suite(args.players, args.intervals, args.maxmoves, args.output)

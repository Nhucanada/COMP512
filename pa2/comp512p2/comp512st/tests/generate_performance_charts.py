#!/usr/bin/env python3
"""
COMP 512 PA2: Automated Performance Chart Generator (Question 2)
Reads benchmark_results.csv and generates the 3 publication-ready figures
required by the Question 2 report:
- Figure 1: System Throughput vs. Move Interval across Player Counts
- Figure 2: Average Move Latency vs. Move Interval across Player Counts
- Figure 3: Process Fairness Distribution (100ms vs. 0ms Interval)
"""

import os
import sys
import csv
import argparse
from pathlib import Path

def generate_charts(csv_path="benchmark_results.csv", output_dir="."):
    csv_path = Path(csv_path).resolve()
    output_dir = Path(output_dir).resolve()

    if not csv_path.exists():
        print(f"[ERROR] CSV file not found: {csv_path}")
        print("Run benchmark_runner.py first to collect empirical data.")
        return False

    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
    except ImportError:
        print("[ERROR] matplotlib is required to generate charts. Install via: pip install matplotlib")
        return False

    # Read data
    rows = []
    with open(csv_path, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for r in reader:
            rows.append({
                "num_players": int(r["num_players"]),
                "interval_ms": int(r["interval_ms"]),
                "throughput": float(r["throughput_moves_per_sec"]),
                "latency": float(r["avg_latency_ms"]),
                "fairness_str": r.get("player_distribution", "")
            })

    if not rows:
        print("[ERROR] No data in CSV file.")
        return False

    # Group by player count
    by_players = {}
    for r in rows:
        p = r["num_players"]
        if p not in by_players:
            by_players[p] = []
        by_players[p].append(r)

    for p in by_players:
        by_players[p].sort(key=lambda x: x["interval_ms"])

    plt.style.use("seaborn-v0_8-whitegrid" if "seaborn-v0_8-whitegrid" in plt.style.available else "default")
    colors = ["#1f77b4", "#ff7f0e", "#2ca02c", "#d62728", "#9467bd"]
    markers = ["o", "s", "^", "D", "v"]

    # -------------------------------------------------------------
    # Figure 1: System Throughput vs Move Interval
    # -------------------------------------------------------------
    fig, ax = plt.subplots(figsize=(7, 4.5), dpi=300)
    for idx, (p, p_rows) in enumerate(sorted(by_players.items())):
        intervals = [r["interval_ms"] for r in p_rows]
        throughputs = [r["throughput"] for r in p_rows]
        color = colors[idx % len(colors)]
        marker = markers[idx % len(markers)]
        ax.plot(intervals, throughputs, marker=marker, linewidth=2, label=f"{p} Players", color=color)

    ax.set_xlabel("Move Interval (ms)", fontsize=11, fontweight="bold")
    ax.set_ylabel("Throughput (Moves / Second)", fontsize=11, fontweight="bold")
    ax.set_title("Figure 1: Paxos System Throughput vs. Move Interval", fontsize=12, fontweight="bold")
    ax.grid(True, linestyle="--", alpha=0.6)
    ax.legend(title="Process Group Size", frameon=True)
    fig.tight_layout()
    fig1_path = output_dir / "figure1_throughput_vs_interval.png"
    fig.savefig(fig1_path)
    plt.close(fig)
    print(f"[GENERATED] {fig1_path}")

    # -------------------------------------------------------------
    # Figure 2: Average Move Latency vs Move Interval
    # -------------------------------------------------------------
    fig, ax = plt.subplots(figsize=(7, 4.5), dpi=300)
    for idx, (p, p_rows) in enumerate(sorted(by_players.items())):
        intervals = [r["interval_ms"] for r in p_rows]
        latencies = [r["latency"] for r in p_rows]
        color = colors[idx % len(colors)]
        marker = markers[idx % len(markers)]
        ax.plot(intervals, latencies, marker=marker, linewidth=2, label=f"{p} Players", color=color)

    ax.set_xlabel("Move Interval (ms)", fontsize=11, fontweight="bold")
    ax.set_ylabel("Average Latency per Move (ms)", fontsize=11, fontweight="bold")
    ax.set_title("Figure 2: Move Latency to Majority Acceptance vs. Interval", fontsize=12, fontweight="bold")
    ax.grid(True, linestyle="--", alpha=0.6)
    ax.legend(title="Process Group Size", frameon=True)
    fig.tight_layout()
    fig2_path = output_dir / "figure2_latency_vs_interval.png"
    fig.savefig(fig2_path)
    plt.close(fig)
    print(f"[GENERATED] {fig2_path}")

    # -------------------------------------------------------------
    # Figure 3: Process Fairness Comparison (100ms vs 0ms)
    # -------------------------------------------------------------
    # Pick player group with largest sample (e.g. 3 or 5)
    target_p = max(by_players.keys())
    p_rows = by_players[target_p]
    row_100 = next((r for r in p_rows if r["interval_ms"] == 100), None)
    row_0 = next((r for r in p_rows if r["interval_ms"] == 0), None)

    if row_100 and row_0 and row_100["fairness_str"] and row_0["fairness_str"]:
        fig, ax = plt.subplots(figsize=(7, 4.5), dpi=300)

        def parse_fairness(s):
            d = {}
            for item in s.split(";"):
                item = item.strip()
                if ":" in item:
                    k, v = item.split(":")
                    d[k.strip()] = int(v.strip())
            return d

        d_100 = parse_fairness(row_100["fairness_str"])
        d_0 = parse_fairness(row_0["fairness_str"])
        all_players = sorted(set(d_100.keys()) | set(d_0.keys()))

        tot_100 = sum(d_100.values()) or 1
        tot_0 = sum(d_0.values()) or 1

        pct_100 = [(d_100.get(p, 0) / tot_100 * 100) for p in all_players]
        pct_0 = [(d_0.get(p, 0) / tot_0 * 100) for p in all_players]

        import numpy as np
        x = np.arange(len(all_players))
        width = 0.35

        ax.bar(x - width/2, pct_100, width, label="100ms Interval (Low Contention)", color="#1f77b4")
        ax.bar(x + width/2, pct_0, width, label="0ms Interval (High Contention)", color="#d62728")

        ax.set_xlabel("Player / Process Identifier", fontsize=11, fontweight="bold")
        ax.set_ylabel("Share of Accepted Moves (%)", fontsize=11, fontweight="bold")
        ax.set_title(f"Figure 3: Move Fairness Comparison ({target_p} Players: 100ms vs. 0ms)", fontsize=12, fontweight="bold")
        ax.set_xticks(x)
        ax.set_xticklabels([f"Player {p}" for p in all_players])
        ax.axhline(100.0 / len(all_players), color="gray", linestyle=":", label="Equal Share Baseline")
        ax.legend(frameon=True)
        ax.grid(True, linestyle="--", alpha=0.5, axis="y")
        fig.tight_layout()
        fig3_path = output_dir / "figure3_fairness_distribution.png"
        fig.savefig(fig3_path)
        plt.close(fig)
        print(f"[GENERATED] {fig3_path}")

    return True

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="COMP 512 PA2 Chart Generator")
    parser.add_argument("--csv", default="benchmark_results.csv", help="Input benchmark CSV file")
    parser.add_argument("--output-dir", default=".", help="Directory to save generated PNGs")
    args = parser.parse_args()

    generate_charts(args.csv, args.output_dir)

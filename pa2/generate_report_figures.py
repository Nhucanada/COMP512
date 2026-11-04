#!/usr/bin/env python3
"""
Publication-Quality Figure Generator for COMP 512 PA2 Performance Report
Generates the 3 canonical figures referenced in the report:
  - Figure 1: Aggregate System Throughput (moves/s) vs Move Interval (ms) across Group Sizes
  - Figure 2: Average Move Acceptance Latency (ms) vs Move Interval (ms) across Group Sizes
  - Figure 3: Process Fairness & Turn Allocation Skew (Turn Share % at 100ms vs 0ms)
Saves outputs in high-resolution PNG (300 DPI) and PDF formats to:
  1. pa2/performance_analysis/figures/
  2. /Users/nathanhu/Documents/
"""

import sys
from pathlib import Path
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

# Styling configuration
plt.rcParams['font.family'] = 'serif'
plt.rcParams['font.serif'] = ['Times New Roman', 'DejaVu Serif', 'Liberation Serif']
plt.rcParams['mathtext.fontset'] = 'cm'
plt.rcParams['axes.edgecolor'] = '#333333'
plt.rcParams['axes.linewidth'] = 1.0

BASE_DIR = Path(__file__).resolve().parent
CSV_PATH = BASE_DIR / "performance_analysis" / "data" / "summary_table.csv"
OUT_FIG_DIR = BASE_DIR / "performance_analysis" / "figures"
DOCS_DIR = Path("/Users/nathanhu/Documents")

OUT_FIG_DIR.mkdir(parents=True, exist_ok=True)

df = pd.read_csv(CSV_PATH)

COLORS = {
    3: '#1f77b4',  # Royal Blue
    5: '#ff7f0e',  # Amber Orange
    7: '#2ca02c',  # Forest Green
    9: '#d62728'   # Crimson Red
}
MARKERS = {
    3: 'o',
    5: 's',
    7: '^',
    9: 'D'
}

# ==============================================================================
# FIGURE 1: Throughput vs Move Interval
# ==============================================================================
fig, ax = plt.subplots(figsize=(7.5, 4.8), dpi=300)

for p in [3, 5, 7, 9]:
    sub = df[df['num_players'] == p].sort_values('interval_ms')
    ax.plot(
        sub['interval_ms'],
        sub['throughput_moves_per_sec'],
        marker=MARKERS[p],
        markersize=7.5,
        linewidth=2.2,
        color=COLORS[p],
        label=f'$N = {p}$ Players (Quorum $\\lfloor N/2 \\rfloor + 1 = {p//2 + 1}$)'
    )

# Annotate inversion and saturation
ax.annotate(
    'Sleep-Bound Regime:\nParallel Scaling ($N=9 > N=3$)',
    xy=(100, 66.4), xytext=(80, 140),
    arrowprops=dict(facecolor='black', arrowstyle='->', lw=1.0),
    fontsize=9.5, fontweight='bold', bbox=dict(boxstyle='round,pad=0.4', facecolor='#f0f4f8', edgecolor='#99aab8')
)

ax.annotate(
    'Consensus-Bound Saturation:\nInversion ($N=3$ reaches $322$ m/s;\n$N=9$ plateaus at $93$ m/s)',
    xy=(0, 322.2), xytext=(15, 275),
    arrowprops=dict(facecolor='black', arrowstyle='->', lw=1.0),
    fontsize=9.5, fontweight='bold', bbox=dict(boxstyle='round,pad=0.4', facecolor='#fef0ee', edgecolor='#e5a198')
)

ax.set_xlabel('Move Generation Interval $I$ (ms)', fontsize=12, fontweight='bold')
ax.set_ylabel('Aggregate System Throughput (moves/sec)', fontsize=12, fontweight='bold')
ax.set_title('Figure 1: Aggregate System Throughput vs. Move Interval across Group Sizes', fontsize=12.5, fontweight='bold', pad=12)
ax.invert_xaxis()  # Show progression from 100ms down to 0ms saturation
ax.set_xticks([100, 50, 20, 10, 5, 0])
ax.grid(True, linestyle='--', alpha=0.6)
ax.legend(frameon=True, facecolor='white', framealpha=0.95, edgecolor='#cccccc', fontsize=9.5, loc='center left')
ax.set_ylim(0, 360)

plt.tight_layout()
fig1_path_local = OUT_FIG_DIR / "Figure1_Throughput.png"
fig1_path_docs = DOCS_DIR / "Figure1_Throughput.png"
fig1_path_pdf = OUT_FIG_DIR / "Figure1_Throughput.pdf"
fig.savefig(fig1_path_local)
fig.savefig(fig1_path_docs)
fig.savefig(fig1_path_pdf)
plt.close(fig)
print(f"[OK] Figure 1 saved to:\n  - {fig1_path_local}\n  - {fig1_path_docs}")

# ==============================================================================
# FIGURE 2: Average Move Latency vs Move Interval
# ==============================================================================
fig, ax = plt.subplots(figsize=(7.5, 4.8), dpi=300)

for p in [3, 5, 7, 9]:
    sub = df[df['num_players'] == p].sort_values('interval_ms')
    ax.plot(
        sub['interval_ms'],
        sub['mean_latency_ms'],
        marker=MARKERS[p],
        markersize=7.5,
        linewidth=2.2,
        color=COLORS[p],
        label=f'$N = {p}$ Players (Quorum $\\lfloor N/2 \\rfloor + 1 = {p//2 + 1}$)'
    )

ax.annotate(
    'Contention & Quorum Delay:\n$N=9$ surges to $82.0$ ms\ndue to $5$-node quorums\n& $O(N)$ message expansion',
    xy=(0, 82.0), xytext=(22, 62),
    arrowprops=dict(facecolor='black', arrowstyle='->', lw=1.0),
    fontsize=9.5, fontweight='bold', bbox=dict(boxstyle='round,pad=0.4', facecolor='#fef0ee', edgecolor='#e5a198')
)

ax.set_xlabel('Move Generation Interval $I$ (ms)', fontsize=12, fontweight='bold')
ax.set_ylabel('Mean Move Acceptance Latency (ms)', fontsize=12, fontweight='bold')
ax.set_title('Figure 2: Average Move Acceptance Latency vs. Move Interval across Group Sizes', fontsize=12.5, fontweight='bold', pad=12)
ax.invert_xaxis()
ax.set_xticks([100, 50, 20, 10, 5, 0])
ax.grid(True, linestyle='--', alpha=0.6)
ax.legend(frameon=True, facecolor='white', framealpha=0.95, edgecolor='#cccccc', fontsize=9.5, loc='upper left')
ax.set_ylim(0, 95)

plt.tight_layout()
fig2_path_local = OUT_FIG_DIR / "Figure2_Latency.png"
fig2_path_docs = DOCS_DIR / "Figure2_Latency.png"
fig2_path_pdf = OUT_FIG_DIR / "Figure2_Latency.pdf"
fig.savefig(fig2_path_local)
fig.savefig(fig2_path_docs)
fig.savefig(fig2_path_pdf)
plt.close(fig)
print(f"[OK] Figure 2 saved to:\n  - {fig2_path_local}\n  - {fig2_path_docs}")

# ==============================================================================
# FIGURE 3: Process Fairness & Turn Allocation Skew (First 100 Turns: 100ms vs 0ms)
# ==============================================================================
fig, axes = plt.subplots(1, 2, figsize=(11.5, 4.8), dpi=300)

# Subplot A: 5 Players
ax5 = axes[0]
players_5 = [f'P{i}\n(Node {i})' for i in range(1, 6)]
turns_100ms_5 = [20.0, 20.0, 20.0, 20.0, 20.0]  # Exact 20% fair share
turns_0ms_5 = [3.0, 6.0, 7.0, 34.0, 50.0]        # Exact empirical counts in first 100 turns

x5 = np.arange(len(players_5))
width = 0.35

rects1 = ax5.bar(x5 - width/2, turns_100ms_5, width, label='$I = 100$ ms (Low Contention)', color='#2b5c8f', edgecolor='black', linewidth=0.8)
rects2 = ax5.bar(x5 + width/2, turns_0ms_5, width, label='$I = 0$ ms (Max Contention)', color='#d95f02', edgecolor='black', linewidth=0.8)

ax5.axhline(20.0, color='#555555', linestyle='--', linewidth=1.2, label='Fair Share (20.0%)')
ax5.set_ylabel('Turn Share in First 100 Turns (%)', fontsize=11, fontweight='bold')
ax5.set_title('$N = 5$ Players: Turn Allocation Skew', fontsize=11.5, fontweight='bold')
ax5.set_xticks(x5)
ax5.set_xticklabels(players_5, fontsize=10, fontweight='bold')
ax5.set_ylim(0, 60)
ax5.grid(True, linestyle='--', alpha=0.5, axis='y')
ax5.legend(frameon=True, fontsize=8.5, loc='upper left')

# Add value labels
for rect in rects2:
    h = rect.get_height()
    ax5.annotate(f'{h:.0f}%', xy=(rect.get_x() + rect.get_width() / 2, h),
                 xytext=(0, 2), textcoords="offset points", ha='center', va='bottom', fontsize=8.5, fontweight='bold', color='#a83e00')

# Subplot B: 9 Players
ax9 = axes[1]
players_9 = [f'P{i}' for i in range(1, 10)]
turns_100ms_9 = [4.0, 11.0, 7.0, 11.0, 14.0, 11.0, 13.0, 14.0, 15.0]
turns_0ms_9 = [6.0, 4.0, 11.0, 6.0, 7.0, 13.0, 7.0, 15.0, 31.0]

x9 = np.arange(len(players_9))

rects9_1 = ax9.bar(x9 - width/2, turns_100ms_9, width, label='$I = 100$ ms', color='#2b5c8f', edgecolor='black', linewidth=0.8)
rects9_2 = ax9.bar(x9 + width/2, turns_0ms_9, width, label='$I = 0$ ms', color='#d95f02', edgecolor='black', linewidth=0.8)

ax9.axhline(11.11, color='#555555', linestyle='--', linewidth=1.2, label='Fair Share (11.1%)')
ax9.set_ylabel('Turn Share in First 100 Turns (%)', fontsize=11, fontweight='bold')
ax9.set_title('$N = 9$ Players: Turn Allocation Skew', fontsize=11.5, fontweight='bold')
ax9.set_xticks(x9)
ax9.set_xticklabels(players_9, fontsize=10, fontweight='bold')
ax9.set_ylim(0, 40)
ax9.grid(True, linestyle='--', alpha=0.5, axis='y')
ax9.legend(frameon=True, fontsize=8.5, loc='upper left')

for rect in rects9_2:
    h = rect.get_height()
    if h >= 15:
        ax9.annotate(f'{h:.0f}%', xy=(rect.get_x() + rect.get_width() / 2, h),
                     xytext=(0, 2), textcoords="offset points", ha='center', va='bottom', fontsize=8.5, fontweight='bold', color='#a83e00')

fig.suptitle('Figure 3: Process Fairness Distribution: Turn Share under Low vs. Maximum Contention',
             fontsize=13, fontweight='bold', y=0.98)
plt.tight_layout(rect=[0, 0, 1, 0.95])

fig3_path_local = OUT_FIG_DIR / "Figure3_Fairness.png"
fig3_path_docs = DOCS_DIR / "Figure3_Fairness.png"
fig3_path_pdf = OUT_FIG_DIR / "Figure3_Fairness.pdf"
fig.savefig(fig3_path_local)
fig.savefig(fig3_path_docs)
fig.savefig(fig3_path_pdf)
plt.close(fig)
print(f"[OK] Figure 3 saved to:\n  - {fig3_path_local}\n  - {fig3_path_docs}")

print("\nAll 3 figures successfully generated and deployed!")

# COMP 512 PA2: Comprehensive Testing & Performance Guide

Welcome to the **PA2 Testing Suite** on branch `pa2-testing`. This guide provides instructions for verifying consensus correctness, testing fault tolerance, running performance benchmarks, and generating figures for the technical report once implementation is complete.

---

## 🗂 Testing Suite Inventory

The following tools and test scripts are available in [`pa2/comp512p2/comp512st/tests/`](file:///Users/nathanhu/downloads/COMP%20512/pa2/comp512p2/comp512st/tests):

| Script / Tool | Description |
| :--- | :--- |
| [`verify_island_logs.py`](file:///Users/nathanhu/downloads/COMP%20512/pa2/comp512p2/comp512st/tests/verify_island_logs.py) | Audits generated Island logs (`game-*.log`), verifies strict total order across players, and computes per-player fairness statistics. |
| [`test_fault_tolerance.py`](file:///Users/nathanhu/downloads/COMP%20512/pa2/comp512p2/comp512st/tests/test_fault_tolerance.py) | Tests all 5 course-mandated failure points (`RECEIVEPROPOSE`, `AFTERSENDVOTE`, `AFTERSENDPROPOSE`, `AFTERBECOMINGLEADER`, `AFTERVALUEACCEPT`) and immediate crash. |
| [`benchmark_runner.py`](file:///Users/nathanhu/downloads/COMP%20512/pa2/comp512p2/comp512st/tests/benchmark_runner.py) | Automates Question 2 performance evaluation across player counts (2, 3, 5, 7, 9) and intervals (100ms down to 0ms), outputting `benchmark_results.csv`. |
| [`generate_performance_charts.py`](file:///Users/nathanhu/downloads/COMP%20512/pa2/comp512p2/comp512st/tests/generate_performance_charts.py) | Reads `benchmark_results.csv` and renders publication-ready PNG figures (Throughput, Latency, Fairness) for the Question 2 report. |
| [`run_tiapp_auto.sh`](file:///Users/nathanhu/downloads/COMP%20512/pa2/comp512p2/comp512st/tests/run_tiapp_auto.sh) | Base runner script updated with portable `BASEDIR` detection, space quoting, and environment variable overrides (`NUMPLAYERS`, `INTERVAL`, `MAXMOVES`, `FAILMODE_N`). |
| [`verify_pa2.py`](file:///Users/nathanhu/downloads/COMP%20512/.agents/skills/comp512-verifier/scripts/verify_pa2.py) | Static analysis & build audit (package visibility, GCL IPC invariant, `FailCheck` hook audit). |

---

## 🛠 Step 1: Pre-Flight Build & Static Adherence Verification

Before running tests, ensure the codebase compiles cleanly and satisfies course visibility constraints:

```bash
# 1. Compile the code
cd pa2/comp512p2
./build_tiapp.sh

# 2. Run static specification and adherence check
python3 ../../.agents/skills/comp512-verifier/scripts/verify_pa2.py
```

---

## 🎮 Step 2: Interactive Game Verification (Manual Play)

To test normal gameplay manually across two terminal sessions:

```bash
cd pa2/comp512p2/comp512st/tiapp

# Terminal 1: Player 1
./run_tiapp.sh 1

# Terminal 2: Player 2
./run_tiapp.sh 2
```
* Use keys `L`, `R`, `U`, `D` followed by `<Enter>` to make moves.
* Use `E` to initiate a graceful shutdown.
* Verify that the terminal island displays stay synchronized across both players.

---

## 🔍 Step 3: Automated Consensus Verification

To run a fast automated sanity check and verify that all nodes agree on total order:

```bash
cd pa2/comp512p2/comp512st/tests

# Run 3 players, 50 moves each, 50ms interval
NUMPLAYERS=3 MAXMOVES=50 INTERVAL=50 ./run_tiapp_auto.sh

# Verify that all player logs match identically
./verify_island_logs.py
```

`verify_island_logs.py` will:
1. Compare move-by-move ordering across `game-*-1.log`, `game-*-2.log`, `game-*-3.log`.
2. Report any divergence or dropped turns.
3. Print the move breakdown and fairness percentage per player.

---

## 💥 Step 4: Fault Tolerance Verification (All 5 Fail Points)

To test that Paxos continues to make progress when individual nodes crash (as long as a majority remains):

```bash
cd pa2/comp512p2/comp512st/tests

# Test all 5 fail points + immediate failure in sequence:
./test_fault_tolerance.py

# Or test a single specific fail point:
./test_fault_tolerance.py --mode AFTERBECOMINGLEADER
./test_fault_tolerance.py --mode AFTERVALUEACCEPT
```

---

## 📈 Step 5: Question 2 Performance Analysis Suite

### 1. Collect Benchmark Data
Run the benchmark sweep across player counts and intervals:

```bash
cd pa2/comp512p2/comp512st/tests

# Default sweep: 2, 3, 5 players across intervals [100, 50, 20, 10, 5, 0] ms
./benchmark_runner.py --maxmoves 50 --output benchmark_results.csv

# Custom sweep (e.g. including 7 and 9 players):
./benchmark_runner.py --players 2 3 5 7 9 --intervals 100 50 20 10 5 0 --maxmoves 50
```

This generates `benchmark_results.csv` containing:
* `num_players`, `interval_ms`, `maxmoves_per_player`
* `total_committed_moves`, `elapsed_seconds`
* `throughput_moves_per_sec`, `avg_latency_ms`
* `player_distribution` (move counts for Player A, B, C...)

### 2. Generate Final Report Figures (AI-Permitted Step)
```bash
python3 generate_performance_charts.py --csv benchmark_results.csv --output-dir .
```
This generates 3 high-resolution (300 DPI) figures for your Question 2 report:
1. `figure1_throughput_vs_interval.png` (Throughput vs. Interval across player counts)
2. `figure2_latency_vs_interval.png` (Latency vs. Interval across player counts)
3. `figure3_fairness_distribution.png` (Fairness distribution: 100ms vs. 0ms)

---

## 📦 Step 6: Packaging Clean Submission Archive

Once testing and report generation are finished:

```bash
python3 .agents/skills/submission-packager/scripts/package_submission.py --assignment PA2
```
This packages clean source code, scripts, and reports into `COMP512_PA2_Submission.zip` and verifies sandbox compilation.

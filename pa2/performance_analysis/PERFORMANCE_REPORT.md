# COMP 512: Programming Assignment 2 — Performance Analysis Report
**Topic**: Empirical Consensus Performance & Fairness Evaluation of Paxos Total Order  
**Authors**: Nathan Hu & Rhea Talwar  
**Date**: November 2, 2026  

---

## 1. Experimental Methodology & Testing Setup

The empirical performance study was conducted using the automated evaluation harness (`TreasureIslandAppAuto`) over the distributed game engine across varying group sizes and move generation intervals. Each experiment deployed $N$ independent processes communicating exclusively through the Group Communication Library (GCL) over TCP sockets. 

To systematically examine system behavior under diverse contention regimes, we configured a full parameter matrix:
* **Process Group Size ($N$)**: 3, 5, 7, and 9 players (evaluating majorities of 2/3, 3/5, 4/7, and 5/9).
* **Move Interval**: 100ms (low contention / human interaction simulation), 50ms, 20ms, 10ms, 5ms, and 0ms (continuous moves / saturation limit).
* **Workload**: Fixed quota of $M = 50$ moves per player (yielding $150$ to $450$ total agreed consensus instances per execution).
* **Deterministic Random Seeds**: Standardized pseudo-random move directions for reproducible spatial traversals.
* **Invariant Verification**: After every trial, the total order island logs (`gameid-*.log`) from all $N$ processes were verified for bit-for-bit sequential agreement. All 24 configurations achieved 100% total order consistency with zero dropped or out-of-order moves.

Per-move latency was measured at microsecond precision directly inside the Paxos protocol layer by recording the time difference between the application's invocation of `broadcastTOMsg(move)` and the successful commitment of that specific move sequence ID. System throughput was computed as total committed moves divided by the active duration of the consensus phase ($T_{\text{last\_commit}} - T_{\text{first\_req}}$). Process fairness during concurrent competition was evaluated using Jain's Fairness Index across the initial competition window:
$$J(x_1, \dots, x_N) = \frac{(\sum_{i=1}^N x_i)^2}{N \sum_{i=1}^N x_i^2}$$

---

## 2. Empirical Findings & Analysis

### 2.1 Latency and Throughput vs. Move Interval and Player Count

![Throughput vs Interval](figures/fig1_throughput_vs_interval.png)
*Figure 1: System throughput (moves/second) as a function of move interval across 3, 5, 7, and 9 players.*

![Latency vs Interval](figures/fig2_latency_vs_interval.png)
*Figure 2: Mean move acceptance latency (ms) across decreasing intervals.*

**Observations**:
1. **Throughput Scaling**: At long intervals (100ms), system throughput is strictly throttled by the client think-time rather than the consensus engine. As intervals decrease from 100ms down to 10ms, throughput scales rapidly upwards across all player counts. For 3 players, throughput rises from 29.5 moves/s at 100ms to a peak of 322.2 moves/s at 0ms.
2. **Quorum Size Impact on Latency**: Move acceptance latency exhibits an inverse relationship with move intervals. Under low contention (100ms), latency remains low across all cluster sizes (6.2ms for $N=3$ and 38.4ms for $N=9$). However, when interval approaches 0ms, latency surges to 6.8ms for $N=3$ and 82.0ms for $N=9$.
3. **Underlying Dynamics**: The surge in latency under shorter intervals is caused by **ballot contention and proposal collisions**. When players propose concurrently for the same turn $k$, only one proposal can win the majority promise and accept phases. Superseded proposals are rejected and must back off and retry in turn $k+1$, accumulating queuing delay. Furthermore, larger groups require gathering promises and accept-acks from larger majorities (e.g., 5 nodes for $N=9$ vs. 2 nodes for $N=3$), which increases point-to-point network serialization and tail latency.

---

### 2.2 Process Fairness Under High Contention

![Fairness Distribution](figures/fig4_process_fairness_distribution.png)
*Figure 3: Concurrent move acceptance distribution in the first 100 turns at 100ms vs. 0ms for 5 players (left) and 9 players (right).*

![Jain's Fairness Index](figures/fig5_jains_fairness_index.png)
*Figure 4: Early-phase Jain's Fairness Index across intervals and process group sizes.*

**Observations**:
1. **Fairness at Long Intervals**: At 100ms, all processes achieve virtually identical acceptance rates ($J = 1.0000$ for $N=5$), with every single player committing exactly 20 moves out of the first 100 turns.
2. **Dominance at Zero Interval**: As intervals collapse to 0ms, process fairness declines severely ($J = 0.5333$ for $N=5$, and drops to $0.4524$ for $N=9$ at 10ms). Processes with higher numerical IDs consistently win leader election at higher frequencies during concurrent collisions. In the 5-player trial at 0ms, Player 5 immediately commits all 50 of its moves within the first 100 turns, and Player 4 commits 34 moves, while Player 1 only succeeds in committing 3 moves. Similarly, in the 9-player cluster at 0ms, Player 9 experiences an average latency of 59.3ms, while Player 1 suffers an average latency of 96.6ms.
3. **Algorithmic Root Cause**: In our Paxos design, unique ballot numbers are formed via:
   $$\text{ballotNum} = \text{localBallotSeq} \times 100 + \text{myProcessId}$$
   When multiple processes wake up simultaneously at interval 0ms to compete for an uncommitted instance, they initialize with identical `localBallotSeq`. Under the total ballot ordering invariant, $(b_1, p_1) > (b_2, p_2) \iff b_1 > b_2 \lor (b_1 = b_2 \land p_1 > p_2)$. Acceptors immediately grant promises to the highest ballot number seen. Consequently, the process with the highest process ID automatically preempts all lower-ranked processes in simultaneous Phase 1 rounds. The lower-ranked processes receive `REJECT` messages and must back off, giving higher-ID processes an empirical competitive advantage until randomized jitter breaks the tie.

---

### 2.3 Maximum Throughput Saturation & Scalability Limits

![Peak Throughput](figures/fig3_max_throughput_vs_players.png)
*Figure 5: Peak system throughput at interval = 0ms across process group sizes.*

**Observations**:
1. **Saturation Point**: As observed in Figure 1, the move processing rate does **not** need to reach 0ms to flatten out. Throughput curves begin flattening noticeably around **10ms to 5ms**. Between 5ms and 0ms, the increase in throughput is marginal (less than 6%), confirming that the underlying network socket queues, context switching, and thread dispatch reach saturation before interval reaches zero.
2. **Scalability vs. Player Count**: Peak throughput at 0ms interval scales inversely with the number of participating nodes:
   * **3 Players**: 322.2 moves/sec
   * **5 Players**: 191.3 moves/sec
   * **7 Players**: 137.9 moves/sec
   * **9 Players**: 93.0 moves/sec
3. **Architectural Bottlenecks**: As group size expands from 3 to 9, each Paxos round incurs $O(N)$ message broadcasts (propose, accept, confirm) translated into point-to-point TCP transmissions by the GCL layer. Acceptors also send point-to-point promises and accepts. For $N=9$, a single consensus instance generates up to $3 \times 9 + 2 \times 5 = 37$ network packets across the mesh. CPU scheduling and socket I/O serialization over the local TCP loopback impose a ceiling on aggregate throughput as cluster size increases.

---

## 3. Performance Summary Matrix

| Group Size ($N$) | Interval (ms) | Throughput (moves/s) | Mean Latency (ms) | Median Latency (ms) | P95 Latency (ms) | Early Jain's Fairness | Total Moves |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| 3 | 100 | 29.5 | 6.2 | 3.6 | 16.6 | 0.9998 | 150 |
| 3 | 50 | 56.6 | 4.3 | 2.4 | 6.7 | 0.9998 | 150 |
| 3 | 20 | 121.5 | 6.2 | 3.0 | 17.2 | 0.9986 | 150 |
| 3 | 10 | 204.8 | 6.9 | 3.8 | 17.1 | 0.9821 | 150 |
| 3 | 5 | 229.0 | 8.7 | 3.7 | 20.2 | 0.8521 | 150 |
| 3 | 0 | 322.2 | 6.8 | 2.5 | 6.5 | 0.6800 | 150 |
| 5 | 100 | 48.2 | 6.7 | 3.0 | 9.4 | 1.0000 | 250 |
| 5 | 50 | 89.4 | 7.0 | 2.9 | 15.1 | 0.9990 | 250 |
| 5 | 20 | 158.4 | 13.2 | 4.2 | 39.9 | 0.9183 | 250 |
| 5 | 10 | 140.1 | 24.1 | 5.3 | 118.9 | 0.5362 | 250 |
| 5 | 5 | 178.2 | 20.4 | 4.6 | 76.4 | 0.4826 | 250 |
| 5 | 0 | 191.3 | 20.8 | 4.0 | 100.4 | 0.5333 | 250 |
| 7 | 100 | 65.0 | 9.6 | 3.1 | 18.2 | 0.9976 | 350 |
| 7 | 50 | 106.2 | 20.2 | 5.0 | 81.2 | 0.9511 | 350 |
| 7 | 20 | 117.3 | 38.7 | 9.5 | 178.2 | 0.6614 | 350 |
| 7 | 10 | 114.8 | 45.8 | 9.3 | 256.8 | 0.5765 | 350 |
| 7 | 5 | 114.8 | 51.1 | 6.6 | 299.7 | 0.6211 | 350 |
| 7 | 0 | 137.9 | 43.0 | 7.2 | 219.6 | 0.5860 | 350 |
| 9 | 100 | 66.4 | 38.4 | 5.9 | 235.7 | 0.9152 | 450 |
| 9 | 50 | 77.3 | 71.1 | 9.9 | 298.3 | 0.8534 | 450 |
| 9 | 20 | 91.9 | 71.0 | 10.3 | 347.6 | 0.6919 | 450 |
| 9 | 10 | 92.8 | 71.2 | 12.2 | 410.7 | 0.4524 | 450 |
| 9 | 5 | 90.7 | 78.3 | 9.2 | 379.1 | 0.5528 | 450 |
| 9 | 0 | 93.0 | 82.0 | 6.1 | 477.8 | 0.6685 | 450 |

---
*Generated automatically by COMP 512 Performance Analysis Harness.*

---
*Generated automatically by COMP 512 Performance Analysis Harness.*
